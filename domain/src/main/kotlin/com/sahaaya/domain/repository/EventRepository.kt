package com.sahaaya.domain.repository

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.EventStatus
import com.sahaaya.domain.model.HealthEvent
import kotlinx.coroutines.flow.Flow

/**
 * The event log: what happened, and who has responded to it.
 *
 * Events are the product of this app. Everything else - pairing, profiles,
 * settings - exists so that these can be raised and delivered.
 */
interface EventRepository {

    /** Newest first, capped at [limit] so a long history never becomes a slow screen. */
    fun observeEventsForPatient(patientId: String, limit: Int = DEFAULT_LIMIT): Flow<List<HealthEvent>>

    /**
     * Events across every patient this caregiver looks after, newest first.
     *
     * Takes the id list rather than resolving pairings itself: the caller
     * already observes them, and a Firestore `whereIn` needs the concrete ids.
     */
    fun observeEventsForPatients(
        patientIds: List<String>,
        limit: Int = DEFAULT_LIMIT,
    ): Flow<List<HealthEvent>>

    fun observeEvent(eventId: String): Flow<HealthEvent?>

    /**
     * Records an event and returns it with its assigned id.
     *
     * This is the only way an alert enters the system. Detectors, the SOS
     * button and the missed-dose worker all funnel through here, so there is
     * exactly one place where an alert is created and one document shape to
     * reason about.
     */
    suspend fun raiseEvent(event: HealthEvent): Outcome<HealthEvent>

    /** Marks an event as seen by a caregiver, or cancelled by the patient. */
    suspend fun updateStatus(
        eventId: String,
        status: EventStatus,
        caregiverId: String? = null,
    ): Outcome<Unit>

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}
