package com.mimiral.app.di

import com.mimiral.app.data.remote.kavita.KavitaReadingListRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Kavita Reading List dependencies.
 *
 * The repository is created directly since it only depends on
 * [KavitaApi] which is already provided by [KavitaApiModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaReadingListModule {

    @Provides
    @Singleton
    fun provideKavitaReadingListRepository(
        kavitaApi: com.mimiral.app.data.remote.kavita.KavitaApi
    ): KavitaReadingListRepository {
        return KavitaReadingListRepository(kavitaApi)
    }
}
