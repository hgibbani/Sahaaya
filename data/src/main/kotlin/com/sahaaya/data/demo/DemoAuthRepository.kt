package com.sahaaya.data.demo

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.CaregiverProfile
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.model.PatientProfile
import com.sahaaya.domain.model.Role
import com.sahaaya.domain.model.User
import com.sahaaya.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Authentication, replaced by a map of email to password.
 *
 * The contract is identical to [com.sahaaya.data.repository.AuthRepositoryImpl],
 * including the failure vocabulary: a wrong password produces
 * [AppError.InvalidCredentials] and a duplicate sign-up produces
 * [AppError.EmailAlreadyInUse], so the sign-in and registration screens show the
 * same messages they would against the real backend.
 */
@Singleton
class DemoAuthRepository @Inject constructor(
    private val store: DemoDataStore,
) : AuthRepository {

    /**
     * Joined with the user record, exactly as the Firestore implementation does,
     * so a rename on the profile screen flows straight back to the dashboard
     * greeting without a reload.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentUser(): Flow<User?> =
        store.currentUid.flatMapLatest { uid ->
            if (uid == null) flowOf(null) else store.users.map { it[uid] }
        }

    override fun currentUserId(): String? = store.currentUid.value

    override suspend fun register(
        email: String,
        password: String,
        displayName: String,
        phoneNumber: String,
        role: Role,
    ): Outcome<User> {
        val normalised = email.trim()
        if (store.userByEmail(normalised) != null) {
            return Outcome.Failure(AppError.EmailAlreadyInUse())
        }

        val now = System.currentTimeMillis()
        val user = User(
            uid = store.nextId("user"),
            email = normalised,
            displayName = displayName,
            role = role,
            phoneNumber = phoneNumber,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )

        store.users.put(user.uid, user)
        store.passwords.put(normalised.lowercase(), password)

        // The role-specific document is created empty, for the same reason the
        // Firestore implementation creates it: the profile screen needs
        // something to read before the user has filled anything in.
        when (role) {
            Role.PATIENT -> {
                store.patientProfiles.put(user.uid, PatientProfile(uid = user.uid))
                store.settings.put(user.uid, MonitoringSettings(patientId = user.uid))
            }
            Role.CAREGIVER ->
                store.caregiverProfiles.put(user.uid, CaregiverProfile(uid = user.uid))
        }

        store.currentUid.value = user.uid
        return Outcome.Success(user)
    }

    override suspend fun signIn(email: String, password: String): Outcome<User> {
        val normalised = email.trim()
        val user = store.userByEmail(normalised)
            ?: return Outcome.Failure(AppError.InvalidCredentials())

        if (store.passwords.value[normalised.lowercase()] != password) {
            return Outcome.Failure(AppError.InvalidCredentials())
        }

        store.currentUid.value = user.uid
        return Outcome.Success(user)
    }

    /**
     * There is no mailbox to send to, so this reports success rather than
     * pretending to fail. Success regardless of whether the address is
     * registered also matches Firebase's own behaviour, which deliberately does
     * not confirm whether an account exists.
     */
    override suspend fun sendPasswordReset(email: String): Outcome<Unit> =
        Outcome.Success(Unit)

    override suspend fun signOut(): Outcome<Unit> {
        store.currentUid.value = null
        return Outcome.Success(Unit)
    }

    override suspend fun refreshCurrentUser(): Outcome<User> {
        val uid = store.currentUid.value
            ?: return Outcome.Failure(AppError.NotAuthenticated())
        return store.users.value[uid]?.let { Outcome.Success(it) }
            ?: Outcome.Failure(AppError.NotFound("That account no longer exists."))
    }
}
