package com.sahaaya.feature.monitoring.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.usecase.medication.FlagMissedDosesUseCase
import com.sahaaya.domain.usecase.monitoring.ReportFallUseCase
import com.sahaaya.domain.usecase.monitoring.ReportGeofenceExitUseCase
import com.sahaaya.domain.usecase.monitoring.ReportInactivityUseCase
import com.sahaaya.domain.usecase.monitoring.TriggerSosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DemoModeUiState(
    val runningSimulation: EventType? = null,
    val lastResult: String? = null,
    val lastFallEventId: String? = null,
    val errorMessage: String? = null,
)

/**
 * Fires each detector's *real* pipeline without performing the physical action.
 *
 * This is the honest way to build a demo mode. Every button here calls exactly
 * the same use case the sensor path calls - the same Firestore write, the same
 * security rules, the same Cloud Function, the same notification. Nothing is
 * mocked and no display-only fixture exists. The only thing skipped is the
 * accelerometer trace, or standing up and walking 200 metres down the road.
 *
 * The consequence worth stating: if a simulated fall reaches the caregiver's
 * phone, a real one will too, because it is the identical code path.
 *
 * Gated behind BuildConfig.DEBUG at the navigation layer, so the entry point
 * does not exist in a release build.
 */
@HiltViewModel
class DemoModeViewModel @Inject constructor(
    private val reportFall: ReportFallUseCase,
    private val reportGeofenceExit: ReportGeofenceExitUseCase,
    private val reportInactivity: ReportInactivityUseCase,
    private val triggerSos: TriggerSosUseCase,
    private val flagMissedDoses: FlagMissedDosesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DemoModeUiState())
    val uiState: StateFlow<DemoModeUiState> = _uiState.asStateFlow()

    /**
     * Simulates a fall, returning the event id so the caller can show the same
     * countdown screen the sensor path shows.
     */
    fun simulateFall() = run(EventType.FALL) {
        when (
            val result = reportFall(
                ReportFallUseCase.Params(
                    // Numbers from a real standing-height fall, so the event
                    // detail a caregiver sees is representative.
                    impactMagnitude = 28.4f,
                    orientationChangeDegrees = 78f,
                ),
            )
        ) {
            is Outcome.Success -> {
                _uiState.update { it.copy(lastFallEventId = result.data.id) }
                "Fall event created. The countdown is now running."
            }
            is Outcome.Failure -> throw DemoFailure(result.error.message)
        }
    }

    fun simulateGeofenceExit() = run(EventType.GEOFENCE_EXIT) {
        // No exit point supplied, so the use case falls back to the device's
        // current location - exactly as it does when the geofence receiver
        // fires without a triggering location.
        when (val result = reportGeofenceExit(null)) {
            is Outcome.Success -> "Safe-zone exit reported to your caregiver."
            is Outcome.Failure -> throw DemoFailure(result.error.message)
        }
    }

    fun simulateInactivity() = run(EventType.INACTIVITY) {
        when (val result = reportInactivity(120)) {
            is Outcome.Success -> "Inactivity alert raised for 2 hours without movement."
            is Outcome.Failure -> throw DemoFailure(result.error.message)
        }
    }

    fun simulateSos() = run(EventType.SOS) {
        when (val result = triggerSos()) {
            is Outcome.Success -> "SOS sent with your current location."
            is Outcome.Failure -> throw DemoFailure(result.error.message)
        }
    }

    /**
     * Sweeps for overdue doses, exactly as the periodic worker does.
     *
     * Reports honestly when there is nothing to flag, rather than inventing a
     * missed dose - a fabricated event would defeat the point of the whole
     * screen.
     */
    fun simulateMissedDose() = run(EventType.MEDICATION_MISSED) {
        when (val result = flagMissedDoses()) {
            is Outcome.Success -> if (result.data == 0) {
                "No doses are overdue. Add a medicine with a time in the past, " +
                    "wait for the reminder, then try again."
            } else {
                "${result.data} missed dose(s) reported to your caregiver."
            }
            is Outcome.Failure -> throw DemoFailure(result.error.message)
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(lastResult = null, errorMessage = null) }
    }

    fun onFallCountdownShown() {
        _uiState.update { it.copy(lastFallEventId = null) }
    }

    private fun run(type: EventType, block: suspend () -> String) {
        if (_uiState.value.runningSimulation != null) return
        _uiState.update {
            it.copy(runningSimulation = type, lastResult = null, errorMessage = null)
        }

        viewModelScope.launch {
            try {
                val message = block()
                _uiState.update { it.copy(runningSimulation = null, lastResult = message) }
            } catch (failure: DemoFailure) {
                _uiState.update {
                    it.copy(runningSimulation = null, errorMessage = failure.message)
                }
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(
                        runningSimulation = null,
                        errorMessage = throwable.message ?: "Simulation failed.",
                    )
                }
            }
        }
    }

    private class DemoFailure(override val message: String) : Exception(message)
}
