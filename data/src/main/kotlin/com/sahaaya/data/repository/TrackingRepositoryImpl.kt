package com.sahaaya.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.TrackPoint
import com.sahaaya.domain.model.TrackingState
import com.sahaaya.domain.repository.TrackingRepository
import com.sahaaya.firebase.SettingsFields
import com.sahaaya.firebase.TrackPointFields
import com.sahaaya.firebase.source.EventDataSource
import com.sahaaya.firebase.source.TrackingDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingRepositoryImpl @Inject constructor(
    private val trackingDataSource: TrackingDataSource,
    private val eventDataSource: EventDataSource,
) : TrackingRepository {

    /**
     * Reads the tracking flags off the patient's settings document.
     *
     * A missing document means tracking is off, not unknown. Absence here is
     * genuinely informative: nobody has ever started a session for this patient.
     */
    override fun observeTrackingState(patientId: String): Flow<TrackingState> =
        eventDataSource.observeSettings(patientId)
            .map { snapshot -> snapshot?.toTrackingState() ?: TrackingState.INACTIVE }

    override suspend fun startTracking(
        patientId: String,
        caregiverId: String,
    ): Outcome<String> {
        val sessionId = trackingDataSource.newSessionId()
        return when (
            val result = trackingDataSource.startSession(
                sessionId = sessionId,
                patientId = patientId,
                caregiverId = caregiverId,
                startedAt = System.currentTimeMillis(),
            )
        ) {
            is Outcome.Failure -> result
            is Outcome.Success -> Outcome.Success(sessionId)
        }
    }

    /**
     * Ends whatever session the settings document currently names.
     *
     * The id is read here rather than passed in by the caller, so a caregiver
     * whose screen is showing a stale session still stops the one that is
     * actually running.
     */
    override suspend fun stopTracking(
        patientId: String,
        caregiverId: String,
    ): Outcome<Unit> {
        val sessionId = when (val current = eventDataSource.getSettings(patientId)) {
            is Outcome.Failure -> null
            is Outcome.Success -> current.data?.getString(SettingsFields.TRACKING_SESSION_ID)
        }
        return trackingDataSource.stopSession(
            sessionId = sessionId,
            patientId = patientId,
            caregiverId = caregiverId,
            stoppedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun appendTrackPoint(
        sessionId: String,
        patientId: String,
        point: TrackPoint,
    ): Outcome<Unit> = trackingDataSource.appendPoint(
        sessionId = sessionId,
        data = buildMap {
            put(TrackPointFields.LATITUDE, point.point.latitude)
            put(TrackPointFields.LONGITUDE, point.point.longitude)
            put(TrackPointFields.ACCURACY, point.point.accuracyMetres?.toDouble())
            put(TrackPointFields.TIMESTAMP, point.recordedAtEpochMillis)
        },
    )

    /** Reversed into walking order; the query asks for newest first. */
    override fun observeRoute(sessionId: String, limit: Int): Flow<List<TrackPoint>> =
        trackingDataSource.observeRoute(sessionId, limit)
            .map { documents -> documents.mapNotNull { it.toTrackPoint() }.reversed() }
}

private fun DocumentSnapshot.toTrackingState(): TrackingState = TrackingState(
    active = getBoolean(SettingsFields.TRACKING_ACTIVE) ?: false,
    sessionId = getString(SettingsFields.TRACKING_SESSION_ID),
    startedAtEpochMillis = getLong(SettingsFields.TRACKING_STARTED_AT),
    startedByCaregiverId = getString(SettingsFields.TRACKING_STARTED_BY),
    stoppedAtEpochMillis = getLong(SettingsFields.TRACKING_STOPPED_AT),
    stoppedByCaregiverId = getString(SettingsFields.TRACKING_STOPPED_BY),
)

/**
 * A point missing a coordinate is dropped rather than defaulted.
 *
 * Defaulting a missing latitude to 0.0 would draw the route through the Gulf of
 * Guinea, which is the kind of "working" map that wastes a caregiver's time in
 * the one situation where they have none.
 */
private fun DocumentSnapshot.toTrackPoint(): TrackPoint? {
    val latitude = getDouble(TrackPointFields.LATITUDE) ?: return null
    val longitude = getDouble(TrackPointFields.LONGITUDE) ?: return null
    return TrackPoint(
        point = GeoPoint(
            latitude = latitude,
            longitude = longitude,
            accuracyMetres = getDouble(TrackPointFields.ACCURACY)?.toFloat(),
        ),
        recordedAtEpochMillis = getLong(TrackPointFields.TIMESTAMP) ?: 0L,
    )
}
