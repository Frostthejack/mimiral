package com.mimiral.app.di

import com.mimiral.app.data.remote.opds.OpdsParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for OPDS client dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object OpdsModule {

    @Provides
    @Singleton
    fun provideOpdsClient(): com.mimiral.app.data.opds.OpdsClient {
        return com.mimiral.app.data.opds.OpdsClient()
    }

    @Provides
    @Singleton
    fun provideRemoteOpdsClient(): com.mimiral.app.data.remote.opds.OpdsClient {
        return com.mimiral.app.data.remote.opds.OpdsClient()
    }

    @Provides
    @Singleton
    fun provideOpdsParser(): OpdsParser {
        return OpdsParser()
    }
}
