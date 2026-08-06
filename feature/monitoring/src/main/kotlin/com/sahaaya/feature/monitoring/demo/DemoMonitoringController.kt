package com.sahaaya.feature.monitoring.demo

import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.MonitoringSettings
import com.sahaaya.domain.repository.MonitoringController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accepts monitoring settings without touching the platform, while
 * [com.sahaaya.core.demo.DemoConfig.ENABLED].
 *
 * The real controller starts a foreground service, registers a geofence and
 * subscribes to activity transitions - all of which need permissions a
 * demonstration device may not have granted, and any one of which failing turns
 * the settings screen into an error message. Recording the settings as applied
 * keeps that screen showing what it actually saved.
 *
 * The detection pipeline itself is not being faked away: the Demo Mode screen
 * still drives the real `ReportFall`, `ReportGeofenceExit`, `ReportInactivity`
 * and `TriggerSos` use cases, so every alert on the timeline came through the
 * production code path.
 */
@Singleton
class DemoMonitoringController @Inject constructor() : MonitoringController {

    private var running = false

    override suspend fun applySettings(settings: MonitoringSettings): Outcome<Unit> {
        running = settings.isMonitoringAnything
        return Outcome.Success(Unit)
    }

    override suspend fun stopAll(): Outcome<Unit> {
        running = false
        return Outcome.Success(Unit)
    }

    override fun isRunning(): Boolean = running
}
