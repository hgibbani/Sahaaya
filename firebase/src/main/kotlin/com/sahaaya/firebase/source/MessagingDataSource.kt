package com.sahaaya.firebase.source

import com.google.firebase.messaging.FirebaseMessaging
import com.sahaaya.core.result.Outcome
import com.sahaaya.firebase.firebaseCall
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingDataSource @Inject constructor(
    private val messaging: FirebaseMessaging,
) {
    /** The current registration token for this device and install. */
    suspend fun currentToken(): Outcome<String> = firebaseCall {
        messaging.token.await()
    }
}
