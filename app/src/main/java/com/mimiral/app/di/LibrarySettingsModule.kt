package com.mimiral.app.di

import android.content.Context
import com.mimiral.app.data.local.settings.LibrarySettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LibrarySettingsModule {

    @Provides
    @Singleton
    fun provideLibrarySettingsRepository(
        @ApplicationContext context: Context
    ): LibrarySettingsRepository {
        return LibrarySettingsRepository(context)
    }
}
