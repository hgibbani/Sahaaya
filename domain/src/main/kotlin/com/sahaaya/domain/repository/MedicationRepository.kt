package com.sahaaya.domain.repository

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.DoseStatus
import com.sahaaya.domain.model.Medication
import com.sahaaya.domain.model.MedicationDose
import kotlinx.coroutines.flow.Flow

interface MedicationRepository {

    fun observeMedications(patientId: String): Flow<List<Medication>>

    suspend fun getMedication(medicationId: String): Outcome<Medication>

    /** A blank [Medication.id] means "new"; the implementation allocates one. */
    suspend fun saveMedication(medication: Medication): Outcome<Medication>

    suspend fun deleteMedication(medicationId: String): Outcome<Unit>

    /** Doses for the caregiver's view and the patient's own history, newest first. */
    fun observeRecentDoses(patientId: String, limit: Int = 60): Flow<List<MedicationDose>>

    /** Doses still awaiting an answer, so the patient screen can prompt. */
    fun observePendingDoses(patientId: String): Flow<List<MedicationDose>>

    /**
     * Creates the dose row when a reminder fires.
     *
     * Idempotent: the id is derived from medication plus scheduled time, so an
     * alarm that fires twice after a reboot updates one row rather than
     * creating a duplicate the caregiver has to interpret.
     */
    suspend fun recordScheduledDose(dose: MedicationDose): Outcome<Unit>

    suspend fun updateDoseStatus(
        doseId: String,
        status: DoseStatus,
        respondedAtEpochMillis: Long,
    ): Outcome<Unit>

    /** Doses whose grace period has elapsed with no response. */
    suspend fun findOverdueDoses(
        patientId: String,
        beforeEpochMillis: Long,
    ): Outcome<List<MedicationDose>>
}
