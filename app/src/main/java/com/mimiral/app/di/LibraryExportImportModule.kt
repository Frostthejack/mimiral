package com.mimiral.app.di

import android.content.Context
import com.mimiral.app.data.exportimport.LibraryExporter
import com.mimiral.app.data.exportimport.LibraryImporter
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.BookmarkDao
import com.mimiral.app.data.local.dao.CollectionDao
import com.mimiral.app.data.local.dao.HighlightDao
import com.mimiral.app.data.local.dao.OpdsCatalogDao
import com.mimiral.app.data.local.dao.PdfSettingsDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.dao.ReadingSessionDao
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LibraryExportImportModule {

    @Provides
    @Singleton
    fun provideLibraryExporter(
        @ApplicationContext context: Context,
        bookDao: BookDao,
        readingProgressDao: ReadingProgressDao,
        bookmarkDao: BookmarkDao,
        highlightDao: HighlightDao,
        collectionDao: CollectionDao,
        tagDao: TagDao,
        readingSessionDao: ReadingSessionDao,
        pdfSettingsDao: PdfSettingsDao,
        serverDao: ServerDao,
        opdsCatalogDao: OpdsCatalogDao
    ): LibraryExporter {
        return LibraryExporter(
            context = context,
            bookDao = bookDao,
            readingProgressDao = readingProgressDao,
            bookmarkDao = bookmarkDao,
            highlightDao = highlightDao,
            collectionDao = collectionDao,
            tagDao = tagDao,
            readingSessionDao = readingSessionDao,
            pdfSettingsDao = pdfSettingsDao,
            serverDao = serverDao,
            opdsCatalogDao = opdsCatalogDao
        )
    }

    @Provides
    @Singleton
    fun provideLibraryImporter(
        bookDao: BookDao,
        readingProgressDao: ReadingProgressDao,
        bookmarkDao: BookmarkDao,
        highlightDao: HighlightDao,
        collectionDao: CollectionDao,
        tagDao: TagDao,
        readingSessionDao: ReadingSessionDao,
        pdfSettingsDao: PdfSettingsDao,
        serverDao: ServerDao,
        opdsCatalogDao: OpdsCatalogDao
    ): LibraryImporter {
        return LibraryImporter(
            bookDao = bookDao,
            readingProgressDao = readingProgressDao,
            bookmarkDao = bookmarkDao,
            highlightDao = highlightDao,
            collectionDao = collectionDao,
            tagDao = tagDao,
            readingSessionDao = readingSessionDao,
            pdfSettingsDao = pdfSettingsDao,
            serverDao = serverDao,
            opdsCatalogDao = opdsCatalogDao
        )
    }
}
