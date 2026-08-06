package com.sahaaya.domain.repository

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.Role
import com.sahaaya.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Identity: who is signed in, and how they get in and out.
 *
 * Implemented by `:data` on top of Firebase Authentication. Nothing above this
 * interface knows Firebase exists.
 */
interface AuthRepository {

    /**
     * The signed-in user, or `null` when signed out.
     *
     * A cold-start emission arrives as soon as Firebase restores the cached
     * session, which is what the splash route waits on before deciding where to
     * send the user.
     */
    fun observeCurrentUser(): Flow<User?>

    /** The signed-in user's uid without suspending, or `null` when signed out. */
    fun currentUserId(): String?

    /**
     * Creates an account and its `users/{uid}` document in one operation.
     *
     * The role is fixed here and never changes afterwards: a patient account
     * cannot become a caregiver account, because the pairings and clinical data
     * hanging off it assume otherwise.
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        phoneNumber: String,
        role: Role,
    ): Outcome<User>

    suspend fun signIn(email: String, password: String): Outcome<User>

    suspend fun sendPasswordReset(email: String): Outcome<Unit>

    suspend fun signOut(): Outcome<Unit>

    /** Loads the `users/{uid}` document for the signed-in account. */
    suspend fun refreshCurrentUser(): Outcome<User>
}
