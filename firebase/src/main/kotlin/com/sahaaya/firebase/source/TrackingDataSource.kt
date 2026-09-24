package com.sahaaya.firebase.source

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.sahaaya.core.result.Outcome
import com.sahaaya.firebase.FirestoreCollections
import com.sahaaya.firebase.SettingsFields
import com.sahaaya.firebase.TrackPointFields
import com.sahaaya.firebase.TrackingSessionFields
import com.sahaaya.firebase.firebaseCall
import com.sahaaya.firebase.orOnError
import com.sahaaya.firebase.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore access for caregiver-controlled live tracking.
 *
 * Two documents move together whenever tracking starts or stops:
 *
 * - `trackingSessions/{sessionId}` - the session and its route,
 * - `settings/{patientId}` - the flags the patient's phone watches.
 *
 * They are written in a batch rather than one after the other. A half-applied
 * start would leave `trackingActive = true` pointing at no session, and the
 * patient's phone would then collect fixes with nowhere to file them; a
 * half-applied stop would leave a patient's phone tracking after the caregiver
 * was told it had stopped, which is the worse of the two failures.
 */
@Singleton
class TrackingDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /** Allocates a session id without writing, so the caller can return it. */
    fun newSessionId(): String =
        firestore.collection(FirestoreCollections.TRACKING_SESSIONS).document().id

    suspend fun startSession(
        sessionId: String,
        patientId: String,
        caregiverId: String,
        startedAt: Long,
    ): Outcome<Unit> = firebaseCall {
        val batch = firestore.batch()

        batch.set(
            firestore.collection(FirestoreCollections.TRACKING_SESSIONS).document(sessionId),
            mapOf(
                TrackingSessionFields.PATIENT_ID to patientId,
                TrackingSessionFields.CAREGIVER_ID to caregiverId,
                TrackingSessionFields.STARTED_AT to startedAt,
                TrackingSessionFields.STOPPED_AT to null,
                TrackingSessionFields.ACTIVE to true,
            ),
        )

        batch.set(
            firestore.collection(FirestoreCollections.SETTINGS).document(patientId),
            mapOf(
                SettingsFields.PATIENT_ID to patientId,
                SettingsFields.TRACKING_ACTIVE to true,
                SettingsFields.TRACKING_SESSION_ID to sessionId,
                SettingsFields.TRACKING_STARTED_AT to startedAt,
                SettingsFields.TRACKING_STARTED_BY to caregiverId,
                SettingsFields.UPDATED_AT to startedAt,
            ),
            SetOptions.merge(),
        )

        batch.commit().await()
    }

    suspend fun stopSession(
        sessionId: String?,
        patientId: String,
        caregiverId: String,
        stoppedAt: Long,
    ): Outcome<Unit> = firebaseCall {
        val batch = firestore.batch()

        if (!sessionId.isNullOrBlank()) {
            batch.set(
                firestore.collection(FirestoreCollections.TRACKING_SESSIONS).document(sessionId),
                mapOf(
                    TrackingSessionFields.STOPPED_AT to stoppedAt,
                    TrackingSessionFields.ACTIVE to false,
                ),
                SetOptions.merge(),
            )
        }

        batch.set(
            firestore.collection(FirestoreCollections.SETTINGS).document(patientId),
            mapOf(
                SettingsFields.PATIENT_ID to patientId,
                SettingsFields.TRACKING_ACTIVE to false,
                SettingsFields.TRACKING_STOPPED_AT to stoppedAt,
                SettingsFields.TRACKING_STOPPED_BY to caregiverId,
                SettingsFields.UPDATED_AT to stoppedAt,
            ),
            SetOptions.merge(),
        )

        batch.commit().await()
    }

    /**
     * Appends one point to a session's route.
     *
     * `add` rather than a deterministic id: two fixes in the same millisecond
     * are possible on a phone that batches location updates, and a collision
     * would silently drop one of them.
     */
    suspend fun appendPoint(
        sessionId: String,
        data: Map<String, Any?>,
    ): Outcome<Unit> = firebaseCall {
        firestore.collection(FirestoreCollections.TRACKING_SESSIONS)
            .document(sessionId)
            .collection(FirestoreCollections.TRACK_LOCATIONS)
            .add(data)
            .await()
        Unit
    }

    /**
     * The newest [limit] points for a session.
     *
     * Ordered newest-first in the query because that is the half worth keeping
     * when a long session exceeds the limit; the repository reverses it so the
     * polyline draws in the order the patient actually walked.
     */
    fun observeRoute(sessionId: String, limit: Int): Flow<List<DocumentSnapshot>> =
        firestore.collection(FirestoreCollections.TRACKING_SESSIONS)
            .document(sessionId)
            .collection(FirestoreCollections.TRACK_LOCATIONS)
            .orderBy(TrackPointFields.TIMESTAMP, Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .snapshots()
            .map { it.documents }
            .orOnError(emptyList())
}
