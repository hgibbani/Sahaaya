package com.sahaaya.firebase.source

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.firebase.firebaseCall
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Authentication, wrapped.
 *
 * Returns [Outcome] and plain uids; the caller never touches a [FirebaseUser]
 * beyond this class.
 */
@Singleton
class AuthDataSource @Inject constructor(
    private val auth: FirebaseAuth,
) {

    /**
     * Emits the uid of the signed-in account, or `null`.
     *
     * Backed by Firebase's own auth-state listener, which fires once on
     * subscribe with the restored session. That first emission is what the
     * splash route waits for, so it never has to guess how long restoring takes.
     */
    fun observeAuthState(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun currentUserId(): String? = auth.currentUser?.uid

    fun currentUserEmail(): String? = auth.currentUser?.email

    suspend fun createAccount(email: String, password: String): Outcome<String> =
        firebaseCall {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.uid ?: error("Firebase returned no user after account creation")
        }

    suspend fun signIn(email: String, password: String): Outcome<String> =
        firebaseCall {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.uid ?: error("Firebase returned no user after sign in")
        }

    suspend fun updateDisplayName(displayName: String): Outcome<Unit> = firebaseCall {
        val user = auth.currentUser ?: throw IllegalStateException("Not signed in")
        val request = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()
        user.updateProfile(request).await()
    }

    suspend fun sendPasswordReset(email: String): Outcome<Unit> = firebaseCall {
        auth.sendPasswordResetEmail(email).await()
    }

    fun signOut(): Outcome<Unit> =
        try {
            auth.signOut()
            Outcome.Success(Unit)
        } catch (throwable: Throwable) {
            Outcome.Failure(AppError.Unknown(cause = throwable))
        }

    /**
     * Removes the account. Used to unwind a half-finished registration: if the
     * Auth user is created but the Firestore profile write fails, an account
     * with no role would be stranded outside every security rule.
     */
    suspend fun deleteCurrentAccount(): Outcome<Unit> = firebaseCall {
        auth.currentUser?.delete()?.await()
        Unit
    }
}
