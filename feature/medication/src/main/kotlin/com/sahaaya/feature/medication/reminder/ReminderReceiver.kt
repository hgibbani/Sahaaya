package com.sahaaya.feature.medication.reminder

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.DoseStatus
import com.sahaaya.domain.model.MedicationDose
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.MedicationRepository
import com.sahaaya.domain.usecase.medication.RecordScheduledDoseUseCase
import com.sahaaya.domain.usecase.medication.RespondToDoseUseCase
import com.sahaaya.feature.medication.R
import com.sahaaya.firebase.messaging.SahaayaNotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires a medicine reminder, and handles the patient's answer to it.
 *
 * Three jobs behind one receiver: show the reminder, record "taken", record
 * "skipped". Taken and Skip are notification actions rather than buttons inside
 * the app, so answering costs one tap from the lock screen. A reminder that
 * requires opening an app to dismiss is a reminder that gets swiped away.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var medicationRepository: MedicationRepository

    @Inject lateinit var recordScheduledDose: RecordScheduledDoseUseCase

    @Inject lateinit var respondToDose: RespondToDoseUseCase

    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID) ?: return
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_TAKEN -> answer(context, intent, taken = true)
                    ACTION_SKIPPED -> answer(context, intent, taken = false)
                    else -> fire(context, medicationId, scheduledAt)
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Reminder handling failed", throwable)
            } finally {
                pending.finish()
            }
        }
    }

    /** Creates the dose row, shows the reminder, and books tomorrow's alarm. */
    private suspend fun fire(context: Context, medicationId: String, scheduledAt: Long) {
        val patientId = authRepository.currentUserId() ?: return

        val medication = when (
            val result = medicationRepository.getMedication(medicationId)
        ) {
            is Outcome.Failure -> return
            is Outcome.Success -> result.data
        }
        if (!medication.isActive) return

        val dose = MedicationDose(
            id = MedicationDose.idFor(medicationId, scheduledAt),
            medicationId = medicationId,
            patientId = patientId,
            medicationName = medication.name,
            dosage = medication.dosage,
            scheduledAtEpochMillis = scheduledAt,
            status = DoseStatus.PENDING,
        )
        recordScheduledDose(dose)

        showReminder(context, dose)

        // Android has no repeating exact alarm, so the next occurrence is
        // booked here, as this one fires.
        reminderScheduler.schedule(medication)
    }

    private suspend fun answer(context: Context, intent: Intent, taken: Boolean) {
        val doseId = intent.getStringExtra(EXTRA_DOSE_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        respondToDose(doseId, taken)

        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.cancel(notificationId)
    }

    private fun showReminder(context: Context, dose: MedicationDose) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val notificationId = dose.id.hashCode() and 0x7FFFFFFF

        fun action(actionName: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            (notificationId + actionName.hashCode()) and 0x7FFFFFFF,
            Intent(context, ReminderReceiver::class.java).apply {
                this.action = actionName
                putExtra(EXTRA_MEDICATION_ID, dose.medicationId)
                putExtra(EXTRA_DOSE_ID, dose.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(
            context,
            SahaayaNotificationChannels.REMINDER_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_medication)
            .setContentTitle("Time for ${dose.medicationName}")
            .setContentText(dose.dosage.ifBlank { "Tap Taken once you have had it." })
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${dose.dosage} - tap Taken once you have had it, or Skip if you " +
                        "are not taking it now.",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Not auto-cancelled: tapping the body must not count as an answer.
            // Only Taken or Skip resolves the dose.
            .setAutoCancel(false)
            .setOngoing(false)
            .addAction(0, "Taken", action(ACTION_TAKEN))
            .addAction(0, "Skip", action(ACTION_SKIPPED))
            .build()

        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.notify(notificationId, notification)
    }

    companion object {
        private const val TAG = "SahaayaReminder"

        const val ACTION_TAKEN = "com.sahaaya.medication.TAKEN"
        const val ACTION_SKIPPED = "com.sahaaya.medication.SKIPPED"

        private const val EXTRA_MEDICATION_ID = "medication_id"
        private const val EXTRA_SCHEDULED_AT = "scheduled_at"
        private const val EXTRA_DOSE_ID = "dose_id"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun intent(
            context: Context,
            medicationId: String,
            scheduledAtEpochMillis: Long,
        ): Intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_SCHEDULED_AT, scheduledAtEpochMillis)
        }
    }
}
