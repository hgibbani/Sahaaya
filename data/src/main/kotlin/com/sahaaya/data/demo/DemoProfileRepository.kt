package com.sahaaya.data.demo

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.CaregiverProfile
import com.sahaaya.domain.model.EmergencyContact
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.model.PatientProfile
import com.sahaaya.domain.model.User
import com.sahaaya.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoProfileRepository @Inject constructor(
    private val store: DemoDataStore,
) : ProfileRepository {

    // --- Identity ----------------------------------------------------------

    override fun observeUser(uid: String): Flow<User?> = store.users.map { it[uid] }

    override suspend fun updateUser(
        uid: String,
        displayName: String,
        phoneNumber: String,
    ): Outcome<Unit> {
        val existing = store.users.value[uid]
            ?: return Outcome.Failure(AppError.NotFound("That account no longer exists."))
        store.users.put(
            uid,
            existing.copy(
                displayName = displayName,
                phoneNumber = phoneNumber,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        return Outcome.Success(Unit)
    }

    // --- Patient -----------------------------------------------------------

    override fun observePatientProfile(uid: String): Flow<PatientProfile?> =
        store.patientProfiles.map { it[uid] }

    override suspend fun getPatientProfile(uid: String): Outcome<PatientProfile> =
        Outcome.Success(store.patientProfiles.value[uid] ?: PatientProfile(uid = uid))

    override suspend fun savePatientProfile(profile: PatientProfile): Outcome<Unit> {
        store.patientProfiles.put(
            profile.uid,
            profile.copy(updatedAtEpochMillis = System.currentTimeMillis()),
        )
        return Outcome.Success(Unit)
    }

    // --- Caregiver ---------------------------------------------------------

    override fun observePatientLocation(patientId: String): Flow<PatientLocation?> =
        store.patientLocations.map { it[patientId] }

    override suspend fun updatePatientLocation(
        patientId: String,
        location: PatientLocation,
    ): Outcome<Unit> {
        store.patientLocations.put(patientId, location)
        return Outcome.Success(Unit)
    }

    override fun observeCaregiverProfile(uid: String): Flow<CaregiverProfile?> =
        store.caregiverProfiles.map { it[uid] }

    override suspend fun getCaregiverProfile(uid: String): Outcome<CaregiverProfile> =
        Outcome.Success(store.caregiverProfiles.value[uid] ?: CaregiverProfile(uid = uid))

    override suspend fun saveCaregiverProfile(profile: CaregiverProfile): Outcome<Unit> {
        store.caregiverProfiles.put(
            profile.uid,
            profile.copy(updatedAtEpochMillis = System.currentTimeMillis()),
        )
        return Outcome.Success(Unit)
    }

    // --- Emergency contacts ------------------------------------------------

    override fun observeEmergencyContacts(patientId: String): Flow<List<EmergencyContact>> =
        store.emergencyContacts.map { all ->
            all[patientId].orEmpty().sortedBy { it.priority }
        }

    override suspend fun saveEmergencyContact(
        patientId: String,
        contact: EmergencyContact,
    ): Outcome<Unit> {
        val withId = if (contact.id.isBlank()) {
            contact.copy(id = store.nextId("contact"))
        } else {
            contact
        }

        store.emergencyContacts.update { all ->
            val current = all[patientId].orEmpty()
            val replaced = current.filterNot { it.id == withId.id } + withId
            all + (patientId to replaced.sortedBy { it.priority })
        }
        return Outcome.Success(Unit)
    }

    override suspend fun deleteEmergencyContact(
        patientId: String,
        contactId: String,
    ): Outcome<Unit> {
        store.emergencyContacts.update { all ->
            all + (patientId to all[patientId].orEmpty().filterNot { it.id == contactId })
        }
        return Outcome.Success(Unit)
    }
}
