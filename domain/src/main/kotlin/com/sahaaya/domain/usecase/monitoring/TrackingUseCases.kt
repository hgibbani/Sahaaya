package com.sahaaya.domain.usecase.monitoring

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.TrackPoint
import com.sahaaya.domain.model.TrackingState
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.PairingRepository
import com.sahaaya.domain.repository.TrackingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * A caregiver starts watching a patient they are actively paired with.
 *
 * The pairing is re-checked here rather than trusted from whatever the dashboard
 * happened to be showing. A dashboard can be minutes stale - a pairing revoked
 * on the patient's phone while the caregiver's screen still lists them would
 * otherwise let that caregiver open a tracking session on somebody who has just
 * withdrawn consent. The Firestore rule refuses it too; this check is what turns
 * that refusal into a sentence the caregiver can read.
 */
class StartTrackingUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val pairingRepository: PairingRepository,
    private val trackingRepository: TrackingRepository,
) {
    suspend operator fun invoke(patientId: String): Outcome<String> {
        val caregiverId = authRepository.currentUserId()
            ?: return Outcome.Failure(TrackingErrors.notSignedIn())

        if (!isActivelyPairedWith(patientId, caregiverId)) {
            return Outcome.Failure(TrackingErrors.notPaired())
        }
        return trackingRepository.startTracking(patientId, caregiverId)
    }

    private suspend fun isActivelyPairedWith(patientId: String, caregiverId: String): Boolean =
        runCatching {
            pairingRepository.observePairingsForCaregiver(caregiverId)
                .first()
                .any { it.patientId == patientId && it.isActive }
        }.getOrDefault(false)
}

/**
 * The caregiver stops watching. The only way tracking ever turns off.
 *
 * There is no patient-facing counterpart anywhere in the codebase, and that
 * absence is the feature. See [TrackingState].
 */
class StopTrackingUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val trackingRepository: TrackingRepository,
) {
    suspend operator fun invoke(patientId: String): Outcome<Unit> {
        val caregiverId = authRepository.currentUserId()
            ?: return Outcome.Failure(TrackingErrors.notSignedIn())
        return trackingRepository.stopTracking(patientId, caregiverId)
    }
}

/** Tracking state for the signed-in patient, for their own status screen. */
class ObserveMyTrackingStateUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val trackingRepository: TrackingRepository,
) {
    operator fun invoke(): Flow<TrackingState> {
        val patientId = authRepository.currentUserId()
            ?: return flowOf(TrackingState.INACTIVE)
        return trackingRepository.observeTrackingState(patientId)
    }
}

/**
 * Decides which fixes are worth filing on the route.
 *
 * Pure and separate from the service so the thinning rule can be tested without
 * a GPS, a Firestore or an Android runtime. Holding the last filed point is the
 * whole of its state.
 *
 * Two fixes are dropped: ones too vague to place a dot honestly, and ones that
 * have not moved far enough to be a different place. See
 * [TrackPoint.MIN_DISTANCE_METRES].
 */
class TrackRouteThinner(
    private val minDistanceMetres: Double = TrackPoint.MIN_DISTANCE_METRES,
    private val maxAccuracyMetres: Float = SafeZoneEvaluator.MAX_ACCURACY_METRES,
) {
    private var lastFiled: GeoPoint? = null

    /** The point to file, or null to skip this fix. */
    fun accept(fix: GeoPoint): GeoPoint? {
        // Same gate the safe-zone evaluator uses. A 400 m cell-tower estimate
        // drawn as a route point is not a place the patient went, it is a lie
        // with a dot on it.
        val accuracy = fix.accuracyMetres
        if (accuracy != null && accuracy > maxAccuracyMetres) return null

        val previous = lastFiled
        if (previous != null && previous.distanceMetresTo(fix) < minDistanceMetres) {
            return null
        }
        lastFiled = fix
        return fix
    }

    /** Forgets the last point, so a new session starts with its own first dot. */
    fun reset() {
        lastFiled = null
    }
}

/** Failures worth showing a caregiver in their own words. */
internal object TrackingErrors {
    fun notSignedIn() = AppError.NotAuthenticated()

    fun notPaired() = AppError.PermissionDenied(
        message = "You are no longer linked to this patient, so tracking cannot start.",
    )
}
