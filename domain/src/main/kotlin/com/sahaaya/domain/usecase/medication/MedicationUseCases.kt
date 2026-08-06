package com.sahaaya.domain.usecase.medication

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.DoseStatus
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.Medication
import com.sahaaya.domain.model.MedicationDose
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.MedicationRepository
import com.sahaaya.domain.repository.SettingsRepository
import com.sahaaya.domain.usecase.event.RaiseEventUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMedicationsUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository,
) {
    operator fun invoke(patientId: String): Flow<List<Medication>> =
        medicationRepository.observeMedications(patientId)
}

class ObserveRecentDosesUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository,
) {
    operator fun invoke(patientId: String): Flow<List<MedicationDose>> =
        medicationRepository.observeRecentDoses(patientId)
}

class ObservePendingDosesUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository,
) {
    operator fun invoke(patientId: String): Flow<List<MedicationDose>> =
        medicationRepository.observePendingDoses(patientId)
}

class SaveMedicationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val medicationRepository: MedicationRepository,
) {

    suspend operator fun invoke(medication: Medication): Outcome<Medication> {
        val patientId = authRepository.currentUserId()
            ?: return Outcome.Failure(AppError.NotAuthenticated())

        val errors = buildMap {
            if (medication.name.isBlank()) put(FIELD_NAME, "Medicine name is required")
            if (medication.name.length > 80) put(FIELD_NAME, "Name is too long")
            if (medication.dosage.isBlank()) put(FIELD_DOSAGE, "Dosage is required")
            if (medication.timesOfDayMinutes.isEmpty()) {
                put(FIELD_TIMES, "Add at least one time of day")
            }
            if (medication.timesOfDayMinutes.size > Medication.MAX_TIMES_PER_DAY) {
                put(FIELD_TIMES, "At most ${Medication.MAX_TIMES_PER_DAY} times a day")
            }
            if (medication.schedule == com.sahaaya.domain.model.MedicationSchedule.SPECIFIC_DAYS &&
                medication.daysOfWeek.isEmpty()
            ) {
                put(FIELD_DAYS, "Choose at least one day")
            }
        }
        if (errors.isNotEmpty()) {
            return Outcome.Failure(
                AppError.Validation("Please correct the highlighted fields.", errors),
            )
        }

        return medicationRepository.saveMedication(
            medication.copy(
                patientId = patientId,
                name = medication.name.trim(),
                dosage = medication.dosage.trim(),
                notes = medication.notes?.trim()?.ifBlank { null },
                // Sorted so the reminder scheduler and the UI agree on order.
                timesOfDayMinutes = medication.timesOfDayMinutes.distinct().sorted(),
            ),
        )
    }

    companion object {
        const val FIELD_NAME = "name"
        const val FIELD_DOSAGE = "dosage"
        const val FIELD_TIMES = "times"
        const val FIELD_DAYS = "days"
    }
}

class DeleteMedicationUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository,
) {
    suspend operator fun invoke(medicationId: String): Outcome<Unit> =
        medicationRepository.deleteMedication(medicationId)
}

/** Called when a reminder notification fires, to create the row to answer. */
class RecordScheduledDoseUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository,
) {
    suspend operator fun invoke(dose: MedicationDose): Outcome<Unit> =
        medicationRepository.recordScheduledDose(dose)
}

/**
 * The patient answers a reminder.
 *
 * Skipping is recorded as a deliberate choice, distinct from missing. A patient
 * who declines a dose has told us something; a patient who never answered has
 * told us nothing, and only the second is worth waking a caregiver for.
 */
class RespondToDoseUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository,
) {
    suspend operator fun invoke(doseId: String, taken: Boolean): Outcome<Unit> =
        medicationRepository.updateDoseStatus(
            doseId = doseId,
            status = if (taken) DoseStatus.TAKEN else DoseStatus.SKIPPED,
            respondedAtEpochMillis = System.currentTimeMillis(),
        )
}

/**
 * Sweeps for doses whose grace period has elapsed and raises one event each.
 *
 * Run periodically by WorkManager rather than by a per-dose alarm: alarms are
 * dropped when the device is in Doze or was off at the scheduled moment, and a
 * missed dose that is never noticed is the failure this whole feature exists to
 * prevent.
 */
class FlagMissedDosesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val medicationRepository: MedicationRepository,
    private val raiseEvent: RaiseEventUseCase,
) {

    suspend operator fun invoke(): Outcome<Int> {
        val patientId = authRepository.currentUserId()
            ?: return Outcome.Failure(AppError.NotAuthenticated())

        val settings = when (val result = settingsRepository.getSettings(patientId)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }

        val cutoff = System.currentTimeMillis() -
            settings.missedDoseGraceMinutes * 60_000L

        val overdue = when (
            val result = medicationRepository.findOverdueDoses(patientId, cutoff)
        ) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.data
        }

        var flagged = 0
        overdue.forEach { dose ->
            val marked = medicationRepository.updateDoseStatus(
                doseId = dose.id,
                status = DoseStatus.MISSED,
                respondedAtEpochMillis = System.currentTimeMillis(),
            )
            if (marked is Outcome.Success) {
                raiseEvent(
                    RaiseEventUseCase.Params(
                        type = EventType.MEDICATION_MISSED,
                        summary = "${dose.medicationName} was not taken",
                        details = mapOf(
                            "Medicine" to dose.medicationName,
                            "Dosage" to dose.dosage,
                        ),
                        // A missed dose says nothing about where the patient is,
                        // and a location lookup here would cost battery for no
                        // benefit to the caregiver.
                        attachLocation = false,
                        occurredAtEpochMillis = dose.scheduledAtEpochMillis,
                    ),
                )
                flagged++
            }
        }

        return Outcome.Success(flagged)
    }
}
