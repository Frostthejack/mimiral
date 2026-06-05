package com.mimiral.app.di

import com.mimiral.app.data.remote.kavita.KavitaCredentialStore
import com.mimiral.app.data.remote.kavita.KavitaRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Kavita server connection dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaModule {

    @Provides
    @Singleton
    fun provideKavitaCredentialStore(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): KavitaCredentialStore {
        return KavitaCredentialStore(context)
    }

    @Provides
    @Singleton
    fun provideKavitaRepository(
        credentialStore: KavitaCredentialStore,
        serverDao: com.mimiral.app.data.local.dao.ServerDao
    ): KavitaRepository {
        return KavitaRepository(credentialStore, serverDao)
    }
}
