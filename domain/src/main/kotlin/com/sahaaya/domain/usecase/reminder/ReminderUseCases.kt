package com.sahaaya.domain.usecase.reminder

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.CareReminder
import com.sahaaya.domain.model.ReminderType
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.ProfileRepository
import com.sahaaya.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ObserveRemindersUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository,
) {
    operator fun invoke(patientId: String): Flow<List<CareReminder>> =
        reminderRepository.observeReminders(patientId)
}

/**
 * Saves a reminder, stamping who added it.
 *
 * The author is taken from the signed-in account rather than trusted from the
 * form, so "Added by Ravi" on the patient's phone is always true.
 */
class SaveReminderUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val reminderRepository: ReminderRepository,
) {
    suspend operator fun invoke(reminder: CareReminder): Outcome<Unit> {
        val uid = authRepository.currentUserId()
            ?: return Outcome.Failure(AppError.NotAuthenticated())

        if (reminder.type == ReminderType.MEDICINE) {
            // Medicines live in the medication feature, with their own schedule
            // and alarms. Storing one here would create a second copy that the
            // medicine alarms know nothing about.
            return Outcome.Failure(
                AppError.Unknown("Add medicines from the Medicines screen."),
            )
        }
        if (reminder.type == ReminderType.HOSPITAL_APPOINTMENT &&
            reminder.hospitalName.isBlank()
        ) {
            return Outcome.Failure(AppError.Unknown("Please enter the hospital name."))
        }
        if (reminder.type == ReminderType.GENERAL && reminder.title.isBlank()) {
            return Outcome.Failure(AppError.Unknown("Please say what the reminder is for."))
        }

        val authorName = runCatching {
            profileRepository.observeUser(uid).first()?.displayName
        }.getOrNull().orEmpty()

        return reminderRepository.saveReminder(
            reminder.copy(
                createdByUid = reminder.createdByUid.ifBlank { uid },
                createdByName = reminder.createdByName.ifBlank { authorName },
                createdAtEpochMillis = reminder.createdAtEpochMillis
                    .takeIf { it > 0 } ?: System.currentTimeMillis(),
            ),
        )
    }
}

class DeleteReminderUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository,
) {
    suspend operator fun invoke(patientId: String, reminderId: String): Outcome<Unit> =
        reminderRepository.deleteReminder(patientId, reminderId)
}
