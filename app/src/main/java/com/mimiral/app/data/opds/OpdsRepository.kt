package com.mimiral.app.data.opds

import android.content.Context
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.OpdsCatalogDao
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Repository for OPDS catalog operations.
 * Fetches feeds, parses entries, and downloads books.
 */
@Singleton
class OpdsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val opdsCatalogDao: OpdsCatalogDao,
    private val bookDao: BookDao,
    private val opdsClient: OpdsClient
) {

    /**
     * Get all active OPDS catalogs from the database.
     */
    fun getActiveCatalogs(): Flow<List<OpdsCatalogEntity>> =
        opdsCatalogDao.getActiveCatalogs()

    /**
     * Add a new OPDS catalog.
     */
    suspend fun addCatalog(name: String, url: String): Long {
        val entity = OpdsCatalogEntity(
            name = name,
            url = url,
            isActive = true
        )
        return opdsCatalogDao.insertCatalog(entity)
    }

    /**
     * Delete an OPDS catalog.
     */
    suspend fun deleteCatalog(catalog: OpdsCatalogEntity) {
        opdsCatalogDao.deleteCatalog(catalog)
    }

    /**
     * Fetch and parse an OPDS feed for the given catalog.
     */
    suspend fun fetchCatalogFeed(
        catalog: OpdsCatalogEntity
    ): Result<OpdsFeed> {
        return opdsClient.fetchFeed(
            url = catalog.url,
            username = catalog.username,
            password = catalog.password
        )
    }

    /**
     * Fetch a sub-feed by URL (for navigation links within a catalog).
     */
    suspend fun fetchFeedByUrl(url: String): Result<OpdsFeed> {
        return opdsClient.fetchFeed(url)
    }

    /**
     * Download a book from an OPDS acquisition link.
     * Saves to the app's files directory and inserts into the local database.
     * Returns the new book ID.
     */
    suspend fun downloadBook(
        entry: OpdsEntry,
        catalogName: String
    ): Long = withContext(Dispatchers.IO) {
        // Find the best acquisition link (prefer EPUB, then PDF, then first available)
        val acquisitionLink = selectBestAcquisitionLink(entry)
            ?: throw IllegalStateException("No download link for: ${entry.title}")

        // Download the file
        val bytes = opdsClient.downloadFile(acquisitionLink.href)
            .getOrElse { throw it }

        // Determine file format from MIME type or link type
        val format = determineFormat(acquisitionLink.type, acquisitionLink.href)

        // Save to disk
        val downloadsDir = File(context.filesDir, "opds_downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val safeTitle = entry.title.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val fileName = "$safeTitle.${format.lowercase()}"
        val file = File(downloadsDir, fileName)

        // Handle duplicate filenames
        val finalFile = if (file.exists()) {
            val baseName = fileName.substringBeforeLast('.')
            val ext = fileName.substringAfterLast('.')
            var counter = 1
            var candidate: File
            do {
                candidate = File(downloadsDir, "${baseName}_$counter.$ext")
                counter++
            } while (candidate.exists())
            candidate
        } else {
            file
        }

        FileOutputStream(finalFile).use { fos ->
            fos.write(bytes)
        }

        // Download cover image if available
        val coverPath = downloadCover(entry, downloadsDir)

        // Insert into database
        val bookEntity = BookEntity(
            filePath = finalFile.absolutePath,
            title = entry.title,
            author = entry.authors.joinToString(", ").ifBlank { null },
            description = entry.summary,
            coverPath = coverPath,
            format = format,
            fileSize = finalFile.length(),
            dateAdded = System.currentTimeMillis(),
            isDownloaded = true,
            source = "OPDS",
            sourceId = entry.id
        )

        bookDao.insertBook(bookEntity)
    }

    /**
     * Get an OPDS book's cover image URL for a given entry.
     */
    fun getCoverImageUrl(entry: OpdsEntry): String? {
        return entry.coverLink?.href ?: entry.thumbnailLink?.href
    }

    /**
     * Seed the default OPDS catalogs if none exist.
     */
    suspend fun seedDefaultCatalogs() {
        val active = opdsCatalogDao.getActiveCatalogs().first()
        if (active.isEmpty()) {
            DefaultOpdsCatalogs.ALL.forEach { catalog ->
                addCatalog(name = catalog.name, url = catalog.url)
            }
        }
    }

    private fun selectBestAcquisitionLink(entry: OpdsEntry): OpdsLink? {
        val links = entry.acquisitionLinks
        if (links.isEmpty()) return null

        // Prefer open-access links
        val openAccess = links.filter {
            it.rel == OpdsRel.ACQUISITION_OPEN || it.rel == OpdsRel.ACQUISITION
        }
        val candidates = openAccess.ifEmpty { links }

        // Prefer EPUB, then PDF
        val epubLink = candidates.find {
            it.type.contains("epub", ignoreCase = true) ||
                it.href.contains(".epub", ignoreCase = true)
        }
        if (epubLink != null) return epubLink

        val pdfLink = candidates.find {
            it.type.contains("pdf", ignoreCase = true) ||
                it.href.contains(".pdf", ignoreCase = true)
        }
        if (pdfLink != null) return pdfLink

        return candidates.first()
    }

    private fun determineFormat(mimeType: String, href: String): String {
        return when {
            mimeType.contains("epub", ignoreCase = true) -> "EPUB"
            mimeType.contains("pdf", ignoreCase = true) -> "PDF"
            mimeType.contains("mobi", ignoreCase = true) -> "MOBI"
            mimeType.contains("fb2", ignoreCase = true) -> "FB2"
            href.contains(".epub", ignoreCase = true) -> "EPUB"
            href.contains(".pdf", ignoreCase = true) -> "PDF"
            href.contains(".mobi", ignoreCase = true) -> "MOBI"
            href.contains(".fb2", ignoreCase = true) -> "FB2"
            else -> "EPUB" // default
        }
    }

    private suspend fun downloadCover(
        entry: OpdsEntry,
        downloadsDir: File
    ): String? {
        val coverUrl = getCoverImageUrl(entry) ?: return null
        return try {
            val bytes = opdsClient.downloadFile(coverUrl).getOrNull()
                ?: return null
            val coverDir = File(downloadsDir, "covers")
            if (!coverDir.exists()) coverDir.mkdirs()
            val safeName = entry.id.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            val ext = when {
                coverUrl.contains(".png", ignoreCase = true) -> ".png"
                coverUrl.contains(".jpg", ignoreCase = true) ||
                    coverUrl.contains(".jpeg", ignoreCase = true) -> ".jpg"
                else -> ".jpg"
            }
            val coverFile = File(coverDir, "opds_${safeName}$ext")
            FileOutputStream(coverFile).use { fos ->
                fos.write(bytes)
            }
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
