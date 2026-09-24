package com.sahaaya.feature.monitoring.fall

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.usecase.monitoring.FallResponseAction
import com.sahaaya.domain.usecase.monitoring.FallResponseStateMachine
import com.sahaaya.domain.usecase.monitoring.ReportFallUseCase
import com.sahaaya.feature.monitoring.R
import com.sahaaya.firebase.messaging.SahaayaNotificationChannels
import com.sahaaya.sensor.fall.FallCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What the "Are you okay?" screen shows. Null means no prompt is open. */
data class FallPrompt(
    val candidate: FallCandidate,
    val deadlineEpochMillis: Long,
)

/**
 * Runs the patient-response step after the sensor reports a possible fall.
 *
 * The one place that decides when a caregiver hears about a fall. The sensor
 * pipeline and the developer simulator both call [onPossibleFall]; everything
 * downstream - the prompt, the timer, the report - is shared.
 *
 * The response timer lives here, in an application-scoped singleton kept alive
 * by the monitoring foreground service, rather than in the prompt activity. A
 * timer in the activity would stop if the activity were killed or never shown,
 * and a patient who could not respond would then never be reported - the case
 * this whole feature exists for.
 */
@Singleton
class FallAlertCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reportFall: ReportFallUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val machine = FallResponseStateMachine()
    private var timeoutJob: Job? = null

    private val _prompt = MutableStateFlow<FallPrompt?>(null)
    val prompt: StateFlow<FallPrompt?> = _prompt.asStateFlow()

    @Synchronized
    fun onPossibleFall(candidate: FallCandidate) {
        when (machine.onPossibleFall()) {
            FallResponseAction.Prompt -> Unit
            else -> {
                Log.i(TAG, "Possible fall ignored: a prompt is already open.")
                return
            }
        }
        val deadline = System.currentTimeMillis() + RESPONSE_WINDOW_MILLIS
        _prompt.value = FallPrompt(candidate, deadline)

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(RESPONSE_WINDOW_MILLIS)
            handle(machine.onTimeout())
        }
        showPrompt()
    }

    fun onImOkay() = handle(machine.onImOkay())

    fun onNeedHelp() = handle(machine.onNeedHelp())

    @Synchronized
    private fun handle(action: FallResponseAction) {
        val candidate = _prompt.value?.candidate
        when (action) {
            FallResponseAction.Dismiss -> {
                Log.i(TAG, "Patient is okay - no event written, nobody notified.")
                close()
            }

            is FallResponseAction.Report -> {
                close()
                if (candidate == null) return
                scope.launch {
                    val result = reportFall(
                        ReportFallUseCase.Params(
                            impactMagnitude = candidate.impactMagnitude,
                            orientationChangeDegrees = candidate.orientationChangeDegrees,
                            detectedAtEpochMillis = candidate.detectedAtEpochMillis,
                            confirmation = action.confirmation,
                            simulated = candidate.simulated,
                        ),
                    )
                    if (result is Outcome.Failure) {
                        Log.e(TAG, "Could not record possible fall: ${result.error.message}")
                    }
                }
            }

            FallResponseAction.Prompt,
            FallResponseAction.Ignore,
            -> Unit
        }
    }

    private fun close() {
        timeoutJob?.cancel()
        timeoutJob = null
        _prompt.value = null
        context.getSystemService(NotificationManager::class.java)?.cancel(PROMPT_NOTIFICATION_ID)
    }

    /**
     * Brings up the prompt.
     *
     * Android blocks activities started from the background, so the reliable
     * route is a full-screen-intent notification: it opens the prompt over the
     * lock screen when allowed, and otherwise appears as a heads-up notification
     * whose "I'm okay" / "Need help" buttons work without opening the app.
     * The direct start covers the common case of the app being in front.
     */
    private fun showPrompt() {
        val open = FallCheckActivity.intent(context)
        runCatching { context.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fullScreen = PendingIntent.getActivity(context, 0, open, immutable)
        fun action(verb: String, code: Int) = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, FallResponseReceiver::class.java).setAction(verb),
            immutable,
        )

        val builder = NotificationCompat.Builder(context, SahaayaNotificationChannels.SAFETY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monitoring)
            .setContentTitle("Possible fall detected")
            .setContentText("Are you okay? Your caregiver will be told if you do not answer.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .addAction(0, "I'm okay", action(FallResponseReceiver.ACTION_OKAY, 1))
            .addAction(0, "Need help", action(FallResponseReceiver.ACTION_HELP, 2))

        val canFullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            manager.canUseFullScreenIntent()
        if (canFullScreen) builder.setFullScreenIntent(fullScreen, true)

        runCatching { manager.notify(PROMPT_NOTIFICATION_ID, builder.build()) }
    }

    companion object {
        private const val TAG = "SahaayaFall"
        private const val PROMPT_NOTIFICATION_ID = 2001
        val RESPONSE_WINDOW_MILLIS: Long = MonitoringSettings.FALL_CONFIRMATION_SECONDS * 1_000L
    }
}
