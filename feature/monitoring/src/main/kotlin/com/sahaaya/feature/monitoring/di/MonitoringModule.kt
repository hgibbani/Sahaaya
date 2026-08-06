package com.sahaaya.feature.monitoring.di

import com.sahaaya.core.demo.DemoConfig
import com.sahaaya.domain.repository.MonitoringController
import com.sahaaya.feature.monitoring.demo.DemoMonitoringController
import com.sahaaya.feature.monitoring.service.MonitoringControllerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * [MonitoringController] is bound here rather than in :data because starting a
 * foreground service and registering a geofence are Android concerns, not
 * Firestore ones. The contract still lives in :domain, so the use cases that
 * turn monitoring on and off know nothing about either.
 *
 * In demo mode the no-platform implementation is used instead, so the settings
 * screen is not blocked by permissions the demonstration device has not
 * granted. See [com.sahaaya.core.demo.DemoConfig].
 */
@Module
@InstallIn(SingletonComponent::class)
object MonitoringModule {

    @Provides
    @Singleton
    fun provideMonitoringController(
        real: Provider<MonitoringControllerImpl>,
        demo: Provider<DemoMonitoringController>,
    ): MonitoringController = if (DemoConfig.ENABLED) demo.get() else real.get()
}
