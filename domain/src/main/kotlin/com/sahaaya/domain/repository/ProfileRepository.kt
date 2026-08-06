package com.sahaaya.domain.repository

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.CaregiverProfile
import com.sahaaya.domain.model.EmergencyContact
import com.sahaaya.domain.model.PatientProfile
import com.sahaaya.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Profile data for both roles, plus a patient's emergency contacts.
 *
 * Reads are exposed as [Flow] because Firestore pushes updates: when a
 * caregiver edits a patient's address on their phone, the patient's own screen
 * should change without a refresh. Writes are one-shot suspends.
 */
interface ProfileRepository {

    // --- Identity ----------------------------------------------------------

    fun observeUser(uid: String): Flow<User?>

    suspend fun updateUser(
        uid: String,
        displayName: String,
        phoneNumber: String,
    ): Outcome<Unit>

    // --- Patient -----------------------------------------------------------

    fun observePatientProfile(uid: String): Flow<PatientProfile?>

    suspend fun getPatientProfile(uid: String): Outcome<PatientProfile>

    suspend fun savePatientProfile(profile: PatientProfile): Outcome<Unit>

    // --- Caregiver ---------------------------------------------------------

    fun observeCaregiverProfile(uid: String): Flow<CaregiverProfile?>

    suspend fun getCaregiverProfile(uid: String): Outcome<CaregiverProfile>

    suspend fun saveCaregiverProfile(profile: CaregiverProfile): Outcome<Unit>

    // --- Emergency contacts ------------------------------------------------

    /** Contacts for [patientId], already ordered by calling priority. */
    fun observeEmergencyContacts(patientId: String): Flow<List<EmergencyContact>>

    /**
     * Adds or replaces a contact. A blank [EmergencyContact.id] means "new" and
     * the implementation allocates one.
     */
    suspend fun saveEmergencyContact(
        patientId: String,
        contact: EmergencyContact,
    ): Outcome<Unit>

    suspend fun deleteEmergencyContact(
        patientId: String,
        contactId: String,
    ): Outcome<Unit>
}
