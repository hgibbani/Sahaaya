package com.sahaaya.data.repository

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.data.mapper.toCreateMap
import com.sahaaya.data.mapper.toUser
import com.sahaaya.domain.model.CaregiverProfile
import com.sahaaya.domain.model.PatientProfile
import com.sahaaya.domain.model.Role
import com.sahaaya.domain.model.User
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.data.mapper.toMap
import com.sahaaya.firebase.FirestoreCollections
import com.sahaaya.firebase.source.AuthDataSource
import com.sahaaya.firebase.source.FirestoreDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authDataSource: AuthDataSource,
    private val firestoreDataSource: FirestoreDataSource,
) : AuthRepository {

    /**
     * Auth state joined with the `users/{uid}` document.
     *
     * Both halves are needed before the app can route anywhere: Firebase Auth
     * knows *that* someone is signed in, but only the Firestore document knows
     * their role, and the role decides which dashboard they belong on.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentUser(): Flow<User?> =
        authDataSource.observeAuthState().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(null)
            } else {
                firestoreDataSource
                    .observeDocument(FirestoreCollections.USERS, uid)
                    .map { snapshot -> snapshot?.toUser() }
            }
        }

    override fun currentUserId(): String? = authDataSource.currentUserId()

    /**
     * Creates the Auth account and its profile documents.
     *
     * If the Auth account is created but the Firestore write fails, the account
     * is deleted again. An account with no `users/{uid}` document has no role,
     * matches no security rule, and cannot be repaired from inside the app - the
     * user would be permanently stuck at a screen that could not load. Better to
     * fail cleanly and let them retry.
     */
    override suspend fun register(
        email: String,
        password: String,
        displayName: String,
        phoneNumber: String,
        role: Role,
    ): Outcome<User> {
        val uidOutcome = authDataSource.createAccount(email, password)
        val uid = when (uidOutcome) {
            is Outcome.Failure -> return uidOutcome
            is Outcome.Success -> uidOutcome.data
        }

        val now = System.currentTimeMillis()
        val user = User(
            uid = uid,
            email = email,
            displayName = displayName,
            role = role,
            phoneNumber = phoneNumber,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )

        val profileWrite = firestoreDataSource.setDocument(
            collection = FirestoreCollections.USERS,
            id = uid,
            data = user.toCreateMap(now),
            merge = false,
        )
        if (profileWrite is Outcome.Failure) {
            authDataSource.deleteCurrentAccount()
            return profileWrite
        }

        // The role-specific document is created empty so that the profile screen
        // has something to read and the security rules have something to guard
        // before the user has filled anything in.
        val roleWrite = when (role) {
            Role.PATIENT -> firestoreDataSource.setDocument(
                collection = FirestoreCollections.PATIENTS,
                id = uid,
                data = PatientProfile(uid = uid).toMap(now),
                merge = false,
            )
            Role.CAREGIVER -> firestoreDataSource.setDocument(
                collection = FirestoreCollections.CAREGIVERS,
                id = uid,
                data = CaregiverProfile(uid = uid).toMap(now),
                merge = false,
            )
        }
        if (roleWrite is Outcome.Failure) {
            authDataSource.deleteCurrentAccount()
            return roleWrite
        }

        authDataSource.updateDisplayName(displayName)
        return Outcome.Success(user)
    }

    override suspend fun signIn(email: String, password: String): Outcome<User> {
        val uidOutcome = authDataSource.signIn(email, password)
        val uid = when (uidOutcome) {
            is Outcome.Failure -> return uidOutcome
            is Outcome.Success -> uidOutcome.data
        }
        return loadUser(uid)
    }

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> =
        authDataSource.sendPasswordReset(email)

    override suspend fun signOut(): Outcome<Unit> = authDataSource.signOut()

    override suspend fun refreshCurrentUser(): Outcome<User> {
        val uid = authDataSource.currentUserId()
            ?: return Outcome.Failure(AppError.NotAuthenticated())
        return loadUser(uid)
    }

    private suspend fun loadUser(uid: String): Outcome<User> {
        return when (val snapshot = firestoreDataSource.getDocument(
            FirestoreCollections.USERS,
            uid,
        )) {
            is Outcome.Failure -> snapshot
            is Outcome.Success -> snapshot.data?.toUser()?.let { Outcome.Success(it) }
                ?: Outcome.Failure(
                    AppError.NotFound(
                        "Your account exists but its profile is missing. " +
                            "Please contact support.",
                    ),
                )
        }
    }
}
