package com.sahaaya.domain.repository

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.model.SafeZone
import kotlinx.coroutines.flow.Flow

/** How closely this patient is watched. Owned by the patient, visible to caregivers. */
interface SettingsRepository {

    fun observeSettings(patientId: String): Flow<MonitoringSettings>

    suspend fun getSettings(patientId: String): Outcome<MonitoringSettings>

    suspend fun saveSettings(settings: MonitoringSettings): Outcome<Unit>

    /**
     * Writes only the safe-zone fields.
     *
     * Separate from [saveSettings] because the caregiver is allowed to author
     * these and nothing else on the document. A whole-document save would touch
     * fall sensitivity, inactivity timeouts and reminder settings too, and the
     * Firestore rule refuses a caregiver write that changes any key outside the
     * safe zone - correctly, since those are the patient's own choices.
     *
     * A null [zone] disables the geofence while leaving the last centre and
     * radius on the document, so turning it back on does not mean re-picking it.
     */
    suspend fun updateSafeZone(
        patientId: String,
        zone: SafeZone?,
        enabled: Boolean,
    ): Outcome<Unit>
}

/**
 * Where the patient is right now.
 *
 * Deliberately narrow. Sahaaya never streams or stores a location trail - it
 * asks for a position at the moment an event is raised, and stores that single
 * point with the event. A continuous trail would be a far more sensitive record
 * than anything the app needs.
 */
interface LocationRepository {

    /** Best available position, or a failure if permission is missing or GPS is off. */
    suspend fun currentLocation(): Outcome<GeoPoint>

    /**
     * A stream of fixes roughly every [intervalMillis].
     *
     * Cold: the platform request is registered when collection starts and
     * removed when it stops, so nothing draws power while nobody is watching.
     * The interval is a request, not a guarantee - Android will batch and delay
     * it to save battery, which is the desired behaviour here.
     */
    fun locationUpdates(intervalMillis: Long): Flow<GeoPoint>

    /** Whether the runtime location permissions needed for geofencing are granted. */
    fun hasLocationPermission(): Boolean

    fun hasBackgroundLocationPermission(): Boolean
}

/**
 * Starts and stops the platform monitors.
 *
 * Implemented in the Android layer because registering a sensor listener,
 * a geofence and an activity-transition request are all Android concerns - but
 * declared here so use cases can turn monitoring on and off when settings
 * change without knowing any of that.
 */
interface MonitoringController {

    /** Applies [settings], starting or stopping each monitor to match. */
    suspend fun applySettings(settings: MonitoringSettings): Outcome<Unit>

    suspend fun stopAll(): Outcome<Unit>

    fun isRunning(): Boolean
}
