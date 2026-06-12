package com.mimiral.app.di

import com.mimiral.app.data.remote.kavita.KavitaAnnotationClient
import com.mimiral.app.data.remote.kavita.KavitaAnnotationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Kavita annotation dependencies.
 *
 * Provides:
 * - [KavitaAnnotationClient] — HTTP client for annotation API calls
 * - [KavitaAnnotationRepository] — Repository orchestrating annotation CRUD
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaAnnotationModule {

    @Provides
    @Singleton
    fun provideKavitaAnnotationClient(): KavitaAnnotationClient {
        return KavitaAnnotationClient()
    }

    @Provides
    @Singleton
    fun provideKavitaAnnotationRepository(
        client: KavitaAnnotationClient,
        serverDao: com.mimiral.app.data.local.dao.ServerDao,
        credentialStore: com.mimiral.app.data.remote.kavita.KavitaCredentialStore
    ): KavitaAnnotationRepository {
        return KavitaAnnotationRepository(client, serverDao, credentialStore)
    }
}
