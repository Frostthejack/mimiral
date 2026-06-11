package com.mimiral.app.di

import com.mimiral.app.data.remote.opds.OpdsParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Hilt DI module for OPDS client dependencies.
 *
 * Provides a single shared OkHttpClient for all OPDS operations with
 * consistent 30s timeouts (connect/read/write).
 */
@Module
@InstallIn(SingletonComponent::class)
object OpdsModule {

    private const val OPDS_TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    fun provideOpdsOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(OPDS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(OPDS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(OPDS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRemoteOpdsClient(
        okHttpClient: OkHttpClient
    ): com.mimiral.app.data.remote.opds.OpdsClient {
        return com.mimiral.app.data.remote.opds.OpdsClient(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideOpdsHttpClient(
        okHttpClient: OkHttpClient
    ): com.mimiral.app.data.remote.opds.OpdsHttpClient {
        return com.mimiral.app.data.remote.opds.OpdsHttpClient(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideOpdsParser(): OpdsParser {
        return OpdsParser()
    }
}
