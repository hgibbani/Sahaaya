package com.sahaaya.feature.pairing.caregiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.core.validation.Validators
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.usecase.pairing.RedeemPairingCodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RedeemCodeUiState(
    val code: String = "",
    val fieldError: String? = null,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val linkedPatientName: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && code.length == Validators.PAIRING_CODE_LENGTH
}

@HiltViewModel
class RedeemCodeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val redeemPairingCode: RedeemPairingCodeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RedeemCodeUiState())
    val uiState: StateFlow<RedeemCodeUiState> = _uiState.asStateFlow()

    /**
     * Normalises as the caregiver types.
     *
     * The code is upper-cased and stripped of anything outside the alphabet,
     * because it is usually being typed from something heard over a phone -
     * lowercase, spaces and hyphens are all likely and none of them should
     * cause a rejection.
     */
    fun onCodeChange(value: String) {
        val cleaned = value
            .uppercase()
            .filter { it in PairingAlphabet }
            .take(Validators.PAIRING_CODE_LENGTH)
        _uiState.update {
            it.copy(code = cleaned, fieldError = null, errorMessage = null)
        }
    }

    fun submit() {
        if (!_uiState.value.canSubmit) return

        val caregiverId = authRepository.currentUserId()
        if (caregiverId == null) {
            _uiState.update {
                it.copy(errorMessage = "You are signed out. Please sign in again.")
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            val result = redeemPairingCode(
                RedeemPairingCodeUseCase.Params(
                    code = _uiState.value.code,
                    caregiverId = caregiverId,
                ),
            )

            when (result) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        linkedPatientName = result.data.patientName
                            .ifBlank { "your patient" },
                    )
                }

                is Outcome.Failure -> _uiState.update { state ->
                    state.copy(
                        isSubmitting = false,
                        errorMessage = result.error.message,
                        fieldError = (result.error as? AppError.Validation)
                            ?.fieldErrors
                            ?.get(RedeemPairingCodeUseCase.FIELD_CODE),
                    )
                }
            }
        }
    }

    fun onLinkAcknowledged() {
        _uiState.update { RedeemCodeUiState() }
    }

    private companion object {
        val PairingAlphabet = com.sahaaya.domain.model.PairingCode.ALPHABET.toSet()
    }
}
