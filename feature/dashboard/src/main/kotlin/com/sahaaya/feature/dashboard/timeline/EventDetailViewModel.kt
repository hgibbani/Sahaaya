package com.sahaaya.feature.dashboard.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.usecase.event.AcknowledgeEventUseCase
import com.sahaaya.domain.usecase.event.ObserveEventUseCase
import com.sahaaya.feature.dashboard.DashboardRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventDetailUiState(
    val event: HealthEvent? = null,
    val isLoading: Boolean = true,
    val isAcknowledging: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeEvent: ObserveEventUseCase,
    private val acknowledgeEvent: AcknowledgeEventUseCase,
) : ViewModel() {

    private val eventId: String =
        savedStateHandle.get<String>(DashboardRoutes.ARG_EVENT_ID).orEmpty()

    private val _uiState = MutableStateFlow(EventDetailUiState())
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    init {
        if (eventId.isBlank()) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "That alert could not be found.")
            }
        } else {
            viewModelScope.launch {
                // Kept live rather than loaded once: if another caregiver
                // acknowledges while this screen is open, it must stop offering
                // the acknowledge button.
                observeEvent(eventId).collect { event ->
                    _uiState.update { it.copy(event = event, isLoading = false) }
                }
            }
        }
    }

    fun acknowledge() {
        if (_uiState.value.isAcknowledging || eventId.isBlank()) return
        _uiState.update { it.copy(isAcknowledging = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = acknowledgeEvent(eventId)) {
                is Outcome.Success -> _uiState.update { it.copy(isAcknowledging = false) }
                is Outcome.Failure -> _uiState.update {
                    it.copy(isAcknowledging = false, errorMessage = result.error.message)
                }
            }
        }
    }
}
