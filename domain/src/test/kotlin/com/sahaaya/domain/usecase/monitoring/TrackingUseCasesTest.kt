package com.sahaaya.domain.usecase.monitoring

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.PairingCode
import com.sahaaya.domain.model.PairingStatus
import com.sahaaya.domain.model.TrackPoint
import com.sahaaya.domain.model.TrackingState
import com.sahaaya.domain.model.User
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.PairingRepository
import com.sahaaya.domain.repository.TrackingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Authorization and state for caregiver-controlled tracking.
 *
 * The rules in firestore.rules are the real enforcement; these tests cover the
 * client-side half - that the app refuses to try when it should, and that the
 * patient-facing path exposes no way to change the state.
 */
class TrackingUseCasesTest {

    private val patientId = "patient-1"
    private val caregiverId = "caregiver-1"

    // --- Starting -------------------------------------------------------

    @Test
    fun `an actively paired caregiver can start tracking`() = runTest {
        val repository = FakeTrackingRepository()
        val useCase = StartTrackingUseCase(
            authRepository = FakeAuth(caregiverId),
            pairingRepository = FakePairing(listOf(pairing(PairingStatus.ACTIVE))),
            trackingRepository = repository,
        )

        val result = useCase(patientId)

        assertTrue(result is Outcome.Success)
        assertTrue(repository.state(patientId).active)
        assertEquals(caregiverId, repository.state(patientId).startedByCaregiverId)
    }

