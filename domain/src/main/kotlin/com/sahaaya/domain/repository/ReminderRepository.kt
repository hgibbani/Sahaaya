package com.sahaaya.domain.repository

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.CareReminder
import kotlinx.coroutines.flow.Flow

/**
 * Appointments, health checks and general reminders for one patient.
 *
 * Both the patient and an actively paired caregiver read and write the same
 * list, so either side's addition appears on the other's phone through the
 * live listener.
 */
interface ReminderRepository {

    /** Every reminder for [patientId], soonest first. */
    fun observeReminders(patientId: String): Flow<List<CareReminder>>

    /** Adds or replaces. A blank [CareReminder.id] means "new". */
    suspend fun saveReminder(reminder: CareReminder): Outcome<Unit>

    suspend fun deleteReminder(patientId: String, reminderId: String): Outcome<Unit>
}
