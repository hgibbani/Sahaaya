package com.sahaaya.feature.dashboard.caregiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.CaregiverProfile
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.User
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.usecase.auth.SignOutUseCase
import com.sahaaya.domain.usecase.event.ObserveCaregiverTimelineUseCase
import com.sahaaya.domain.usecase.pairing.ObserveCaregiverPatientsUseCase
import com.sahaaya.domain.usecase.pairing.RevokePairingUseCase
import com.sahaaya.domain.usecase.profile.ObserveCaregiverProfileUseCase
import com.sahaaya.feature.dashboard.alerts.InAppAlertNotifier
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

data class CaregiverDashboardUiState(
    val user: User? = null,
    val profile: CaregiverProfile? = null,
    val patients: List<Pairing> = emptyList(),
    val recentEvents: List<HealthEvent> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val unlinkingPairingId: String? = null,
) {
    val hasNoPatients: Boolean get() = patients.isEmpty()

    /** Events nobody has taken responsibility for yet. */
    val unresolvedEvents: List<HealthEvent>
        get() = recentEvents.filter { it.isUnresolved }

    val hasCriticalUnresolved: Boolean
        get() = unresolvedEvents.any { it.isCritical }

    /** The three newest outstanding alerts, for the dashboard preview. */
    val topUnresolved: List<HealthEvent> get() = unresolvedEvents.take(3)
}

@HiltViewModel
class CaregiverDashboardViewModel @Inject constructor(
    authRepository: AuthRepository,
    observeCaregiverProfile: ObserveCaregiverProfileUseCase,
    observePatients: ObserveCaregiverPatientsUseCase,
    observeTimeline: ObserveCaregiverTimelineUseCase,
    private val alertNotifier: InAppAlertNotifier,
    private val revokePairingUseCase: RevokePairingUseCase,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {

    private val uid = authRepository.currentUserId()

    private val transientError = MutableStateFlow<String?>(null)
    private val unlinking = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CaregiverDashboardUiState> =
        if (uid == null) {
            flowOf(
                CaregiverDashboardUiState(
                    isLoading = false,
                    errorMessage = "You are signed out. Please sign in again.",
                ),
            )
        } else {
            combine(
                observeCaregiverProfile(uid),
                observePatients(uid),
                observeTimeline(uid),
                transientError,
                unlinking,
            ) { snapshot, patients, events, error, unlinkingId ->
                CaregiverDashboardUiState(
                    user = snapshot.user,
                    profile = snapshot.profile,
                    patients = patients,
                    recentEvents = events.sortedByDescending { it.occurredAtEpochMillis }
                        .also {
                            // Spark-plan fallback: raise a local notification for
                            // anything new while the app is running. The Cloud
                            // Function in functions/ is the real delivery path -
                            // see InAppAlertNotifier for why both exist.
                            alertNotifier.notifyNewEvents(it)
                        },
                    isLoading = false,
                    errorMessage = error,
                    unlinkingPairingId = unlinkingId,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CaregiverDashboardUiState(),
        )

    /**
     * Ends the link with a patient.
     *
     * No optimistic update: the list is driven by the Firestore listener, so the
     * row disappears only once the write has actually landed. For something that
     * decides who receives a fall alert, the screen must not claim a change that
     * has not happened.
     */
    fun unlinkPatient(pairingId: String) {
        if (unlinking.value != null) return
        unlinking.update { pairingId }

        viewModelScope.launch {
            when (val result = revokePairingUseCase(pairingId)) {
                is Outcome.Success -> transientError.update { null }
                is Outcome.Failure -> transientError.update { result.error.message }
            }
            unlinking.update { null }
        }
    }

    fun onErrorShown() {
        transientError.update { null }
    }

    fun signOut() {
        viewModelScope.launch {
            when (val result = signOutUseCase()) {
                is Outcome.Success -> transientError.update { null }
                is Outcome.Failure -> transientError.update { result.error.message }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
