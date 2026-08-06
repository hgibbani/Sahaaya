package com.sahaaya.data.repository

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.repository.MessagingRepository
import com.sahaaya.firebase.source.FirestoreDataSource
import com.sahaaya.firebase.source.MessagingDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingRepositoryImpl @Inject constructor(
    private val messagingDataSource: MessagingDataSource,
    private val firestoreDataSource: FirestoreDataSource,
) : MessagingRepository {

    override suspend fun registerDeviceToken(uid: String): Outcome<Unit> =
        when (val token = messagingDataSource.currentToken()) {
            is Outcome.Failure -> token
            is Outcome.Success -> firestoreDataSource.addFcmToken(uid, token.data)
        }

    override suspend fun unregisterDeviceToken(uid: String): Outcome<Unit> =
        when (val token = messagingDataSource.currentToken()) {
            is Outcome.Failure -> token
            is Outcome.Success -> firestoreDataSource.removeFcmToken(uid, token.data)
        }
}
