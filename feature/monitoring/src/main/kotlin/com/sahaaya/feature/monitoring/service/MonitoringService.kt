package com.sahaaya.feature.monitoring.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.SettingsRepository
import com.sahaaya.domain.usecase.monitoring.ReportFallUseCase
import com.sahaaya.feature.monitoring.R
import com.sahaaya.firebase.messaging.SahaayaNotificationChannels
import com.sahaaya.sensor.fall.FallSensorMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the accelerometer alive while the patient's phone is in their pocket.
 *
 * A foreground service, which Android requires for continuous sensor access and
 * which forces a permanent notification. That notification is not a nuisance
 * here - it is the honest disclosure that the phone is monitoring, and the
 * patient can see and stop it.
 *
 * Only the fall detector lives in this service. Geofencing and activity
 * recognition are registered with the OS and fire through broadcast receivers
 * even when nothing of ours is running, so keeping them here would burn battery
 * for no benefit.
 */
@AndroidEntryPoint
class MonitoringService : Service() {

    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var fallSensorMonitor: FallSensorMonitor

    @Inject lateinit var reportFall: ReportFallUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var detectionJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        observeSettingsAndDetect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // START_STICKY: if Android kills this under memory pressure it should
        // come back. A monitoring service that quietly stays dead is the worst
        // possible failure mode for this app.
        return START_STICKY
    }

    override fun onDestroy() {
        detectionJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSettingsAndDetect() {
        val patientId = authRepository.currentUserId()
        if (patientId == null) {
            stopSelf()
            return
        }

        detectionJob = scope.launch {
            settingsRepository.observeSettings(patientId)
                .map { settings ->
                    if (settings.fallDetectionEnabled) settings.fallSensitivity else null
                }
                // Restart the sensor only when the sensitivity actually changes,
                // not on every unrelated settings write.
                .distinctUntilChanged()
                .flatMapLatest { sensitivity ->
                    if (sensitivity == null) {
                        emptyFlow()
                    } else {
                        fallSensorMonitor.detectFalls(sensitivity)
                    }
                }
                .collect { candidate ->
                    when (
                        val result = reportFall(
                            ReportFallUseCase.Params(
                                impactMagnitude = candidate.impactMagnitude,
                                orientationChangeDegrees = candidate.orientationChangeDegrees,
                                detectedAtEpochMillis = candidate.detectedAtEpochMillis,
                            ),
                        )
                    ) {
                        is Outcome.Success -> {
                            // The event is already in Firestore. The countdown
                            // screen now gives the patient a chance to withdraw
                            // it; if they do not, nothing more needs to happen.
                            FallConfirmationActivity.launch(
                                context = this@MonitoringService,
                                eventId = result.data.id,
                            )
                        }

                        is Outcome.Failure -> Log.e(
                            TAG,
                            "Could not record a detected fall: ${result.error.message}",
                        )
                    }
                }
        }
    }

    private fun startForegroundWithNotification() {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MonitoringService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(
            this,
            SahaayaNotificationChannels.MONITORING_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_monitoring)
            .setContentTitle("Sahaaya is watching over you")
            .setContentText("Fall detection is on. Tap Stop to turn it off.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Stop", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "SahaayaMonitoring"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.sahaaya.monitoring.STOP"

        fun start(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
