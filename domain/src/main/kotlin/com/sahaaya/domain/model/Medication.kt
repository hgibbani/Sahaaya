package com.sahaaya.domain.model

/**
 * A medicine the patient is expected to take, and when.
 *
 * Stored in the top-level `medications` collection keyed by [patientId] so a
 * caregiver can query across their patients without walking sub-collections.
 */
data class Medication(
    val id: String,
    val patientId: String,
    val name: String,
    val dosage: String,
    /** Minutes past midnight, local time. One entry per dose in the day. */
    val timesOfDayMinutes: List<Int>,
    val schedule: MedicationSchedule = MedicationSchedule.DAILY,
    /** For [MedicationSchedule.SPECIFIC_DAYS]: 1 = Monday … 7 = Sunday. */
    val daysOfWeek: Set<Int> = emptySet(),
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long = 0L,
) {
    /** Whether a dose is expected on [isoDayOfWeek] (1 = Monday … 7 = Sunday). */
    fun isScheduledOn(isoDayOfWeek: Int): Boolean = when (schedule) {
        MedicationSchedule.DAILY -> true
        MedicationSchedule.SPECIFIC_DAYS -> isoDayOfWeek in daysOfWeek
    }

    companion object {
        const val MAX_TIMES_PER_DAY = 6
    }
}

enum class MedicationSchedule(val storageKey: String, val displayName: String) {
    DAILY("daily", "Every day"),
    SPECIFIC_DAYS("specific_days", "Certain days"),
    ;

    companion object {
        fun fromStorageKey(key: String?): MedicationSchedule =
            entries.firstOrNull { it.storageKey == key } ?: DAILY
    }
}

/**
 * One scheduled occurrence of a dose, and what happened to it.
 *
 * A dose row is created when the reminder fires rather than in advance, so the
 * collection does not fill with rows for a future that may never arrive.
 * Document id is deterministic - `{medicationId}_{scheduledAt}` - so a reminder
 * that fires twice (device reboot, alarm rescheduled) cannot produce two rows
 * for the same dose.
 */
data class MedicationDose(
    val id: String,
    val medicationId: String,
    val patientId: String,
    val medicationName: String,
    val dosage: String,
    val scheduledAtEpochMillis: Long,
    val status: DoseStatus = DoseStatus.PENDING,
    val respondedAtEpochMillis: Long? = null,
) {
    companion object {
        fun idFor(medicationId: String, scheduledAtEpochMillis: Long): String =
            "${medicationId}_$scheduledAtEpochMillis"
    }
}

enum class DoseStatus(val storageKey: String, val displayName: String) {
    /** Reminder fired, patient has not answered yet. */
    PENDING("pending", "Waiting"),
    TAKEN("taken", "Taken"),
    /** Patient deliberately declined. Not a failure - it is information. */
    SKIPPED("skipped", "Skipped"),
    /** Grace period elapsed with no response. Raises an event for the caregiver. */
    MISSED("missed", "Missed"),
    ;

    companion object {
        fun fromStorageKey(key: String?): DoseStatus =
            entries.firstOrNull { it.storageKey == key } ?: PENDING
    }
}
