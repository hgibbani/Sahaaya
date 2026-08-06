package com.sahaaya.core.result

/**
 * Every failure the user can encounter, expressed in the app's own vocabulary.
 *
 * Firebase exceptions are translated into these at the [com.sahaaya.core] boundary
 * so that no layer above :data ever imports a Firebase type, and so the UI can
 * decide what to show without string-matching on vendor messages.
 */
sealed interface AppError {

    /** A short, human-readable description safe to show to a caregiver or patient. */
    val message: String

    /** The underlying cause, kept for logging. Never shown to the user. */
    val cause: Throwable?

    // --- Connectivity ------------------------------------------------------

    data class Network(
        override val message: String = "No internet connection. Please check your network and try again.",
        override val cause: Throwable? = null,
    ) : AppError

    data class Timeout(
        override val message: String = "The request took too long. Please try again.",
        override val cause: Throwable? = null,
    ) : AppError

    // --- Authentication ----------------------------------------------------

    data class InvalidCredentials(
        override val message: String = "Email or password is incorrect.",
        override val cause: Throwable? = null,
    ) : AppError

    data class EmailAlreadyInUse(
        override val message: String = "An account already exists with this email address.",
        override val cause: Throwable? = null,
    ) : AppError

    data class WeakPassword(
        override val message: String = "Password is too weak. Use at least 8 characters.",
        override val cause: Throwable? = null,
    ) : AppError

    data class InvalidEmail(
        override val message: String = "That email address is not valid.",
        override val cause: Throwable? = null,
    ) : AppError

    data class NotAuthenticated(
        override val message: String = "You are signed out. Please sign in again.",
        override val cause: Throwable? = null,
    ) : AppError

    data class TooManyRequests(
        override val message: String = "Too many attempts. Please wait a moment and try again.",
        override val cause: Throwable? = null,
    ) : AppError

    // --- Authorisation / data ----------------------------------------------

    data class PermissionDenied(
        override val message: String = "You do not have permission to do that.",
        override val cause: Throwable? = null,
    ) : AppError

    data class NotFound(
        override val message: String = "We could not find what you were looking for.",
        override val cause: Throwable? = null,
    ) : AppError

    data class Conflict(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    // --- Input -------------------------------------------------------------

    /**
     * Input that failed validation. [fieldErrors] maps a field key (see
     * `FormField` in the feature modules) to the reason, so a form can highlight
     * the exact input rather than showing one generic banner.
     */
    data class Validation(
        override val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
        override val cause: Throwable? = null,
    ) : AppError

    // --- Fallback ----------------------------------------------------------

    data class Unknown(
        override val message: String = "Something went wrong. Please try again.",
        override val cause: Throwable? = null,
    ) : AppError
}
