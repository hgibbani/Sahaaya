package com.sahaaya.domain.usecase.monitoring

import com.sahaaya.core.demo.FeatureScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.model.SafeZone
import com.sahaaya.domain.model.SafeZoneStatus
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.LocationRepository
import com.sahaaya.domain.repository.ProfileRepository
import com.sahaaya.domain.repository.SettingsRepository
import com.sahaaya.domain.usecase.event.RaiseEventUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.abs

/**
 * Watches the patient's position against the safe zone their caregiver set.
 *
 * Runs inside the monitoring foreground service, which is what lets it keep
 * working while the patient is not looking at the app - the only situation that
 * matters, since somebody who has wandered off is not holding their phone.
 *
 * The loop is deliberately dull:
 *
 * 1. take a fix,
 * 2. ask [SafeZoneEvaluator] what it means,
 * 3. write the position and status to `patients/{uid}` so the caregiver's card
 *    updates,
 * 4. raise an event **only** on a confirmed inside -> outside crossing.
 *
 * Step 4 is the whole reason the evaluator exists. Writing a position on every
 * fix is cheap and silent; waking a caregiver is not, so that happens once per
 * crossing and never again while the patient stays outside.
 */
class TrackSafeZoneUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val locationRepository: LocationRepository,
    private val raiseEvent: RaiseEventUseCase,
) {

    private val evaluator = SafeZoneEvaluator()

    /**
     * Collects fixes until the caller cancels. Never returns normally.
     *
     * @param zone the safe zone to watch. Re-invoked by the service whenever the
     *   caregiver changes it, so this does not need to observe settings itself.
     */
    suspend operator fun invoke(zone: SafeZone) {
        val patientId = authRepository.currentUserId() ?: return

        // Pick up where the last process left off, so an app restart while the
        // patient is already outside does not raise a second "they have left"
        // alert for a crossing that was reported hours ago.
        //
        // Time-boxed, and deliberately so. This read is a nicety - it only
        // avoids a duplicate alert - whereas the tracking below is the whole
        // feature. Waiting on it unbounded made a slow or offline Firestore
        // listener stop location tracking from ever starting: the service ran,
        // the notification said monitoring was on, and `dumpsys location`
        // showed ProviderRequest[OFF] because requestLocationUpdates was never
        // reached. Losing the restore costs at most one extra alert; losing the
        // tracking costs the feature.
        val restored = withTimeoutOrNull(RESTORE_TIMEOUT_MILLIS) {
            runCatching {
                profileRepository.observePatientLocation(patientId).first()?.status
            }.getOrNull()
        }
        if (restored != null && restored != SafeZoneStatus.UNKNOWN) {
            evaluator.restore(restored)
        }

        val updates = locationRepository.locationUpdates(
            FeatureScope.LOCATION_INTERVAL_MILLIS,
        )

        updates.collect { fix ->
            val decision = evaluator.evaluate(fix, zone)

            // An unusable fix is not written at all. Overwriting a good position
            // with a vague one would make the caregiver's card worse, and the
            // status has not changed anyway.
            if (!decision.accepted) return@collect

            profileRepository.updatePatientLocation(
                patientId = patientId,
                location = PatientLocation(
                    point = fix,
                    recordedAtEpochMillis = System.currentTimeMillis(),
                    status = decision.status,
                    metresFromBoundary = decision.metresFromBoundary,
                ),
            )

            if (decision.raiseExitAlert) {
                val beyond = decision.metresFromBoundary?.let { abs(it) }
                raiseEvent(
                    RaiseEventUseCase.Params(
                        type = EventType.GEOFENCE_EXIT,
                        summary = if (beyond != null && beyond > 0) {
                            "Left the ${zone.label} zone - about $beyond m beyond " +
                                "the boundary"
                        } else {
                            "Left the ${zone.label} zone"
                        },
                        details = buildMap {
                            put("Safe zone", zone.label)
                            put("Radius", "${zone.radiusMetres} m")
                            beyond?.let { put("Distance beyond boundary", "$it m") }
                        },
                        // The fix that decided it, rather than a second lookup
                        // that could land somewhere else moments later.
                        knownLocation = fix,
                    ),
                )
            }
        }
    }

    /** Current state, for callers that need it without waiting for a fix. */
    fun status(): SafeZoneStatus = evaluator.currentStatus()

    private companion object {
        /** Long enough for a healthy listener, short enough not to strand tracking. */
        const val RESTORE_TIMEOUT_MILLIS = 5_000L
    }
}

/** A caregiver sets or moves the safe zone for a patient they are linked to. */
class SetSafeZoneForPatientUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Writes only the safe-zone fields onto `settings/{patientId}`.
     *
     * The caregiver is the author here, which is why the Firestore rule for that
     * document had to admit an actively paired caregiver. It admits them for
     * these fields alone - the rule rejects any caregiver write that touches a
     * key outside the safe zone, so fall sensitivity, inactivity timeouts and
     * reminder preferences remain the patient's own.
     */
    suspend operator fun invoke(patientId: String, zone: SafeZone): Outcome<Unit> =
        settingsRepository.updateSafeZone(
            patientId = patientId,
            zone = zone.copy(
                radiusMetres = zone.radiusMetres.coerceIn(
                    SafeZone.MIN_RADIUS_METRES,
                    SafeZone.MAX_RADIUS_METRES,
                ),
            ),
            enabled = true,
        )
}

/** Turns the safe zone off without forgetting where it was. */
class ClearSafeZoneUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(patientId: String): Outcome<Unit> =
        settingsRepository.updateSafeZone(patientId = patientId, zone = null, enabled = false)
}

/**
 * Publishes a single fix when the patient opens their dashboard.
 *
 * This exists to break a deadlock found in testing. The caregiver centres the
 * safe zone on the patient's last known position, but the tracking service only
 * runs once a zone exists - so with neither in place, the caregiver had nothing
 * to centre on and could never create the first zone.
 *
 * One fix on dashboard open costs almost nothing and removes the deadlock. The
 * status is written as UNKNOWN rather than guessed: with no zone configured
 * there is nothing to be inside or outside of, and claiming otherwise would put
 * a green badge on the caregiver's card that means nothing.
 */
class PublishCurrentLocationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val locationRepository: LocationRepository,
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(): Outcome<Unit> {
        val patientId = authRepository.currentUserId()
            ?: return Outcome.Success(Unit)

        val point = when (val result = locationRepository.currentLocation()) {
            // A refused permission or a cold GPS is not an error worth showing:
            // the dashboard has plenty else to display, and the tracking service
            // will publish properly once a zone is set.
            is Outcome.Failure -> return Outcome.Success(Unit)
            is Outcome.Success -> result.data
        }

        return profileRepository.updatePatientLocation(
            patientId = patientId,
            location = PatientLocation(
                point = point,
                recordedAtEpochMillis = System.currentTimeMillis(),
                status = SafeZoneStatus.UNKNOWN,
                metresFromBoundary = null,
            ),
        )
    }
}
