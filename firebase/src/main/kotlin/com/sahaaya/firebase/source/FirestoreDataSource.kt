package com.sahaaya.firebase.source

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.sahaaya.core.result.Outcome
import com.sahaaya.firebase.FirestoreCollections
import com.sahaaya.firebase.PairingCodeFields
import com.sahaaya.firebase.PairingFields
import com.sahaaya.firebase.UserFields
import com.sahaaya.firebase.firebaseCall
import com.sahaaya.firebase.orOnError
import com.sahaaya.firebase.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every Firestore read and write in the app.
 *
 * Deals only in `Map<String, Any?>` and [DocumentSnapshot]. Turning those into
 * domain models is the job of the mappers in `:data`, which keeps this class
 * about *access* and that one about *meaning*.
 */
@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    // --- Generic document access -------------------------------------------

    fun observeDocument(collection: String, id: String): Flow<DocumentSnapshot?> =
        firestore.collection(collection).document(id)
            .snapshots()
            .map { snapshot -> snapshot.takeIf { it.exists() } }
            .orOnError(null)

    suspend fun getDocument(collection: String, id: String): Outcome<DocumentSnapshot?> =
        firebaseCall {
            firestore.collection(collection).document(id).get().await()
                .takeIf { it.exists() }
        }

    /** Creates or merges a document. Merge is the default so a partial profile
     *  save never wipes fields the current screen does not edit. */
    suspend fun setDocument(
        collection: String,
        id: String,
        data: Map<String, Any?>,
        merge: Boolean = true,
    ): Outcome<Unit> = firebaseCall {
        val reference = firestore.collection(collection).document(id)
        if (merge) {
            reference.set(data, SetOptions.merge()).await()
        } else {
            reference.set(data).await()
        }
    }

    suspend fun updateDocument(
        collection: String,
        id: String,
        data: Map<String, Any?>,
    ): Outcome<Unit> = firebaseCall {
        firestore.collection(collection).document(id).update(data).await()
    }

    suspend fun deleteDocument(collection: String, id: String): Outcome<Unit> =
        firebaseCall {
            firestore.collection(collection).document(id).delete().await()
        }

    // --- Sub-collections ---------------------------------------------------

    fun observeSubCollection(
        parentCollection: String,
        parentId: String,
        subCollection: String,
        orderBy: String? = null,
    ): Flow<List<DocumentSnapshot>> {
        val base = firestore.collection(parentCollection)
            .document(parentId)
            .collection(subCollection)
        val query: Query = if (orderBy != null) base.orderBy(orderBy) else base
        return query.snapshots()
            .map { snapshot -> snapshot.documents }
            .orOnError(emptyList())
    }

    suspend fun setSubDocument(
        parentCollection: String,
        parentId: String,
        subCollection: String,
        documentId: String,
        data: Map<String, Any?>,
    ): Outcome<Unit> = firebaseCall {
        firestore.collection(parentCollection)
            .document(parentId)
            .collection(subCollection)
            .document(documentId)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun deleteSubDocument(
        parentCollection: String,
        parentId: String,
        subCollection: String,
        documentId: String,
    ): Outcome<Unit> = firebaseCall {
        firestore.collection(parentCollection)
            .document(parentId)
            .collection(subCollection)
            .document(documentId)
            .delete()
            .await()
    }

    /** Allocates an id without writing, so a new emergency contact has a stable
     *  identity before the user presses save. */
    fun newSubDocumentId(
        parentCollection: String,
        parentId: String,
        subCollection: String,
    ): String = firestore.collection(parentCollection)
        .document(parentId)
        .collection(subCollection)
        .document()
        .id

    // --- Pairings ----------------------------------------------------------

    fun observePairingsWhere(field: String, value: String): Flow<List<DocumentSnapshot>> =
        firestore.collection(FirestoreCollections.PAIRINGS)
            .whereEqualTo(field, value)
            .snapshots()
            .map { snapshot -> snapshot.documents }
            .orOnError(emptyList())

    /** The patient's live, unredeemed code - at most one by construction. */
    fun observeActivePairingCode(patientId: String): Flow<DocumentSnapshot?> =
        firestore.collection(FirestoreCollections.PAIRING_CODES)
            .whereEqualTo(PairingCodeFields.PATIENT_ID, patientId)
            .whereEqualTo(PairingCodeFields.REDEEMED_BY, null)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.maxByOrNull {
                    it.getLong(PairingCodeFields.EXPIRES_AT) ?: 0L
                }
            }
            .orOnError(null)

    suspend fun deleteUnredeemedCodesFor(patientId: String): Outcome<Unit> = firebaseCall {
        val existing = firestore.collection(FirestoreCollections.PAIRING_CODES)
            .whereEqualTo(PairingCodeFields.PATIENT_ID, patientId)
            .whereEqualTo(PairingCodeFields.REDEEMED_BY, null)
            .get()
            .await()
        val batch = firestore.batch()
        existing.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    /**
     * Redeems a code and creates the pairing in one transaction.
     *
     * A transaction rather than two writes because both devices are online at
     * this moment and a half-created link - a consumed code with no pairing -
     * would leave the caregiver unable to retry and the patient unable to see
     * who has access.
     */
    suspend fun redeemCodeAndCreatePairing(
        code: String,
        caregiverId: String,
        caregiverName: String,
        buildPairing: (
            patientId: String,
            patientName: String,
            patientPhone: String,
        ) -> Pair<String, Map<String, Any?>>,
        onInvalidCode: () -> Throwable,
        onAlreadyPaired: () -> Throwable,
    ): Outcome<Map<String, Any?>> = firebaseCall {
        firestore.runTransaction { transaction ->
            val codeRef = firestore
                .collection(FirestoreCollections.PAIRING_CODES)
                .document(code)
            val codeSnapshot = transaction.get(codeRef)

            if (!codeSnapshot.exists()) throw onInvalidCode()
            if (codeSnapshot.getString(PairingCodeFields.REDEEMED_BY) != null) {
                throw onInvalidCode()
            }
            val expiresAt = codeSnapshot.getLong(PairingCodeFields.EXPIRES_AT) ?: 0L
            if (System.currentTimeMillis() >= expiresAt) throw onInvalidCode()

            val patientId = codeSnapshot.getString(PairingCodeFields.PATIENT_ID)
                ?: throw onInvalidCode()
            val patientName = codeSnapshot.getString(PairingCodeFields.PATIENT_NAME).orEmpty()
            val patientPhone = codeSnapshot.getString(PairingCodeFields.PATIENT_PHONE).orEmpty()

            val (pairingId, pairingData) = buildPairing(patientId, patientName, patientPhone)
            val pairingRef = firestore
                .collection(FirestoreCollections.PAIRINGS)
                .document(pairingId)
            val existing = transaction.get(pairingRef)
            if (existing.exists() &&
                existing.getString(PairingFields.STATUS) == STATUS_ACTIVE
            ) {
                throw onAlreadyPaired()
            }

            transaction.set(pairingRef, pairingData)
            transaction.update(codeRef, PairingCodeFields.REDEEMED_BY, caregiverId)
            pairingData
        }.await()
    }

    // --- Messaging tokens --------------------------------------------------

    suspend fun addFcmToken(uid: String, token: String): Outcome<Unit> = firebaseCall {
        firestore.collection(FirestoreCollections.USERS)
            .document(uid)
            .update(UserFields.FCM_TOKENS, FieldValue.arrayUnion(token))
            .await()
    }

    suspend fun removeFcmToken(uid: String, token: String): Outcome<Unit> = firebaseCall {
        firestore.collection(FirestoreCollections.USERS)
            .document(uid)
            .update(UserFields.FCM_TOKENS, FieldValue.arrayRemove(token))
            .await()
    }

    private companion object {
        const val STATUS_ACTIVE = "active"
    }
}
