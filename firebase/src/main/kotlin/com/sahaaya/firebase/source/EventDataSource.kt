package com.sahaaya.firebase.source

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.sahaaya.core.result.Outcome
import com.sahaaya.firebase.DoseFields
import com.sahaaya.firebase.EventFields
import com.sahaaya.firebase.FirestoreCollections
import com.sahaaya.firebase.MedicationFields
import com.sahaaya.firebase.firebaseCall
import com.sahaaya.firebase.orOnError
import com.sahaaya.firebase.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore access for events, medications and doses.
 *
 * Split from [FirestoreDataSource] because these are the collections that grow
 * without bound and therefore need query limits and explicit ordering, whereas
 * the profile collections are one document per user.
 */
@Singleton
class EventDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    // --- events ------------------------------------------------------------

    fun observeEventsForPatient(patientId: String, limit: Int): Flow<List<DocumentSnapshot>> =
        firestore.collection(FirestoreCollections.EVENTS)
            .whereEqualTo(EventFields.PATIENT_ID, patientId)
            .orderBy(EventFields.OCCURRED_AT, Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .snapshots()
            .map { it.documents }
            .orOnError(emptyList())

    /**
     * Events across several patients.
     *
     * Firestore's `whereIn` accepts at most 30 values. A caregiver with more
     * than 30 linked patients is outside anything this app is designed for, so
     * the list is truncated rather than paged - and truncating the *oldest*
     * pairings keeps the most recently linked patients visible.
     */
    fun observeEventsForPatients(
        patientIds: List<String>,
        limit: Int,
    ): Flow<List<DocumentSnapshot>> =
        firestore.collection(FirestoreCollections.EVENTS)
            .whereIn(EventFields.PATIENT_ID, patientIds.take(MAX_WHERE_IN))
            .orderBy(EventFields.OCCURRED_AT, Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .snapshots()
            .map { it.documents }
            .orOnError(emptyList())

    fun observeEvent(eventId: String): Flow<DocumentSnapshot?> =
        firestore.collection(FirestoreCollections.EVENTS).document(eventId)
            .snapshots()
            .map { snapshot -> snapshot.takeIf { it.exists() } }
            .orOnError(null)

    /** Allocates an id without writing, so the caller can return a complete event. */
    fun newEventId(): String =
        firestore.collection(FirestoreCollections.EVENTS).document().id

    suspend fun writeEvent(id: String, data: Map<String, Any?>): Outcome<Unit> =
        firebaseCall {
            firestore.collection(FirestoreCollections.EVENTS)
                .document(id)
                .set(data)
                .await()
        }

    suspend fun updateEvent(id: String, data: Map<String, Any?>): Outcome<Unit> =
        firebaseCall {
            firestore.collection(FirestoreCollections.EVENTS)
                .document(id)
                .update(data)
                .await()
        }

    // --- medications -------------------------------------------------------

    fun observeMedications(patientId: String): Flow<List<DocumentSnapshot>> =
        firestore.collection(FirestoreCollections.MEDICATIONS)
            .whereEqualTo(MedicationFields.PATIENT_ID, patientId)
            .whereEqualTo(MedicationFields.IS_ACTIVE, true)
            .snapshots()
            .map { it.documents }
            .orOnError(emptyList())

    suspend fun getMedication(id: String): Outcome<DocumentSnapshot?> = firebaseCall {
        firestore.collection(FirestoreCollections.MEDICATIONS).document(id).get().await()
            .takeIf { it.exists() }
    }

    fun newMedicationId(): String =
        firestore.collection(FirestoreCollections.MEDICATIONS).document().id

    suspend fun writeMedication(id: String, data: Map<String, Any?>): Outcome<Unit> =
        firebaseCall {
            firestore.collection(FirestoreCollections.MEDICATIONS)
                .document(id)
                .set(data, SetOptions.merge())
                .await()
        }

    /**
     * Soft delete.
     *
     * The dose rows already written reference this medication by id and by name;
     * hard-deleting it would leave a caregiver looking at a history of missed
     * doses for a medicine that no longer appears to exist.
     */
    suspend fun deactivateMedication(id: String): Outcome<Unit> = firebaseCall {
        firestore.collection(FirestoreCollections.MEDICATIONS)
            .document(id)
            .update(MedicationFields.IS_ACTIVE, false)
            .await()
    }

    // --- doses -------------------------------------------------------------

    fun observeRecentDoses(patientId: String, limit: Int): Flow<List<DocumentSnapshot>> =
        firestore.collection(FirestoreCollections.DOSES)
            .whereEqualTo(DoseFields.PATIENT_ID, patientId)
            .orderBy(DoseFields.SCHEDULED_AT, Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .snapshots()
            .map { it.documents }
            .orOnError(emptyList())

    fun observeDosesWithStatus(patientId: String, status: String): Flow<List<DocumentSnapshot>> =
        firestore.collection(FirestoreCollections.DOSES)
            .whereEqualTo(DoseFields.PATIENT_ID, patientId)
            .whereEqualTo(DoseFields.STATUS, status)
            .orderBy(DoseFields.SCHEDULED_AT, Query.Direction.DESCENDING)
            .snapshots()
            .map { it.documents }
            .orOnError(emptyList())

    suspend fun writeDose(id: String, data: Map<String, Any?>): Outcome<Unit> = firebaseCall {
        firestore.collection(FirestoreCollections.DOSES)
            .document(id)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun updateDose(id: String, data: Map<String, Any?>): Outcome<Unit> = firebaseCall {
        firestore.collection(FirestoreCollections.DOSES)
            .document(id)
            .update(data)
            .await()
    }

    /** Pending doses scheduled before [beforeEpochMillis] - i.e. past their grace period. */
    suspend fun findOverdueDoses(
        patientId: String,
        pendingStatus: String,
        beforeEpochMillis: Long,
    ): Outcome<List<DocumentSnapshot>> = firebaseCall {
        firestore.collection(FirestoreCollections.DOSES)
            .whereEqualTo(DoseFields.PATIENT_ID, patientId)
            .whereEqualTo(DoseFields.STATUS, pendingStatus)
            .whereLessThan(DoseFields.SCHEDULED_AT, beforeEpochMillis)
            .get()
            .await()
            .documents
    }

    // --- settings ----------------------------------------------------------

    fun observeSettings(patientId: String): Flow<DocumentSnapshot?> =
        firestore.collection(FirestoreCollections.SETTINGS).document(patientId)
            .snapshots()
            .map { snapshot -> snapshot.takeIf { it.exists() } }
            .orOnError(null)

    suspend fun getSettings(patientId: String): Outcome<DocumentSnapshot?> = firebaseCall {
        firestore.collection(FirestoreCollections.SETTINGS).document(patientId).get().await()
            .takeIf { it.exists() }
    }

    suspend fun writeSettings(patientId: String, data: Map<String, Any?>): Outcome<Unit> =
        firebaseCall {
            firestore.collection(FirestoreCollections.SETTINGS)
                .document(patientId)
                .set(data, SetOptions.merge())
                .await()
        }

    private companion object {
        const val MAX_WHERE_IN = 30
    }
}
