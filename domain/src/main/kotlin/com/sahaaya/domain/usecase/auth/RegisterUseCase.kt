package com.sahaaya.domain.usecase.auth

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.core.result.flatMap
import com.sahaaya.core.validation.Validators
import com.sahaaya.domain.model.Role
import com.sahaaya.domain.model.User
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.MessagingRepository
import javax.inject.Inject

/**
 * Creates an account.
 *
 * Validation lives here rather than in the ViewModel so that the same rules
 * apply no matter which screen (or future entry point) creates an account, and
 * so they can be unit tested without Android.
 *
 * Registering also registers the device for push. That happens now, at sign-up,
 * because a caregiver who never opens settings must still be reachable the first
 * time something goes wrong.
 */
class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val messagingRepository: MessagingRepository,
) {

    data class Params(
        val email: String,
        val password: String,
        val confirmPassword: String,
        val displayName: String,
        val phoneNumber: String,
        val role: Role,
    )

    suspend operator fun invoke(params: Params): Outcome<User> {
        validate(params)?.let { return Outcome.Failure(it) }

        return authRepository.register(
            email = params.email.trim(),
            password = params.password,
            displayName = params.displayName.trim(),
            phoneNumber = params.phoneNumber.trim(),
            role = params.role,
        ).flatMap { user ->
            // A failure to register the token must not fail the sign-up: the
            // account exists and is usable. The token is retried on next launch.
            messagingRepository.registerDeviceToken(user.uid)
            Outcome.Success(user)
        }
    }

    private fun validate(params: Params): AppError.Validation? {
        val errors = buildMap {
            Validators.fullName(params.displayName)?.let { put(FIELD_NAME, it) }
            Validators.email(params.email)?.let { put(FIELD_EMAIL, it) }
            Validators.phoneNumber(params.phoneNumber)?.let { put(FIELD_PHONE, it) }
            Validators.password(params.password)?.let { put(FIELD_PASSWORD, it) }
            Validators.confirmPassword(params.password, params.confirmPassword)
                ?.let { put(FIELD_CONFIRM_PASSWORD, it) }
        }
        return if (errors.isEmpty()) {
            null
        } else {
            AppError.Validation(
                message = "Please correct the highlighted fields.",
                fieldErrors = errors,
            )
        }
    }

    companion object {
        const val FIELD_NAME = "displayName"
        const val FIELD_EMAIL = "email"
        const val FIELD_PHONE = "phoneNumber"
        const val FIELD_PASSWORD = "password"
        const val FIELD_CONFIRM_PASSWORD = "confirmPassword"
    }
}
