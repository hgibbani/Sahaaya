package com.sahaaya.data.di

import com.sahaaya.core.coroutines.DefaultDispatcherProvider
import com.sahaaya.core.coroutines.DispatcherProvider
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
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds each domain contract to its Firestore-backed implementation.
 *
 * This module is the only place the two halves meet. Because the bindings live
 * in `:data` rather than `:app`, swapping a repository - for an in-memory fake
 * in tests, or a different backend later - is a one-line change here and nothing
 * above it recompiles differently.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindPairingRepository(impl: PairingRepositoryImpl): PairingRepository

    @Binds
    @Singleton
    abstract fun bindMessagingRepository(impl: MessagingRepositoryImpl): MessagingRepository

    @Binds
    @Singleton
    abstract fun bindEventRepository(impl: EventRepositoryImpl): EventRepository

    @Binds
    @Singleton
    abstract fun bindMedicationRepository(impl: MedicationRepositoryImpl): MedicationRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
