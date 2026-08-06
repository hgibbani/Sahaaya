package com.sahaaya.data.repository

import com.sahaaya.core.result.Outcome
import com.sahaaya.data.mapper.toHealthEvent
import com.sahaaya.data.mapper.toMap
import com.sahaaya.domain.model.EventStatus
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.repository.EventRepository
import com.sahaaya.firebase.EventFields
import com.sahaaya.firebase.source.EventDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val eventDataSource: EventDataSource,
) : EventRepository {

    override fun observeEventsForPatient(
        patientId: String,
        limit: Int,
    ): Flow<List<HealthEvent>> =
        eventDataSource.observeEventsForPatient(patientId, limit)
            .map { documents -> documents.mapNotNull { it.toHealthEvent() } }

    override fun observeEventsForPatients(
        patientIds: List<String>,
        limit: Int,
    ): Flow<List<HealthEvent>> =
        eventDataSource.observeEventsForPatients(patientIds, limit)
            .map { documents -> documents.mapNotNull { it.toHealthEvent() } }

    override fun observeEvent(eventId: String): Flow<HealthEvent?> =
        eventDataSource.observeEvent(eventId)
            .map { snapshot -> snapshot?.toHealthEvent() }

    /**
     * Writes the event with a client-allocated id.
     *
     * The id is generated locally rather than by the server so that the caller
     * has it immediately - the fall countdown needs to be able to cancel this
     * exact document, and it cannot wait for a round trip that may not complete
     * before the patient presses the button.
     */
    override suspend fun raiseEvent(event: HealthEvent): Outcome<HealthEvent> {
        val id = event.id.ifBlank { eventDataSource.newEventId() }
        val stored = event.copy(id = id)

        return when (val write = eventDataSource.writeEvent(id, stored.toMap())) {
            is Outcome.Failure -> write
            is Outcome.Success -> Outcome.Success(stored)
        }
    }

    override suspend fun updateStatus(
        eventId: String,
        status: EventStatus,
        caregiverId: String?,
    ): Outcome<Unit> = eventDataSource.updateEvent(
        id = eventId,
        data = buildMap {
            put(EventFields.STATUS, status.storageKey)
            if (status == EventStatus.ACKNOWLEDGED) {
                put(EventFields.ACKNOWLEDGED_BY, caregiverId)
                put(EventFields.ACKNOWLEDGED_AT, System.currentTimeMillis())
            }
        },
    )
}
