package com.mimiral.app.di

import com.mimiral.app.data.remote.KavitaSyncApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KavitaApiClient

/**
 * Dagger Hilt module providing the Kavita Retrofit API client.
 * Reads the server URL from the active Kavita server in the database.
 * For now, uses a placeholder base URL that should be configured.
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
        @KavitaApiClient okHttpClient: OkHttpClient
    ): KavitaSyncApi {
        // Default base URL — should match the Kavita server URL
        // In production, this could be built dynamically from ServerEntity
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost:5000/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(KavitaSyncApi::class.java)
    }
}
