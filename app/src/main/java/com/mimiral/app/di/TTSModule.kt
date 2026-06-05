package com.mimiral.app.di

import android.content.Context
import com.mimiral.app.data.local.settings.TTSSettingsRepository
import com.mimiral.app.tts.TTSManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TTSModule {

    @Provides
    @Singleton
    fun provideTTSManager(@ApplicationContext context: Context): TTSManager {
        return TTSManager(context)
    }

    @Provides
    @Singleton
    fun provideTTSSettingsRepository(@ApplicationContext context: Context): TTSSettingsRepository {
        return TTSSettingsRepository(context)
    }
}
