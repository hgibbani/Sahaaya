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
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.model.SafeZoneStatus
import com.sahaaya.domain.model.TrackPoint
import com.sahaaya.domain.repository.LocationRepository
import com.sahaaya.domain.repository.ProfileRepository
import com.sahaaya.domain.repository.SettingsRepository
import com.sahaaya.domain.repository.TrackingRepository
import com.sahaaya.domain.usecase.monitoring.TrackRouteThinner
import com.sahaaya.core.demo.FeatureScope
import com.sahaaya.feature.monitoring.fall.FallAlertCoordinator
import com.sahaaya.domain.usecase.monitoring.TrackSafeZoneUseCase
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

    @Inject lateinit var fallAlertCoordinator: FallAlertCoordinator

    @Inject lateinit var trackSafeZone: TrackSafeZoneUseCase

    @Inject lateinit var trackingRepository: TrackingRepository

    @Inject lateinit var locationRepository: LocationRepository

    @Inject lateinit var profileRepository: ProfileRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var detectionJob: Job? = null
    private var safeZoneJob: Job? = null
    private var trackingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // From Android 14 a "health" foreground service may only start if a
        // health permission (here ACTIVITY_RECOGNITION) has been granted. If the
        // patient refused it, startForeground throws - and an uncaught throw
        // here takes the whole app down at the moment monitoring was supposed to
        // begin. Stopping quietly is bad; crashing the patient's phone is worse.
        try {
            startForegroundWithNotification()
        } catch (security: SecurityException) {
            Log.e(TAG, "Cannot start monitoring foreground service", security)
            stopSelf()
            return
        }
        // Each of these owns its own job and its own uid wait, so one being
        // disabled or failing cannot stop the others. That independence is not
        // theoretical: observeSettingsAndDetect() returns early when fall
        // detection is off in this build, and while these three calls were a
        // single chain that early return silently took safe-zone tracking with
        // it - the service ran, the notification claimed monitoring was on, and
        // the caregiver's safe-zone card sat on "Checking" forever.
        observeSettingsAndDetect()
        observeSafeZone()
        observeTracking()
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
        trackingJob?.cancel()
        safeZoneJob?.cancel()
        detectionJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSettingsAndDetect() {
        // The flag is checked here, not only in the use cases that apply
        // settings to the controller.
        //
        // This service observes `settings/{uid}` directly, so those use cases
        // cannot gate it. Since safe-zone tracking now keeps the service
        // running, a stored `fallDetectionEnabled = true` - written back when
        // that was still the default - was enough to register the accelerometer
        // and start raising fall events in a build where fall detection is
        // supposed to be off. That is where the unexpected alarms came from.
        if (!FeatureScope.FALL_DETECTION_ACTIVE) {
            Log.i(TAG, "Fall detection is disabled in this build; sensor not registered.")
            return
        }

        detectionJob = scope.launch {
            // Waits for the uid like the other two loops. Reading it once here
            // meant that when START_STICKY restarted the service before Auth
            // had restored the session, the accelerometer was never registered
            // for the life of the process.
            awaitSignedInUid()
                .flatMapLatest { patientId -> settingsRepository.observeSettings(patientId) }
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
                    // The sensor only says "possible fall". The coordinator asks
                    // the patient first; nobody is notified until they answer
                    // "Need help" or the response window runs out.
                    fallAlertCoordinator.onPossibleFall(candidate)
                }
        }
    }

    /**
     * Follows the caregiver's safe-zone configuration and tracks against it.
     *
     * flatMapLatest means a caregiver moving the zone or changing the radius
     * restarts tracking against the new one immediately, and switching the zone
     * off cancels location updates rather than leaving the radio running.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSafeZone() {
        safeZoneJob = scope.launch {
            // Waits for the uid instead of reading it once.
            //
            // Reading it once was a real defect: this service can be created
            // before Firebase Auth has restored the session - most obviously
            // when START_STICKY brings it back after Android kills it - and the
            // uid is null at that moment. The old code returned immediately
            // and, because onCreate does not run again for an already-running
            // service, safe-zone tracking stayed dead for the life of the
            // process while the notification still claimed monitoring was on.
            awaitSignedInUid()
                .flatMapLatest { patientId ->
                    settingsRepository.observeSettings(patientId)
                }
                .map { settings ->
                    settings.safeZone.takeIf {
                        settings.geofenceEnabled && FeatureScope.GEOFENCE_ACTIVE
                    }
                }
                .distinctUntilChanged()
                .collectLatest { zone ->
                    if (zone == null) return@collectLatest
                    try {
                        trackSafeZone(zone)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        Log.e(TAG, "Safe-zone tracking stopped", throwable)
                    }
                }
        }
    }

    /**
     * Files route points while a caregiver has tracking switched on.
     *
     * Runs beside safe-zone tracking rather than inside it, because the two
     * answer different questions and are switched on by different people: the
     * patient's safe zone may be off while a caregiver is watching them walk,
     * and a zone may be armed with nobody watching.
     *
     * Both collect from [LocationRepository.locationUpdates] at the same
     * interval. That is one hardware request as far as the fused provider is
     * concerned, so the second collector costs battery only in the callbacks,
     * not in the radio - which is why this does not raise the update rate or
     * add a second location service, per the brief.
     *
     * `publishLatest` is the important subtlety. The safe-zone loop already
     * writes `patients/{uid}` with an INSIDE/OUTSIDE verdict on every accepted
     * fix. If this loop also wrote that document it would overwrite that
     * verdict with UNKNOWN - a green or red badge on the caregiver's card
     * replaced by "Location unavailable" seconds later. So the latest position
     * is published from here only when no zone is armed to publish it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTracking() {
        trackingJob = scope.launch {
            awaitSignedInUid()
                .flatMapLatest { patientId ->
                    combine(
                        trackingRepository.observeTrackingState(patientId),
                        settingsRepository.observeSettings(patientId).map { settings ->
                            settings.geofenceEnabled &&
                                settings.safeZone != null &&
                                FeatureScope.GEOFENCE_ACTIVE
                        },
                    ) { tracking, zoneArmed ->
                        Triple(patientId, tracking.activeSessionId, zoneArmed)
                    }
                }
                .distinctUntilChanged()
                .collectLatest { (patientId, sessionId, zoneArmed) ->
                    if (sessionId == null) return@collectLatest
                    try {
                        collectRoute(
                            patientId = patientId,
                            sessionId = sessionId,
                            publishLatest = !zoneArmed,
                        )
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        Log.e(TAG, "Live tracking stopped", throwable)
                    }
                }
        }
    }

    private suspend fun collectRoute(
        patientId: String,
        sessionId: String,
        publishLatest: Boolean,
    ) {
        // A fresh thinner per session, so the first fix of a new session is
        // always filed even if the patient has not moved since the last one.
        val thinner = TrackRouteThinner()

        locationRepository.locationUpdates(FeatureScope.LOCATION_INTERVAL_MILLIS)
            .collect { fix ->
                if (publishLatest) {
                    profileRepository.updatePatientLocation(
                        patientId = patientId,
                        location = PatientLocation(
                            point = fix,
                            recordedAtEpochMillis = System.currentTimeMillis(),
                            status = SafeZoneStatus.UNKNOWN,
                            metresFromBoundary = null,
                        ),
                    )
                }

                val worthFiling = thinner.accept(fix) ?: return@collect
                val result = trackingRepository.appendTrackPoint(
                    sessionId = sessionId,
                    patientId = patientId,
                    point = TrackPoint(
                        point = worthFiling,
                        recordedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
                if (result is Outcome.Failure) {
                    Log.e(TAG, "Could not file route point: ${result.error.message}")
                }
            }
    }

    /**
     * Emits the signed-in uid once there is one, retrying until then.
     *
     * Deliberately uses the synchronous Auth check rather than
     * `observeCurrentUser()`. That flow joins Auth with the Firestore
     * `users/{uid}` document, so depending on it made location tracking wait on
     * a Firestore listener - and when that listener was slow or offline, the
     * tracker never started at all and `dumpsys location` showed no request
     * from this app. Whether the patient is signed in is an Auth question, and
     * answering it must not require the network.
     */
    private fun awaitSignedInUid(): Flow<String> = flow {
        var uid = authRepository.currentUserId()
        while (uid == null) {
            delay(UID_RETRY_MILLIS)
            uid = authRepository.currentUserId()
        }
        emit(uid)
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
            .setContentText("Safe-zone monitoring is on. Tap Stop to turn it off.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Stop", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val UID_RETRY_MILLIS = 1_000L
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
