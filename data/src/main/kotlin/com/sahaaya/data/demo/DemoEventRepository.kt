package com.sahaaya.data.demo

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.EventStatus
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The event log, in memory.
 *
 * Because the collections are [kotlinx.coroutines.flow.MutableStateFlow]s, a
 * write here re-emits to every collector immediately. That is what makes the
 * fall / SOS / missed-dose demos land on the caregiver timeline the instant
 * they are raised, with no polling and no refresh - the same behaviour a
 * Firestore snapshot listener gives.
 */
@Singleton
class DemoEventRepository @Inject constructor(
    private val store: DemoDataStore,
) : EventRepository {

    override fun observeEventsForPatient(
        patientId: String,
        limit: Int,
    ): Flow<List<HealthEvent>> = store.events.map { all ->
        all.values
            .filter { it.patientId == patientId }
            .sortedByDescending { it.occurredAtEpochMillis }
            .take(limit)
    }

    override fun observeEventsForPatients(
        patientIds: List<String>,
        limit: Int,
    ): Flow<List<HealthEvent>> = store.events.map { all ->
        all.values
            .filter { it.patientId in patientIds }
            .sortedByDescending { it.occurredAtEpochMillis }
            .take(limit)
    }

    override fun observeEvent(eventId: String): Flow<HealthEvent?> =
        store.events.map { it[eventId] }

    override suspend fun raiseEvent(event: HealthEvent): Outcome<HealthEvent> {
        val stored = event.copy(id = event.id.ifBlank { store.nextId("event") })
        store.events.put(stored.id, stored)
        return Outcome.Success(stored)
    }

    override suspend fun updateStatus(
        eventId: String,
        status: EventStatus,
        caregiverId: String?,
    ): Outcome<Unit> {
        val existing = store.events.value[eventId]
            ?: return Outcome.Failure(AppError.NotFound("That alert no longer exists."))

        val updated = if (status == EventStatus.ACKNOWLEDGED) {
            existing.copy(
                status = status,
                acknowledgedByCaregiverId = caregiverId,
                acknowledgedAtEpochMillis = System.currentTimeMillis(),
            )
        } else {
            existing.copy(status = status)
        }

        store.events.put(eventId, updated)
        return Outcome.Success(Unit)
    }
}
