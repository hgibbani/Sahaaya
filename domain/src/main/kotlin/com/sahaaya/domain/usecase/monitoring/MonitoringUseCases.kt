package com.sahaaya.domain.usecase.monitoring

import com.sahaaya.core.demo.FeatureScope
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.model.SafeZone
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.LocationRepository
import com.sahaaya.domain.repository.MonitoringController
import com.sahaaya.domain.repository.SettingsRepository
import com.sahaaya.domain.usecase.event.RaiseEventUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlin.math.roundToInt

class ObserveMonitoringSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(patientId: String): Flow<MonitoringSettings> =
        settingsRepository.observeSettings(patientId)
}

/**
 * Saves settings and immediately re-applies them to the running monitors.
 *
 * One operation on purpose. A settings screen that saves without restarting the
 * detectors leaves the phone doing something different from what it says on
 * screen, and nobody would find out until an alert failed to arrive.
 */
class SaveMonitoringSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val monitoringController: MonitoringController,
) {
    suspend operator fun invoke(settings: MonitoringSettings): Outcome<Unit> {
        val clamped = settings.copy(
            inactivityTimeoutMinutes = settings.inactivityTimeoutMinutes.coerceIn(
                MonitoringSettings.MIN_INACTIVITY_MINUTES,
                MonitoringSettings.MAX_INACTIVITY_MINUTES,
            ),
            missedDoseGraceMinutes = settings.missedDoseGraceMinutes.coerceIn(
                MonitoringSettings.MIN_MISSED_DOSE_GRACE_MINUTES,
                MonitoringSettings.MAX_MISSED_DOSE_GRACE_MINUTES,
            ),
            safeZone = settings.safeZone?.let { zone ->
                zone.copy(
                    radiusMetres = zone.radiusMetres.coerceIn(
                        SafeZone.MIN_RADIUS_METRES,
                        SafeZone.MAX_RADIUS_METRES,
                    ),
                )
            },
            updatedAtEpochMillis = System.currentTimeMillis(),
        )

        val saved = settingsRepository.saveSettings(clamped)
        if (saved is Outcome.Failure) return saved

        // The patient's choice is stored exactly as they made it, so nothing is
        // lost when the feature is switched back on. What is *applied* to the
        // platform is filtered by FeatureScope, so turning fall detection on
        // from the settings screen in this build saves the preference without
        // starting the accelerometer.
        return monitoringController.applySettings(
            clamped.copy(
                fallDetectionEnabled = clamped.fallDetectionEnabled &&
                    FeatureScope.FALL_DETECTION_ACTIVE,
                inactivityDetectionEnabled = clamped.inactivityDetectionEnabled &&
                    FeatureScope.INACTIVITY_DETECTION_ACTIVE,
                geofenceEnabled = clamped.geofenceEnabled && FeatureScope.GEOFENCE_ACTIVE,
            ),
        )
    }
}

/**
 * Starts the monitors the patient's saved settings say should be running.
 *
 * Called when the patient dashboard opens. Until this existed the only things
 * that ever started the detectors were the settings screen and the boot
 * receiver, which meant a patient who signed in and went to their dashboard was
 * not actually being monitored - the app said "watching for falls" while the
 * accelerometer was not registered. That gap is the whole reason this exists.
 *
 * Idempotent: applying the same settings twice starts nothing twice, because
 * [MonitoringController] starts a service that is already running as a no-op.
 *
 * Failures are deliberately swallowed by the caller. A missing permission
 * should degrade monitoring, not block the dashboard from loading.
 */
class EnsureMonitoringRunningUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val monitoringController: MonitoringController,
) {
    suspend operator fun invoke(): Outcome<Unit> {
        val patientId = authRepository.currentUserId()
            ?: return Outcome.Failure(AppError.NotAuthenticated())

        val settings = when (val result = settingsRepository.getSettings(patientId)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }

        // Detectors this build does not run are forced off before anything is
        // applied, so a stored `true` from an earlier install - or from a build
        // where the flag was on - cannot start the accelerometer behind the
        // patient's back. See FeatureScope.
        val scoped = settings.copy(
            fallDetectionEnabled = settings.fallDetectionEnabled &&
                FeatureScope.FALL_DETECTION_ACTIVE,
            inactivityDetectionEnabled = settings.inactivityDetectionEnabled &&
                FeatureScope.INACTIVITY_DETECTION_ACTIVE,
            geofenceEnabled = settings.geofenceEnabled && FeatureScope.GEOFENCE_ACTIVE,
        )

        if (!scoped.isMonitoringAnything) return Outcome.Success(Unit)

        return monitoringController.applySettings(scoped)
    }
}