    @Test
    fun `a caregiver whose pairing was revoked cannot start tracking`() = runTest {
        val repository = FakeTrackingRepository()
        val useCase = StartTrackingUseCase(
            authRepository = FakeAuth(caregiverId),
            pairingRepository = FakePairing(listOf(pairing(PairingStatus.REVOKED))),
            trackingRepository = repository,
        )

        val result = useCase(patientId)

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AppError.PermissionDenied)
        // Nothing was written: a refused start must not leave a session behind.
        assertNull(repository.state(patientId).sessionId)
    }

    @Test
    fun `a caregiver paired with somebody else cannot start tracking this patient`() = runTest {
        val repository = FakeTrackingRepository()
        val useCase = StartTrackingUseCase(
            authRepository = FakeAuth(caregiverId),
            pairingRepository = FakePairing(
                listOf(pairing(PairingStatus.ACTIVE, patientId = "someone-else")),
            ),
            trackingRepository = repository,
        )

        assertTrue(useCase(patientId) is Outcome.Failure)
        assertTrue(!repository.state(patientId).active)
    }

    @Test
    fun `a signed-out caller cannot start tracking`() = runTest {
        val useCase = StartTrackingUseCase(
            authRepository = FakeAuth(null),
            pairingRepository = FakePairing(listOf(pairing(PairingStatus.ACTIVE))),
            trackingRepository = FakeTrackingRepository(),
        )

        val result = useCase(patientId)

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AppError.NotAuthenticated)
    }

    // --- Stopping -------------------------------------------------------

    @Test
    fun `the caregiver can stop tracking and the stop is attributed to them`() = runTest {
        val repository = FakeTrackingRepository()
        repository.startTracking(patientId, caregiverId)

        val result = StopTrackingUseCase(FakeAuth(caregiverId), repository)(patientId)

        assertTrue(result is Outcome.Success)
        val state = repository.state(patientId)
        assertTrue(!state.active)
        assertEquals(caregiverId, state.stoppedByCaregiverId)
        assertNotNull(state.stoppedAtEpochMillis)
    }

    @Test
    fun `the session id survives stopping so the route can still be read`() = runTest {
        val repository = FakeTrackingRepository()
        val sessionId = (repository.startTracking(patientId, caregiverId) as Outcome.Success).data

        StopTrackingUseCase(FakeAuth(caregiverId), repository)(patientId)

        assertEquals(sessionId, repository.state(patientId).sessionId)
        // ...but no longer as a session points may be filed against.
        assertNull(repository.state(patientId).activeSessionId)
    }

    // --- What the patient can do ----------------------------------------

    @Test
    fun `the patient-facing use case only reads state`() = runTest {
        val repository = FakeTrackingRepository()
        repository.startTracking(patientId, caregiverId)

        val observe = ObserveMyTrackingStateUseCase(FakeAuth(patientId), repository)

        assertTrue(observe().first().active)
        // The class has no start/stop of its own; that is the whole point, and
        // this assertion documents it so adding one breaks a test.
        val methods = ObserveMyTrackingStateUseCase::class.java.methods.map { it.name }
        assertTrue(methods.none { it.contains("start", true) || it.contains("stop", true) })
    }

    @Test
    fun `a signed-out patient sees tracking as inactive rather than crashing`() = runTest {
        val observe = ObserveMyTrackingStateUseCase(FakeAuth(null), FakeTrackingRepository())
        assertEquals(TrackingState.INACTIVE, observe().first())
    }

    // --- activeSessionId ------------------------------------------------

    @Test
    fun `a session id without an active flag is not a session to file against`() {
        val state = TrackingState(active = false, sessionId = "s1")
        assertNull(state.activeSessionId)
    }

    @Test
    fun `an active flag without a session id is not a session to file against`() {
        assertNull(TrackingState(active = true, sessionId = null).activeSessionId)
        assertNull(TrackingState(active = true, sessionId = "  ").activeSessionId)
    }

    // --- Route thinning -------------------------------------------------

    @Test
    fun `the first fix of a session is always filed`() {
        val thinner = TrackRouteThinner()
        assertNotNull(thinner.accept(GeoPoint(12.99, 77.61, 5f)))
    }

    @Test
    fun `a stationary phone does not fill the route with duplicates`() {
        val thinner = TrackRouteThinner()
        val here = GeoPoint(12.99, 77.61, 5f)
        thinner.accept(here)

        // Roughly 5 m away - inside ordinary GPS jitter.
        val jitter = GeoPoint(12.990045, 77.61, 5f)
        assertNull(thinner.accept(jitter))
        assertNull(thinner.accept(jitter))
    }

    @Test
    fun `a real walk is filed`() {
        val thinner = TrackRouteThinner()
        thinner.accept(GeoPoint(12.99, 77.61, 5f))

        // About 55 m north.
        assertNotNull(thinner.accept(GeoPoint(12.9905, 77.61, 5f)))
    }

    @Test
    fun `a vague fix is never drawn on the route`() {
        val thinner = TrackRouteThinner()
        // A 400 m cell-tower estimate is not a place the patient went.
        assertNull(thinner.accept(GeoPoint(12.99, 77.61, 400f)))
        // And it did not become the baseline for the next comparison either.
        assertNotNull(thinner.accept(GeoPoint(12.99, 77.61, 5f)))
    }

    @Test
    fun `a fix with no accuracy at all is allowed through`() {
        // Some providers omit accuracy. Dropping those would silently produce
        // an empty route on devices that never report it.
        assertNotNull(TrackRouteThinner().accept(GeoPoint(12.99, 77.61, null)))
    }

    @Test
    fun `resetting means a new session starts with its own first point`() {
        val thinner = TrackRouteThinner()
        val here = GeoPoint(12.99, 77.61, 5f)
        thinner.accept(here)
        assertNull(thinner.accept(here))

        thinner.reset()

        assertNotNull(thinner.accept(here))
    }

    @Test
    fun `the distance threshold is far enough out of GPS noise to be useful`() {
        assertTrue(TrackPoint.MIN_DISTANCE_METRES >= 10.0)
        assertTrue(TrackPoint.ROUTE_LIMIT in 50..1_000)
    }

    // --- fakes ----------------------------------------------------------

    private fun pairing(
        status: PairingStatus,
        patientId: String = this.patientId,
    ) = Pairing(
        id = "${patientId}_$caregiverId",
        patientId = patientId,
        caregiverId = caregiverId,
        patientName = "Asha",
        caregiverName = "Ravi",
        status = status,
    )

    private class FakeAuth(private val uid: String?) : AuthRepository {
        override fun currentUserId(): String? = uid
        override fun observeCurrentUser(): Flow<User?> = flowOf(null)
        override suspend fun signIn(email: String, password: String) =
            Outcome.Failure(AppError.NotAuthenticated())
        override suspend fun register(
            email: String,
            password: String,
            displayName: String,
            phoneNumber: String,
            role: com.sahaaya.domain.model.Role,
        ) = Outcome.Failure(AppError.NotAuthenticated())
        override suspend fun sendPasswordReset(email: String) = Outcome.Success(Unit)
        override suspend fun signOut() = Outcome.Success(Unit)
        override suspend fun refreshCurrentUser() = Outcome.Failure(AppError.NotAuthenticated())
    }

    private class FakePairing(private val pairings: List<Pairing>) : PairingRepository {
        override fun observePairingsForPatient(patientId: String): Flow<List<Pairing>> =
            flowOf(pairings)
        override fun observePairingsForCaregiver(caregiverId: String): Flow<List<Pairing>> =
            flowOf(pairings)
        override suspend fun generatePairingCode(patientId: String) =
            Outcome.Failure(AppError.Unknown())
        override fun observeActivePairingCode(patientId: String): Flow<PairingCode?> = flowOf(null)
        override suspend fun redeemPairingCode(code: String, caregiverId: String) =
            Outcome.Failure(AppError.Unknown())
        override suspend fun revokePairing(pairingId: String) = Outcome.Success(Unit)
    }

    private class FakeTrackingRepository : TrackingRepository {
        private val states = MutableStateFlow<Map<String, TrackingState>>(emptyMap())
        private val routes = mutableMapOf<String, MutableList<TrackPoint>>()
        private var counter = 0

        fun state(patientId: String): TrackingState =
            states.value[patientId] ?: TrackingState.INACTIVE

        override fun observeTrackingState(patientId: String): Flow<TrackingState> =
            states.map { it[patientId] ?: TrackingState.INACTIVE }

        override suspend fun startTracking(
            patientId: String,
            caregiverId: String,
        ): Outcome<String> {
            val sessionId = "session-${++counter}"
            states.value = states.value + (
                patientId to TrackingState(
                    active = true,
                    sessionId = sessionId,
                    startedAtEpochMillis = 1_000L,
                    startedByCaregiverId = caregiverId,
                )
                )
            return Outcome.Success(sessionId)
        }

        override suspend fun stopTracking(
            patientId: String,
            caregiverId: String,
        ): Outcome<Unit> {
            val current = state(patientId)
            states.value = states.value + (
                patientId to current.copy(
                    active = false,
                    stoppedAtEpochMillis = 2_000L,
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
            routes.getOrPut(sessionId) { mutableListOf() }.add(point)
            return Outcome.Success(Unit)
        }

        override fun observeRoute(sessionId: String, limit: Int): Flow<List<TrackPoint>> =
            flowOf(routes[sessionId].orEmpty().takeLast(limit))
    }
}

private suspend fun <T> Flow<T>.first(): T {
    var result: T? = null
    var seen = false
    collect { value ->
        if (!seen) {
            result = value
            seen = true
        }
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
}
