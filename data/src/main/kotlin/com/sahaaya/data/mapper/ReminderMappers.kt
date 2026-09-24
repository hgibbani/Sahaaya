package com.sahaaya.data.mapper

import com.google.firebase.firestore.DocumentSnapshot
import com.sahaaya.domain.model.CareReminder
import com.sahaaya.domain.model.ReminderType
import com.sahaaya.firebase.ReminderFields

fun DocumentSnapshot.toCareReminder(): CareReminder? {
    val patientId = getString(ReminderFields.PATIENT_ID) ?: return null
    val scheduledAt = getLong(ReminderFields.SCHEDULED_AT) ?: return null
    return CareReminder(
        id = getString(ReminderFields.ID) ?: id,
        patientId = patientId,
        type = ReminderType.fromStorageKey(getString(ReminderFields.TYPE)),
        title = getString(ReminderFields.TITLE).orEmpty(),
        hospitalName = getString(ReminderFields.HOSPITAL_NAME).orEmpty(),
        doctorName = getString(ReminderFields.DOCTOR_NAME).orEmpty(),
        scheduledAtEpochMillis = scheduledAt,
        notes = getString(ReminderFields.NOTES).orEmpty(),
        createdByUid = getString(ReminderFields.CREATED_BY_UID).orEmpty(),
        createdByName = getString(ReminderFields.CREATED_BY_NAME).orEmpty(),
        createdAtEpochMillis = getLong(ReminderFields.CREATED_AT) ?: 0L,
    )
}

fun CareReminder.toMap(): Map<String, Any?> = mapOf(
    ReminderFields.ID to id,
    ReminderFields.PATIENT_ID to patientId,
    ReminderFields.TYPE to type.storageKey,
    ReminderFields.TITLE to title,
    ReminderFields.HOSPITAL_NAME to hospitalName,
    ReminderFields.DOCTOR_NAME to doctorName,
    ReminderFields.SCHEDULED_AT to scheduledAtEpochMillis,
    ReminderFields.NOTES to notes,
    ReminderFields.CREATED_BY_UID to createdByUid,
    ReminderFields.CREATED_BY_NAME to createdByName,
    ReminderFields.CREATED_AT to createdAtEpochMillis,
)
