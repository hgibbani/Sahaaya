package com.sahaaya.data.demo

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.repository.MessagingRepository
import com.sahaaya.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitoring settings, in memory.
 *
 * A missing record yields the defaults rather than null, for the same reason the
 * Firestore implementation does: "no settings saved yet" is not a state the
 * detectors can act on.
 */
@Singleton
class DemoSettingsRepository @Inject constructor(
    private val store: DemoDataStore,
) : SettingsRepository {

    override fun observeSettings(patientId: String): Flow<MonitoringSettings> =
        store.settings.map { it[patientId] ?: MonitoringSettings(patientId = patientId) }

    override suspend fun getSettings(patientId: String): Outcome<MonitoringSettings> =
        Outcome.Success(
            store.settings.value[patientId] ?: MonitoringSettings(patientId = patientId),
        )

    override suspend fun saveSettings(settings: MonitoringSettings): Outcome<Unit> {
        store.settings.put(settings.patientId, settings)
        return Outcome.Success(Unit)
    }
}

/**
 * Push registration with nothing to register against.
 *
 * Reports success so that sign-in and sign-up complete. Nothing downstream of
 * these two calls reads a token in demo mode, and failing them would surface a
 * misleading error on a screen that has otherwise worked.
 */
@Singleton
class DemoMessagingRepository @Inject constructor() : MessagingRepository {

    override suspend fun registerDeviceToken(uid: String): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun unregisterDeviceToken(uid: String): Outcome<Unit> = Outcome.Success(Unit)
}
