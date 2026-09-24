package com.sahaaya.data.demo

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.TrackPoint
import com.sahaaya.domain.model.TrackingState
import com.sahaaya.domain.repository.TrackingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline stand-in for [TrackingRepository], used when [com.sahaaya.core.demo.DemoConfig]
 * is on.
 *
 * In-memory and therefore single-device by construction: it exists so the app
 * still builds and navigates without Firebase, not so tracking can be
 * demonstrated. Real tracking is inherently two-device and cannot be faked
 * here - the caregiver and the patient are different processes on different
 * phones, and nothing in this class crosses that gap.
 */
@Singleton
class DemoTrackingRepository @Inject constructor() : TrackingRepository {

    private val states = MutableStateFlow<Map<String, TrackingState>>(emptyMap())
    private val routes = MutableStateFlow<Map<String, List<TrackPoint>>>(emptyMap())

    override fun observeTrackingState(patientId: String): Flow<TrackingState> =
        states.asStateFlow().map { it[patientId] ?: TrackingState.INACTIVE }

    override suspend fun startTracking(
        patientId: String,
        caregiverId: String,
    ): Outcome<String> {
        val sessionId = "demo-session-${System.currentTimeMillis()}"
        states.value = states.value + (
            patientId to TrackingState(
                active = true,
                sessionId = sessionId,
                startedAtEpochMillis = System.currentTimeMillis(),
                startedByCaregiverId = caregiverId,
            )
            )
        return Outcome.Success(sessionId)
    }

    override suspend fun stopTracking(
        patientId: String,
        caregiverId: String,
    ): Outcome<Unit> {
        val current = states.value[patientId] ?: TrackingState.INACTIVE
        states.value = states.value + (
            patientId to current.copy(
                active = false,
                stoppedAtEpochMillis = System.currentTimeMillis(),
                stoppedByCaregiverId = caregiverId,
            )
            )
        return Outcome.Success(Unit)
    }

    override suspend fun appendTrackPoint(
        sessionId: String,
        patientId: String,
        point: TrackPoint,
    ): Outcome<Unit> {
        val existing = routes.value[sessionId].orEmpty()
        routes.value = routes.value + (sessionId to existing + point)
        return Outcome.Success(Unit)
    }

    override fun observeRoute(sessionId: String, limit: Int): Flow<List<TrackPoint>> =
        routes.asStateFlow().map { it[sessionId].orEmpty().takeLast(limit) }
}
