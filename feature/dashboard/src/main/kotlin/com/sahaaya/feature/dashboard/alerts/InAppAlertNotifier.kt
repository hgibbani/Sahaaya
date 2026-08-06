package com.sahaaya.feature.dashboard.alerts

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.feature.dashboard.R
import com.sahaaya.firebase.messaging.SahaayaNotificationChannels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raises a local notification when a new event arrives over the Firestore
 * listener while the caregiver app is running.
 *
 * This is a **fallback, not the delivery mechanism.** Real delivery is the
 * Cloud Function in `functions/`, which reaches a caregiver whose phone is
 * asleep and whose app is closed - the situation that actually matters.
 *
 * It exists because Cloud Functions require the Blaze plan, and a student team
 * demonstrating this on the free Spark plan would otherwise have no way to show
 * an alert arriving at all. With the app open on the caregiver's device, this
 * gives an honest end-to-end demo of the same event reaching the same screen.
 *
 * Deduplicated by event id, so re-emissions of the Firestore snapshot - which
 * happen on every unrelated write - do not re-notify.
 */
@Singleton
class InAppAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val alreadyNotified = mutableSetOf<String>()

    /** Call with every unresolved event the caregiver can currently see. */
    fun notifyNewEvents(events: List<HealthEvent>) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        events
            .filter { it.isUnresolved && it.id !in alreadyNotified }
            // Events already on screen when the app opened are not news. Only
            // things raised in the last few minutes are worth a notification -
            // otherwise opening the app after a quiet week fires a burst.
            .filter { System.currentTimeMillis() - it.occurredAtEpochMillis < RECENT_WINDOW_MILLIS }
            .forEach { event ->
                alreadyNotified += event.id

                val notification = NotificationCompat.Builder(
                    context,
                    if (event.isCritical) {
                        SahaayaNotificationChannels.EMERGENCY_CHANNEL_ID
                    } else {
                        SahaayaNotificationChannels.UPDATES_CHANNEL_ID
                    },
                )
                    .setSmallIcon(R.drawable.ic_alert)
                    .setContentTitle(
                        "${event.patientName.ifBlank { "Patient" }}: ${event.type.displayName}",
                    )
                    .setContentText(event.summary.ifBlank { event.type.displayName })
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(event.summary.ifBlank { event.type.displayName }),
                    )
                    .setPriority(
                        if (event.isCritical) {
                            NotificationCompat.PRIORITY_MAX
                        } else {
                            NotificationCompat.PRIORITY_DEFAULT
                        },
                    )
                    .setCategory(
                        if (event.isCritical) {
                            NotificationCompat.CATEGORY_ALARM
                        } else {
                            NotificationCompat.CATEGORY_STATUS
                        },
                    )
                    .setAutoCancel(true)
                    .build()

                manager.notify(event.id.hashCode() and 0x7FFFFFFF, notification)
            }

        // Bound the set so a long session does not grow it without limit.
        if (alreadyNotified.size > MAX_REMEMBERED) {
            val keep = alreadyNotified.toList().takeLast(MAX_REMEMBERED / 2)
            alreadyNotified.clear()
            alreadyNotified += keep
        }
    }

    private companion object {
        const val RECENT_WINDOW_MILLIS = 10 * 60 * 1000L
        const val MAX_REMEMBERED = 200
    }
}
