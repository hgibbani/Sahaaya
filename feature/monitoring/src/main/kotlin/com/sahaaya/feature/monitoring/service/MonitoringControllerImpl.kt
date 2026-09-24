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

        // --- The foreground service ---
        //
        // It now hosts two things: fall detection (off in this build) and
        // safe-zone tracking. Either being enabled is reason to run it, and
        // neither means stop.
        val needsService = settings.fallDetectionEnabled ||
            (settings.geofenceEnabled && settings.safeZone != null)

        if (needsService) {
            MonitoringService.start(context)
            running = true
        } else {
            MonitoringService.stop(context)
            running = false
        }

        // --- Safe zone ---
        //
        // The platform Geofencing API is deliberately NOT registered.
        //
        // It would be a second, competing source of exit events: the service
        // already samples location and decides inside/outside itself, so having
        // the OS also fire GEOFENCE_TRANSITION_EXIT into
        // GeofenceBroadcastReceiver would report the same crossing twice and
        // wake the caregiver twice.
        //
        // Sampling in the service is also what makes the caregiver's card
        // possible at all - the OS geofence reports crossings but never
        // "currently 82 m inside", and it needs ACCESS_BACKGROUND_LOCATION,
        // which a foreground service with the `location` type does not.
        //
        // GeofenceManager stays in the tree for the moment that trade-off is
        // revisited; it is simply not the authority today.
        geofenceManager.unregister()

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
