package com.sahaaya.domain.model

/**
 * The account itself: identity and role, nothing clinical.
 *
 * Deliberately split from [PatientProfile] / [CaregiverProfile] so that the
 * document every signed-in user must read (their own identity) stays tiny and
 * cheap, while medical detail lives in a document with stricter access rules.
 * See docs/FIREBASE.md for the collection layout.
 */
data class User(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: Role,
    val phoneNumber: String? = null,
    val photoUrl: String? = null,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
) {
    val isPatient: Boolean get() = role == Role.PATIENT
    val isCaregiver: Boolean get() = role == Role.CAREGIVER

    /** First name, used for greetings. Falls back to the whole name. */
    val firstName: String
        get() = displayName.trim().substringBefore(' ').ifBlank { displayName }
}
