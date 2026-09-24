package com.sahaaya.feature.monitoring.safezone

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.model.SafeZone
import com.sahaaya.domain.usecase.monitoring.ClearSafeZoneUseCase
import com.sahaaya.domain.usecase.monitoring.ObserveMonitoringSettingsUseCase
import com.sahaaya.domain.usecase.monitoring.SetSafeZoneForPatientUseCase
import com.sahaaya.domain.usecase.profile.ObservePatientLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CaregiverSafeZoneUiState(
    val patientLocation: PatientLocation? = null,
    val savedZone: SafeZone? = null,
    val geofenceEnabled: Boolean = false,
    val selectedRadiusMetres: Int = SafeZone.DEFAULT_RADIUS_METRES,
    val isSaving: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
) {
    /**
     * A zone can only be created once the patient's phone has reported at least
     * one position, because that position is the centre.
     *
     * This is the deliberate consequence of not adding a map picker: without a
     * map the caregiver has no way to point at a spot, so the patient's own last
     * known location is the one trustworthy centre available.
     */
    val canSave: Boolean get() = patientLocation != null && !isSaving
}

/**
 * Lets a caregiver draw the safe zone for a patient they are linked to.
 *
 * The centre is the patient's most recent position rather than a point picked on
 * a map. That is a real constraint, not a shortcut: this project has no Maps SDK
 * and adding one would need an API key and a billing account on the Google Cloud
 * project. Anchoring to the patient's own last fix gives the caregiver a centre
 * they can trust without any of that.
 */
@HiltViewModel
class CaregiverSafeZoneViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observePatientLocation: ObservePatientLocationUseCase,
    observeSettings: ObserveMonitoringSettingsUseCase,
    private val setSafeZone: SetSafeZoneForPatientUseCase,
    private val clearSafeZone: ClearSafeZoneUseCase,
) : ViewModel() {

    private val patientId: String =
        savedStateHandle.get<String>(ARG_PATIENT_ID).orEmpty()

    private val transient = MutableStateFlow(Transient())

    private data class Transient(
        val radius: Int? = null,
        val isSaving: Boolean = false,
        val message: String? = null,
        val errorMessage: String? = null,
    )

    val uiState: StateFlow<CaregiverSafeZoneUiState> = combine(
        observePatientLocation(patientId),
        observeSettings(patientId),
        transient,
    ) { location, settings, extra ->
        CaregiverSafeZoneUiState(
            patientLocation = location,
            savedZone = settings.safeZone,
            geofenceEnabled = settings.geofenceEnabled,
            // The caregiver's in-progress choice wins over the stored one, so
            // moving the slider does not snap back on the next Firestore push.
            selectedRadiusMetres = extra.radius
                ?: settings.safeZone?.radiusMetres
                ?: SafeZone.DEFAULT_RADIUS_METRES,
            isSaving = extra.isSaving,
            message = extra.message,
            errorMessage = extra.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CaregiverSafeZoneUiState(),
    )

    fun setRadius(metres: Int) {
        transient.update { it.copy(radius = metres) }
    }

    /** Centres the zone on wherever the patient's phone last reported. */
    fun saveHere() {
        val location = uiState.value.patientLocation ?: return
        if (transient.value.isSaving) return

        transient.update { it.copy(isSaving = true, message = null, errorMessage = null) }

        viewModelScope.launch {
            val zone = SafeZone(
                centre = location.point,
                radiusMetres = uiState.value.selectedRadiusMetres,
                label = "Home",
            )
            when (val result = setSafeZone(patientId, zone)) {
                is Outcome.Success -> transient.update {
                    it.copy(
                        isSaving = false,
                        message = "Safe zone saved. The patient's phone will start " +
                            "watching it within a couple of minutes.",
                    )
                }
                is Outcome.Failure -> transient.update {
                    it.copy(isSaving = false, errorMessage = result.error.message)
                }
            }
        }
    }

    fun turnOff() {
        if (transient.value.isSaving) return
        transient.update { it.copy(isSaving = true, message = null, errorMessage = null) }

        viewModelScope.launch {
            when (val result = clearSafeZone(patientId)) {
                is Outcome.Success -> transient.update {
                    it.copy(isSaving = false, message = "Safe-zone alerts turned off.")
                }
                is Outcome.Failure -> transient.update {
                    it.copy(isSaving = false, errorMessage = result.error.message)
                }
            }
        }
    }

    fun onMessageShown() {
        transient.update { it.copy(message = null, errorMessage = null) }
    }

    companion object {
        const val ARG_PATIENT_ID = "patientId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
