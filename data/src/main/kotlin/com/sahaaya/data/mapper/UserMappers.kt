package com.sahaaya.data.mapper

import com.google.firebase.firestore.DocumentSnapshot
import com.sahaaya.domain.model.CaregiverProfile
import com.sahaaya.domain.model.DementiaStage
import com.sahaaya.domain.model.EmergencyContact
import com.sahaaya.domain.model.Gender
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.model.SafeZoneStatus
import com.sahaaya.domain.model.PatientProfile
import com.sahaaya.domain.model.Role
import com.sahaaya.domain.model.User
import com.sahaaya.firebase.CaregiverFields
import com.sahaaya.firebase.EmergencyContactFields
import com.sahaaya.firebase.PatientFields
import com.sahaaya.firebase.UserFields

/**
 * Firestore documents to domain models and back.
 *
 * Reads are defensive on purpose. A document written by an older build of the
 * app, or partially synced, must still produce a usable object: a caregiver
 * looking at a patient's screen during an emergency should see the fields that
 * did load rather than a crash. Missing values fall back, they never throw.
 */

// --- users/{uid} ------------------------------------------------------------

fun DocumentSnapshot.toUser(): User? {
    val uid = getString(UserFields.UID) ?: id.takeIf { it.isNotBlank() } ?: return null
    val role = Role.fromStorageKey(getString(UserFields.ROLE)) ?: return null
    return User(
        uid = uid,
        email = getString(UserFields.EMAIL).orEmpty(),
        displayName = getString(UserFields.DISPLAY_NAME).orEmpty(),
        role = role,
        phoneNumber = getString(UserFields.PHONE_NUMBER),
        photoUrl = getString(UserFields.PHOTO_URL),
        createdAtEpochMillis = getLong(UserFields.CREATED_AT) ?: 0L,
        updatedAtEpochMillis = getLong(UserFields.UPDATED_AT) ?: 0L,
    )
}

fun User.toCreateMap(nowEpochMillis: Long): Map<String, Any?> = mapOf(
    UserFields.UID to uid,
    UserFields.EMAIL to email,
    UserFields.DISPLAY_NAME to displayName,
    UserFields.ROLE to role.storageKey,
    UserFields.PHONE_NUMBER to phoneNumber,
    UserFields.PHOTO_URL to photoUrl,
    UserFields.FCM_TOKENS to emptyList<String>(),
    UserFields.CREATED_AT to nowEpochMillis,
    UserFields.UPDATED_AT to nowEpochMillis,
)

// --- patients/{uid} ---------------------------------------------------------

fun DocumentSnapshot.toPatientProfile(): PatientProfile? {
    val uid = getString(PatientFields.UID) ?: id.takeIf { it.isNotBlank() } ?: return null
    return PatientProfile(
        uid = uid,
        dateOfBirth = getString(PatientFields.DATE_OF_BIRTH),
        gender = Gender.fromStorageKey(getString(PatientFields.GENDER)),
        bloodGroup = getString(PatientFields.BLOOD_GROUP),
        height = getString(PatientFields.HEIGHT),
        address = getString(PatientFields.ADDRESS),
        diagnosisStage = DementiaStage.fromStorageKey(
            getString(PatientFields.DIAGNOSIS_STAGE),
        ),
        diagnosedOn = getString(PatientFields.DIAGNOSED_ON),
        medicalNotes = getString(PatientFields.MEDICAL_NOTES),
        allergies = getString(PatientFields.ALLERGIES),
        primaryCaregiverId = getString(PatientFields.PRIMARY_CAREGIVER_ID),
        updatedAtEpochMillis = getLong(PatientFields.UPDATED_AT) ?: 0L,
    )
}

fun PatientProfile.toMap(nowEpochMillis: Long): Map<String, Any?> = mapOf(
    PatientFields.UID to uid,
    PatientFields.DATE_OF_BIRTH to dateOfBirth,
    PatientFields.GENDER to gender.storageKey,
    PatientFields.BLOOD_GROUP to bloodGroup,
    PatientFields.HEIGHT to height,
    PatientFields.ADDRESS to address,
    PatientFields.DIAGNOSIS_STAGE to diagnosisStage.storageKey,
    PatientFields.DIAGNOSED_ON to diagnosedOn,
    PatientFields.MEDICAL_NOTES to medicalNotes,
    PatientFields.ALLERGIES to allergies,
    PatientFields.PRIMARY_CAREGIVER_ID to primaryCaregiverId,
    PatientFields.UPDATED_AT to nowEpochMillis,
)

