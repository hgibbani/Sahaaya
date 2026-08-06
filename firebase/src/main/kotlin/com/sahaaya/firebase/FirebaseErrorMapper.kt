package com.sahaaya.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.sahaaya.core.result.AppError
import java.util.concurrent.TimeoutException

/**
 * Translates Firebase exceptions into [AppError].
 *
 * This is the boundary: above it, nothing knows what Firebase is or what its
 * error codes mean. It also means the user is never shown a raw SDK message -
 * a caregiver reading "ERROR_INVALID_LOGIN_CREDENTIALS" at 2am learns nothing.
 */
object FirebaseErrorMapper {

    fun map(throwable: Throwable): AppError = when (throwable) {

        is FirebaseNetworkException ->
            AppError.Network(cause = throwable)

        is TimeoutException ->
            AppError.Timeout(cause = throwable)

        is FirebaseTooManyRequestsException ->
            AppError.TooManyRequests(cause = throwable)

        is FirebaseAuthWeakPasswordException ->
            AppError.WeakPassword(cause = throwable)

        is FirebaseAuthUserCollisionException ->
            AppError.EmailAlreadyInUse(cause = throwable)

        // Fires for a malformed email and for a wrong password alike. We do not
        // distinguish them to the user on purpose: saying which one was wrong
        // tells an attacker whether an account exists.
        is FirebaseAuthInvalidCredentialsException ->
            AppError.InvalidCredentials(cause = throwable)

        // Disabled or deleted account.
        is FirebaseAuthInvalidUserException ->
            AppError.InvalidCredentials(cause = throwable)

        is FirebaseFirestoreException -> mapFirestore(throwable)

        else -> AppError.Unknown(cause = throwable)
    }

    private fun mapFirestore(exception: FirebaseFirestoreException): AppError =
        when (exception.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                AppError.PermissionDenied(cause = exception)

            FirebaseFirestoreException.Code.NOT_FOUND ->
                AppError.NotFound(cause = exception)

            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                AppError.NotAuthenticated(cause = exception)

            FirebaseFirestoreException.Code.ALREADY_EXISTS ->
                AppError.Conflict("That already exists.", exception)

            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                AppError.Timeout(cause = exception)

            FirebaseFirestoreException.Code.UNAVAILABLE ->
                AppError.Network(cause = exception)

            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                AppError.TooManyRequests(cause = exception)

            else -> AppError.Unknown(cause = exception)
        }
}
