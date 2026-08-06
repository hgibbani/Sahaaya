package com.sahaaya.domain.model

/**
 * The link between one patient and one caregiver, stored at `pairings/{id}`.
 *
 * This is the single most important document in Sahaaya: it is what the
 * Firestore security rules consult to decide whether a caregiver may read a
 * patient's profile, and - from Phase 3 - whether they receive that patient's
 * fall and geofence alerts. It is many-to-many by design: a patient may have
 * several caregivers, and a caregiver may look after several patients.
 *
 * Pairings are never deleted, only [PairingStatus.REVOKED], so that a family can
 * see who had access and when it ended.
 */
data class Pairing(
    val id: String,
    val patientId: String,
    val caregiverId: String,
    val patientName: String,
    val caregiverName: String,
    val status: PairingStatus,
    val createdAtEpochMillis: Long = 0L,
    val revokedAtEpochMillis: Long? = null,
) {
    val isActive: Boolean get() = status == PairingStatus.ACTIVE

    companion object {
        /**
         * Deterministic id from the two participants, so the same pair can never
         * produce two competing documents even if both devices act at once.
         */
        fun idFor(patientId: String, caregiverId: String): String =
            "${patientId}_$caregiverId"
    }
}

enum class PairingStatus(val storageKey: String, val displayName: String) {
    ACTIVE("active", "Active"),
    REVOKED("revoked", "Revoked"),
    ;

    companion object {
        fun fromStorageKey(key: String?): PairingStatus =
            entries.firstOrNull { it.storageKey == key } ?: REVOKED
    }
}

/**
 * A short-lived code a patient reads out to a caregiver, stored at
 * `pairingCodes/{code}`.
 *
 * Possession of the code is the patient's consent to be monitored, so it is
 * deliberately awkward to guess and quick to expire. The alphabet excludes
 * I, O, 0 and 1 because these codes are spoken aloud, often over the phone, by
 * someone who may already be confused.
 */
data class PairingCode(
    val code: String,
    val patientId: String,
    val patientName: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val redeemedByCaregiverId: String? = null,
) {
    fun isUsable(nowEpochMillis: Long): Boolean =
        redeemedByCaregiverId == null && nowEpochMillis < expiresAtEpochMillis

    fun remainingMillis(nowEpochMillis: Long): Long =
        (expiresAtEpochMillis - nowEpochMillis).coerceAtLeast(0L)

    companion object {
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val LENGTH = 6
        const val VALIDITY_MILLIS = 15 * 60 * 1000L
    }
}
