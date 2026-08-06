package com.sahaaya.feature.dashboard.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.EmergencyContact
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.PatientProfile
import com.sahaaya.domain.model.User
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.usecase.auth.SignOutUseCase
import com.sahaaya.domain.usecase.medication.ObservePendingDosesUseCase
import com.sahaaya.domain.usecase.monitoring.ObserveMonitoringSettingsUseCase
import com.sahaaya.domain.usecase.monitoring.TriggerSosUseCase
import com.sahaaya.domain.usecase.pairing.ObservePatientCaregiversUseCase
import com.sahaaya.domain.usecase.profile.ObserveEmergencyContactsUseCase
import com.sahaaya.domain.usecase.profile.ObservePatientProfileUseCase
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

data class PatientDashboardUiState(
    val user: User? = null,
    val profile: PatientProfile? = null,
    val caregivers: List<Pairing> = emptyList(),
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val settings: MonitoringSettings? = null,
    val pendingDoseCount: Int = 0,
    val isLoading: Boolean = true,
    val isSendingSos: Boolean = false,
    val sosSentMessage: String? = null,
    val errorMessage: String? = null,
) {
    val needsProfileCompletion: Boolean
        get() = profile?.isSufficientForCare == false

    val hasNoCaregiver: Boolean get() = caregivers.isEmpty()

    val hasNoEmergencyContact: Boolean get() = emergencyContacts.isEmpty()

    /**
     * Plain-language statement of what the phone is currently watching.
     *
     * The patient should never have to guess whether monitoring is on. Silence
     * about it would be indistinguishable from it being broken.
     */
    val monitoringSummary: String?
        get() {
            val current = settings ?: return null
            val active = buildList {
                if (current.fallDetectionEnabled) add("falls")
                if (current.geofenceEnabled && current.safeZone != null) add("your safe zone")
                if (current.inactivityDetectionEnabled) add("long periods without movement")
            }
            return if (active.isEmpty()) {
                "Monitoring is switched off. Tap Monitoring settings to turn it on."
            } else {
                "Sahaaya is watching for " + when (active.size) {
                    1 -> active[0]
                    2 -> "${active[0]} and ${active[1]}"
                    else -> "${active.dropLast(1).joinToString(", ")} and ${active.last()}"
                } + "."
            }
        }
}

@HiltViewModel
class PatientDashboardViewModel @Inject constructor(
    authRepository: AuthRepository,
    observePatientProfile: ObservePatientProfileUseCase,
    observeCaregivers: ObservePatientCaregiversUseCase,
    observeEmergencyContacts: ObserveEmergencyContactsUseCase,
    observeSettings: ObserveMonitoringSettingsUseCase,
    observePendingDoses: ObservePendingDosesUseCase,
    private val triggerSos: TriggerSosUseCase,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {

    private val uid = authRepository.currentUserId()

    private val transient = MutableStateFlow(TransientState())

    private data class TransientState(
        val isSendingSos: Boolean = false,
        val sosSentMessage: String? = null,
        val errorMessage: String? = null,
    )

    val uiState: StateFlow<PatientDashboardUiState> =
        if (uid == null) {
            flowOf(
                PatientDashboardUiState(
                    isLoading = false,
                    errorMessage = "You are signed out. Please sign in again.",
                ),
            )
        } else {
            combine(
                observePatientProfile(uid),
                observeCaregivers(uid),
                observeEmergencyContacts(uid),
                observeSettings(uid),
                observePendingDoses(uid),
                transient,
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val snapshot =
                    values[0] as com.sahaaya.domain.usecase.profile.PatientProfileSnapshot
                @Suppress("UNCHECKED_CAST")
                val caregivers = values[1] as List<Pairing>
                @Suppress("UNCHECKED_CAST")
                val contacts = values[2] as List<EmergencyContact>
                val settings = values[3] as MonitoringSettings
                @Suppress("UNCHECKED_CAST")
                val pending = values[4] as List<com.sahaaya.domain.model.MedicationDose>
                val extra = values[5] as TransientState

                PatientDashboardUiState(
                    user = snapshot.user,
                    profile = snapshot.profile,
                    caregivers = caregivers,
                    emergencyContacts = contacts,
                    settings = settings,
                    pendingDoseCount = pending.size,
                    isLoading = false,
                    isSendingSos = extra.isSendingSos,
                    sosSentMessage = extra.sosSentMessage,
                    errorMessage = extra.errorMessage,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PatientDashboardUiState(),
        )

    /**
     * Sends the SOS and waits for the write to land before reporting success.
     *
     * A fire-and-forget SOS that silently failed would be the worst bug in the
     * app, so the button stays busy until Firestore has actually accepted it.
     */
    fun triggerSos() {
        if (transient.value.isSendingSos) return
        transient.update {
            it.copy(isSendingSos = true, sosSentMessage = null, errorMessage = null)
        }

        viewModelScope.launch {
            when (val result = triggerSos.invoke()) {
                is Outcome.Success -> transient.update {
                    it.copy(
                        isSendingSos = false,
                        sosSentMessage = "Your caregiver has been alerted and sent " +
                            "your location.",
                    )
                }

                is Outcome.Failure -> transient.update {
                    it.copy(
                        isSendingSos = false,
                        errorMessage = "Could not send the alert. ${result.error.message}",
                    )
                }
            }
        }
    }

    fun onMessageShown() {
        transient.update { it.copy(sosSentMessage = null, errorMessage = null) }
    }

    fun signOut() {
        viewModelScope.launch {
            when (val result = signOutUseCase()) {
                is Outcome.Success -> transient.update { it.copy(errorMessage = null) }
                is Outcome.Failure -> transient.update {
                    it.copy(errorMessage = result.error.message)
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
