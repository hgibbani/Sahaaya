package com.sahaaya.sensor.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one authoritative source of position in Sahaaya.
 *
 * Two modes, deliberately kept in the same class so no second location stack can
 * grow beside it:
 *
 * - [currentLocation] - a single fix, on demand, attached to an event as it is
 *   raised.
 * - [locationUpdates] - a periodic stream, used only while safe-zone monitoring
 *   is switched on.
 *
 * Even the stream is not a location trail: each fix replaces the last one on
 * `patients/{uid}`, and no history is kept. A stored trail would be far more
 * sensitive than anything this app needs, and it is the thing families object
 * to most.
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
) : LocationRepository {

    override fun locationUpdates(intervalMillis: Long): Flow<GeoPoint> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        // HIGH_ACCURACY, not BALANCED.
        //
        // BALANCED leans on wifi and cell positioning and typically returns
        // 50-100 m accuracy. That is unusable here for two compounding reasons:
        // the smallest safe zone the app allows is 100 m, so a 100 m fix cannot
        // say which side of the boundary the patient is on; and
        // SafeZoneEvaluator rejects anything worse than 50 m, so those fixes
        // were being discarded before they could confirm anything. The status
        // then sat at UNKNOWN indefinitely and the caregiver's card read
        // "Location unavailable" for a patient standing still at home.
        //
        // The battery cost is paid back by the interval, which is what actually
        // governs how often the radio wakes.
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMillis,
        )
            // Never faster than half the requested interval, however many other
            // apps are asking for fixes. Without this the callback can fire far
            // more often than intended and the battery cost stops being ours to
            // reason about.
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            // Deliberately NO minimum displacement.
            //
            // Filtering out fixes that have not moved looks like a free battery
            // saving, and it silently breaks the safe zone: a patient sitting
            // still at home produces one fix and then nothing, so the evaluator
            // never receives the second confirming fix it needs and the status
            // stays UNKNOWN forever. The caregiver's card reads "Location
            // unavailable" for a patient who is simply indoors - which is
            // exactly what two-device testing showed.
            //
            // The interval is the battery control here; a displacement filter
            // is not a substitute for it, because "no news" and "not moving"
            // have to stay distinguishable.
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(
                    GeoPoint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMetres = if (location.hasAccuracy()) {
                            location.accuracy
                        } else {
                            null
                        },
                    ),
                )
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper(),
            )
        } catch (security: SecurityException) {
            close(security)
            return@callbackFlow
        }

        awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    override suspend fun currentLocation(): Outcome<GeoPoint> {
        if (!hasLocationPermission()) {
            return Outcome.Failure(
                AppError.PermissionDenied(
                    "Location permission is needed to include where this happened.",
                ),
            )
        }

        return try {
            // A fresh fix can take many seconds and an alert must not wait that
            // long, so a timeout falls back to the last known position. A
            // slightly stale location beats no location in an emergency.
            val fresh = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MILLIS) {
                fusedLocationClient.getCurrentLocation(
                    CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setGranularity(Granularity.GRANULARITY_FINE)
                        .setMaxUpdateAgeMillis(MAX_UPDATE_AGE_MILLIS)
                        .build(),
                    null,
                ).await()
            }

            val location = fresh ?: fusedLocationClient.lastLocation.await()

            if (location == null) {
                Outcome.Failure(
                    AppError.NotFound(
                        "Could not determine the location. Check that GPS is on.",
                    ),
                )
            } else {
                Outcome.Success(
                    GeoPoint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMetres = location.accuracy,
                    ),
                )
            }
        } catch (security: SecurityException) {
            Outcome.Failure(AppError.PermissionDenied(cause = security))
        } catch (throwable: Throwable) {
            Outcome.Failure(AppError.Unknown(cause = throwable))
        }
    }

    override fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Geofencing only fires reliably in the background with this grant, and on
     * Android 10+ it must be requested separately, after foreground location has
     * already been granted.
     */
    override fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasLocationPermission()
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val FRESH_FIX_TIMEOUT_MILLIS = 8_000L
        const val MAX_UPDATE_AGE_MILLIS = 60_000L
    }
}
