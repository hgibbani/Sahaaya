package com.sahaaya.sensor.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.Priority
import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single position, on demand.
 *
 * Sahaaya never streams or stores a location trail. It asks where the patient is
 * at the moment an event is raised, attaches that one point, and stops. A
 * continuous trail would be a far more sensitive record than anything the app
 * needs, and it is the thing families object to most.
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
) : LocationRepository {

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
