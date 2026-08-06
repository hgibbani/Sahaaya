package com.sahaaya.sensor.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.SafeZone
import com.sahaaya.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the patient's safe zone with the platform geofencing service.
 *
 * The OS does the monitoring, not the app: it batches geofence evaluation across
 * every app on the device against location fixes it was taking anyway. Polling
 * GPS ourselves would drain the battery of a phone that has to last all day and
 * still be alive when something goes wrong.
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geofencingClient: GeofencingClient,
    private val locationRepository: LocationRepository,
) {

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    suspend fun register(zone: SafeZone): Outcome<Unit> {
        if (!locationRepository.hasLocationPermission()) {
            return Outcome.Failure(
                AppError.PermissionDenied(
                    "Location permission is needed to watch the safe zone.",
                ),
            )
        }
        if (!locationRepository.hasBackgroundLocationPermission()) {
            return Outcome.Failure(
                AppError.PermissionDenied(
                    "Safe-zone alerts need location access set to \"Allow all the time\".",
                ),
            )
        }

        val geofence = Geofence.Builder()
            .setRequestId(SAFE_ZONE_ID)
            .setCircularRegion(
                zone.centre.latitude,
                zone.centre.longitude,
                zone.radiusMetres.toFloat(),
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            // EXIT only. Sahaaya cares that the patient left, not that they came
            // home - and every extra transition is another wake-up.
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            // A short delay before reporting, so a GPS fix that jitters across
            // the boundary and back does not raise an alert.
            .setLoiteringDelay(LOITERING_DELAY_MILLIS)
            .setNotificationResponsiveness(RESPONSIVENESS_MILLIS)
            .build()

        val request = GeofencingRequest.Builder()
            // No INITIAL_TRIGGER_EXIT: without it, setting up a safe zone while
            // already outside it would fire an alert during setup.
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()

        return try {
            geofencingClient.removeGeofences(pendingIntent).await()
            geofencingClient.addGeofences(request, pendingIntent).await()
            Outcome.Success(Unit)
        } catch (security: SecurityException) {
            Outcome.Failure(AppError.PermissionDenied(cause = security))
        } catch (throwable: Throwable) {
            Outcome.Failure(
                AppError.Unknown(
                    "Could not start safe-zone monitoring. Check that location is on.",
                    throwable,
                ),
            )
        }
    }

    suspend fun unregister(): Outcome<Unit> = try {
        geofencingClient.removeGeofences(pendingIntent).await()
        Outcome.Success(Unit)
    } catch (throwable: Throwable) {
        Outcome.Failure(AppError.Unknown(cause = throwable))
    }

    companion object {
        const val SAFE_ZONE_ID = "sahaaya_safe_zone"
        private const val REQUEST_CODE = 4001
        private const val LOITERING_DELAY_MILLIS = 30_000
        private const val RESPONSIVENESS_MILLIS = 60_000
    }
}
