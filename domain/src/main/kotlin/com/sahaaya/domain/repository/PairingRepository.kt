package com.sahaaya.domain.repository

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.PairingCode
import kotlinx.coroutines.flow.Flow

/**
 * Linking a patient to a caregiver.
 *
 * The flow is: the patient generates a short code on their phone, reads it to
 * the caregiver, and the caregiver redeems it. Possession of a code that expires
 * in fifteen minutes is treated as the patient's consent - which is why the
 * patient side is the only side that can create one, and either side can revoke.
 */
interface PairingRepository {

    /** Active and revoked pairings where [patientId] is the patient. */
    fun observePairingsForPatient(patientId: String): Flow<List<Pairing>>

    /** Active and revoked pairings where [caregiverId] is the caregiver. */
    fun observePairingsForCaregiver(caregiverId: String): Flow<List<Pairing>>

    /**
     * Issues a fresh code for [patientId], invalidating any earlier unredeemed
     * one so that only a single code is ever live for a patient.
     */
    suspend fun generatePairingCode(patientId: String): Outcome<PairingCode>

    /** The patient's currently live code, or `null` if none is outstanding. */
    fun observeActivePairingCode(patientId: String): Flow<PairingCode?>

    /**
     * Redeems [code] on behalf of [caregiverId], creating the pairing.
     *
     * Fails with [com.sahaaya.core.result.AppError.NotFound] for an unknown or
     * expired code, and with [com.sahaaya.core.result.AppError.Conflict] when
     * the two are already linked.
     */
    suspend fun redeemPairingCode(
        code: String,
        caregiverId: String,
    ): Outcome<Pairing>

    /**
     * Ends a pairing. Callable by either participant; the document is marked
     * revoked rather than deleted so the history survives.
     */
    suspend fun revokePairing(pairingId: String): Outcome<Unit>
}
