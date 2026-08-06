package com.sahaaya.domain.usecase.profile

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.core.validation.Validators
import com.sahaaya.domain.model.EmergencyContact
import com.sahaaya.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveEmergencyContactsUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    operator fun invoke(patientId: String): Flow<List<EmergencyContact>> =
        profileRepository.observeEmergencyContacts(patientId)
}

/**
 * Adds or edits an emergency contact.
 *
 * The list is capped at [EmergencyContact.MAX_CONTACTS]. That is a care decision
 * rather than a technical one: an escalation chain nobody can recall the shape
 * of is not a plan, and in an emergency a caregiver needs to know who the phone
 * will call next.
 */
class SaveEmergencyContactUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {

    data class Params(
        val patientId: String,
        val contact: EmergencyContact,
        val existingCount: Int,
    )

    suspend operator fun invoke(params: Params): Outcome<Unit> {
        val errors = buildMap {
            Validators.fullName(params.contact.name)?.let { put(FIELD_NAME, it) }
            Validators.phoneNumber(params.contact.phoneNumber)?.let { put(FIELD_PHONE, it) }
            Validators.relationship(params.contact.relationship)
                ?.let { put(FIELD_RELATIONSHIP, it) }
        }
        if (errors.isNotEmpty()) {
            return Outcome.Failure(
                AppError.Validation("Please correct the highlighted fields.", errors),
            )
        }

        val isNew = params.contact.id.isBlank()
        if (isNew && params.existingCount >= EmergencyContact.MAX_CONTACTS) {
            return Outcome.Failure(
                AppError.Conflict(
                    "You can save up to ${EmergencyContact.MAX_CONTACTS} emergency " +
                        "contacts. Remove one before adding another.",
                ),
            )
        }

        return profileRepository.saveEmergencyContact(
            patientId = params.patientId,
            contact = params.contact.copy(
                name = params.contact.name.trim(),
                phoneNumber = params.contact.phoneNumber.trim().replace(" ", ""),
                relationship = params.contact.relationship.trim(),
            ),
        )
    }

    companion object {
        const val FIELD_NAME = "name"
        const val FIELD_PHONE = "phoneNumber"
        const val FIELD_RELATIONSHIP = "relationship"
    }
}

class DeleteEmergencyContactUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(patientId: String, contactId: String): Outcome<Unit> =
        profileRepository.deleteEmergencyContact(patientId, contactId)
}
