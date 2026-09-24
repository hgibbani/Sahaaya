package com.sahaaya.feature.dashboard.caregiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.CaregiverProfile
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.model.SafeZone
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.User
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.usecase.auth.SignOutUseCase
import com.sahaaya.domain.usecase.event.AcknowledgeAllEventsUseCase
import com.sahaaya.domain.usecase.event.ObserveCaregiverTimelineUseCase
import com.sahaaya.domain.usecase.monitoring.ObserveMonitoringSettingsUseCase
import com.sahaaya.domain.model.TrackingState
import com.sahaaya.domain.repository.TrackingRepository
import com.sahaaya.domain.usecase.monitoring.StartTrackingUseCase
import com.sahaaya.domain.usecase.monitoring.StopTrackingUseCase
import com.sahaaya.domain.usecase.profile.ObservePatientLocationUseCase
import com.sahaaya.domain.usecase.pairing.ObserveCaregiverPatientsUseCase
import com.sahaaya.domain.usecase.pairing.RevokePairingUseCase
import com.sahaaya.domain.usecase.profile.ObserveCaregiverProfileUseCase
import com.sahaaya.feature.dashboard.alerts.InAppAlertNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
    val isClearingAll: Boolean = false,
) {
    /** Ids "Clear all" would acknowledge - the outstanding ones only. */
    val clearableEventIds: List<String>
        get() = recentEvents.filter { it.isUnresolved }.map { it.id }

    val canClearAll: Boolean get() = clearableEventIds.isNotEmpty() && !isClearingAll

    val hasNoPatients: Boolean get() = patients.isEmpty()

    /** Events nobody has taken responsibility for yet. */
    val unresolvedEvents: List<HealthEvent>
        get() = recentEvents.filter { it.isUnresolved }

    val hasCriticalUnresolved: Boolean
        get() = unresolvedEvents.any { it.isCritical }

    /** The three newest outstanding alerts, for the dashboard preview. */
    val topUnresolved: List<HealthEvent> get() = unresolvedEvents.take(3)

    /**
     * The most recent event that carried coordinates.
     *
     * Sahaaya does not stream continuous location - a fix is taken at the
     * moment an alert is raised - so "where is the patient" is answered by the
     * newest located event rather than by a live tracker. Saying so plainly
     * beats implying a tracking capability that does not exist.
     */
    val latestLocatedEvent: HealthEvent?
        get() = recentEvents.firstOrNull { it.location != null }
}

@HiltViewModel
class CaregiverDashboardViewModel @Inject constructor(
    authRepository: AuthRepository,
    observeCaregiverProfile: ObserveCaregiverProfileUseCase,
    observePatients: ObserveCaregiverPatientsUseCase,
    observeTimeline: ObserveCaregiverTimelineUseCase,
    private val observePatientLocation: ObservePatientLocationUseCase,
    private val observePatientSettings: ObserveMonitoringSettingsUseCase,
    private val alertNotifier: InAppAlertNotifier,
    private val acknowledgeAllEvents: AcknowledgeAllEventsUseCase,
    private val revokePairingUseCase: RevokePairingUseCase,
    private val trackingRepository: TrackingRepository,
    private val startTrackingUseCase: StartTrackingUseCase,
    private val stopTrackingUseCase: StopTrackingUseCase,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {

    private val uid = authRepository.currentUserId()

    private val transientError = MutableStateFlow<String?>(null)
    private val unlinking = MutableStateFlow<String?>(null)
    private val clearingAll = MutableStateFlow(false)

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
                clearingAll,
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val snapshot =
                    values[0] as com.sahaaya.domain.usecase.profile.CaregiverProfileSnapshot
                @Suppress("UNCHECKED_CAST")
                val patients = values[1] as List<Pairing>
                @Suppress("UNCHECKED_CAST")
                val events = values[2] as List<HealthEvent>
                val error = values[3] as String?
                val unlinkingId = values[4] as String?
                val isClearingAll = values[5] as Boolean
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
                    isClearingAll = isClearingAll,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CaregiverDashboardUiState(),
        )

    /**
     * The first linked patient's live position and the zone it is judged
     * against.
     *
     * Kept out of [uiState] rather than folded into its `combine`: those five
     * sources decide whether the dashboard can render at all, and a patient who
     * has never sent a fix would otherwise hold the whole screen on its
     * spinner. This pair simply arrives later and fills the card in.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val patientLocation: StateFlow<PatientLocation?> =
        uiState
            .map { it.patients.firstOrNull { pairing -> pairing.isActive }?.patientId }
            .distinctUntilChanged()
            .flatMapLatest { patientId ->
                if (patientId == null) flowOf(null) else observePatientLocation(patientId)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null,
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val safeZone: StateFlow<SafeZone?> =
        uiState
            .map { it.patients.firstOrNull { pairing -> pairing.isActive }?.patientId }
            .distinctUntilChanged()
            .flatMapLatest { patientId ->
                if (patientId == null) {
                    flowOf(null)
                } else {
                    observePatientSettings(patientId).map { settings ->
                        settings.safeZone.takeIf { settings.geofenceEnabled }
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null,
            )

    /**
     * Whether a caregiver currently has live tracking on for their patient.
     *
     * Read from Firestore rather than held locally, so the dashboard is right
     * even when tracking was started from this caregiver's other device - or
     * from the live map screen rather than from here.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val trackingState: StateFlow<TrackingState> =
        uiState
            .map { it.patients.firstOrNull { pairing -> pairing.isActive }?.patientId }
            .distinctUntilChanged()
            .flatMapLatest { patientId ->
                if (patientId == null) {
                    flowOf(TrackingState.INACTIVE)
                } else {
                    trackingRepository.observeTrackingState(patientId)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = TrackingState.INACTIVE,
            )

    /** Starts live tracking for the linked patient. Caregiver-only by design. */
    fun startTracking(patientId: String) {
        viewModelScope.launch {
            val result = startTrackingUseCase(patientId)
            if (result is Outcome.Failure) {
                transientError.update { result.error.message }
            }
        }
    }

    /** Stops it. The patient has no equivalent of this method anywhere. */
    fun stopTracking(patientId: String) {
        viewModelScope.launch {
            val result = stopTrackingUseCase(patientId)
            if (result is Outcome.Failure) {
                transientError.update { result.error.message }
            }
        }
    }

    /**
     * Marks every outstanding alert as seen, in one action.
     *
     * Same [AcknowledgeAllEventsUseCase] the Alerts screen uses - this is a
     * second entry point to one action, not a second implementation. It is
     * offered here because this dashboard is where the backlog is actually
     * visible, and clearing sixty alerts one at a time is not a workflow.
     */
    /** Marks one alert as seen - the same acknowledge path as Clear all. */
    fun acknowledge(eventId: String) {
        viewModelScope.launch {
            when (val result = acknowledgeAllEvents(listOf(eventId))) {
                is Outcome.Success -> if (result.data > 0) {
                    transientError.update { "Could not mark that alert as seen." }
                }
                is Outcome.Failure -> transientError.update { result.error.message }
            }
        }
    }

    fun clearAllAlerts() {
        val ids = uiState.value.clearableEventIds
        if (ids.isEmpty() || clearingAll.value) return
        clearingAll.update { true }

        viewModelScope.launch {
            when (val result = acknowledgeAllEvents(ids)) {
                is Outcome.Success -> transientError.update {
                    if (result.data == 0) {
                        null
                    } else {
                        "${result.data} of ${ids.size} alert(s) could not be cleared."
                    }
                }
                is Outcome.Failure -> transientError.update { result.error.message }
            }
            clearingAll.update { false }
        }
    }

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
