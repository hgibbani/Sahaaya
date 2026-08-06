package com.sahaaya.sensor.di

import android.content.Context
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.sahaaya.core.demo.DemoConfig
import com.sahaaya.domain.repository.LocationRepository
import com.sahaaya.sensor.demo.DemoLocationRepository
import com.sahaaya.sensor.location.LocationRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SensorModule {

    @Provides
    @Singleton
    fun provideFusedLocationClient(
        @ApplicationContext context: Context,
    ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun provideGeofencingClient(
        @ApplicationContext context: Context,
    ): GeofencingClient = LocationServices.getGeofencingClient(context)

    @Provides
    @Singleton
    fun provideActivityRecognitionClient(
        @ApplicationContext context: Context,
    ): ActivityRecognitionClient = ActivityRecognition.getClient(context)
}

/**
 * [LocationRepository] is bound here rather than in :data because its
 * implementation is a platform concern, not a Firestore one. The contract still
 * lives in :domain, so nothing above knows which module satisfies it.
 *
 * In demo mode the fixed-position implementation is used instead, so an alert
 * raised on an emulator still carries coordinates. See
 * [com.sahaaya.core.demo.DemoConfig].
 */
@Module
@InstallIn(SingletonComponent::class)
object SensorBindingsModule {

    @Provides
    @Singleton
    fun provideLocationRepository(
        real: Provider<LocationRepositoryImpl>,
        demo: Provider<DemoLocationRepository>,
    ): LocationRepository = if (DemoConfig.ENABLED) demo.get() else real.get()
}
