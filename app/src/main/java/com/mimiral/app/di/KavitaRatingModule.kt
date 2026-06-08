package com.mimiral.app.di

import com.mimiral.app.data.remote.kavita.KavitaRatingClient
import com.mimiral.app.data.remote.kavita.KavitaRatingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Kavita rating and review dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaRatingModule {

    @Provides
    @Singleton
    fun provideKavitaRatingClient(): KavitaRatingClient {
        return KavitaRatingClient()
    }

    @Provides
    @Singleton
    fun provideKavitaRatingRepository(
        client: KavitaRatingClient,
        serverDao: com.mimiral.app.data.local.dao.ServerDao
    ): KavitaRatingRepository {
        return KavitaRatingRepository(client, serverDao)
    }
}
