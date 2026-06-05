package com.mimiral.app.di

import com.mimiral.app.data.remote.kavita.KavitaBookmarkClient
import com.mimiral.app.data.remote.kavita.KavitaBookmarkRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Kavita bookmark sync dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object KavitaBookmarkModule {

    @Provides
    @Singleton
    fun provideKavitaBookmarkClient(): KavitaBookmarkClient {
        return KavitaBookmarkClient()
    }

    @Provides
    @Singleton
    fun provideKavitaBookmarkRepository(
        client: KavitaBookmarkClient,
        bookmarkDao: com.mimiral.app.data.local.dao.BookmarkDao,
        serverDao: com.mimiral.app.data.local.dao.ServerDao
    ): KavitaBookmarkRepository {
        return KavitaBookmarkRepository(client, bookmarkDao, serverDao)
    }
}
