package com.mimiral.app.di

import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.remote.KavitaSyncApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KavitaApiClient

/**
 * Dagger Hilt module providing the Kavita Retrofit API client.
 *
 * Uses a dynamic base URL that resolves from the active Kavita server
 * in the database via [KavitaServerUrlProvider]. This avoids hardcoding
 * a placeholder URL that could cause network calls to a non-existent host.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaApiModule {

    /**
     * Placeholder base URL used when no Kavita server is configured.
     * All API calls will fail fast with a clear error rather than
     * attempting to connect to localhost.
     */
    internal const val PLACEHOLDER_URL = "https://kavita.placeholder.invalid/"

    @Provides
    @Singleton
    @KavitaApiClient
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @KavitaApiClient
    fun provideKavitaSyncApi(
        @KavitaApiClient okHttpClient: OkHttpClient,
        serverUrlProvider: KavitaServerUrlProvider
    ): KavitaSyncApi {
        val baseUrl = serverUrlProvider.getActiveServerUrl() ?: PLACEHOLDER_URL
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(KavitaSyncApi::class.java)
    }

    @Provides
    @Singleton
    fun provideServerUrlProvider(serverDao: ServerDao): KavitaServerUrlProvider {
        return KavitaServerUrlProvider(serverDao)
    }
}

/**
 * Provides the active Kavita server URL from the database.
 *
 * This is resolved at DI creation time for the Retrofit instance.
 * For per-request resolution (when the server URL may change),
 * repositories should call [ServerDao.getActiveServerByType] directly.
 */
class KavitaServerUrlProvider(
    private val serverDao: ServerDao
) {
    /**
     * Get the active Kavita server URL, or null if none configured.
     * Runs on IO dispatcher to avoid blocking the main thread.
     */
    fun getActiveServerUrl(): String? {
        return try {
            runBlocking(Dispatchers.IO) {
                val server = serverDao.getActiveServerByType("KAVITA")
                server?.url
            }
        } catch (_: Exception) {
            null
        }
    }
}
