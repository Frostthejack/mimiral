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
import kotlinx.coroutines.runBlocking

/**
 * Hilt DI module for Kavita client dependencies.
 *
 * Resolves the base URL dynamically from the active Kavita server
 * in the database. If no server is configured, provides a no-op
 * client that gracefully returns empty results instead of attempting
 * network calls to a hardcoded address.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaModule {

    /**
     * Provides a KavitaClient configured from the active server settings.
     *
     * Resolves the active Kavita server from the database. If no server
     * is configured, returns a no-op placeholder client that will not
     * attempt any network calls (preventing ConnectException crashes).
     */
    @Provides
    @Singleton
    fun provideKavitaClient(serverDao: ServerDao): KavitaClient {
        val server = runBlocking {
            serverDao.getActiveServerByType("KAVITA")
        }
        return if (server != null) {
            KavitaClient(
                baseUrl = server.url,
                apiKey = server.apiKey,
                jwtToken = server.jwtToken
            )
        } else {
            // No active Kavita server — return a no-op placeholder.
            // Uses a sentinel URL so any accidental network call fails
            // immediately with a clear error rather than blocking 30s.
            KavitaClient(
                baseUrl = NO_OP_BASE_URL,
                apiKey = null,
                jwtToken = null
            )
        }
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

    /** Sentinel URL for the no-op client when no Kavita server is configured. */
    private const val NO_OP_BASE_URL = "http://kavita-not-configured.local"
}
