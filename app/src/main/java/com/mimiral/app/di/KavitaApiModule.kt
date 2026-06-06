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
import kotlinx.coroutines.runBlocking
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
 * Resolves the server URL dynamically from the active Kavita server
 * in the database. If no server is configured, uses a sentinel URL
 * that fails immediately instead of blocking for 30s on localhost.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaApiModule {

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
        serverDao: ServerDao
    ): KavitaSyncApi {
        val server = runBlocking {
            serverDao.getActiveServerByType("KAVITA")
        }
        val baseUrl = if (server != null) {
            server.url.trimEnd('/') + "/"
        } else {
            // No active Kavita server — use sentinel URL that fails fast
            NO_OP_BASE_URL
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(KavitaSyncApi::class.java)
    }

    /** Sentinel URL for the no-op client when no Kavita server is configured. */
    private const val NO_OP_BASE_URL = "http://kavita-not-configured.local/"
}
