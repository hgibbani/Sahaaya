package com.sahaaya.data.repository

import com.sahaaya.core.result.Outcome
import com.sahaaya.data.mapper.toMap
import com.sahaaya.data.mapper.toMonitoringSettings
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.repository.SettingsRepository
import com.sahaaya.firebase.source.EventDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val eventDataSource: EventDataSource,
) : SettingsRepository {

    /**
     * A missing document yields the defaults rather than null.
     *
     * Monitoring must have a definite configuration at all times. "No settings
     * saved yet" is not a state the detectors can act on, and defaulting to
     * *off* would leave a patient unmonitored simply because nobody had opened
     * the settings screen.
     */
    override fun observeSettings(patientId: String): Flow<MonitoringSettings> =
        eventDataSource.observeSettings(patientId)
            .map { snapshot ->
                snapshot?.toMonitoringSettings(patientId)
                    ?: MonitoringSettings(patientId = patientId)
            }

    override suspend fun getSettings(patientId: String): Outcome<MonitoringSettings> =
        when (val result = eventDataSource.getSettings(patientId)) {
            is Outcome.Failure -> result
            is Outcome.Success -> Outcome.Success(
                result.data?.toMonitoringSettings(patientId)
                    ?: MonitoringSettings(patientId = patientId),
            )
        }

    override suspend fun saveSettings(settings: MonitoringSettings): Outcome<Unit> =
        eventDataSource.writeSettings(
            patientId = settings.patientId,
            data = settings.toMap(),
        )
}
