package com.sahaaya.feature.medication.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.usecase.medication.FlagMissedDosesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Turns unanswered reminders into missed-dose events for the caregiver.
 *
 * Periodic rather than one alarm per dose. An alarm scheduled for "grace period
 * after the dose" is exactly the alarm Doze delays or drops, and a missed dose
 * nobody notices is the failure this whole feature exists to prevent. A sweep
 * that runs hourly cannot miss one - it can only report it slightly late.
 */
@HiltWorker
class MissedDoseWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val flagMissedDoses: FlagMissedDosesUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = when (flagMissedDoses()) {
        is Outcome.Success -> Result.success()
        // Offline or signed out: retry rather than silently dropping the sweep.
        is Outcome.Failure -> Result.retry()
    }

    companion object {
        private const val WORK_NAME = "sahaaya_missed_dose_sweep"

        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<MissedDoseWorker>(1, TimeUnit.HOURS).build(),
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}
