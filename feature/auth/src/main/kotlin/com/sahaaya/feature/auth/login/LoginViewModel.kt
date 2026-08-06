package com.sahaaya.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.usecase.auth.SignInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && email.isNotBlank() && password.isNotBlank()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInUseCase: SignInUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(email = value, fieldErrors = it.fieldErrors - SignInUseCase.FIELD_EMAIL)
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                fieldErrors = it.fieldErrors - SignInUseCase.FIELD_PASSWORD,
            )
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun submit() {
        if (!_uiState.value.canSubmit) return

        _uiState.update {
            it.copy(isSubmitting = true, errorMessage = null, fieldErrors = emptyMap())
        }

        viewModelScope.launch {
            val current = _uiState.value
            val result = signInUseCase(
                SignInUseCase.Params(email = current.email, password = current.password),
            )

            when (result) {
                // The root graph reacts to the session change and navigates.
                is Outcome.Success -> _uiState.update {
                    it.copy(isSubmitting = false, password = "")
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
