package com.mimiral.app.data.exportimport

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
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
import com.mimiral.app.data.local.entity.BookCollectionCrossRef
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.BookTagCrossRef
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.local.entity.CollectionEntity
import com.mimiral.app.data.local.entity.HighlightEntity
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import com.mimiral.app.data.local.entity.PdfSettingsEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.local.entity.ReadingSessionEntity
import com.mimiral.app.data.local.entity.ServerEntity
import com.mimiral.app.data.local.entity.TagEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Exports the entire library database to a JSON file for backup or transfer.
 * Collects all entities from all DAOs and serializes them via Gson.
 */
class LibraryExporter(
    private val context: Context,
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
    private val collectionDao: CollectionDao,
    private val tagDao: TagDao,
    private val readingSessionDao: ReadingSessionDao,
    private val pdfSettingsDao: PdfSettingsDao,
    private val serverDao: ServerDao,
    private val opdsCatalogDao: OpdsCatalogDao
) {
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    /**
     * Export all library data to a JSON file.
     * Returns the File if successful, null on error.
     */
    suspend fun exportLibrary(): File? = withContext(Dispatchers.IO) {
        try {
            // Collect all data from DAOs
            // Use Flow.first() to get the first emission from each Flow-based DAO
            val books = bookDao.getAllBooks().first()
            val progressList = readingProgressDao.getAllProgress().first()
            val collections = collectionDao.getAllCollections().first()
            val bookCollections = collectionDao.getAllBookCollections()
            val tags = tagDao.getAllTags().first()
            val bookTags = tagDao.getAllBookTags()
            val servers = serverDao.getActiveServers().first()
            val opdsCatalogs = opdsCatalogDao.getActiveCatalogs().first()

            // Per-book data requires iterating books
            val bookmarks = mutableListOf<BookmarkEntity>()
            val highlights = mutableListOf<HighlightEntity>()
            val sessions = mutableListOf<ReadingSessionEntity>()
            val pdfSettingsList = mutableListOf<PdfSettingsEntity>()

            for (book in books) {
                bookmarks.addAll(bookmarkDao.getBookmarksForBook(book.id).first())
                highlights.addAll(highlightDao.getHighlightsForBook(book.id).first())
                sessions.addAll(readingSessionDao.getSessionsForBook(book.id).first())
                pdfSettingsDao.getSettingsForBook(book.id)?.let { pdfSettingsList.add(it) }
            }

            val export = LibraryExport(
                version = LibraryExport.CURRENT_VERSION,
                exportedAt = System.currentTimeMillis(),
                books = books.map { it.toExport() },
                readingProgress = progressList.map { it.toExport() },
                bookmarks = bookmarks.map { it.toExport() },
                highlights = highlights.map { it.toExport() },
                collections = collections.map { it.toExport() },
                bookCollections = bookCollections.map { it.toExport() },
                tags = tags.map { it.toExport() },
                bookTags = bookTags.map { it.toExport() },
                readingSessions = sessions.map { it.toExport() },
                pdfSettings = pdfSettingsList.map { it.toExport() },
                servers = servers.map { it.toExport() },
                opdsCatalogs = opdsCatalogs.map { it.toExport() }
            )

            val json = gson.toJson(export)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
            val timestamp = dateFormat.format(Date())
            val filename = "mimiral_library_$timestamp.json"

            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val file = File(exportDir, filename)
            file.writeText(json)
            file
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Share an exported JSON file via Android SEND intent with FileProvider.
     */
    fun shareExportedFile(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Mimiral Library Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share library backup via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    // ── Entity to Export mapping ──

    private fun BookEntity.toExport() = ExportBook(
        id = id,
        filePath = filePath,
        title = title,
        author = author,
        description = description,
        coverPath = coverPath,
        format = format,
        fileSize = fileSize,
        dateAdded = dateAdded,
        dateModified = dateModified,
        isDownloaded = isDownloaded,
        source = source,
        sourceId = sourceId,
        kavitaSeriesId = kavitaSeriesId,
        kavitaLibraryId = kavitaLibraryId
    )

    private fun ReadingProgressEntity.toExport() = ExportReadingProgress(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        characterOffset = characterOffset,
        totalCharacters = totalCharacters,
        pageNumber = pageNumber,
        totalPages = totalPages,
        progressPercent = progressPercent,
        lastReadPosition = lastReadPosition,
        lastReadTime = lastReadTime,
        totalTimeRead = totalTimeRead,
        kavitaSynced = kavitaSynced
    )

    private fun BookmarkEntity.toExport() = ExportBookmark(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        pageNumber = pageNumber,
        position = position,
        title = title,
        note = note,
        createdTime = createdTime,
        kavitaSynced = kavitaSynced,
        kavitaChapterId = kavitaChapterId,
        modifiedTime = modifiedTime
    )

    private fun HighlightEntity.toExport() = ExportHighlight(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        startPosition = startPosition,
        endPosition = endPosition,
        selectedText = selectedText,
        color = color,
        note = note,
        createdTime = createdTime
    )

    private fun CollectionEntity.toExport() = ExportCollection(
        id = id,
        name = name,
        description = description,
        createdTime = createdTime,
        sortOrder = sortOrder
    )

    private fun BookCollectionCrossRef.toExport() = ExportBookCollectionCrossRef(
        bookId = bookId,
        collectionId = collectionId
    )

    private fun TagEntity.toExport() = ExportTag(
        id = id,
        name = name
    )

    private fun BookTagCrossRef.toExport() = ExportBookTagCrossRef(
        bookId = bookId,
        tagId = tagId
    )

    private fun ReadingSessionEntity.toExport() = ExportReadingSession(
        id = id,
        bookId = bookId,
        startTime = createdAt,
        endTime = createdAt + durationMs,
        durationSeconds = durationMs / 1000,
        pagesRead = pagesRead,
        date = sessionDate.toString()
    )

    private fun PdfSettingsEntity.toExport() = ExportPdfSettings(
        bookId = bookId,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom,
        autoDetected = autoDetected
    )

    private fun ServerEntity.toExport() = ExportServer(
        id = id,
        name = name,
        type = type,
        url = url,
        username = username,
        apiKey = apiKey,
        isActive = isActive,
        lastSyncTime = lastSyncTime
    )

    private fun OpdsCatalogEntity.toExport() = ExportOpdsCatalog(
        id = id,
        name = name,
        url = url,
        username = username,
        authType = authType,
        isActive = isActive
    )
}
