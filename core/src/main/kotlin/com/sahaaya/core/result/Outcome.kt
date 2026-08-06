package com.sahaaya.core.result

/**
 * The result type used across every layer boundary in Sahaaya.
 *
 * Repositories and use cases never throw for expected failures: a network drop,
 * a wrong password or a missing document are ordinary outcomes of a healthcare
 * app running on a phone in a house with patchy signal. They are returned as
 * [Failure] carrying an [AppError] the UI can render.
 *
 * Programming errors are still thrown, because those are bugs, not outcomes.
 */
sealed interface Outcome<out T> {

    data class Success<out T>(val data: T) : Outcome<T>

    data class Failure(val error: AppError) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    val isFailure: Boolean get() = this is Failure
}

/** Returns the value on success, or `null` on failure. */
fun <T> Outcome<T>.getOrNull(): T? = when (this) {
    is Outcome.Success -> data
    is Outcome.Failure -> null
}

/** Returns the error on failure, or `null` on success. */
fun <T> Outcome<T>.errorOrNull(): AppError? = when (this) {
    is Outcome.Success -> null
    is Outcome.Failure -> error
}

/** Returns the value on success, or [fallback] on failure. */
fun <T> Outcome<T>.getOrElse(fallback: T): T = when (this) {
    is Outcome.Success -> data
    is Outcome.Failure -> fallback
}

/** Transforms the success value, leaving a failure untouched. */
inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(data))
    is Outcome.Failure -> this
}

/** Chains another outcome-producing step, short-circuiting on the first failure. */
inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(data)
    is Outcome.Failure -> this
}

/** Runs [action] when the outcome is a success. Returns the receiver for chaining. */
inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(data)
    return this
}

/** Runs [action] when the outcome is a failure. Returns the receiver for chaining. */
inline fun <T> Outcome<T>.onFailure(action: (AppError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}

/** Collapses both branches into a single value. */
inline fun <T, R> Outcome<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (AppError) -> R,
): R = when (this) {
    is Outcome.Success -> onSuccess(data)
    is Outcome.Failure -> onFailure(error)
}

/** Convenience builder for a successful outcome. */
fun <T> T.asSuccess(): Outcome<T> = Outcome.Success(this)

/** Convenience builder for a failed outcome. */
fun AppError.asFailure(): Outcome<Nothing> = Outcome.Failure(this)
