package com.sahaaya.domain.model

/**
 * Detail specific to a caregiver account, stored at `caregivers/{uid}`.
 *
 * [isAvailableForAlerts] is the switch a caregiver uses when they hand over to
 * someone else for the night. Phase 3 reads it to decide who an alert is
 * delivered to first; Phase 2 records and displays it.
 */
data class CaregiverProfile(
    val uid: String,
    val relationshipToPatient: String? = null,
    val address: String? = null,
    val isAvailableForAlerts: Boolean = true,
    val updatedAtEpochMillis: Long = 0L,
)
