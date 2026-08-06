package com.sahaaya.domain.usecase.auth

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.core.result.flatMap
import com.sahaaya.core.validation.Validators
import com.sahaaya.domain.model.User
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.MessagingRepository
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val messagingRepository: MessagingRepository,
) {

    data class Params(val email: String, val password: String)

    suspend operator fun invoke(params: Params): Outcome<User> {
        val errors = buildMap {
            Validators.email(params.email)?.let { put(FIELD_EMAIL, it) }
            if (params.password.isEmpty()) put(FIELD_PASSWORD, "Password is required")
        }
        if (errors.isNotEmpty()) {
            return Outcome.Failure(
                AppError.Validation(
                    message = "Please correct the highlighted fields.",
                    fieldErrors = errors,
                ),
            )
        }

        return authRepository
            .signIn(params.email.trim(), params.password)
            .flatMap { user ->
                // Tokens rotate; refresh on every sign-in so the account on this
                // device is always the one that receives its alerts.
                messagingRepository.registerDeviceToken(user.uid)
                Outcome.Success(user)
            }
    }

    companion object {
        const val FIELD_EMAIL = "email"
        const val FIELD_PASSWORD = "password"
    }
}
