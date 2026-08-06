package com.sahaaya.data.mapper

import com.google.firebase.firestore.DocumentSnapshot
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.PairingCode
import com.sahaaya.domain.model.PairingStatus
import com.sahaaya.firebase.PairingCodeFields
import com.sahaaya.firebase.PairingFields

// --- pairings/{id} ----------------------------------------------------------

fun DocumentSnapshot.toPairing(): Pairing? {
    val patientId = getString(PairingFields.PATIENT_ID) ?: return null
    val caregiverId = getString(PairingFields.CAREGIVER_ID) ?: return null
    return Pairing(
        id = getString(PairingFields.ID) ?: id,
        patientId = patientId,
        caregiverId = caregiverId,
        patientName = getString(PairingFields.PATIENT_NAME).orEmpty(),
        caregiverName = getString(PairingFields.CAREGIVER_NAME).orEmpty(),
        status = PairingStatus.fromStorageKey(getString(PairingFields.STATUS)),
        createdAtEpochMillis = getLong(PairingFields.CREATED_AT) ?: 0L,
        revokedAtEpochMillis = getLong(PairingFields.REVOKED_AT),
    )
}

fun Pairing.toMap(): Map<String, Any?> = mapOf(
    PairingFields.ID to id,
    PairingFields.PATIENT_ID to patientId,
    PairingFields.CAREGIVER_ID to caregiverId,
    PairingFields.PATIENT_NAME to patientName,
    PairingFields.CAREGIVER_NAME to caregiverName,
    PairingFields.STATUS to status.storageKey,
    PairingFields.CREATED_AT to createdAtEpochMillis,
    PairingFields.REVOKED_AT to revokedAtEpochMillis,
)

// --- pairingCodes/{code} ----------------------------------------------------

fun DocumentSnapshot.toPairingCode(): PairingCode? {
    val code = getString(PairingCodeFields.CODE) ?: id.takeIf { it.isNotBlank() } ?: return null
    val patientId = getString(PairingCodeFields.PATIENT_ID) ?: return null
    return PairingCode(
        code = code,
        patientId = patientId,
        patientName = getString(PairingCodeFields.PATIENT_NAME).orEmpty(),
        createdAtEpochMillis = getLong(PairingCodeFields.CREATED_AT) ?: 0L,
        expiresAtEpochMillis = getLong(PairingCodeFields.EXPIRES_AT) ?: 0L,
        redeemedByCaregiverId = getString(PairingCodeFields.REDEEMED_BY),
    )
}

fun PairingCode.toMap(): Map<String, Any?> = mapOf(
    PairingCodeFields.CODE to code,
    PairingCodeFields.PATIENT_ID to patientId,
    PairingCodeFields.PATIENT_NAME to patientName,
    PairingCodeFields.CREATED_AT to createdAtEpochMillis,
    PairingCodeFields.EXPIRES_AT to expiresAtEpochMillis,
    // Written explicitly as null so the "unredeemed" query can match on it;
    // Firestore cannot filter on a field that is absent.
    PairingCodeFields.REDEEMED_BY to redeemedByCaregiverId,
)
