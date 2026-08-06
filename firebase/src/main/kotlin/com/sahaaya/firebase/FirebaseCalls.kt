package com.sahaaya.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.sahaaya.core.result.Outcome
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * Runs a Firebase call and converts any throw into an [Outcome.Failure].
 *
 * Every suspend function in this module funnels through here, so no Firebase
 * exception can escape the module and no caller has to remember a try/catch.
 */
suspend inline fun <T> firebaseCall(crossinline block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        // Cancellation is cooperative control flow, never an application error.
        throw cancellation
    } catch (throwable: Throwable) {
        Outcome.Failure(FirebaseErrorMapper.map(throwable))
    }

/**
 * A document as a stream of snapshots.
 *
 * [MetadataChanges.EXCLUDE] keeps the flow from re-emitting purely because a
 * write finished syncing to the server; the UI only cares when the data itself
 * differs. Errors terminate the flow rather than crash: a caregiver losing
 * permission mid-session should see an empty screen, not a stack trace.
 */
fun DocumentReference.snapshots(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
        when {
            error != null -> close(error)
            snapshot != null -> trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}.flowOn(Dispatchers.IO)

/** A query as a stream of snapshots. See [DocumentReference.snapshots]. */
fun Query.snapshots(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
        when {
            error != null -> close(error)
            snapshot != null -> trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}.flowOn(Dispatchers.IO)

/**
 * Swallows a stream failure and emits [fallback] instead.
 *
 * Used for the observe* repository functions, whose signatures return plain
 * values rather than [Outcome]: a listener that dies because the device went
 * offline should leave the last rendered state in place, not tear down the UI.
 */
fun <T> Flow<T>.orOnError(fallback: T): Flow<T> = catch { emit(fallback) }
