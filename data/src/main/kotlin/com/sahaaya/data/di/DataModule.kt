package com.sahaaya.data.di

import com.sahaaya.core.coroutines.DefaultDispatcherProvider
import com.sahaaya.core.coroutines.DispatcherProvider
import com.sahaaya.core.demo.DemoConfig
import com.sahaaya.data.demo.DemoAuthRepository
import com.sahaaya.data.demo.DemoEventRepository
import com.sahaaya.data.demo.DemoMedicationRepository
import com.sahaaya.data.demo.DemoMessagingRepository
import com.sahaaya.data.demo.DemoPairingRepository
import com.sahaaya.data.demo.DemoProfileRepository
import com.sahaaya.data.demo.DemoSettingsRepository
import com.sahaaya.data.repository.AuthRepositoryImpl
import com.sahaaya.data.repository.EventRepositoryImpl
import com.sahaaya.data.repository.MedicationRepositoryImpl
import com.sahaaya.data.repository.MessagingRepositoryImpl
import com.sahaaya.data.repository.PairingRepositoryImpl
import com.sahaaya.data.repository.ProfileRepositoryImpl
import com.sahaaya.data.repository.SettingsRepositoryImpl
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.EventRepository
import com.sahaaya.domain.repository.MedicationRepository
import com.sahaaya.domain.repository.MessagingRepository
import com.sahaaya.domain.repository.PairingRepository
import com.sahaaya.domain.repository.ProfileRepository
import com.sahaaya.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Binds each domain contract to an implementation.
 *
 * This module is the only place the two halves meet, which is exactly what the
 * offline demo needed: every provider below asks
 * [DemoConfig.ENABLED][com.sahaaya.core.demo.DemoConfig.ENABLED] which side to
 * return, and nothing above these interfaces recompiles differently or knows
 * which one it got.
 *
 * Both sides are injected as [Provider] rather than directly. That is
 * load-bearing, not style: taking `AuthRepositoryImpl` by value would make
 * Dagger construct it - and therefore construct `FirebaseAuth` and
 * `FirebaseFirestore` - even in demo mode, where the Firebase project is not
 * reachable. With a `Provider`, the Firebase graph is only touched if the flag
 * says to use it.
 *
 * The Firestore implementations are untouched and still fully wired. Setting
 * the flag to `false` puts the app back on Firebase with no other edit.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        firebase: Provider<AuthRepositoryImpl>,
        demo: Provider<DemoAuthRepository>,
    ): AuthRepository = if (DemoConfig.ENABLED) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideProfileRepository(
        firebase: Provider<ProfileRepositoryImpl>,
        demo: Provider<DemoProfileRepository>,
    ): ProfileRepository = if (DemoConfig.ENABLED) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun providePairingRepository(
        firebase: Provider<PairingRepositoryImpl>,
        demo: Provider<DemoPairingRepository>,
    ): PairingRepository = if (DemoConfig.ENABLED) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideMessagingRepository(
        firebase: Provider<MessagingRepositoryImpl>,
        demo: Provider<DemoMessagingRepository>,
    ): MessagingRepository = if (DemoConfig.ENABLED) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideEventRepository(
        firebase: Provider<EventRepositoryImpl>,
        demo: Provider<DemoEventRepository>,
    ): EventRepository = if (DemoConfig.ENABLED) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideMedicationRepository(
        firebase: Provider<MedicationRepositoryImpl>,
        demo: Provider<DemoMedicationRepository>,
    ): MedicationRepository = if (DemoConfig.ENABLED) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideSettingsRepository(
        firebase: Provider<SettingsRepositoryImpl>,
        demo: Provider<DemoSettingsRepository>,
    ): SettingsRepository = if (DemoConfig.ENABLED) demo.get() else firebase.get()
}

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
