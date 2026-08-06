package com.sahaaya.feature.monitoring.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.SettingsRepository
import com.sahaaya.domain.usecase.monitoring.ReportInactivityUseCase
import com.sahaaya.sensor.activity.ActivityRecognitionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodically asks: how long since this person last moved?
 *
 * WorkManager rather than an alarm per timeout, because the question is
 * inherently "check every so often", and WorkManager survives reboots, Doze and
 * app death - all of which an inactive patient's phone is likely to be in.
 *
 * Fifteen minutes is WorkManager's minimum period. Against a default timeout of
 * ninety minutes that gives at most a quarter-hour of lag, which is acceptable
 * for a warning-severity signal and costs far less battery than polling.
 */
@HiltWorker
class InactivityCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val activityRecognitionManager: ActivityRecognitionManager,
    private val reportInactivity: ReportInactivityUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val patientId = authRepository.currentUserId() ?: return Result.success()

        val settings = when (val result = settingsRepository.getSettings(patientId)) {
            is Outcome.Failure -> return Result.retry()
            is Outcome.Success -> result.data
        }

        if (!settings.inactivityDetectionEnabled) return Result.success()

        val minutesInactive = activityRecognitionManager.minutesSinceLastMovement()
        if (minutesInactive < settings.inactivityTimeoutMinutes) return Result.success()

        // One alert per quiet spell. Without this the worker would raise a new
        // event every fifteen minutes for someone having an afternoon nap.
        val lastAlert = activityRecognitionManager.lastInactivityAlertAtMillis()
        val lastMovement = activityRecognitionManager.lastMovementAtMillis()
        if (lastAlert > lastMovement) return Result.success()

        return when (reportInactivity(minutesInactive)) {
            is Outcome.Success -> {
                activityRecognitionManager.recordInactivityAlert()
                Result.success()
            }
            // Offline: try again rather than losing the signal entirely.
            is Outcome.Failure -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "sahaaya_inactivity_check"

        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<InactivityCheckWorker>(
                    15,
                    TimeUnit.MINUTES,
                ).build(),
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}
