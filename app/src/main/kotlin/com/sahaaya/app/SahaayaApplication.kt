package com.sahaaya.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.sahaaya.feature.medication.reminder.MissedDoseWorker
import com.sahaaya.firebase.messaging.SahaayaNotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point and the root of the Hilt object graph.
 *
 * Notification channels are created here rather than lazily on first alert.
 * Android will not deliver a notification to a channel that does not exist yet,
 * and the first alert this app ever sends is the one that matters most.
 *
 * Implements [Configuration.Provider] so WorkManager can construct
 * `@HiltWorker` workers - the inactivity check and the missed-dose sweep both
 * need repositories injected, which the default worker factory cannot supply.
 */
@HiltAndroidApp
class SahaayaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        SahaayaNotificationChannels.createAll(this)

        // The missed-dose sweep is scheduled unconditionally: it is cheap, it
        // exits immediately when there is nothing to do, and it must be running
        // before the first reminder fires rather than after it is missed.
        MissedDoseWorker.schedule(WorkManager.getInstance(this))
    }
}
