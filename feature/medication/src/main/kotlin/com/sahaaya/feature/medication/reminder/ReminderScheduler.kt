package com.sahaaya.feature.medication.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sahaaya.domain.model.Medication
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Books the OS alarms that fire medicine reminders.
 *
 * AlarmManager rather than WorkManager for the reminder itself: a dose at 9:00
 * means 9:00, and WorkManager explicitly reserves the right to batch work into a
 * maintenance window. WorkManager still handles the *missed-dose sweep*, where
 * a quarter-hour of slack costs nothing.
 *
 * Each dose time gets its own alarm, re-booked when it fires. Android has no
 * repeating exact alarm, so "every day at 9:00" is implemented as "9:00
 * tomorrow, re-booked each morning".
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Whether exact alarms are permitted.
     *
     * Android 12+ can revoke this, and 14+ does not grant it to most apps by
     * default. When it is denied the app degrades to inexact alarms rather than
     * failing: a reminder a few minutes late is far better than no reminder,
     * and the settings screen tells the patient what happened.
     */
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager?.canScheduleExactAlarms() == true
    }

    fun scheduleAll(medications: List<Medication>) {
        medications.filter { it.isActive }.forEach { schedule(it) }
    }

    fun schedule(medication: Medication) {
        val manager = alarmManager ?: return

        medication.timesOfDayMinutes.forEachIndexed { index, minutesOfDay ->
            val triggerAt = nextOccurrence(medication, minutesOfDay) ?: return@forEachIndexed
            val intent = ReminderReceiver.intent(
                context = context,
                medicationId = medication.id,
                scheduledAtEpochMillis = triggerAt,
            )
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(medication.id, index),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            if (canScheduleExactAlarms()) {
                // setExactAndAllowWhileIdle: the phone of someone who naps all
                // afternoon is in Doze precisely when the dose is due.
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            }
        }
    }

    fun cancel(medication: Medication) {
        val manager = alarmManager ?: return
        medication.timesOfDayMinutes.indices.forEach { index ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(medication.id, index),
                ReminderReceiver.intent(context, medication.id, 0L),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.cancel(pendingIntent)
        }
    }

    /**
     * The next moment this dose is due, or null if the schedule never comes
     * round again (which cannot happen today, but would if a schedule with an
     * end date were added).
     */
    private fun nextOccurrence(medication: Medication, minutesOfDay: Int): Long? {
        val now = Calendar.getInstance()

        // Look ahead a week; any weekly schedule must recur inside that.
        for (dayOffset in 0..7) {
            val candidate = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
                set(Calendar.MINUTE, minutesOfDay % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (candidate.timeInMillis <= now.timeInMillis) continue

            // Calendar.MONDAY is 2; convert to ISO where Monday is 1.
            val isoDay = when (val day = candidate.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> 7
                else -> day - 1
            }
            if (medication.isScheduledOn(isoDay)) return candidate.timeInMillis
        }
        return null
    }

    /**
     * A stable per-dose request code.
     *
     * Derived from the medication id and the index of the time, so re-scheduling
     * replaces the existing alarm instead of stacking a second one that would
     * prompt the patient twice.
     */
    private fun requestCode(medicationId: String, timeIndex: Int): Int =
        (medicationId.hashCode() * 31 + timeIndex) and 0x7FFFFFFF
}
