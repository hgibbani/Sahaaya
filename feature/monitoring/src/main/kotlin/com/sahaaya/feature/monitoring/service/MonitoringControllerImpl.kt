package com.sahaaya.feature.monitoring.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.repository.MonitoringController
import com.sahaaya.feature.monitoring.work.InactivityCheckWorker
import com.sahaaya.sensor.activity.ActivityRecognitionManager
import com.sahaaya.sensor.geofence.GeofenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a [MonitoringSettings] object into the platform state it describes.
 *
 * Each monitor is started or stopped to match, so the phone is always doing
 * exactly what the settings screen says it is doing - no drift between the two.
 *
 * Failures are collected rather than short-circuiting. If the safe zone cannot
 * be registered because background location was refused, fall detection and
 * inactivity monitoring should still start; partial monitoring is far better
 * than none, and the caller is told which part failed.
 */
@Singleton
class MonitoringControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geofenceManager: GeofenceManager,
    private val activityRecognitionManager: ActivityRecognitionManager,
) : MonitoringController {

    private var running = false

    override suspend fun applySettings(settings: MonitoringSettings): Outcome<Unit> {
        val problems = mutableListOf<String>()

        // --- Fall detection: the foreground service ---
        if (settings.fallDetectionEnabled) {
            MonitoringService.start(context)
            running = true
        } else {
            MonitoringService.stop(context)
            running = false
        }

        // --- Safe zone ---
        val zone = settings.safeZone
        if (settings.geofenceEnabled && zone != null) {
            when (val result = geofenceManager.register(zone)) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> problems += result.error.message
            }
        } else {
            geofenceManager.unregister()
        }

        // --- Inactivity ---
        if (settings.inactivityDetectionEnabled) {
            when (val result = activityRecognitionManager.startTracking()) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> problems += result.error.message
            }
            InactivityCheckWorker.schedule(WorkManager.getInstance(context))
        } else {
            activityRecognitionManager.stopTracking()
            InactivityCheckWorker.cancel(WorkManager.getInstance(context))
        }

        return if (problems.isEmpty()) {
            Outcome.Success(Unit)
        } else {
            Outcome.Failure(
                com.sahaaya.core.result.AppError.PermissionDenied(
                    problems.joinToString(" "),
                ),
            )
        }
    }

    override suspend fun stopAll(): Outcome<Unit> {
        MonitoringService.stop(context)
        geofenceManager.unregister()
        activityRecognitionManager.stopTracking()
        InactivityCheckWorker.cancel(WorkManager.getInstance(context))
        running = false
        return Outcome.Success(Unit)
    }

    override fun isRunning(): Boolean = running

    private companion object {
        val KEEP: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE
    }
}
