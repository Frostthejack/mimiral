package com.mimiral.app.di

import android.content.Context
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.entity.ServerEntity
import com.mimiral.app.data.remote.kavita.KavitaClient
import com.mimiral.app.data.remote.kavita.KavitaRepository
import com.mimiral.app.data.repository.BookRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Kavita client dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaModule {

    /**
     * Provides a KavitaClient configured from the active server settings.
     * The client will be created with the server's base URL and auth credentials.
     */
    @Provides
    @Singleton
    fun provideKavitaClient(serverDao: ServerDao): KavitaClient {
        // Create a default client - the actual server URL will be resolved
        // per-request from the active server configuration
        return KavitaClient(
            baseUrl = "http://localhost:5000",
            apiKey = null,
            jwtToken = null
        )
    }

    /**
     * Provides a KavitaClient for a specific server configuration.
     * Use this when connecting to a specific known server.
     */
    fun provideKavitaClientForServer(server: ServerEntity): KavitaClient {
        return KavitaClient(
            baseUrl = server.url,
            apiKey = server.apiKey,
            jwtToken = server.jwtToken
        )
    }

    @Provides
    @Singleton
    fun provideKavitaRepository(
        @ApplicationContext context: Context,
        kavitaClient: KavitaClient,
        serverDao: ServerDao,
        bookRepository: BookRepository
    ): KavitaRepository {
        return KavitaRepository(
            context = context,
            kavitaClient = kavitaClient,
            serverDao = serverDao,
            bookRepository = bookRepository
        )
    }
}
