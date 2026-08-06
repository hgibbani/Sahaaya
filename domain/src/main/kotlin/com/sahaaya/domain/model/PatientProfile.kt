package com.sahaaya.domain.model

/**
 * Clinical and personal detail for a patient.
 *
 * Stored at `patients/{uid}`, separate from `users/{uid}`, so that security
 * rules can grant a caregiver read access to this document only while an active
 * [Pairing] exists - without exposing it to anyone else who can see the user's
 * name.
 */
data class PatientProfile(
    val uid: String,
    val dateOfBirth: String? = null,
    val gender: Gender = Gender.UNSPECIFIED,
    val bloodGroup: String? = null,
    val address: String? = null,
    val diagnosisStage: DementiaStage = DementiaStage.UNSPECIFIED,
    val diagnosedOn: String? = null,
    val medicalNotes: String? = null,
    val allergies: String? = null,
    val primaryCaregiverId: String? = null,
    val updatedAtEpochMillis: Long = 0L,
) {
    /**
     * Whether enough is filled in for a caregiver to act on this profile in an
     * emergency. Drives the "complete your profile" prompt on the dashboard.
     */
    val isSufficientForCare: Boolean
        get() = !dateOfBirth.isNullOrBlank() &&
            !address.isNullOrBlank() &&
            diagnosisStage != DementiaStage.UNSPECIFIED
}

enum class Gender(val storageKey: String, val displayName: String) {
    FEMALE("female", "Female"),
    MALE("male", "Male"),
    OTHER("other", "Other"),
    UNSPECIFIED("unspecified", "Prefer not to say"),
    ;

    companion object {
        fun fromStorageKey(key: String?): Gender =
            entries.firstOrNull { it.storageKey == key } ?: UNSPECIFIED
    }
}

/**
 * Clinical staging, using the vocabulary a family is given at diagnosis rather
 * than a research scale. Phase 3 uses this to pick sensible defaults for
 * inactivity thresholds and geofence radius.
 */
enum class DementiaStage(val storageKey: String, val displayName: String) {
    EARLY("early", "Early stage"),
    MODERATE("moderate", "Moderate stage"),
    ADVANCED("advanced", "Advanced stage"),
    UNSPECIFIED("unspecified", "Not recorded"),
    ;

    companion object {
        fun fromStorageKey(key: String?): DementiaStage =
            entries.firstOrNull { it.storageKey == key } ?: UNSPECIFIED
    }
}
