package com.mimiral.app.di

import android.content.Context
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.remote.kavita.KavitaClient
import com.mimiral.app.data.remote.kavita.KavitaRepository
import com.mimiral.app.data.remote.kavita.KavitaStatsRepository
import com.mimiral.app.data.repository.BookRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Kavita client dependencies.
 *
 * The KavitaClient is created with a placeholder URL since the
 * actual server URL is resolved at runtime from the active server
 * configuration in the database. Repositories that need a properly
 * configured client should resolve the active server via ServerDao
 * and create a client with the resolved URL.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaModule {

    /**
     * Provides a placeholder KavitaClient.
     *
     * The actual server URL is resolved per-request from the active
     * server configuration. This placeholder avoids blocking the
     * DI graph on a database read at creation time.
     *
     * Use [KavitaModule.provideKavitaClientForServer] to create a
     * properly configured client for a specific server.
     */
    @Provides
    @Singleton
    fun provideKavitaClient(): KavitaClient {
        // Placeholder client — the actual server URL will be resolved
        // per-request from the active server configuration.
        // See KavitaRepository.testConnection() for an example of
        // creating a properly configured client.
        return KavitaClient(baseUrl = "")
    }

    /**
     * Provides a KavitaClient for a specific server configuration.
     * Use this when connecting to a specific known server.
     */
    fun provideKavitaClientForServer(
        url: String,
        apiKey: String? = null,
        jwtToken: String? = null
    ): KavitaClient {
        return KavitaClient(
            baseUrl = url,
            apiKey = apiKey,
            jwtToken = jwtToken
        )
    }

    @Provides
    @Singleton
    fun provideKavitaRepository(
        @ApplicationContext context: Context,
        kavitaApi: com.mimiral.app.data.remote.kavita.KavitaApi,
        serverDao: ServerDao,
        bookRepository: BookRepository
    ): KavitaRepository {
        return KavitaRepository(
            context = context,
            kavitaApi = kavitaApi,
            serverDao = serverDao,
            bookRepository = bookRepository
        )
    }

    @Provides
    @Singleton
    fun provideKavitaStatsRepository(
        kavitaApi: com.mimiral.app.data.remote.kavita.KavitaApi
    ): KavitaStatsRepository {
        return KavitaStatsRepository(kavitaApi = kavitaApi)
    }
}
