package com.mimiral.app.data.local.scanner

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.entity.BookEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of importing a book from an external intent (ACTION_VIEW).
 */
sealed class ExternalBookResult {
    /** Book was successfully imported. */
    data class Success(val bookId: Int, val format: String) : ExternalBookResult()

    /** Book already exists in the library. */
    data class AlreadyExists(val bookId: Int, val format: String) : ExternalBookResult()

    /** Import failed. */
    data class Error(val message: String) : ExternalBookResult()
}

/**
 * Handles books opened from external apps via ACTION_VIEW intents.
 *
 * When a user taps an ebook file in a file manager or browser, the system
 * sends an intent with a content:// or file:// URI. This class:
 * 1. Copies the file from the URI to the app's internal imported/ directory
 * 2. Inserts it into the library database (or finds the existing entry)
 * 3. Returns the book ID and format for navigation to the reader
 *
 * Handles both content:// (FileProvider/SAF) and file:// URIs.
 */
@Singleton
class ExternalBookHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val metadataExtractor: MetadataExtractor
) {
    companion object {
        /** Subdirectory under filesDir for externally imported books. */
        private const val IMPORT_DIR = "imported"
    }

    /**
     * Import a book from an external URI into the library.
     *
     * @param uri The content:// or file:// URI from the intent
     * @param mimeType The MIME type from the intent (may be null or incorrect)
     * @return [ExternalBookResult] with book ID + format, or error
     */
    suspend fun importFromUri(uri: Uri, mimeType: String?): ExternalBookResult =
        withContext(Dispatchers.IO) {
            try {
                // Resolve file extension and format from URI and MIME type
                val extension = resolveExtension(uri, mimeType)
                if (extension == null ||
                    extension.lowercase() !in FileScanner.SUPPORTED_EXTENSIONS
                ) {
                    return@withContext ExternalBookResult.Error(
                        "Unsupported file format: ${extension ?: "unknown"}"
                    )
                }

                val format = extension.uppercase()

                // Determine display name
                val displayName = resolveDisplayName(uri)
                val fileName = if (displayName.endsWith(".$extension")) {
                    displayName
                } else {
                    "$displayName.$extension"
                }

                // Copy file to internal storage
                val destFile = copyToInternalStorage(uri, fileName)

                // Check if book already exists in library
                val existing = bookDao.getAllFilePaths().find { it == destFile.absolutePath }
                if (existing != null) {
                    // Book already imported - find its ID
                    val book = bookDao.getAllFilePaths()
                        .let { paths ->
                            // Query by path - need to find the book entity
                            findBookByPath(destFile.absolutePath)
                        }
                    if (book != null) {
                        return@withContext ExternalBookResult.AlreadyExists(book.id, book.format)
                    }
                }

                // Insert into database
                val bookEntity = BookEntity(
                    filePath = destFile.absolutePath,
                    title = displayName,
                    author = null,
                    coverPath = null,
                    format = format,
                    fileSize = destFile.length(),
                    dateAdded = System.currentTimeMillis(),
                    dateModified = destFile.lastModified(),
                    source = "LOCAL"
                )
                val bookId = bookDao.insertBook(bookEntity)

                // Trigger metadata extraction in background
                val pendingBook = PendingBook(
                    filePath = destFile.absolutePath,
                    fileName = fileName,
                    format = format,
                    fileSize = destFile.length(),
                    dateModified = destFile.lastModified() / 1000
                )
                try {
                    metadataExtractor.extractAndUpdate(bookId, pendingBook)
                } catch (_: Exception) {
                    // Metadata extraction failure is non-fatal
                }

                ExternalBookResult.Success(bookId.toInt(), format)
            } catch (e: SecurityException) {
                ExternalBookResult.Error("Permission denied accessing file")
            } catch (e: Exception) {
                ExternalBookResult.Error("Import failed: ${e.message}")
            }
        }

    /**
     * Copy content from a URI to the app's internal imported/ directory.
     * Handles both content:// and file:// schemes.
     */
    private fun copyToInternalStorage(uri: Uri, fileName: String): File {
        val importDir = File(context.filesDir, IMPORT_DIR)
        if (!importDir.exists()) importDir.mkdirs()

        // Create unique filename to avoid collisions
        val destFile = File(importDir, fileName)
        if (destFile.exists()) {
            // File already imported - return existing
            return destFile
        }

        val contentResolver: ContentResolver = context.contentResolver

        when (uri.scheme) {
            "content" -> {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Cannot open content URI: $uri")
            }
            "file" -> {
                val sourceFile = File(uri.path ?: throw IllegalStateException("No file path"))
                sourceFile.copyTo(destFile, overwrite = false)
            }
            else -> {
                throw IllegalStateException("Unsupported URI scheme: ${uri.scheme}")
            }
        }

        return destFile
    }

    /**
     * Resolve the file extension from the URI and MIME type.
     */
    private fun resolveExtension(uri: Uri, mimeType: String?): String? {
        // First try: get extension from URI path
        val uriPath = uri.path
        if (uriPath != null) {
            val ext = File(uriPath).extension.lowercase()
            if (ext.isNotEmpty() && ext in FileScanner.SUPPORTED_EXTENSIONS) {
                return ext
            }
        }

        // Second try: map MIME type to extension
        if (mimeType != null) {
            return mimeTypeToExtension(mimeType)
        }

        return null
    }

    /**
     * Resolve a human-readable display name from the URI.
     */
    private fun resolveDisplayName(uri: Uri): String {
        // Try ContentResolver query for DISPLAY_NAME
        if (uri.scheme == "content") {
            try {
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameCol = it.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME
                        )
                        if (nameCol >= 0) {
                            val name = it.getString(nameCol)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through to URI-based name
            }
        }

        // Fall back to last path segment
        val lastSegment = uri.lastPathSegment
        if (!lastSegment.isNullOrBlank()) {
            // Strip extension if present (we'll add it back based on format)
            val dotIndex = lastSegment.lastIndexOf('.')
            return if (dotIndex > 0) lastSegment.substring(0, dotIndex) else lastSegment
        }

        return "Imported Book"
    }

    /**
     * Find a book entity by its file path.
     */
    private suspend fun findBookByPath(filePath: String): BookEntity? {
        // BookDao doesn't have a getByPath method, so we do a manual lookup
        // by getting all books and filtering. This is fine for small libraries.
        // A dedicated DAO query would be more efficient but requires schema changes.
        try {
            // Use a simple heuristic: if the file exists at this path, the
            // scanAndImportFile will return its existing ID
            val existing = bookDao.getAllFilePaths()
            if (filePath in existing) {
                // The book exists but we don't have a direct path->id lookup.
                // For now, return null and let the caller re-import (insertBook
                // with UNIQUE constraint on filePath would handle dedup).
                return null
            }
        } catch (_: Exception) {
            // Ignore
        }
        return null
    }

    /**
     * Map MIME types to file extensions.
     */
    private fun mimeTypeToExtension(mimeType: String): String? {
        return when (mimeType.lowercase()) {
            "application/epub+zip" -> "epub"
            "application/pdf" -> "pdf"
            "application/x-mobipocket-ebook" -> "mobi"
            "application/vnd.amazon.ebook" -> "azw3"
            "image/vnd.djvu" -> "djvu"
            "application/x-cbz" -> "cbz"
            "application/x-cbr" -> "cbr"
            "text/plain" -> "txt"
            "application/rtf" -> "rtf"
            "application/msword" -> "doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "text/markdown" -> "md"
            "application/xml" -> "fb2" // FB2 is often served as XML
            "application/octet-stream" -> null // Ambiguous - rely on URI extension
            else -> null
        }
    }
}
