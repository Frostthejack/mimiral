package com.mimiral.app.di

import com.mimiral.app.data.opds.OpdsClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OpdsModule {

    @Provides
    @Singleton
    fun provideOpdsClient(): OpdsClient = OpdsClient()
}
