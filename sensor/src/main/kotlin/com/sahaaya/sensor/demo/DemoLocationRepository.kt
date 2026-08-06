package com.sahaaya.sensor.demo

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.repository.LocationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A fixed position, used while [com.sahaaya.core.demo.DemoConfig.ENABLED].
 *
 * The real implementation needs runtime location permission and a GPS fix,
 * neither of which is reliable on an emulator in a room with no sky. Returning a
 * known point keeps the event detail screen and the geofence-exit summary
 * showing what they would show in the field, instead of "location unavailable"
 * on every alert.
 *
 * It lives in `:sensor` rather than `:data` because that is where the contract
 * is satisfied - the layering does not change just because the demo does.
 */
@Singleton
class DemoLocationRepository @Inject constructor() : LocationRepository {

    override suspend fun currentLocation(): Outcome<GeoPoint> = Outcome.Success(
        GeoPoint(latitude = 9.9312, longitude = 76.2673, accuracyMetres = 12f),
    )

    override fun hasLocationPermission(): Boolean = true

    override fun hasBackgroundLocationPermission(): Boolean = true
}
