package com.sahaaya.data.demo

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.DoseStatus
import com.sahaaya.domain.model.Medication
import com.sahaaya.domain.model.MedicationDose
import com.sahaaya.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoMedicationRepository @Inject constructor(
    private val store: DemoDataStore,
) : MedicationRepository {

    override fun observeMedications(patientId: String): Flow<List<Medication>> =
        store.medications.map { all ->
            all.values
                .filter { it.patientId == patientId && it.isActive }
                .sortedBy { it.timesOfDayMinutes.minOrNull() ?: Int.MAX_VALUE }
        }

    override suspend fun getMedication(medicationId: String): Outcome<Medication> =
        store.medications.value[medicationId]?.let { Outcome.Success(it) }
            ?: Outcome.Failure(AppError.NotFound("That medicine is no longer saved."))

    override suspend fun saveMedication(medication: Medication): Outcome<Medication> {
        val stored = medication.copy(
            id = medication.id.ifBlank { store.nextId("med") },
            createdAtEpochMillis = medication.createdAtEpochMillis
                .takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        store.medications.put(stored.id, stored)
        return Outcome.Success(stored)
    }

    /** Deactivated rather than deleted, matching the Firestore implementation. */
    override suspend fun deleteMedication(medicationId: String): Outcome<Unit> {
        val existing = store.medications.value[medicationId]
            ?: return Outcome.Failure(AppError.NotFound("That medicine is no longer saved."))
        store.medications.put(medicationId, existing.copy(isActive = false))
        return Outcome.Success(Unit)
    }

    override fun observeRecentDoses(patientId: String, limit: Int): Flow<List<MedicationDose>> =
        store.doses.map { all ->
            all.values
                .filter { it.patientId == patientId }
                .sortedByDescending { it.scheduledAtEpochMillis }
                .take(limit)
        }

    override fun observePendingDoses(patientId: String): Flow<List<MedicationDose>> =
        store.doses.map { all ->
            all.values
                .filter { it.patientId == patientId && it.status == DoseStatus.PENDING }
                .sortedBy { it.scheduledAtEpochMillis }
        }

    /** Idempotent on the derived id, so a reminder that fires twice writes one row. */
    override suspend fun recordScheduledDose(dose: MedicationDose): Outcome<Unit> {
        val id = dose.id.ifBlank {
            MedicationDose.idFor(dose.medicationId, dose.scheduledAtEpochMillis)
        }
        store.doses.put(id, dose.copy(id = id))
        return Outcome.Success(Unit)
    }

    override suspend fun updateDoseStatus(
        doseId: String,
        status: DoseStatus,
        respondedAtEpochMillis: Long,
    ): Outcome<Unit> {
        val existing = store.doses.value[doseId]
            ?: return Outcome.Failure(AppError.NotFound("That dose is no longer listed."))
        store.doses.put(
            doseId,
            existing.copy(status = status, respondedAtEpochMillis = respondedAtEpochMillis),
        )
        return Outcome.Success(Unit)
    }

    override suspend fun findOverdueDoses(
        patientId: String,
        beforeEpochMillis: Long,
    ): Outcome<List<MedicationDose>> = Outcome.Success(
        store.doses.value.values.filter {
            it.patientId == patientId &&
                it.status == DoseStatus.PENDING &&
                it.scheduledAtEpochMillis < beforeEpochMillis
        },
    )
}
