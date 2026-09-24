package com.sahaaya.data.repository

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.data.mapper.toCaregiverProfile
import com.sahaaya.data.mapper.toEmergencyContact
import com.sahaaya.data.mapper.toMap
import com.sahaaya.data.mapper.toPatientLocation
import com.sahaaya.data.mapper.toPatientProfile
import com.sahaaya.data.mapper.toUser
import com.sahaaya.domain.model.CaregiverProfile
import com.sahaaya.domain.model.EmergencyContact
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.model.PatientProfile
import com.sahaaya.domain.model.User
import com.sahaaya.domain.repository.ProfileRepository
import com.sahaaya.firebase.EmergencyContactFields
import com.sahaaya.firebase.FirestoreCollections
import com.sahaaya.firebase.UserFields
import com.sahaaya.firebase.source.FirestoreDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
) : ProfileRepository {

    // --- Identity ----------------------------------------------------------

    override fun observeUser(uid: String): Flow<User?> =
        firestoreDataSource.observeDocument(FirestoreCollections.USERS, uid)
            .map { snapshot -> snapshot?.toUser() }

    override suspend fun updateUser(
        uid: String,
        displayName: String,
        phoneNumber: String,
    ): Outcome<Unit> = firestoreDataSource.setDocument(
        collection = FirestoreCollections.USERS,
        id = uid,
        data = mapOf(
            UserFields.DISPLAY_NAME to displayName,
            UserFields.PHONE_NUMBER to phoneNumber,
            UserFields.UPDATED_AT to System.currentTimeMillis(),
        ),
    )

    // --- Patient -----------------------------------------------------------

    override fun observePatientProfile(uid: String): Flow<PatientProfile?> =
        firestoreDataSource.observeDocument(FirestoreCollections.PATIENTS, uid)
            .map { snapshot -> snapshot?.toPatientProfile() }

    override suspend fun getPatientProfile(uid: String): Outcome<PatientProfile> =
        when (val result = firestoreDataSource.getDocument(
            FirestoreCollections.PATIENTS,
            uid,
        )) {
            is Outcome.Failure -> result
            is Outcome.Success -> result.data?.toPatientProfile()
                ?.let { Outcome.Success(it) }
                // An empty document is created at registration, so a missing one
                // means the profile has genuinely not been set up yet. Returning
                // a blank profile keeps the edit screen usable.
                ?: Outcome.Success(PatientProfile(uid = uid))
        }

    override suspend fun savePatientProfile(profile: PatientProfile): Outcome<Unit> =
        firestoreDataSource.setDocument(
            collection = FirestoreCollections.PATIENTS,
            id = profile.uid,
            data = profile.toMap(System.currentTimeMillis()),
        )

    // --- Latest position ---------------------------------------------------

    override fun observePatientLocation(patientId: String): Flow<PatientLocation?> =
        firestoreDataSource.observeDocument(FirestoreCollections.PATIENTS, patientId)
            .map { snapshot -> snapshot?.toPatientLocation() }

    /**
     * A merge write of six fields, not a document replace.
     *
     * `setDocument(merge = true)` rather than `update()` because the patient
     * document exists from registration but has never held these keys, and
     * `update()` on a missing field path fails rather than creating it.
     */
    override suspend fun updatePatientLocation(
        patientId: String,
        location: PatientLocation,
    ): Outcome<Unit> = firestoreDataSource.setDocument(
        collection = FirestoreCollections.PATIENTS,
        id = patientId,
        data = location.toMap(),
        merge = true,
    )

    // --- Caregiver ---------------------------------------------------------

    override fun observeCaregiverProfile(uid: String): Flow<CaregiverProfile?> =
        firestoreDataSource.observeDocument(FirestoreCollections.CAREGIVERS, uid)
            .map { snapshot -> snapshot?.toCaregiverProfile() }

    override suspend fun getCaregiverProfile(uid: String): Outcome<CaregiverProfile> =
        when (val result = firestoreDataSource.getDocument(
            FirestoreCollections.CAREGIVERS,
            uid,
        )) {
            is Outcome.Failure -> result
            is Outcome.Success -> result.data?.toCaregiverProfile()
                ?.let { Outcome.Success(it) }
                ?: Outcome.Success(CaregiverProfile(uid = uid))
        }

    override suspend fun saveCaregiverProfile(profile: CaregiverProfile): Outcome<Unit> =
        firestoreDataSource.setDocument(
            collection = FirestoreCollections.CAREGIVERS,
            id = profile.uid,
            data = profile.toMap(System.currentTimeMillis()),
        )

    // --- Emergency contacts ------------------------------------------------

    override fun observeEmergencyContacts(patientId: String): Flow<List<EmergencyContact>> =
        firestoreDataSource.observeSubCollection(
            parentCollection = FirestoreCollections.PATIENTS,
            parentId = patientId,
            subCollection = FirestoreCollections.EMERGENCY_CONTACTS,
            orderBy = EmergencyContactFields.PRIORITY,
        ).map { documents -> documents.mapNotNull { it.toEmergencyContact() } }

    override suspend fun saveEmergencyContact(
        patientId: String,
        contact: EmergencyContact,
    ): Outcome<Unit> {
        val id = contact.id.ifBlank {
            firestoreDataSource.newSubDocumentId(
                parentCollection = FirestoreCollections.PATIENTS,
                parentId = patientId,
                subCollection = FirestoreCollections.EMERGENCY_CONTACTS,
            )
        }
        if (id.isBlank()) {
            return Outcome.Failure(AppError.Unknown("Could not save this contact."))
        }
        return firestoreDataSource.setSubDocument(
            parentCollection = FirestoreCollections.PATIENTS,
            parentId = patientId,
            subCollection = FirestoreCollections.EMERGENCY_CONTACTS,
            documentId = id,
            data = contact.copy(id = id).toMap(),
        )
    }

    override suspend fun deleteEmergencyContact(
        patientId: String,
        contactId: String,
    ): Outcome<Unit> = firestoreDataSource.deleteSubDocument(
        parentCollection = FirestoreCollections.PATIENTS,
        parentId = patientId,
        subCollection = FirestoreCollections.EMERGENCY_CONTACTS,
        documentId = contactId,
    )
}
