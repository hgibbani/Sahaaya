package com.sahaaya.sensor.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Notes that the patient moved.
 *
 * Every delivered transition resets the inactivity clock. Nothing is written to
 * the network here - this fires often, and the only thing that matters is the
 * local timestamp that [InactivityCheckWorker] later compares against the
 * configured timeout.
 */
@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {

    @Inject lateinit var activityRecognitionManager: ActivityRecognitionManager

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        if (result.transitionEvents.isEmpty()) return

        activityRecognitionManager.recordMovement()
    }
}
