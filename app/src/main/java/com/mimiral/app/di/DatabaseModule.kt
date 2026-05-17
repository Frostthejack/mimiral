package com.mimiral.app.di

import android.content.Context
import androidx.room.Room
import com.mimiral.app.data.local.database.MimiralDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MimiralDatabase {
        return Room.databaseBuilder(
            context,
            MimiralDatabase::class.java,
            "mimiral_database"
        ).build()
    }

    @Provides
    fun provideBookDao(database: MimiralDatabase) = database.bookDao()

    @Provides
    fun provideReadingProgressDao(database: MimiralDatabase) = database.readingProgressDao()

    @Provides
    fun provideBookmarkDao(database: MimiralDatabase) = database.bookmarkDao()

    @Provides
    fun provideHighlightDao(database: MimiralDatabase) = database.highlightDao()

    @Provides
    fun provideCollectionDao(database: MimiralDatabase) = database.collectionDao()

    @Provides
    fun provideServerDao(database: MimiralDatabase) = database.serverDao()

    @Provides
    fun provideOpdsCatalogDao(database: MimiralDatabase) = database.opdsCatalogDao()
}
