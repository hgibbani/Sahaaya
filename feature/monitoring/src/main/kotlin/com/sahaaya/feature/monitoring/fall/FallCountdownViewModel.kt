package com.sahaaya.feature.monitoring.fall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.usecase.monitoring.CancelFallAlertUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FallCountdownUiState(
    val secondsRemaining: Int = MonitoringSettings.FALL_CONFIRMATION_SECONDS,
    val outcome: CountdownOutcome = CountdownOutcome.COUNTING,
    val errorMessage: String? = null,
)

enum class CountdownOutcome {
    /** Waiting for the patient to answer. */
    COUNTING,

    /** The patient said they were fine; the event has been withdrawn. */
    CANCELLED,

    /** Nobody answered; the alert stands and caregivers have been told. */
    CONFIRMED,
}

/**
 * The five seconds between a detected fall and the caregiver being told.
 *
 * The event is already in Firestore by the time this screen appears - see
 * ReportFallUseCase for why. So this countdown is not "should we send the
 * alert", it is "should we withdraw the one already sent". That ordering is
 * what makes a phone that dies on impact still report the fall.
 */
@HiltViewModel
class FallCountdownViewModel @Inject constructor(
    private val cancelFallAlert: CancelFallAlertUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FallCountdownUiState())
    val uiState: StateFlow<FallCountdownUiState> = _uiState.asStateFlow()

    private var eventId: String = ""
    private var started = false

    fun start(eventId: String) {
        if (started) return
        started = true
        this.eventId = eventId

        viewModelScope.launch {
            var remaining = MonitoringSettings.FALL_CONFIRMATION_SECONDS
            while (isActive && remaining > 0) {
                _uiState.update { it.copy(secondsRemaining = remaining) }
                delay(1_000L)
                remaining--

                // The patient answered while we were waiting.
                if (_uiState.value.outcome != CountdownOutcome.COUNTING) return@launch
            }
            _uiState.update {
                it.copy(secondsRemaining = 0, outcome = CountdownOutcome.CONFIRMED)
            }
        }
    }

    /** "I'm fine" - withdraws the alert. */
    fun cancel() {
        if (_uiState.value.outcome != CountdownOutcome.COUNTING) return
        // Flip the state first so the countdown stops immediately, rather than
        // after a network round trip the patient has to sit and watch.
        _uiState.update { it.copy(outcome = CountdownOutcome.CANCELLED) }

        viewModelScope.launch {
            if (eventId.isBlank()) return@launch
            when (val result = cancelFallAlert(eventId)) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> _uiState.update {
                    // Be honest: the local state says cancelled but the write
                    // failed, so a caregiver may still be alerted.
                    it.copy(
                        errorMessage = "Could not withdraw the alert - your caregiver " +
                            "may still be notified. ${result.error.message}",
                    )
                }
            }
        }
    }

    /** "I need help" - stops the countdown and confirms straight away. */
    fun confirmNow() {
        if (_uiState.value.outcome != CountdownOutcome.COUNTING) return
        _uiState.update { it.copy(secondsRemaining = 0, outcome = CountdownOutcome.CONFIRMED) }
    }
}
