package com.sahaaya.domain.repository

import com.sahaaya.core.result.Outcome

/**
 * Push-notification registration.
 *
 * Phase 2 only registers the device token against the signed-in account. Phase 3
 * is what actually sends alerts through it, and it will not need to change this
 * contract: by then every account already has a live token on file, which is the
 * part that has to be in place *before* the first fall is detected.
 */
interface MessagingRepository {

    /** Stores the current FCM token on `users/{uid}`, replacing a stale one. */
    suspend fun registerDeviceToken(uid: String): Outcome<Unit>

    /** Removes this device's token. Called on sign-out so a shared phone does
     *  not keep receiving another person's alerts. */
    suspend fun unregisterDeviceToken(uid: String): Outcome<Unit>
}
