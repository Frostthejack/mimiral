package com.mimiral.app.di

import com.mimiral.app.data.remote.kavita.KavitaApiClient
import com.mimiral.app.data.remote.kavita.KavitaRepository
import com.mimiral.app.data.local.dao.ServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Kavita API client dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaModule {

    @Provides
    @Singleton
    fun provideKavitaRepository(serverDao: ServerDao): KavitaRepository {
        return KavitaRepository(serverDao)
    }

    @Provides
    @Singleton
    fun provideServerDao(
        database: com.mimiral.app.data.local.database.MimiralDatabase
    ): ServerDao {
        return database.serverDao()
    }
}
