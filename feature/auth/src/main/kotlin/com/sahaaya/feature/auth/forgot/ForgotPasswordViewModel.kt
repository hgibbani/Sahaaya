package com.sahaaya.feature.auth.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.usecase.auth.SendPasswordResetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotPasswordUiState(
    val email: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val isSent: Boolean = false,
) {
    val canSubmit: Boolean get() = !isSubmitting && email.isNotBlank()
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val sendPasswordResetUseCase: SendPasswordResetUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                isSent = false,
                fieldErrors = it.fieldErrors - SendPasswordResetUseCase.FIELD_EMAIL,
            )
        }
    }

    fun submit() {
        if (!_uiState.value.canSubmit) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = sendPasswordResetUseCase(_uiState.value.email)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(isSubmitting = false, isSent = true)
                }

                is Outcome.Failure -> _uiState.update { state ->
                    state.copy(
                        isSubmitting = false,
                        errorMessage = result.error.message,
                        fieldErrors = (result.error as? AppError.Validation)
                            ?.fieldErrors
                            .orEmpty(),
                    )
                }
            }
        }
    }
}
