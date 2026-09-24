package com.sahaaya.data.demo

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.PairingCode
import com.sahaaya.domain.model.PairingStatus
import com.sahaaya.domain.repository.PairingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pairing handshake, entirely on-device.
 *
 * The code alphabet, length, fifteen-minute expiry and "only one live code per
 * patient" rule are the same ones the Firestore transaction enforces - so the
 * demo shows the real interaction: the patient generates a code, it is read out,
 * the caregiver types it in, and the link appears on both dashboards.
 */
@Singleton
class DemoPairingRepository @Inject constructor(
    private val store: DemoDataStore,
) : PairingRepository {

    private val random = SecureRandom()

    override fun observePairingsForPatient(patientId: String): Flow<List<Pairing>> =
        store.pairings.map { all -> all.values.filter { it.patientId == patientId } }

    override fun observePairingsForCaregiver(caregiverId: String): Flow<List<Pairing>> =
        store.pairings.map { all -> all.values.filter { it.caregiverId == caregiverId } }

    override fun observeActivePairingCode(patientId: String): Flow<PairingCode?> =
        store.pairingCodes.map { all ->
            all.values
                .filter { it.patientId == patientId }
                .firstOrNull { it.isUsable(System.currentTimeMillis()) }
        }

    override suspend fun generatePairingCode(patientId: String): Outcome<PairingCode> {
        val patientName = store.users.value[patientId]?.displayName
            ?: return Outcome.Failure(AppError.NotAuthenticated())

        // Any outstanding unredeemed code stops working, so a patient who taps
        // "new code" cannot leave a code live that they believe they cancelled.
        store.pairingCodes.update { all ->
            all.filterValues { it.patientId != patientId || it.redeemedByCaregiverId != null }
        }

        val now = System.currentTimeMillis()
        val code = PairingCode(
            code = generateCode(),
            patientId = patientId,
            patientName = patientName,
            createdAtEpochMillis = now,
            expiresAtEpochMillis = now + PairingCode.VALIDITY_MILLIS,
        )
        store.pairingCodes.put(code.code, code)
        return Outcome.Success(code)
    }

    override suspend fun redeemPairingCode(
        code: String,
        caregiverId: String,
    ): Outcome<Pairing> {
        val caregiverName = store.users.value[caregiverId]?.displayName
            ?: return Outcome.Failure(AppError.NotAuthenticated())

        val now = System.currentTimeMillis()
        val stored = store.pairingCodes.value[code]
        if (stored == null || !stored.isUsable(now)) {
            return Outcome.Failure(
                AppError.NotFound(
                    "That code is not valid any more. Ask for a new one - codes " +
                        "expire after 15 minutes.",
                ),
            )
        }

        val id = Pairing.idFor(stored.patientId, caregiverId)
        if (store.pairings.value[id]?.isActive == true) {
            return Outcome.Failure(
                AppError.Conflict("You are already linked to this patient."),
            )
        }

        val pairing = Pairing(
            id = id,
            patientId = stored.patientId,
            caregiverId = caregiverId,
            patientName = stored.patientName,
            caregiverName = caregiverName,
            status = PairingStatus.ACTIVE,
            createdAtEpochMillis = now,
        )

        store.pairings.put(id, pairing)
        store.pairingCodes.put(code, stored.copy(redeemedByCaregiverId = caregiverId))
        return Outcome.Success(pairing)
    }

    /** Marked revoked rather than deleted, so the history survives. */
    override suspend fun revokePairing(pairingId: String): Outcome<Unit> {
        val existing = store.pairings.value[pairingId]
            ?: return Outcome.Failure(AppError.NotFound("That link no longer exists."))
        store.pairings.put(
            pairingId,
            existing.copy(
                status = PairingStatus.REVOKED,
                revokedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        return Outcome.Success(Unit)
    }

    private fun generateCode(): String = buildString {
        repeat(PairingCode.LENGTH) {
            append(PairingCode.ALPHABET[random.nextInt(PairingCode.ALPHABET.length)])
        }
    }
}
