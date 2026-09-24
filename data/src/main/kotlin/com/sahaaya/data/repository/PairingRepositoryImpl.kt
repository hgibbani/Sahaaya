package com.sahaaya.data.repository

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.data.mapper.toMap
import com.sahaaya.data.mapper.toPairing
import com.sahaaya.data.mapper.toPairingCode
import com.sahaaya.data.mapper.toUser
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.PairingCode
import com.sahaaya.domain.model.PairingStatus
import com.sahaaya.domain.repository.PairingRepository
import com.sahaaya.firebase.FirestoreCollections
import com.sahaaya.firebase.PairingFields
import com.sahaaya.firebase.source.FirestoreDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairingRepositoryImpl @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
) : PairingRepository {

    // Cryptographically strong: a guessable code is a stranger reading a
    // patient's medical profile and, from Phase 3, their live location.
    private val random = SecureRandom()

    override fun observePairingsForPatient(patientId: String): Flow<List<Pairing>> =
        firestoreDataSource
            .observePairingsWhere(PairingFields.PATIENT_ID, patientId)
            .map { documents -> documents.mapNotNull { it.toPairing() } }

    override fun observePairingsForCaregiver(caregiverId: String): Flow<List<Pairing>> =
        firestoreDataSource
            .observePairingsWhere(PairingFields.CAREGIVER_ID, caregiverId)
            .map { documents -> documents.mapNotNull { it.toPairing() } }

    override fun observeActivePairingCode(patientId: String): Flow<PairingCode?> =
        firestoreDataSource.observeActivePairingCode(patientId)
            .map { snapshot ->
                snapshot?.toPairingCode()
                    ?.takeIf { it.isUsable(System.currentTimeMillis()) }
            }

    /**
     * Issues a code, replacing any outstanding one.
     *
     * Only one code is live per patient at a time. If a patient taps "new code"
     * because they misread the last one, the old one must stop working - two
     * valid codes means a caregiver can be linked using a code the patient
     * believes they cancelled.
     */
    override suspend fun generatePairingCode(patientId: String): Outcome<PairingCode> {
        val userResult = firestoreDataSource.getDocument(
            FirestoreCollections.USERS,
            patientId,
        )
        val patient = when (userResult) {
            is Outcome.Failure -> return userResult
            is Outcome.Success -> userResult.data?.toUser()
        }
        val patientName = patient?.displayName.orEmpty()

        val cleared = firestoreDataSource.deleteUnredeemedCodesFor(patientId)
        if (cleared is Outcome.Failure) return cleared

        val now = System.currentTimeMillis()
        val code = PairingCode(
            code = generateCode(),
            patientId = patientId,
            patientName = patientName,
            // Travels with the code so the caregiver can copy it onto the
            // pairing, giving each side a number to call the other on.
            patientPhone = patient?.phoneNumber.orEmpty(),
            createdAtEpochMillis = now,
            expiresAtEpochMillis = now + PairingCode.VALIDITY_MILLIS,
        )

        return when (
            val write = firestoreDataSource.setDocument(
                collection = FirestoreCollections.PAIRING_CODES,
                id = code.code,
                data = code.toMap(),
                merge = false,
            )
        ) {
            is Outcome.Failure -> write
            is Outcome.Success -> Outcome.Success(code)
        }
    }

    override suspend fun redeemPairingCode(
        code: String,
        caregiverId: String,
    ): Outcome<Pairing> {
        val caregiverResult = firestoreDataSource.getDocument(
            FirestoreCollections.USERS,
            caregiverId,
        )
        val caregiver = when (caregiverResult) {
            is Outcome.Failure -> return caregiverResult
            is Outcome.Success -> caregiverResult.data?.toUser()
        }
        val caregiverName = caregiver?.displayName.orEmpty()

        val now = System.currentTimeMillis()
        var built: Pairing? = null

        val result = firestoreDataSource.redeemCodeAndCreatePairing(
            code = code,
            caregiverId = caregiverId,
            caregiverName = caregiverName,
            buildPairing = { patientId, patientName, patientPhone ->
                val pairing = Pairing(
                    id = Pairing.idFor(patientId, caregiverId),
                    patientId = patientId,
                    caregiverId = caregiverId,
                    patientName = patientName,
                    caregiverName = caregiverName,
                    patientPhone = patientPhone,
                    caregiverPhone = caregiver?.phoneNumber.orEmpty(),
                    status = PairingStatus.ACTIVE,
                    createdAtEpochMillis = now,
                )
                built = pairing
                pairing.id to pairing.toMap()
            },
            onInvalidCode = { InvalidPairingCode() },
            onAlreadyPaired = { AlreadyPaired() },
        )

        return when (result) {
            is Outcome.Success -> built?.let { Outcome.Success(it) }
                ?: Outcome.Failure(AppError.Unknown("Could not complete pairing."))

            is Outcome.Failure -> Outcome.Failure(translate(result.error))
        }
    }

    override suspend fun revokePairing(pairingId: String): Outcome<Unit> =
        firestoreDataSource.updateDocument(
            collection = FirestoreCollections.PAIRINGS,
            id = pairingId,
            data = mapOf(
                PairingFields.STATUS to PairingStatus.REVOKED.storageKey,
                PairingFields.REVOKED_AT to System.currentTimeMillis(),
            ),
        )

    private fun generateCode(): String = buildString {
        repeat(PairingCode.LENGTH) {
            append(PairingCode.ALPHABET[random.nextInt(PairingCode.ALPHABET.length)])
        }
    }

    /**
     * The transaction signals its two business failures by throwing; the
     * Firebase error mapper cannot know what they mean, so they are translated
     * back into domain errors here.
     */
    private fun translate(error: AppError): AppError {
        val cause = error.cause
        return when (cause) {
            is InvalidPairingCode -> AppError.NotFound(
                "That code is not valid any more. Ask for a new one - codes " +
                    "expire after 15 minutes.",
            )
            is AlreadyPaired -> AppError.Conflict(
                "You are already linked to this patient.",
            )
            else -> error
        }
    }

    private class InvalidPairingCode : Exception("Pairing code is invalid or expired")

    private class AlreadyPaired : Exception("Pairing already exists")
}
