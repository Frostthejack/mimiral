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
        )
            .addMigrations(
                MimiralDatabase.MIGRATION_1_2,
                MimiralDatabase.MIGRATION_2_3,
                MimiralDatabase.MIGRATION_3_4,
                MimiralDatabase.MIGRATION_4_5,
                MimiralDatabase.MIGRATION_5_6,
                MimiralDatabase.MIGRATION_6_7,
                MimiralDatabase.MIGRATION_7_8,
                MimiralDatabase.MIGRATION_8_9,
                MimiralDatabase.MIGRATION_9_10,
                MimiralDatabase.MIGRATION_10_11,
                MimiralDatabase.MIGRATION_11_12
            )
            .fallbackToDestructiveMigration()
            .build()
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

    @Provides
    fun providePdfSettingsDao(database: MimiralDatabase) = database.pdfSettingsDao()

    @Provides
    fun provideChapterDao(database: MimiralDatabase) = database.chapterDao()

    @Provides
    fun provideReadingSessionDao(database: MimiralDatabase) = database.readingSessionDao()

    @Provides
    fun provideReadingListDao(database: MimiralDatabase) = database.readingListDao()

    @Provides
    fun provideTagDao(database: MimiralDatabase) = database.tagDao()

    @Provides
    fun provideReadingTimeDao(database: MimiralDatabase) = database.readingTimeDao()

    @Provides
    fun provideReadingGoalDao(database: MimiralDatabase) = database.readingGoalDao()

    @Provides
    fun providePendingOperationDao(database: MimiralDatabase) = database.pendingOperationDao()
}
