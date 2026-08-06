package com.sahaaya.feature.monitoring.di

import com.sahaaya.domain.repository.MonitoringController
import com.sahaaya.feature.monitoring.service.MonitoringControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * [MonitoringController] is bound here rather than in :data because starting a
 * foreground service and registering a geofence are Android concerns, not
 * Firestore ones. The contract still lives in :domain, so the use cases that
 * turn monitoring on and off know nothing about either.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MonitoringModule {

    @Binds
    @Singleton
    abstract fun bindMonitoringController(
        impl: MonitoringControllerImpl,
    ): MonitoringController
}
