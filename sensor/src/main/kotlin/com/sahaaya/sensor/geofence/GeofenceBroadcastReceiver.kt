package com.sahaaya.sensor.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.usecase.monitoring.ReportGeofenceExitUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives safe-zone exits from the platform geofencing service.
 *
 * A [BroadcastReceiver] rather than a service because the OS delivers geofence
 * transitions here even when the app is not running - which is the only
 * situation that matters, since a patient who has wandered off is not looking at
 * their phone.
 */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var reportGeofenceExit: ReportGeofenceExitUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return

        if (event.hasError()) {
            Log.w(TAG, "Geofencing error code ${event.errorCode}")
            return
        }

        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT) return

        val exitPoint = event.triggeringLocation?.let { location ->
            GeoPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMetres = location.accuracy,
            )
        }

        // goAsync keeps the process alive past onReceive's ~10 s budget while
        // the Firestore write completes. Without it the alert is dropped
        // exactly when the app was not already running - the case this receiver
        // exists for.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                reportGeofenceExit(exitPoint)
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to report safe-zone exit", throwable)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SahaayaGeofence"
    }
}
