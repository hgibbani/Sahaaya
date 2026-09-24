package com.sahaaya.domain.usecase.profile

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.core.validation.Validators
import com.sahaaya.domain.model.CaregiverProfile
import com.sahaaya.domain.model.PatientProfile
import com.sahaaya.domain.model.User
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Identity plus clinical detail for one patient, as a single stream. */
data class PatientProfileSnapshot(
    val user: User?,
    val profile: PatientProfile?,
)

/** Identity plus caregiver detail, as a single stream. */
data class CaregiverProfileSnapshot(
    val user: User?,
    val profile: CaregiverProfile?,
)

/**
 * Combines `users/{uid}` and `patients/{uid}` so a screen subscribes once.
 *
 * They are separate documents because the security rules treat them
 * differently, but no screen ever wants one without the other.
 */
class ObservePatientProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    operator fun invoke(uid: String): Flow<PatientProfileSnapshot> = combine(
        profileRepository.observeUser(uid),
        profileRepository.observePatientProfile(uid),
    ) { user, profile -> PatientProfileSnapshot(user, profile) }
}

class ObserveCaregiverProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    operator fun invoke(uid: String): Flow<CaregiverProfileSnapshot> = combine(
        profileRepository.observeUser(uid),
        profileRepository.observeCaregiverProfile(uid),
    ) { user, profile -> CaregiverProfileSnapshot(user, profile) }
}

/**
 * Saves a patient's identity and clinical detail together.
 *
 * Both writes are issued, and the first failure is returned. They are not
 * atomic across the two documents, and deliberately so: a partial save leaves
 * the profile usable, whereas a transaction that fails on a flaky connection
 * would leave a caregiver with nothing.
 */
class SavePatientProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {

    data class Params(
        val uid: String,
        val displayName: String,
        val phoneNumber: String,
        val profile: PatientProfile,
    )

    suspend operator fun invoke(params: Params): Outcome<Unit> {
        validate(params)?.let { return Outcome.Failure(it) }

        val identity = profileRepository.updateUser(
            uid = params.uid,
            displayName = params.displayName.trim(),
            phoneNumber = params.phoneNumber.trim(),
        )
        if (identity is Outcome.Failure) return identity

        return profileRepository.savePatientProfile(
            params.profile.copy(
                uid = params.uid,
                address = params.profile.address?.trim(),
                medicalNotes = params.profile.medicalNotes?.trim(),
                allergies = params.profile.allergies?.trim(),
            ),
        )
    }

    private fun validate(params: Params): AppError.Validation? {
        val errors = buildMap {
            Validators.fullName(params.displayName)?.let { put(FIELD_NAME, it) }
            Validators.phoneNumber(params.phoneNumber)?.let { put(FIELD_PHONE, it) }
            Validators.notes(params.profile.medicalNotes.orEmpty())
                ?.let { put(FIELD_NOTES, it) }
            Validators.notes(params.profile.allergies.orEmpty(), maxLength = 300)
                ?.let { put(FIELD_ALLERGIES, it) }
        }
        return if (errors.isEmpty()) {
            null
        } else {
            AppError.Validation("Please correct the highlighted fields.", errors)
        }
    }

    companion object {
        const val FIELD_NAME = "displayName"
        const val FIELD_PHONE = "phoneNumber"
        const val FIELD_NOTES = "medicalNotes"
        const val FIELD_ALLERGIES = "allergies"
    }
}

class SaveCaregiverProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {

    data class Params(
        val uid: String,
        val displayName: String,
        val phoneNumber: String,
        val profile: CaregiverProfile,
    )

    suspend operator fun invoke(params: Params): Outcome<Unit> {
        val errors = buildMap {
            Validators.fullName(params.displayName)?.let { put(FIELD_NAME, it) }
            Validators.phoneNumber(params.phoneNumber)?.let { put(FIELD_PHONE, it) }
            params.profile.relationshipToPatient
                ?.takeIf { it.isNotBlank() }
                ?.let { value -> Validators.relationship(value)?.let { put(FIELD_RELATIONSHIP, it) } }
        }
        if (errors.isNotEmpty()) {
            return Outcome.Failure(
                AppError.Validation("Please correct the highlighted fields.", errors),
            )
        }

        val identity = profileRepository.updateUser(
            uid = params.uid,
            displayName = params.displayName.trim(),
            phoneNumber = params.phoneNumber.trim(),
        )
        if (identity is Outcome.Failure) return identity

        return profileRepository.saveCaregiverProfile(
            params.profile.copy(
                uid = params.uid,
                relationshipToPatient = params.profile.relationshipToPatient?.trim(),
                address = params.profile.address?.trim(),
            ),
        )
    }

    companion object {
        const val FIELD_NAME = "displayName"
        const val FIELD_PHONE = "phoneNumber"
        const val FIELD_RELATIONSHIP = "relationship"
    }
}

/**
 * The patient's most recent position, for the caregiver's dashboard card.
 *
 * Emits null until a fix has been recorded, which the card renders as
 * "Waiting for the patient's phone" rather than as a location at (0, 0).
 */
class ObservePatientLocationUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    operator fun invoke(patientId: String): Flow<PatientLocation?> =
        profileRepository.observePatientLocation(patientId)
}
