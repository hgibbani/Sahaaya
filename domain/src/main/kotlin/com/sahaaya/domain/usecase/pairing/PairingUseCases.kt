package com.sahaaya.domain.usecase.pairing

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.core.validation.Validators
import com.sahaaya.domain.model.Pairing
import com.sahaaya.domain.model.PairingCode
import com.sahaaya.domain.repository.PairingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Caregivers currently linked to [patientId]. Revoked links are filtered out. */
class ObservePatientCaregiversUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {
    operator fun invoke(patientId: String): Flow<List<Pairing>> =
        pairingRepository.observePairingsForPatient(patientId)
            .map { pairings -> pairings.filter { it.isActive } }
}

/** Patients this caregiver currently looks after. */
class ObserveCaregiverPatientsUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {
    operator fun invoke(caregiverId: String): Flow<List<Pairing>> =
        pairingRepository.observePairingsForCaregiver(caregiverId)
            .map { pairings -> pairings.filter { it.isActive } }
}

class ObserveActivePairingCodeUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {
    operator fun invoke(patientId: String): Flow<PairingCode?> =
        pairingRepository.observeActivePairingCode(patientId)
}

class GeneratePairingCodeUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {
    suspend operator fun invoke(patientId: String): Outcome<PairingCode> =
        pairingRepository.generatePairingCode(patientId)
}

/**
 * Links a caregiver to a patient using a code the patient read out.
 *
 * The code is upper-cased and trimmed before it is checked, because it will
 * usually have been typed in a hurry from something said over the phone.
 */
class RedeemPairingCodeUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {

    data class Params(val code: String, val caregiverId: String)

    suspend operator fun invoke(params: Params): Outcome<Pairing> {
        val normalised = params.code.trim().uppercase()
        Validators.pairingCode(normalised)?.let { reason ->
            return Outcome.Failure(
                AppError.Validation(reason, mapOf(FIELD_CODE to reason)),
            )
        }
        return pairingRepository.redeemPairingCode(normalised, params.caregiverId)
    }

    companion object {
        const val FIELD_CODE = "code"
    }
}

/**
 * Ends a pairing.
 *
 * Either side may call this. In Phase 3 a revoked pairing is also what stops
 * alerts being delivered, so this is the switch a family uses when a caregiver
 * changes - which is why it is a first-class action on both dashboards rather
 * than something buried in settings.
 */
class RevokePairingUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {
    suspend operator fun invoke(pairingId: String): Outcome<Unit> =
        pairingRepository.revokePairing(pairingId)
}
