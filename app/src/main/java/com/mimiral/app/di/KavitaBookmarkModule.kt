package com.mimiral.app.di

import com.mimiral.app.data.remote.kavita.KavitaApi
import com.mimiral.app.data.remote.kavita.KavitaBookmarkRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Kavita bookmark sync dependencies.
 * Uses the Retrofit-based KavitaApi for all bookmark network operations.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaBookmarkModule {

    @Provides
    @Singleton
    fun provideKavitaBookmarkRepository(
        kavitaApi: KavitaApi,
        bookmarkDao: com.mimiral.app.data.local.dao.BookmarkDao
    ): KavitaBookmarkRepository {
        return KavitaBookmarkRepository(kavitaApi, bookmarkDao)
    }
}
