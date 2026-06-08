package com.mimiral.app.di

import com.mimiral.app.data.remote.kavita.KavitaMarkReadClient
import com.mimiral.app.data.remote.kavita.KavitaMarkReadRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Kavita mark-read / mark-unread dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaMarkReadModule {

    @Provides
    @Singleton
    fun provideKavitaMarkReadClient(): KavitaMarkReadClient {
        return KavitaMarkReadClient()
    }

    @Provides
    @Singleton
    fun provideKavitaMarkReadRepository(
        client: KavitaMarkReadClient,
        serverDao: com.mimiral.app.data.local.dao.ServerDao
    ): KavitaMarkReadRepository {
        return KavitaMarkReadRepository(client, serverDao)
    }
}
