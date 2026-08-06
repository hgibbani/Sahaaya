package com.sahaaya.data.repository

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.data.mapper.toMap
import com.sahaaya.data.mapper.toMedication
import com.sahaaya.data.mapper.toMedicationDose
import com.sahaaya.domain.model.DoseStatus
import com.sahaaya.domain.model.Medication
import com.sahaaya.domain.model.MedicationDose
import com.sahaaya.domain.repository.MedicationRepository
import com.sahaaya.firebase.DoseFields
import com.sahaaya.firebase.source.EventDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepositoryImpl @Inject constructor(
    private val eventDataSource: EventDataSource,
) : MedicationRepository {

    override fun observeMedications(patientId: String): Flow<List<Medication>> =
        eventDataSource.observeMedications(patientId)
            .map { documents ->
                documents.mapNotNull { it.toMedication() }
                    // Ordered client-side: sorting by the first dose time in
                    // Firestore would need a composite index for a list that is
                    // never more than a handful of rows.
                    .sortedBy { it.timesOfDayMinutes.minOrNull() ?: Int.MAX_VALUE }
            }

    override suspend fun getMedication(medicationId: String): Outcome<Medication> =
        when (val result = eventDataSource.getMedication(medicationId)) {
            is Outcome.Failure -> result
            is Outcome.Success -> result.data?.toMedication()?.let { Outcome.Success(it) }
                ?: Outcome.Failure(AppError.NotFound("That medicine is no longer saved."))
        }

    override suspend fun saveMedication(medication: Medication): Outcome<Medication> {
        val id = medication.id.ifBlank { eventDataSource.newMedicationId() }
        val stored = medication.copy(
            id = id,
            createdAtEpochMillis = medication.createdAtEpochMillis
                .takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        return when (val write = eventDataSource.writeMedication(id, stored.toMap())) {
            is Outcome.Failure -> write
            is Outcome.Success -> Outcome.Success(stored)
        }
    }

    override suspend fun deleteMedication(medicationId: String): Outcome<Unit> =
        eventDataSource.deactivateMedication(medicationId)

    override fun observeRecentDoses(patientId: String, limit: Int): Flow<List<MedicationDose>> =
        eventDataSource.observeRecentDoses(patientId, limit)
            .map { documents -> documents.mapNotNull { it.toMedicationDose() } }

    override fun observePendingDoses(patientId: String): Flow<List<MedicationDose>> =
        eventDataSource.observeDosesWithStatus(patientId, DoseStatus.PENDING.storageKey)
            .map { documents -> documents.mapNotNull { it.toMedicationDose() } }

    override suspend fun recordScheduledDose(dose: MedicationDose): Outcome<Unit> {
        val id = dose.id.ifBlank {
            MedicationDose.idFor(dose.medicationId, dose.scheduledAtEpochMillis)
        }
        return eventDataSource.writeDose(id, dose.copy(id = id).toMap())
    }

    override suspend fun updateDoseStatus(
        doseId: String,
        status: DoseStatus,
        respondedAtEpochMillis: Long,
    ): Outcome<Unit> = eventDataSource.updateDose(
        id = doseId,
        data = mapOf(
            DoseFields.STATUS to status.storageKey,
            DoseFields.RESPONDED_AT to respondedAtEpochMillis,
        ),
    )

    override suspend fun findOverdueDoses(
        patientId: String,
        beforeEpochMillis: Long,
    ): Outcome<List<MedicationDose>> = when (
        val result = eventDataSource.findOverdueDoses(
            patientId = patientId,
            pendingStatus = DoseStatus.PENDING.storageKey,
            beforeEpochMillis = beforeEpochMillis,
        )
    ) {
        is Outcome.Failure -> result
        is Outcome.Success -> Outcome.Success(
            result.data.mapNotNull { it.toMedicationDose() },
        )
    }
}
