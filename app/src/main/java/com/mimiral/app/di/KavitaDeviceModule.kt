package com.mimiral.app.di

import com.mimiral.app.data.remote.kavita.KavitaDeviceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Kavita send-to-device dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaDeviceModule {

    @Provides
    @Singleton
    fun provideKavitaDeviceRepository(
        kavitaClient: com.mimiral.app.data.remote.kavita.KavitaClient,
        serverDao: com.mimiral.app.data.local.dao.ServerDao,
        credentialStore: com.mimiral.app.data.remote.kavita.KavitaCredentialStore
    ): KavitaDeviceRepository {
        return KavitaDeviceRepository(kavitaClient, serverDao, credentialStore)
    }
}
