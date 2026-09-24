package com.sahaaya.data.repository

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.data.mapper.toCareReminder
import com.sahaaya.data.mapper.toMap
import com.sahaaya.domain.model.CareReminder
import com.sahaaya.domain.repository.ReminderRepository
import com.sahaaya.firebase.FirestoreCollections
import com.sahaaya.firebase.ReminderFields
import com.sahaaya.firebase.source.FirestoreDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminders as a sub-collection of the patient, using the same generic helpers
 * emergency contacts use. Placing them under `patients/{uid}` is what lets one
 * security rule, keyed on the patient id in the path, admit both the patient
 * and their paired caregivers.
 */
@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
) : ReminderRepository {

    override fun observeReminders(patientId: String): Flow<List<CareReminder>> =
        firestoreDataSource.observeSubCollection(
            parentCollection = FirestoreCollections.PATIENTS,
            parentId = patientId,
            subCollection = FirestoreCollections.REMINDERS,
            orderBy = ReminderFields.SCHEDULED_AT,
        ).map { documents -> documents.mapNotNull { it.toCareReminder() } }

    override suspend fun saveReminder(reminder: CareReminder): Outcome<Unit> {
        val id = reminder.id.ifBlank {
            firestoreDataSource.newSubDocumentId(
                parentCollection = FirestoreCollections.PATIENTS,
                parentId = reminder.patientId,
                subCollection = FirestoreCollections.REMINDERS,
            )
        }
        if (id.isBlank()) {
            return Outcome.Failure(AppError.Unknown("Could not save this reminder."))
        }

        return firestoreDataSource.setSubDocument(
            parentCollection = FirestoreCollections.PATIENTS,
            parentId = reminder.patientId,
            subCollection = FirestoreCollections.REMINDERS,
            documentId = id,
            data = reminder.copy(id = id).toMap(),
        )
    }

    override suspend fun deleteReminder(patientId: String, reminderId: String): Outcome<Unit> =
        firestoreDataSource.deleteSubDocument(
            parentCollection = FirestoreCollections.PATIENTS,
            parentId = patientId,
            subCollection = FirestoreCollections.REMINDERS,
            documentId = reminderId,
        )
}
