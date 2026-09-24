package com.sahaaya.feature.dashboard.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.usecase.event.AcknowledgeAllEventsUseCase
import com.sahaaya.domain.usecase.event.AcknowledgeEventUseCase
import com.sahaaya.domain.usecase.event.ObserveCaregiverTimelineUseCase
import com.sahaaya.domain.usecase.event.SummariseEventsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUiState(
    val events: List<HealthEvent> = emptyList(),
    val counts: Map<EventType, Int> = emptyMap(),
    val filter: EventType? = null,
    val isLoading: Boolean = true,
    val acknowledgingEventId: String? = null,
    val isClearingAll: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Ids the "Clear all" action would acknowledge. Only unresolved ones. */
    val clearableEventIds: List<String>
        get() = visibleEvents.filter { it.isUnresolved }.map { it.id }

    val canClearAll: Boolean get() = clearableEventIds.isNotEmpty() && !isClearingAll

    val visibleEvents: List<HealthEvent>
        get() = if (filter == null) events else events.filter { it.type == filter }

    val unresolvedCount: Int get() = events.count { it.isUnresolved }

    val hasCriticalUnresolved: Boolean
        get() = events.any { it.isUnresolved && it.isCritical }
}

/**
 * The caregiver's timeline.
 *
 * Backed by a live Firestore listener, so an event raised on the patient's phone
 * appears here without a refresh. That is the whole product promise: the
 * caregiver should not have to check.
 */
@HiltViewModel
class TimelineViewModel @Inject constructor(
    authRepository: AuthRepository,
    observeTimeline: ObserveCaregiverTimelineUseCase,
    private val summariseEvents: SummariseEventsUseCase,
    private val acknowledgeEvent: AcknowledgeEventUseCase,
    private val acknowledgeAllEvents: AcknowledgeAllEventsUseCase,
) : ViewModel() {

    private val caregiverId = authRepository.currentUserId()

    private val filter = MutableStateFlow<EventType?>(null)
    private val acknowledging = MutableStateFlow<String?>(null)
    private val clearingAll = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TimelineUiState> =
        if (caregiverId == null) {
            flowOf(TimelineUiState(isLoading = false, errorMessage = "You are signed out."))
        } else {
            combine(
                observeTimeline(caregiverId),
                filter,
                acknowledging,
                clearingAll,
                error,
            ) { events, selectedFilter, acknowledgingId, isClearingAll, errorMessage ->
                TimelineUiState(
                    // Sorted client-side as well as in the query: the caregiver
                    // timeline merges several patients, and a caregiver looking
                    // for the newest event must never see them out of order.
                    events = events.sortedByDescending { it.occurredAtEpochMillis },
                    counts = summariseEvents(events),
                    filter = selectedFilter,
                    isLoading = false,
                    acknowledgingEventId = acknowledgingId,
                    isClearingAll = isClearingAll,
                    errorMessage = errorMessage,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TimelineUiState(),
        )

    fun setFilter(type: EventType?) {
        filter.update { if (it == type) null else type }
    }

    fun acknowledge(eventId: String) {
        if (acknowledging.value != null) return
        acknowledging.update { eventId }

        viewModelScope.launch {
            when (val result = acknowledgeEvent(eventId)) {
                is Outcome.Success -> error.update { null }
                is Outcome.Failure -> error.update { result.error.message }
            }
            acknowledging.update { null }
        }
    }

    /**
     * Clears every outstanding alert currently in view.
     *
     * Scoped to `visibleEvents`, so clearing while a type filter is applied
     * clears what the caregiver can actually see rather than silently
     * acknowledging alerts they are not looking at.
     */
    fun clearAll() {
        val ids = uiState.value.clearableEventIds
        if (ids.isEmpty() || clearingAll.value) return
        clearingAll.update { true }

        viewModelScope.launch {
            when (val result = acknowledgeAllEvents(ids)) {
                is Outcome.Success -> error.update {
                    if (result.data == 0) {
                        null
                    } else {
                        "${result.data} of ${ids.size} alert(s) could not be cleared. " +
                            "Pull down to refresh and try again."
                    }
                }
                is Outcome.Failure -> error.update { result.error.message }
            }
            clearingAll.update { false }
        }
    }

    fun onErrorShown() {
        error.update { null }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
