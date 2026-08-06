package com.sahaaya.firebase.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

/**
 * Notification channels, created once at application start.
 *
 * Sahaaya keeps emergencies on their own channel at [NotificationManager.IMPORTANCE_HIGH]
 * with vibration and an alarm-category sound. The separation matters: a
 * caregiver must be able to silence routine reminders overnight without also
 * silencing a fall alert, and Android only lets them do that per channel.
 */
object SahaayaNotificationChannels {

    /** Falls, geofence breaches, prolonged inactivity. Phase 3 publishes to this. */
    const val EMERGENCY_CHANNEL_ID = "sahaaya_emergency"

    /** Pairing activity, profile changes - things that can wait. */
    const val UPDATES_CHANNEL_ID = "sahaaya_updates"

    /** Medication reminders on the patient's own phone. */
    const val REMINDER_CHANNEL_ID = "sahaaya_reminders"

    /**
     * The permanent "monitoring is on" notice for the foreground service.
     * IMPORTANCE_LOW so it sits silently in the shade: it is disclosure that
     * the phone is watching, not an alert.
     */
    const val MONITORING_CHANNEL_ID = "sahaaya_monitoring"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val emergency = NotificationChannel(
            EMERGENCY_CHANNEL_ID,
            "Emergency alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Falls, safe-zone alerts and other urgent events."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500)
            enableLights(true)
            setBypassDnd(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

        val updates = NotificationChannel(
            UPDATES_CHANNEL_ID,
            "Care updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Pairing and profile activity."
            enableVibration(true)
        }

        val reminders = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Medicine reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Prompts to take a dose."
            enableVibration(true)
        }

        val monitoring = NotificationChannel(
            MONITORING_CHANNEL_ID,
            "Monitoring status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows while fall detection is running."
            setShowBadge(false)
            enableVibration(false)
        }

        manager.createNotificationChannel(emergency)
        manager.createNotificationChannel(updates)
        manager.createNotificationChannel(reminders)
        manager.createNotificationChannel(monitoring)
    }
}
