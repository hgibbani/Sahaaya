package com.sahaaya.sensor.activity

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks when the patient last moved.
 *
 * Uses the Activity Transition API rather than sampling the accelerometer
 * ourselves: the platform already classifies still/walking/in-vehicle from
 * sensor data it collects anyway, so this costs almost nothing, whereas keeping
 * our own accelerometer awake purely to notice stillness would be the largest
 * battery cost in the app.
 *
 * The last-movement timestamp lives in SharedPreferences rather than Firestore.
 * It is written on every transition, which is far too often for a network round
 * trip, and it is meaningless on any device other than this one.
 */
@Singleton
class ActivityRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityRecognitionClient: ActivityRecognitionClient,
) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ActivityTransitionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun startTracking(): Outcome<Unit> {
        if (!hasPermission()) {
            return Outcome.Failure(
                AppError.PermissionDenied(
                    "Physical activity permission is needed to notice long periods without movement.",
                ),
            )
        }

        // Only movement transitions are requested. Entering STILL is not
        // interesting - a patient sitting down is normal. What matters is how
        // long it has been since they last moved, so every movement resets the
        // clock and the elapsed time is checked periodically.
        val transitions = listOf(
            transition(DetectedActivity.WALKING, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.RUNNING, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.ON_FOOT, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.ON_BICYCLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.STILL, ActivityTransition.ACTIVITY_TRANSITION_EXIT),
        )

        return try {
            activityRecognitionClient
                .requestActivityTransitionUpdates(
                    ActivityTransitionRequest(transitions),
                    pendingIntent,
                )
                .await()
            // Start the clock now, so a fresh install does not immediately look
            // like hours of inactivity.
            recordMovement()
            Outcome.Success(Unit)
        } catch (security: SecurityException) {
            Outcome.Failure(AppError.PermissionDenied(cause = security))
        } catch (throwable: Throwable) {
            Outcome.Failure(AppError.Unknown(cause = throwable))
        }
    }

    suspend fun stopTracking(): Outcome<Unit> = try {
        activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent).await()
        Outcome.Success(Unit)
    } catch (throwable: Throwable) {
        Outcome.Failure(AppError.Unknown(cause = throwable))
    }

    fun recordMovement(atMillis: Long = System.currentTimeMillis()) {
        preferences.edit { putLong(KEY_LAST_MOVEMENT, atMillis) }
    }

    fun lastMovementAtMillis(): Long =
        preferences.getLong(KEY_LAST_MOVEMENT, 0L)

    fun minutesSinceLastMovement(nowMillis: Long = System.currentTimeMillis()): Int {
        val last = lastMovementAtMillis()
        if (last == 0L) return 0
        return ((nowMillis - last) / 60_000L).toInt().coerceAtLeast(0)
    }

    /** Remembers that an alert was raised, so one quiet spell is one alert. */
    fun recordInactivityAlert(atMillis: Long = System.currentTimeMillis()) {
        preferences.edit { putLong(KEY_LAST_ALERT, atMillis) }
    }

    fun lastInactivityAlertAtMillis(): Long =
        preferences.getLong(KEY_LAST_ALERT, 0L)

    private fun transition(activity: Int, type: Int): ActivityTransition =
        ActivityTransition.Builder()
            .setActivityType(activity)
            .setActivityTransition(type)
            .build()

    private companion object {
        const val PREFS_NAME = "sahaaya_activity"
        const val KEY_LAST_MOVEMENT = "last_movement_at"
        const val KEY_LAST_ALERT = "last_inactivity_alert_at"
        const val REQUEST_CODE = 4002
    }
}
