package com.sahaaya.feature.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.Role
import com.sahaaya.domain.usecase.auth.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Everything the registration screen renders, in one immutable object.
 *
 * A single state object rather than several flows means the screen can never
 * paint a half-updated combination - "submitting" with a stale error still on
 * screen, for instance.
 */
data class RegisterUiState(
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting &&
            displayName.isNotBlank() &&
            email.isNotBlank() &&
            phoneNumber.isNotBlank() &&
            password.isNotBlank() &&
            confirmPassword.isNotBlank()
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    // Each edit clears that field's error. Leaving a stale error under a field
    // the user has just corrected reads as the app not listening.
    fun onDisplayNameChange(value: String) = update(RegisterUseCase.FIELD_NAME) {
        it.copy(displayName = value)
    }

    fun onEmailChange(value: String) = update(RegisterUseCase.FIELD_EMAIL) {
        it.copy(email = value)
    }

    fun onPhoneNumberChange(value: String) = update(RegisterUseCase.FIELD_PHONE) {
        it.copy(phoneNumber = value)
    }

    fun onPasswordChange(value: String) = update(RegisterUseCase.FIELD_PASSWORD) {
        it.copy(password = value)
    }

    fun onConfirmPasswordChange(value: String) =
        update(RegisterUseCase.FIELD_CONFIRM_PASSWORD) {
            it.copy(confirmPassword = value)
        }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Submits the form.
     *
     * There is no success signal back to the screen: the root graph observes
     * the session and moves the user to their dashboard the moment the account
     * exists. Success here is simply the screen ceasing to be on the back stack.
     */
    fun submit(role: Role) {
        if (!_uiState.value.canSubmit) return

        _uiState.update {
            it.copy(isSubmitting = true, errorMessage = null, fieldErrors = emptyMap())
        }

        viewModelScope.launch {
            val current = _uiState.value
            val result = registerUseCase(
                RegisterUseCase.Params(
                    email = current.email,
                    password = current.password,
                    confirmPassword = current.confirmPassword,
                    displayName = current.displayName,
                    phoneNumber = current.phoneNumber,
                    role = role,
                ),
            )

            when (result) {
                is Outcome.Success -> _uiState.update { it.copy(isSubmitting = false) }
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

    private fun update(clearedField: String, transform: (RegisterUiState) -> RegisterUiState) {
        _uiState.update { state ->
            transform(state).copy(fieldErrors = state.fieldErrors - clearedField)
        }
    }
}
