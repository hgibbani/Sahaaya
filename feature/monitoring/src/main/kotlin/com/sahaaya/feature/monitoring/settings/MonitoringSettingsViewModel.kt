package com.sahaaya.feature.monitoring.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.FallSensitivity
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.model.SafeZone
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.LocationRepository
import com.sahaaya.domain.usecase.monitoring.ObserveMonitoringSettingsUseCase
import com.sahaaya.domain.usecase.monitoring.SaveMonitoringSettingsUseCase
import com.sahaaya.domain.usecase.monitoring.SetSafeZoneToCurrentLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MonitoringSettingsUiState(
    val settings: MonitoringSettings? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSettingSafeZone: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val savedAtLeastOnce: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class MonitoringSettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val locationRepository: LocationRepository,
    private val observeSettings: ObserveMonitoringSettingsUseCase,
    private val saveSettings: SaveMonitoringSettingsUseCase,
    private val setSafeZoneToCurrentLocation: SetSafeZoneToCurrentLocationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitoringSettingsUiState())
    val uiState: StateFlow<MonitoringSettingsUiState> = _uiState.asStateFlow()

    private val patientId = authRepository.currentUserId()

    init {
        refreshPermissions()
        val uid = patientId
        if (uid == null) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "You are signed out.")
            }
        } else {
            viewModelScope.launch {
                observeSettings(uid).collect { settings ->
                    _uiState.update { current ->
                        current.copy(
                            // Only adopt the remote value while nothing local is
                            // in flight, so a save in progress is not overwritten
                            // by the echo of the previous value.
                            settings = if (current.isSaving) current.settings else settings,
                            isLoading = false,
                        )
                    }
                }
            }
        }
    }

    fun refreshPermissions() {
        _uiState.update {
            it.copy(
                hasLocationPermission = locationRepository.hasLocationPermission(),
                hasBackgroundLocationPermission =
                    locationRepository.hasBackgroundLocationPermission(),
            )
        }
    }

    fun setFallDetectionEnabled(enabled: Boolean) =
        mutate { it.copy(fallDetectionEnabled = enabled) }

    fun setFallSensitivity(sensitivity: FallSensitivity) =
        mutate { it.copy(fallSensitivity = sensitivity) }

    fun setInactivityEnabled(enabled: Boolean) =
        mutate { it.copy(inactivityDetectionEnabled = enabled) }

    fun setInactivityTimeout(minutes: Int) =
        mutate { it.copy(inactivityTimeoutMinutes = minutes) }

    fun setGeofenceEnabled(enabled: Boolean) =
        mutate { it.copy(geofenceEnabled = enabled) }

    fun setSafeZoneRadius(metres: Int) = mutate { current ->
        val zone = current.safeZone ?: return@mutate current
        current.copy(safeZone = zone.copy(radiusMetres = metres))
    }

    fun setMedicationRemindersEnabled(enabled: Boolean) =
        mutate { it.copy(medicationRemindersEnabled = enabled) }

    fun setMissedDoseGrace(minutes: Int) =
        mutate { it.copy(missedDoseGraceMinutes = minutes) }

    /** Pins the safe zone to wherever the phone is standing right now. */
    fun setSafeZoneHere() {
        val current = _uiState.value
        if (current.isSettingSafeZone) return

        _uiState.update {
            it.copy(isSettingSafeZone = true, errorMessage = null, infoMessage = null)
        }

        viewModelScope.launch {
            val radius = current.settings?.safeZone?.radiusMetres
                ?: SafeZone.DEFAULT_RADIUS_METRES

            when (val result = setSafeZoneToCurrentLocation(radius)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        isSettingSafeZone = false,
                        infoMessage = "Safe zone set to this location, " +
                            "${result.data.radiusMetres} m across.",
                    )
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isSettingSafeZone = false, errorMessage = result.error.message)
                }
            }
        }
    }

    /**
     * Writes the current settings and restarts the monitors to match.
     *
     * A failure here often means a permission was refused rather than a write
     * problem, so the message is surfaced rather than swallowed: the patient
     * needs to know that the thing they just switched on is not actually on.
     */
    fun save() {
        val settings = _uiState.value.settings ?: return
        if (_uiState.value.isSaving) return

        _uiState.update {
            it.copy(
                isSaving = true,
                errorMessage = null,
                infoMessage = null,
                savedAtLeastOnce = false,
            )
        }

        viewModelScope.launch {
            when (val result = saveSettings(settings)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(isSaving = false, savedAtLeastOnce = true)
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedAtLeastOnce = true,
                        errorMessage = result.error.message,
                    )
                }
            }
            refreshPermissions()
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private fun mutate(transform: (MonitoringSettings) -> MonitoringSettings) {
        _uiState.update { state ->
            val settings = state.settings ?: return@update state
            state.copy(settings = transform(settings), savedAtLeastOnce = false)
        }
    }
}