/**
 * Sets the safe zone to wherever the patient is standing now.
 *
 * The alternative - a map picker - asks a patient with dementia to identify
 * their own home on an aerial photograph. Standing in the kitchen and pressing
 * one button is a better interaction for the person who has to do it.
 */
class SetSafeZoneToCurrentLocationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    private val saveSettings: SaveMonitoringSettingsUseCase,
) {
    suspend operator fun invoke(radiusMetres: Int, label: String = "Home"): Outcome<SafeZone> {
        val patientId = authRepository.currentUserId()
            ?: return Outcome.Failure(AppError.NotAuthenticated())

        val location = when (val result = locationRepository.currentLocation()) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }

        val current = when (val result = settingsRepository.getSettings(patientId)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }

        val zone = SafeZone(
            centre = location,
            radiusMetres = radiusMetres.coerceIn(
                SafeZone.MIN_RADIUS_METRES,
                SafeZone.MAX_RADIUS_METRES,
            ),
            label = label,
        )

        return when (
            val saved = saveSettings(
                current.copy(safeZone = zone, geofenceEnabled = true),
            )
        ) {
            is Outcome.Failure -> saved
            is Outcome.Success -> Outcome.Success(zone)
        }
    }
}

/**
 * Raises the geofence-exit alert.
 *
 * Called by the geofence receiver. The distance beyond the boundary is computed
 * here and put in the summary, because "300 m outside the safe zone" tells a
 * caregiver whether to walk to the gate or start driving, and "left safe zone"
 * does not.
 */
class ReportGeofenceExitUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val raiseEvent: RaiseEventUseCase,
) {
    suspend operator fun invoke(exitPoint: GeoPoint?): Outcome<Unit> {
        val patientId = authRepository.currentUserId()
            ?: return Outcome.Failure(AppError.NotAuthenticated())

        val settings = when (val result = settingsRepository.getSettings(patientId)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }

        val zone = settings.safeZone
        val distance = if (zone != null && exitPoint != null) {
            (zone.centre.distanceMetresTo(exitPoint) - zone.radiusMetres)
                .coerceAtLeast(0.0)
                .roundToInt()
        } else {
            null
        }

        val summary = when {
            distance != null && distance > 0 ->
                "Left the ${zone?.label ?: "safe"} zone - about $distance m beyond the boundary"
            else -> "Left the ${zone?.label ?: "safe"} zone"
        }

        val result = raiseEvent(
            RaiseEventUseCase.Params(
                type = EventType.GEOFENCE_EXIT,
                summary = summary,
                details = buildMap {
                    zone?.let {
                        put("Safe zone", it.label)
                        put("Radius", "${it.radiusMetres} m")
                    }
                    distance?.let { put("Distance beyond boundary", "$it m") }
                },
                knownLocation = exitPoint,
            ),
        )
        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> Outcome.Success(Unit)
        }
    }
}

/** Raises the inactivity alert after the configured quiet period. */
class ReportInactivityUseCase @Inject constructor(
    private val raiseEvent: RaiseEventUseCase,
) {
    suspend operator fun invoke(minutesInactive: Int): Outcome<Unit> {
        val hours = minutesInactive / 60
        val minutes = minutesInactive % 60
        val readable = when {
            hours > 0 && minutes > 0 -> "$hours h $minutes min"
            hours > 0 -> "$hours h"
            else -> "$minutes min"
        }

        val result = raiseEvent(
            RaiseEventUseCase.Params(
                type = EventType.INACTIVITY,
                summary = "No movement detected for $readable",
                details = mapOf("Time without movement" to readable),
            ),
        )
        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> Outcome.Success(Unit)
        }
    }
}

/**
 * The SOS button.
 *
 * Location is attached synchronously and the event is written before this
 * returns, so the button can honestly report success or failure. A fire-and-
 * forget SOS that silently failed would be the worst bug in the app.
 */
class TriggerSosUseCase @Inject constructor(
    private val raiseEvent: RaiseEventUseCase,
) {
    suspend operator fun invoke(): Outcome<Unit> {
        val result = raiseEvent(
            RaiseEventUseCase.Params(
                type = EventType.SOS,
                summary = "Emergency help requested by the patient",
                details = mapOf("Raised by" to "Patient, using the SOS button"),
            ),
        )
        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> Outcome.Success(Unit)
        }
    }
}
