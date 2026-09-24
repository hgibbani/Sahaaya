package com.sahaaya.domain.model

/**
 * Something the patient needs to remember that is not a daily medicine dose.
 *
 * Stored at `patients/{patientId}/reminders/{id}`, under the patient rather than
 * at the top level, for the same reason emergency contacts are: the security
 * rules can then grant access by the patient id in the path, so the patient and
 * any actively paired caregiver see the same list and nobody else sees it.
 *
 * Either side may write. A caregiver books a hospital appointment and it appears
 * on the patient's phone; the patient notes a blood-pressure reading and it
 * appears on the caregiver's. That shared list is the whole point of the
 * feature, and [createdByName] is kept so each side can see who added what.
 *
 * Daily medicines are deliberately *not* modelled here. They already have their
 * own schedule, dose history and alarms in the medication feature; a second
 * representation would drift from it. [ReminderType.MEDICINE] exists so the
 * reminders screen can offer medicines as a choice, and routes there.
 */
data class CareReminder(
    val id: String,
    val patientId: String,
    val type: ReminderType,
    /** Free text for GENERAL; for the other types, a readable default is used. */
    val title: String = "",
    val hospitalName: String = "",
    val doctorName: String = "",
    val scheduledAtEpochMillis: Long,
    val notes: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val createdAtEpochMillis: Long = 0L,
) {
    /** One line a patient can read at a glance. */
    val headline: String
        get() = when (type) {
            ReminderType.HOSPITAL_APPOINTMENT -> buildString {
                append(hospitalName.ifBlank { "Hospital appointment" })
                if (doctorName.isNotBlank()) append(" - ${doctorName.trim()}")
            }
            ReminderType.GENERAL -> title.ifBlank { "Reminder" }
            else -> type.displayName
        }

    fun isPast(nowEpochMillis: Long): Boolean = scheduledAtEpochMillis < nowEpochMillis
}

enum class ReminderType(val storageKey: String, val displayName: String) {
    MEDICINE("medicine", "Medicine"),
    HOSPITAL_APPOINTMENT("hospital_appointment", "Hospital appointment"),
    BLOOD_PRESSURE_CHECK("blood_pressure_check", "Blood pressure check"),
    BLOOD_SUGAR_CHECK("blood_sugar_check", "Blood sugar check"),
    GENERAL("general", "General reminder"),
    ;

    companion object {
        fun fromStorageKey(key: String?): ReminderType =
            entries.firstOrNull { it.storageKey == key } ?: GENERAL

        /** The types this list stores. MEDICINE lives in the medication feature. */
        val storedTypes: List<ReminderType>
            get() = entries.filter { it != MEDICINE }
    }
}
