package com.sahaaya.domain.usecase.event

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.EventStatus
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.EventRepository
import com.sahaaya.domain.repository.PairingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The patient's own history. */
class ObservePatientEventsUseCase @Inject constructor(
    private val eventRepository: EventRepository,
) {
    operator fun invoke(patientId: String): Flow<List<HealthEvent>> =
        eventRepository.observeEventsForPatient(patientId)
}

/**
 * The caregiver timeline: every event from every patient they look after.
 *
 * Rebuilt whenever the pairing list changes, so unlinking a patient stops their
 * events appearing immediately rather than at the next app launch.
 */
class ObserveCaregiverTimelineUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val eventRepository: EventRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(caregiverId: String): Flow<List<HealthEvent>> =
        pairingRepository.observePairingsForCaregiver(caregiverId)
            .map { pairings -> pairings.filter { it.isActive }.map { it.patientId } }
            .flatMapLatest { patientIds ->
                if (patientIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    eventRepository.observeEventsForPatients(patientIds)
                }
            }
}

class ObserveEventUseCase @Inject constructor(
    private val eventRepository: EventRepository,
) {
    operator fun invoke(eventId: String): Flow<HealthEvent?> =
        eventRepository.observeEvent(eventId)
}

/**
 * A caregiver takes responsibility for an event.
 *
 * Recorded with who and when, so a family with several caregivers can see that
 * someone is already dealing with it and does not need to act - and so that
 * nobody assumes someone else did.
 */
class AcknowledgeEventUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val eventRepository: EventRepository,
) {
    suspend operator fun invoke(eventId: String): Outcome<Unit> {
        val caregiverId = authRepository.currentUserId()
            ?: return Outcome.Failure(AppError.NotAuthenticated())
        return eventRepository.updateStatus(
            eventId = eventId,
            status = EventStatus.ACKNOWLEDGED,
            caregiverId = caregiverId,
        )
    }
}

/**
 * Acknowledges every outstanding alert at once.
 *
 * Clearing 61 alerts one tap at a time is not a workflow, and a caregiver who
 * gives up halfway leaves a timeline that no longer means anything.
 *
 * This is the same [AcknowledgeEventUseCase] applied to a list, deliberately:
 * "clear" already means "acknowledged by this caregiver, at this time", and a
 * bulk action that deleted rows instead would destroy the record of what
 * happened. Nothing is removed - the events keep their type, time, location and
 * details, and stay visible in history. Only their status changes.
 *
 * Failures are collected rather than aborting the run. One rejected write should
 * not leave the other sixty untouched, so the caller is told how many did not
 * land and the rest still clear.
 */
class AcknowledgeAllEventsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val eventRepository: EventRepository,
) {
    /** @return how many failed to clear. Zero means all of them cleared. */
    suspend operator fun invoke(eventIds: List<String>): Outcome<Int> {
        val caregiverId = authRepository.currentUserId()
            ?: return Outcome.Failure(AppError.NotAuthenticated())

        var failures = 0
        eventIds.forEach { eventId ->
            val result = eventRepository.updateStatus(
                eventId = eventId,
                status = EventStatus.ACKNOWLEDGED,
                caregiverId = caregiverId,
            )
            if (result is Outcome.Failure) failures++
        }
        return Outcome.Success(failures)
    }
}

/**
 * The patient cancels an alert during the confirmation countdown.
 *
 * The event is marked cancelled rather than deleted. A pattern of cancelled
 * falls is itself clinically interesting - it may mean the sensitivity is wrong,
 * or that the person is stumbling often without hurting themselves.
 */
class CancelEventUseCase @Inject constructor(
    private val eventRepository: EventRepository,
) {
    suspend operator fun invoke(eventId: String): Outcome<Unit> =
        eventRepository.updateStatus(eventId, EventStatus.CANCELLED)
}

/** Counts per type for the caregiver's summary cards. Unresolved events only. */
class SummariseEventsUseCase @Inject constructor() {
    operator fun invoke(events: List<HealthEvent>): Map<EventType, Int> =
        events.filter { it.isUnresolved }.groupingBy { it.type }.eachCount()
}
