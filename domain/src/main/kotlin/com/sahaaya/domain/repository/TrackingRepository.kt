package com.sahaaya.domain.repository

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.TrackPoint
import com.sahaaya.domain.model.TrackingState
import kotlinx.coroutines.flow.Flow

/**
 * Caregiver-controlled live tracking.
 *
 * Split from [SettingsRepository] even though the tracking flags live on the
 * same `settings/{patientId}` document, because the authority is inverted:
 * everything else on that document is the patient's to change and the caregiver
 * may only read, whereas these fields are the caregiver's to change and the
 * patient may only read. Keeping them behind their own interface makes that
 * inversion visible at the call site instead of buried in a rules file.
 */
interface TrackingRepository {

    /** Current tracking state for [patientId]. Both sides may observe this. */
    fun observeTrackingState(patientId: String): Flow<TrackingState>

    /**
     * Starts a session, written by the caregiver.
     *
     * Creates `trackingSessions/{sessionId}` and points the patient's settings
     * document at it in the same operation, so a patient device can never see
     * `trackingActive = true` with no session to file points against.
     */
    suspend fun startTracking(
        patientId: String,
        caregiverId: String,
    ): Outcome<String>

    /** Ends the active session. Route points already filed are kept. */
    suspend fun stopTracking(
        patientId: String,
        caregiverId: String,
    ): Outcome<Unit>

    /**
     * Files one point on the route. Called on the patient's device only.
     *
     * Appends to `trackingSessions/{sessionId}/locations`; the patient's latest
     * position continues to be written where it always was, by
     * [ProfileRepository.updatePatientLocation].
     */
    suspend fun appendTrackPoint(
        sessionId: String,
        patientId: String,
        point: TrackPoint,
    ): Outcome<Unit>

    /** The route for [sessionId], oldest first, ready to draw as a polyline. */
    fun observeRoute(sessionId: String, limit: Int): Flow<List<TrackPoint>>
}
