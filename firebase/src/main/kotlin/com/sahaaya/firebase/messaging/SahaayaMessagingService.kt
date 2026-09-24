package com.sahaaya.firebase.messaging

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sahaaya.firebase.FirestoreCollections
import com.sahaaya.firebase.R
import com.sahaaya.firebase.UserFields
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives Cloud Messaging traffic.
 *
 * Two jobs, both of which must work before Phase 3 can send a single alert:
 *
 * 1. [onNewToken] keeps `users/{uid}.fcmTokens` current. Tokens rotate on
 *    reinstall, restore and app-data clear, and a stale token fails silently -
 *    the send succeeds and nobody's phone rings. Storing an array rather than
 *    one token supports a caregiver with a phone and a tablet.
 *
 * 2. [onMessageReceived] renders the notification, choosing the channel from
 *    the payload so an emergency is never delivered at the quiet importance.
 */
@AndroidEntryPoint
class SahaayaMessagingService : FirebaseMessagingService() {

    @Inject lateinit var firestore: FirebaseFirestore

    @Inject lateinit var auth: FirebaseAuth

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = auth.currentUser?.uid ?: return
        // Fire-and-forget: if the device is offline, Firestore replays this
        // write from its local queue once the connection returns.
        firestore.collection(FirestoreCollections.USERS)
            .document(uid)
            .update(UserFields.FCM_TOKENS, FieldValue.arrayUnion(token))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data[DATA_TITLE]
            ?: return
        val body = message.notification?.body
            ?: message.data[DATA_BODY]
            .orEmpty()

        val category = message.data[DATA_CATEGORY]
        val isEmergency = category == CATEGORY_EMERGENCY
        showNotification(this, title, body, isEmergency, isSafety = category == CATEGORY_SAFETY)
    }

    private fun showNotification(
        context: Context,
        title: String,
        body: String,
        isEmergency: Boolean,
        isSafety: Boolean = false,
    ) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }

        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val channelId = when {
            isEmergency -> SahaayaNotificationChannels.EMERGENCY_CHANNEL_ID
            isSafety -> SahaayaNotificationChannels.SAFETY_CHANNEL_ID
            else -> SahaayaNotificationChannels.UPDATES_CHANNEL_ID
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_sahaaya_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (isEmergency) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setCategory(
                if (isEmergency) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_STATUS,
            )
            .setAutoCancel(true)
            .apply { pendingIntent?.let(::setContentIntent) }
            .build()

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private companion object {
        const val DATA_TITLE = "title"
        const val DATA_BODY = "body"
        const val DATA_CATEGORY = "category"
        const val CATEGORY_EMERGENCY = "emergency"
        const val CATEGORY_SAFETY = "safety"
    }
}
