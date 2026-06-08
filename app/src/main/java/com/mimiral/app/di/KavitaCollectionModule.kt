package com.mimiral.app.di

import com.mimiral.app.data.remote.kavita.KavitaCollectionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Kavita collection management dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaCollectionModule {

    @Provides
    @Singleton
    fun provideKavitaCollectionRepository(
        kavitaApi: com.mimiral.app.data.remote.kavita.KavitaApi
    ): KavitaCollectionRepository {
        return KavitaCollectionRepository(kavitaApi)
    }
}
