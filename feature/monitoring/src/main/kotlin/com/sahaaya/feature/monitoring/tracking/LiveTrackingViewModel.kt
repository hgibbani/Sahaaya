package com.sahaaya.feature.monitoring.tracking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.model.SafeZone
import com.sahaaya.domain.model.SafeZoneStatus
import com.sahaaya.domain.model.TrackPoint
import com.sahaaya.domain.model.TrackingState
import com.sahaaya.domain.repository.TrackingRepository
import com.sahaaya.domain.usecase.monitoring.ObserveMonitoringSettingsUseCase
import com.sahaaya.domain.usecase.monitoring.StartTrackingUseCase
import com.sahaaya.domain.usecase.monitoring.StopTrackingUseCase
import com.sahaaya.domain.usecase.profile.ObservePatientLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveTrackingUiState(
    val patientId: String = "",
    val tracking: TrackingState = TrackingState.INACTIVE,
    val location: PatientLocation? = null,
    val route: List<TrackPoint> = emptyList(),
    val zone: SafeZone? = null,
    val geofenceEnabled: Boolean = false,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
) {
    val isTracking: Boolean get() = tracking.active

    /**
     * Whether to draw the patient as inside the zone.
     *
     * [SafeZoneStatus.UNKNOWN] is drawn as inside rather than outside. Outside
     * is the alarming colour, and showing it because a fix has not arrived yet
     * would cry wolf; the status line says "Location unavailable" in words, so
     * nothing is being hidden.
     */
    val isInsideZone: Boolean
        get() = location?.status != SafeZoneStatus.OUTSIDE

    val statusLabel: String
        get() = when {
            !geofenceEnabled || zone == null -> "No safe zone set"
            location?.status == SafeZoneStatus.OUTSIDE -> "Outside safe zone"
            location?.status == SafeZoneStatus.INSIDE -> "Inside safe zone"
            else -> "Location unavailable"
        }

    /** Metres the patient has walked this session, along the recorded route. */
    val routeDistanceMetres: Double
        get() = route.zipWithNext().sumOf { (from, to) ->
            from.point.distanceMetresTo(to.point)
        }

    /** Wall-clock length of the session so far. */
    val sessionDurationMillis: Long?
        get() = tracking.startedAtEpochMillis?.let { started ->
            val end = if (tracking.active) System.currentTimeMillis()
            else tracking.stoppedAtEpochMillis ?: System.currentTimeMillis()
            (end - started).coerceAtLeast(0)
        }
}

/**
 * The caregiver's live view of a patient they are tracking.
 *
 * Every number on this screen comes from a Firestore listener, so the map moves
 * when the patient's phone reports a new fix and nothing here polls or asks the
 * caregiver to refresh.
 *
 * The route is observed through [flatMapLatest] on the session id: when the
 * caregiver stops and starts tracking again, the new session's listener replaces
 * the old one rather than accumulating, so the map never draws two walks as one
 * continuous line.
 */
@HiltViewModel
class LiveTrackingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observePatientLocation: ObservePatientLocationUseCase,
    observeSettings: ObserveMonitoringSettingsUseCase,
    trackingRepository: TrackingRepository,
    private val startTracking: StartTrackingUseCase,
    private val stopTracking: StopTrackingUseCase,
) : ViewModel() {

    private val patientId: String = savedStateHandle[ARG_PATIENT_ID] ?: ""

    private val transient = MutableStateFlow(
        LiveTrackingUiState(patientId = patientId),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val trackingState = trackingRepository.observeTrackingState(patientId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val route = trackingState
        .flatMapLatest { state ->
            val sessionId = state.sessionId
            if (sessionId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                // Not gated on `active`: after the caregiver stops, the walk
                // they were just watching is the thing they most want to still
                // be able to look at.
                trackingRepository.observeRoute(sessionId, TrackPoint.ROUTE_LIMIT)
            }
        }

    val uiState: StateFlow<LiveTrackingUiState> = combine(
        transient,
        trackingState,
        route,
        observePatientLocation(patientId),
        observeSettings(patientId),
    ) { base, tracking, points, location, settings ->
        base.copy(
            tracking = tracking,
            route = points,
            location = location,
            zone = settings.safeZone,
            geofenceEnabled = settings.geofenceEnabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = LiveTrackingUiState(patientId = patientId),
    )

    fun onStartTracking() {
        if (patientId.isBlank()) return
        transient.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            val result = startTracking(patientId)
            transient.update {
                it.copy(
                    isBusy = false,
                    errorMessage = (result as? Outcome.Failure)?.error?.message,
                )
            }
        }
    }

    fun onStopTracking() {
        if (patientId.isBlank()) return
        transient.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            val result = stopTracking(patientId)
            transient.update {
                it.copy(
                    isBusy = false,
                    errorMessage = (result as? Outcome.Failure)?.error?.message,
                )
            }
        }
    }

    fun onMessageShown() {
        transient.update { it.copy(errorMessage = null) }
    }

    companion object {
        const val ARG_PATIENT_ID = "patientId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