// --- caregivers/{uid} -------------------------------------------------------

fun DocumentSnapshot.toCaregiverProfile(): CaregiverProfile? {
    val uid = getString(CaregiverFields.UID) ?: id.takeIf { it.isNotBlank() } ?: return null
    return CaregiverProfile(
        uid = uid,
        relationshipToPatient = getString(CaregiverFields.RELATIONSHIP),
        address = getString(CaregiverFields.ADDRESS),
        isAvailableForAlerts = getBoolean(CaregiverFields.AVAILABLE_FOR_ALERTS) ?: true,
        updatedAtEpochMillis = getLong(CaregiverFields.UPDATED_AT) ?: 0L,
    )
}

fun CaregiverProfile.toMap(nowEpochMillis: Long): Map<String, Any?> = mapOf(
    CaregiverFields.UID to uid,
    CaregiverFields.RELATIONSHIP to relationshipToPatient,
    CaregiverFields.ADDRESS to address,
    CaregiverFields.AVAILABLE_FOR_ALERTS to isAvailableForAlerts,
    CaregiverFields.UPDATED_AT to nowEpochMillis,
)

// --- patients/{uid}/emergencyContacts/{id} ----------------------------------

fun DocumentSnapshot.toEmergencyContact(): EmergencyContact? {
    val name = getString(EmergencyContactFields.NAME) ?: return null
    val phone = getString(EmergencyContactFields.PHONE_NUMBER) ?: return null
    return EmergencyContact(
        id = getString(EmergencyContactFields.ID) ?: id,
        name = name,
        phoneNumber = phone,
        relationship = getString(EmergencyContactFields.RELATIONSHIP).orEmpty(),
        priority = (getLong(EmergencyContactFields.PRIORITY)
            ?: EmergencyContact.DEFAULT_PRIORITY.toLong()).toInt(),
        isPrimary = getBoolean(EmergencyContactFields.IS_PRIMARY) ?: false,
    )
}

fun EmergencyContact.toMap(): Map<String, Any?> = mapOf(
    EmergencyContactFields.ID to id,
    EmergencyContactFields.NAME to name,
    EmergencyContactFields.PHONE_NUMBER to phoneNumber,
    EmergencyContactFields.RELATIONSHIP to relationship,
    EmergencyContactFields.PRIORITY to priority,
    EmergencyContactFields.IS_PRIMARY to isPrimary,
)

// --- patients/{uid} latest position -----------------------------------------

/**
 * Reads the location fields off the patient document.
 *
 * Returns null unless a real fix has been recorded: latitude, longitude and a
 * timestamp must all be present. A half-written position is treated as no
 * position rather than as coordinates near (0, 0) in the Gulf of Guinea.
 */
fun DocumentSnapshot.toPatientLocation(): PatientLocation? {
    val latitude = getDouble(PatientFields.LAST_LATITUDE) ?: return null
    val longitude = getDouble(PatientFields.LAST_LONGITUDE) ?: return null
    val recordedAt = getLong(PatientFields.LAST_LOCATION_AT) ?: return null

    return PatientLocation(
        point = GeoPoint(
            latitude = latitude,
            longitude = longitude,
            accuracyMetres = getDouble(PatientFields.LAST_ACCURACY)?.toFloat(),
        ),
        recordedAtEpochMillis = recordedAt,
        status = SafeZoneStatus.fromStorageKey(getString(PatientFields.SAFE_ZONE_STATUS)),
        metresFromBoundary = getLong(PatientFields.METRES_FROM_BOUNDARY)?.toInt(),
    )
}

fun PatientLocation.toMap(): Map<String, Any?> = mapOf(
    PatientFields.LAST_LATITUDE to point.latitude,
    PatientFields.LAST_LONGITUDE to point.longitude,
    PatientFields.LAST_ACCURACY to point.accuracyMetres?.toDouble(),
    PatientFields.LAST_LOCATION_AT to recordedAtEpochMillis,
    PatientFields.SAFE_ZONE_STATUS to status.storageKey,
    PatientFields.METRES_FROM_BOUNDARY to metresFromBoundary?.toLong(),
)
