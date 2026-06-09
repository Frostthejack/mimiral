package com.mimiral.app.di

import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.remote.KavitaSyncApi
import com.mimiral.app.data.remote.kavita.KavitaApi
import com.mimiral.app.data.remote.kavita.KavitaAuthInterceptor
import com.mimiral.app.data.remote.kavita.KavitaAuthService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KavitaApiClient

/**
 * Dagger Hilt module providing the Kavita Retrofit API client and auth services.
 *
 * Uses a dynamic base URL interceptor that resolves the active Kavita server
 * from the database on each request, plus [KavitaAuthInterceptor] for token
 * injection and 401 refresh retry.
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
    fun provideOkHttpClient(
        serverDao: ServerDao,
        authInterceptor: KavitaAuthInterceptor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            // Order matters: base URL resolves first, then auth, then logging
            .addInterceptor(KavitaBaseUrlInterceptor(serverDao))
            .addInterceptor(authInterceptor)
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
        @KavitaApiClient okHttpClient: OkHttpClient
    ): KavitaSyncApi {
        // Use placeholder URL as the Retrofit base — the interceptor
        // will replace it with the real server URL on each request.
        val retrofit = Retrofit.Builder()
            .baseUrl(PLACEHOLDER_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(KavitaSyncApi::class.java)
    }

    @Provides
    @Singleton
    fun provideKavitaApi(
        @KavitaApiClient okHttpClient: OkHttpClient
    ): KavitaApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(PLACEHOLDER_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(KavitaApi::class.java)
    }

    @Provides
    @Singleton
    @KavitaApiClient
    fun provideKavitaApiQualified(
        @KavitaApiClient okHttpClient: OkHttpClient
    ): KavitaApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(PLACEHOLDER_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(KavitaApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthFailedCallback(
        authService: KavitaAuthService
    ): () -> Unit {
        return { authService.clearTokens() }
    }
}

/**
 * OkHttp interceptor that dynamically resolves the Kavita server base URL
 * from the database on each request.
 *
 * This replaces the previous approach of resolving the URL during DI creation
 * (which used runBlocking on the main thread and caused ANR).
 * The interceptor runs on OkHttp's background thread, so the database
 * read does not block the UI.
 *
 * NOTE: Auth header injection (JWT + API key) is handled by
 * [KavitaAuthInterceptor] which runs after this interceptor.
 * This interceptor only resolves the base URL.
 */
class KavitaBaseUrlInterceptor(
    private val serverDao: ServerDao
) : Interceptor {

    companion object {
        private const val TAG = "KavitaBaseUrl"
    }

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()

        // Resolve the active Kavita server URL on OkHttp's background thread
        val server = try {
            runBlocking(Dispatchers.IO) {
                serverDao.getActiveServerByType("KAVITA")
            }
        } catch (_: Exception) {
            null
        }

        if (server == null || server.url.isBlank()) {
            // No server configured — let the request go to the placeholder URL,
            // which will fail fast with a connection error.
            return chain.proceed(originalRequest)
        }

        val serverUrl = server.url

        // Normalize the server URL — ensure it ends with /
        val normalizedUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"

        // Build the new request with the resolved server URL
        val newUrl = originalRequest.url.newBuilder()
            .scheme(normalizedUrl.toHttpUrlOrNull()?.scheme ?: "https")
            .host(normalizedUrl.toHttpUrlOrNull()?.host ?: "")
            .port(normalizedUrl.toHttpUrlOrNull()?.port ?: 443)
            .build()

        val requestBuilder = originalRequest.newBuilder()
            .url(newUrl)

        // NOTE: Auth headers (Authorization + X-Api-Key) are now injected by
        // KavitaAuthInterceptor which runs after this interceptor in the chain.
        // We no longer add X-Api-Key here to avoid duplication.

        return chain.proceed(requestBuilder.build())
    }
}
