package com.mimiral.app.di

import com.mimiral.app.data.remote.opds.OpdsParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for OPDS parser dependency.
 */
@Module
@InstallIn(SingletonComponent::class)
object OpdsModule {

    @Provides
    @Singleton
    fun provideOpdsParser(): OpdsParser {
        return OpdsParser()
    }
}
