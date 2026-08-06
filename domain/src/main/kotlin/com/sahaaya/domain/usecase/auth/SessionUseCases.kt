package com.sahaaya.domain.usecase.auth

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.core.validation.Validators
import com.sahaaya.domain.model.User
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.MessagingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The signed-in user as a stream.
 *
 * The root navigation graph collects this: a `null` sends the user to the
 * welcome screen, a value routes by [com.sahaaya.domain.model.Role]. Because it
 * is observed rather than read once, a session expiring anywhere in the app
 * lands the user back on sign-in without any screen having to check.
 */
class ObserveSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): Flow<User?> = authRepository.observeCurrentUser()
}

/**
 * Signs out, unregistering this device's push token first.
 *
 * Order matters: the token must be removed while the user is still
 * authenticated, otherwise the write is rejected by the security rules and a
 * shared family phone keeps receiving the previous user's alerts.
 */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val messagingRepository: MessagingRepository,
) {
    suspend operator fun invoke(): Outcome<Unit> {
        authRepository.currentUserId()?.let { uid ->
            messagingRepository.unregisterDeviceToken(uid)
        }
        return authRepository.signOut()
    }
}

class SendPasswordResetUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String): Outcome<Unit> {
        Validators.email(email)?.let { reason ->
            return Outcome.Failure(
                AppError.Validation(
                    message = reason,
                    fieldErrors = mapOf(FIELD_EMAIL to reason),
                ),
            )
        }
        return authRepository.sendPasswordReset(email.trim())
    }

    companion object {
        const val FIELD_EMAIL = "email"
    }
}
