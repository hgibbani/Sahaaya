package com.sahaaya.data.demo

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.CareReminder
import com.sahaaya.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory reminders for LOCAL DEMO MODE. */
@Singleton
class DemoReminderRepository @Inject constructor(
    private val store: DemoDataStore,
) : ReminderRepository {

    override fun observeReminders(patientId: String): Flow<List<CareReminder>> =
        store.reminders.map { all ->
            all.values
                .filter { it.patientId == patientId }
                .sortedBy { it.scheduledAtEpochMillis }
        }

    override suspend fun saveReminder(reminder: CareReminder): Outcome<Unit> {
        val stored = reminder.copy(id = reminder.id.ifBlank { store.nextId("reminder") })
        store.reminders.put(stored.id, stored)
        return Outcome.Success(Unit)
    }

    override suspend fun deleteReminder(patientId: String, reminderId: String): Outcome<Unit> {
        store.reminders.value = store.reminders.value - reminderId
        return Outcome.Success(Unit)
    }
}
