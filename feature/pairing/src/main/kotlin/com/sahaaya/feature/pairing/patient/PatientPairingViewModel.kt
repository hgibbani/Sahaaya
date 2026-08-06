package com.sahaaya.feature.pairing.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.PairingCode
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.usecase.pairing.GeneratePairingCodeUseCase
import com.sahaaya.domain.usecase.pairing.ObserveActivePairingCodeUseCase
import com.sahaaya.domain.usecase.pairing.ObservePatientCaregiversUseCase
import com.sahaaya.domain.usecase.pairing.RevokePairingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientPairingUiState(
    val code: PairingCode? = null,
    val remainingSeconds: Int = 0,
    val caregivers: List<Pairing> = emptyList(),
    val isGenerating: Boolean = false,
    val revokingPairingId: String? = null,
    val errorMessage: String? = null,
) {
    val hasLiveCode: Boolean get() = code != null && remainingSeconds > 0
}

@HiltViewModel
class PatientPairingViewModel @Inject constructor(
    authRepository: AuthRepository,
    observeActiveCode: ObserveActivePairingCodeUseCase,
    observeCaregivers: ObservePatientCaregiversUseCase,
    private val generatePairingCode: GeneratePairingCodeUseCase,
    private val revokePairing: RevokePairingUseCase,
) : ViewModel() {

    private val uid = authRepository.currentUserId()

    private val generating = MutableStateFlow(false)
    private val revoking = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)

    /**
     * Ticks once a second so the countdown on screen is real.
     *
     * A code that says "expires in 14:59" while actually being dead is worse
     * than no timer: the patient reads out a code, the caregiver is told it is
     * invalid, and neither knows why. The clock drives the display.
     */
    private val nowMillis = MutableStateFlow(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            while (isActive) {
                nowMillis.value = System.currentTimeMillis()
                delay(TICK_MILLIS)
            }
        }
    }

    val uiState: StateFlow<PatientPairingUiState> =
        if (uid == null) {
            flowOf(
                PatientPairingUiState(
                    errorMessage = "You are signed out. Please sign in again.",
                ),
            )
        } else {
            combine(
                observeActiveCode(uid),
                observeCaregivers(uid),
                nowMillis,
                generating,
                revoking,
            ) { code, caregivers, now, isGenerating, revokingId ->
                val remaining = code?.remainingMillis(now)?.div(1000)?.toInt() ?: 0
                PatientPairingUiState(
                    code = code?.takeIf { remaining > 0 },
                    remainingSeconds = remaining,
                    caregivers = caregivers,
                    isGenerating = isGenerating,
                    revokingPairingId = revokingId,
                    errorMessage = error.value,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PatientPairingUiState(),
        )

    fun generateCode() {
        val patientId = uid ?: return
        if (generating.value) return

        generating.update { true }
        error.update { null }

        viewModelScope.launch {
            when (val result = generatePairingCode(patientId)) {
                is Outcome.Success -> error.update { null }
                is Outcome.Failure -> error.update { result.error.message }
            }
            generating.update { false }
            nowMillis.value = System.currentTimeMillis()
        }
    }

    fun revokeCaregiver(pairingId: String) {
        if (revoking.value != null) return
        revoking.update { pairingId }

        viewModelScope.launch {
            when (val result = revokePairing(pairingId)) {
                is Outcome.Success -> error.update { null }
                is Outcome.Failure -> error.update { result.error.message }
            }
            revoking.update { null }
        }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
