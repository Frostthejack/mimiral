package com.mimiral.app.data.exportimport

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports library data from a JSON export file.
 * Handles conflict resolution by file_path for books (skip if exists),
 * and by primary key for other entities.
 */
class LibraryImporter(
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
    private val gson = Gson()

    /**
     * Parse and import library data from JSON string.
     * Returns an ImportResult with counts of imported/skipped items.
     */
    suspend fun importLibrary(json: String): ImportResult = withContext(Dispatchers.IO) {
        val export = try {
            gson.fromJson(json, LibraryExport::class.java)
        } catch (e: JsonSyntaxException) {
            return@withContext ImportResult(
                success = false,
                errors = listOf("Invalid JSON format: ${e.message}")
            )
        } catch (e: Exception) {
            return@withContext ImportResult(
                success = false,
                errors = listOf("Failed to parse export file: ${e.message}")
            )
        }

        // Version check
        if (export.version > LibraryExport.CURRENT_VERSION) {
            return@withContext ImportResult(
                success = false,
                errors = listOf(
                    "Export version ${export.version} is newer than supported version " +
                        "${LibraryExport.CURRENT_VERSION}. Please update the app."
                )
            )
        }

        val errors = mutableListOf<String>()
        var booksImported = 0
        var booksSkipped = 0
        var progressImported = 0
        var bookmarksImported = 0
        var highlightsImported = 0
        var collectionsImported = 0
        var tagsImported = 0
        var readingSessionsImported = 0
        var pdfSettingsImported = 0
        var serversImported = 0
        var opdsCatalogsImported = 0

        // Build a mapping from old book IDs to new book IDs
        val bookIdMap = mutableMapOf<Int, Int>()

        // Step 1: Import books (skip duplicates by file_path)
        for (exportBook in export.books) {
            try {
                val existing = bookDao.getBookByFilePath(exportBook.filePath)
                if (existing != null) {
                    bookIdMap[exportBook.id] = existing.id
                    booksSkipped++
                } else {
                    val newBook = exportBook.toEntity()
                    val newId = bookDao.insertBook(newBook).toInt()
                    bookIdMap[exportBook.id] = newId
                    booksImported++
                }
            } catch (e: Exception) {
                errors.add("Failed to import book '${exportBook.title}': ${e.message}")
            }
        }

        // Helper to remap book IDs
        fun remapBookId(oldId: Int): Int = bookIdMap[oldId] ?: oldId

        // Step 2: Import collections
        val collectionIdMap = mutableMapOf<Int, Int>()
        for (exportCollection in export.collections) {
            try {
                val existing = collectionDao.getCollectionById(exportCollection.id)
                if (existing != null) {
                    collectionIdMap[exportCollection.id] = existing.id
                } else {
                    val newCollection = exportCollection.toEntity()
                    val newId = collectionDao.insertCollection(newCollection).toInt()
                    collectionIdMap[exportCollection.id] = newId
                    collectionsImported++
                }
            } catch (e: Exception) {
                errors.add("Failed to import collection '${exportCollection.name}': ${e.message}")
            }
        }

        // Step 3: Import tags
        val tagIdMap = mutableMapOf<Int, Int>()
        for (exportTag in export.tags) {
            try {
                val existing = tagDao.getTagByName(exportTag.name)
                if (existing != null) {
                    tagIdMap[exportTag.id] = existing.id
                } else {
                    val newTag = exportTag.toEntity()
                    val newId = tagDao.insertTag(newTag).toInt()
                    tagIdMap[exportTag.id] = newId
                    tagsImported++
                }
            } catch (e: Exception) {
                errors.add("Failed to import tag '${exportTag.name}': ${e.message}")
            }
        }

        // Step 4: Import reading progress
        for (exportProgress in export.readingProgress) {
            try {
                val newBookId = remapBookId(exportProgress.bookId)
                val entity = exportProgress.toEntity(newBookId)
                readingProgressDao.saveProgress(entity)
                progressImported++
            } catch (e: Exception) {
                errors.add(
                    "Failed to import reading progress for " +
                        "book ${exportProgress.bookId}: ${e.message}"
                )
            }
        }

        // Step 5: Import bookmarks
        for (exportBookmark in export.bookmarks) {
            try {
                val newBookId = remapBookId(exportBookmark.bookId)
                val entity = exportBookmark.toEntity(newBookId)
                bookmarkDao.insertBookmark(entity)
                bookmarksImported++
            } catch (e: Exception) {
                errors.add(
                    "Failed to import bookmark for book ${exportBookmark.bookId}: ${e.message}"
                )
            }
        }

        // Step 6: Import highlights
        for (exportHighlight in export.highlights) {
            try {
                val newBookId = remapBookId(exportHighlight.bookId)
                val entity = exportHighlight.toEntity(newBookId)
                highlightDao.insertHighlight(entity)
                highlightsImported++
            } catch (e: Exception) {
                errors.add(
                    "Failed to import highlight for book ${exportHighlight.bookId}: ${e.message}"
                )
            }
        }

        // Step 7: Import book-collection cross references
        for (exportBookCol in export.bookCollections) {
            try {
                val newBookId = remapBookId(exportBookCol.bookId)
                val newColId = collectionIdMap[exportBookCol.collectionId]
                    ?: exportBookCol.collectionId
                collectionDao.addBookToCollection(
                    BookCollectionCrossRef(newBookId, newColId)
                )
            } catch (e: Exception) {
                errors.add("Failed to import book-collection link: ${e.message}")
            }
        }

        // Step 8: Import book-tag cross references
        for (exportBookTag in export.bookTags) {
            try {
                val newBookId = remapBookId(exportBookTag.bookId)
                val newTagId = tagIdMap[exportBookTag.tagId] ?: exportBookTag.tagId
                tagDao.addBookTag(
                    BookTagCrossRef(newBookId, newTagId)
                )
            } catch (e: Exception) {
                errors.add("Failed to import book-tag link: ${e.message}")
            }
        }

        // Step 9: Import reading sessions
        for (exportSession in export.readingSessions) {
            try {
                val newBookId = remapBookId(exportSession.bookId)
                val entity = exportSession.toEntity(newBookId)
                readingSessionDao.insertSession(entity)
                readingSessionsImported++
            } catch (e: Exception) {
                errors.add("Failed to import reading session: ${e.message}")
            }
        }

        // Step 10: Import PDF settings
        for (exportPdf in export.pdfSettings) {
            try {
                val newBookId = remapBookId(exportPdf.bookId)
                val entity = exportPdf.toEntity(newBookId)
                pdfSettingsDao.saveSettings(entity)
                pdfSettingsImported++
            } catch (e: Exception) {
                errors.add("Failed to import PDF settings: ${e.message}")
            }
        }

        // Step 11: Import servers
        for (exportServer in export.servers) {
            try {
                val entity = exportServer.toEntity()
                serverDao.insertServer(entity)
                serversImported++
            } catch (e: Exception) {
                errors.add("Failed to import server '${exportServer.name}': ${e.message}")
            }
        }

        // Step 12: Import OPDS catalogs
        for (exportOpds in export.opdsCatalogs) {
            try {
                val entity = exportOpds.toEntity()
                opdsCatalogDao.insertCatalog(entity)
                opdsCatalogsImported++
            } catch (e: Exception) {
                errors.add("Failed to import OPDS catalog '${exportOpds.name}': ${e.message}")
            }
        }

        ImportResult(
            success = true,
            booksImported = booksImported,
            booksSkipped = booksSkipped,
            progressImported = progressImported,
            bookmarksImported = bookmarksImported,
            highlightsImported = highlightsImported,
            collectionsImported = collectionsImported,
            tagsImported = tagsImported,
            readingSessionsImported = readingSessionsImported,
            pdfSettingsImported = pdfSettingsImported,
            serversImported = serversImported,
            opdsCatalogsImported = opdsCatalogsImported,
            errors = errors
        )
    }

    // ── Export to Entity mapping ──

    private fun ExportBook.toEntity() = BookEntity(
        id = 0, // Auto-generate new ID
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

    private fun ExportReadingProgress.toEntity(bookId: Int) = ReadingProgressEntity(
        id = 0,
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
        kavitaSynced = false // Reset sync status on import
    )

    private fun ExportBookmark.toEntity(bookId: Int) = BookmarkEntity(
        id = 0,
        bookId = bookId,
        chapterIndex = chapterIndex,
        pageNumber = pageNumber,
        position = position,
        title = title,
        note = note,
        createdTime = createdTime,
        kavitaSynced = false,
        kavitaChapterId = null,
        modifiedTime = modifiedTime
    )

    private fun ExportHighlight.toEntity(bookId: Int) = HighlightEntity(
        id = 0,
        bookId = bookId,
        chapterIndex = chapterIndex,
        startPosition = startPosition,
        endPosition = endPosition,
        selectedText = selectedText,
        color = color,
        note = note,
        createdTime = createdTime
    )

    private fun ExportCollection.toEntity() = CollectionEntity(
        id = 0,
        name = name,
        description = description,
        createdTime = createdTime,
        sortOrder = sortOrder
    )

    private fun ExportTag.toEntity() = TagEntity(
        id = 0,
        name = name
    )

    private fun ExportReadingSession.toEntity(bookId: Int) = ReadingSessionEntity(
        id = 0,
        bookId = bookId,
        startTime = startTime,
        endTime = endTime,
        durationSeconds = durationSeconds,
        pagesRead = pagesRead,
        date = date
    )

    private fun ExportPdfSettings.toEntity(bookId: Int) = PdfSettingsEntity(
        bookId = bookId,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom,
        autoDetected = autoDetected
    )

    private fun ExportServer.toEntity() = ServerEntity(
        id = 0,
        name = name,
        type = type,
        url = url,
        username = username,
        password = null, // Do not import passwords for security
        apiKey = null, // Do not import API keys for security
        jwtToken = null,
        isActive = isActive,
        lastSyncTime = null
    )

    private fun ExportOpdsCatalog.toEntity() = OpdsCatalogEntity(
        id = 0,
        name = name,
        url = url,
        username = username,
        password = null, // Do not import passwords for security
        token = null,
        authType = authType,
        isActive = isActive
    )
}
