package com.mimiral.app.data.local.scanner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.entity.BookEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Represents the current state of a file scan operation.
 */
sealed class ScanState {
    /** No scan is running. */
    object Idle : ScanState()

    /** Scan is actively discovering files. */
    data class Scanning(
        val filesFound: Int = 0,
        val filesProcessed: Int = 0,
        val newFiles: Int = 0
    ) : ScanState()

    /** Scan completed successfully. */
    data class Completed(
        val totalFound: Int,
        val newFiles: Int,
        val duplicatesSkipped: Int
    ) : ScanState()

    /** Scan failed with an error. */
    data class Error(val message: String, val cause: Throwable? = null) : ScanState()
}

/**
 * A discovered ebook file awaiting metadata extraction.
 */
data class PendingBook(
    val filePath: String,
    val fileName: String,
    val format: String,
    val fileSize: Long,
    val dateModified: Long
)

/**
 * Hybrid file scanner that discovers ebook files on device storage.
 *
 * Uses MediaStore as the primary discovery mechanism (fast, indexed) with
 * SAF (Storage Access Framework) fallback for directories not indexed by
 * MediaStore. Scans common locations: Downloads, Documents, and the
 * standard external storage directories.
 *
 * Architecture:
 * - Shallow discovery pass queries MediaStore for supported MIME types
 * - Results are cross-referenced against the local Room DB to skip duplicates
 * - New files are flagged as PENDING_METADATA
 * - Metadata extraction is performed lazily in a background coroutine pool
 *
 * Thread-safety: This class is designed to be used as a singleton. The scan
 * operation is launched in a coroutine pool and can be observed via [scanState].
 */
@Singleton
class FileScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val metadataExtractor: MetadataExtractor
) {
    companion object {
        /** Supported ebook file extensions (lowercase, without dot). */
        val SUPPORTED_EXTENSIONS = setOf(
            "epub", "pdf", "mobi", "azw3", "fb2", "djvu",
            "cbz", "cbr", "txt", "rtf", "doc", "docx", "md"
        )

        /** MIME types corresponding to supported formats for MediaStore queries. */
        private val SUPPORTED_MIME_TYPES = arrayOf(
            "application/epub+zip", // .epub
            "application/pdf", // .pdf
            "application/x-mobipocket-ebook", // .mobi
            "application/vnd.amazon.ebook", // .azw3
            "application/xml", // .fb2 (often served as XML)
            "image/vnd.djvu", // .djvu
            "application/x-cbz", // .cbz
            "application/x-cbr", // .cbr
            "text/plain", // .txt
            "application/rtf", // .rtf
            "application/msword", // .doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "text/markdown" // .md
        )

        /** MediaStore URI for external volume. */
        private val MEDIA_STORE_URI = MediaStore.Files.getContentUri("external")

        /** Batch size for DB cross-referencing to avoid huge IN clauses. */
        private const val BATCH_SIZE = 200

        /** Number of concurrent metadata extraction coroutines. */
        private const val METADATA_CONCURRENCY = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /**
     * Initiates a full scan of device storage for ebook files.
     *
     * This method is idempotent -- if a scan is already running, it will
     * return without starting a new one. The scan state can be observed
     * via [scanState].
     *
     * @param scanDownloads Whether to scan the Downloads directory (default: true)
     * @param scanDocuments Whether to scan the Documents directory (default: true)
     * @param additionalRoots Optional list of additional directories to scan
     */
    fun startScan(
        scanDownloads: Boolean = true,
        scanDocuments: Boolean = true,
        additionalRoots: List<File> = emptyList()
    ) {
        if (_scanState.value is ScanState.Scanning) return

        scope.launch {
            try {
                performScan(scanDownloads, scanDocuments, additionalRoots)
            } catch (e: Exception) {
                _scanState.value = ScanState.Error("Scan failed: ${e.message}", e)
            }
        }
    }

    /**
     * Performs the actual scan operation.
     */
    private suspend fun performScan(
        scanDownloads: Boolean,
        scanDocuments: Boolean,
        additionalRoots: List<File>
    ) = withContext(Dispatchers.IO) {
        _scanState.value = ScanState.Scanning()

        // Step 1: Collect all file paths from MediaStore
        val discoveredFiles = mutableListOf<PendingBook>()

        if (scanDownloads || scanDocuments) {
            val mediaStoreFiles = queryMediaStore(scanDownloads, scanDocuments)
            discoveredFiles.addAll(mediaStoreFiles)
        }

        // Step 2: SAF fallback for additional roots
        for (root in additionalRoots) {
            if (root.exists() && root.isDirectory) {
                val safFiles = scanDirectory(root)
                discoveredFiles.addAll(safFiles)
            }
        }

        val totalFound = discoveredFiles.size

        // Step 3: Cross-reference with Room DB to find new files
        val existingPaths = bookDao.getAllFilePaths().toSet()
        val newFiles = discoveredFiles.filter { it.filePath !in existingPaths }
        val duplicatesSkipped = totalFound - newFiles.size

        _scanState.value = ScanState.Scanning(
            filesFound = totalFound,
            filesProcessed = 0,
            newFiles = newFiles.size
        )

        // Step 4: Insert new files as PENDING_METADATA and extract metadata lazily
        if (newFiles.isNotEmpty()) {
            extractMetadataForNewFiles(newFiles)
        }

        _scanState.value = ScanState.Completed(
            totalFound = totalFound,
            newFiles = newFiles.size,
            duplicatesSkipped = duplicatesSkipped
        )
    }

    /**
     * Queries MediaStore for files matching supported MIME types in
     * Downloads and/or Documents directories.
     *
     * Note: On Android 10+ (API 29+), READ_EXTERNAL_STORAGE permission must be
     * granted for MediaStore to return non-empty results. On Android 11+ (API 30+),
     * MANAGE_EXTERNAL_STORAGE is required for broad file access. The caller is
     * responsible for ensuring permissions are granted before invoking this method.
     */
    private fun queryMediaStore(
        includeDownloads: Boolean,
        includeDocuments: Boolean
    ): List<PendingBook> {
        val results = mutableListOf<PendingBook>()
        val contentResolver = context.contentResolver

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH
        )

        // Build selection: MIME type filter
        val mimeTypePlaceholders = SUPPORTED_MIME_TYPES.joinToString(",") { "?" }
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IN ($mimeTypePlaceholders)"

        // Build selection args for relative path filtering
        val selectionArgs = mutableListOf<String>().apply {
            addAll(SUPPORTED_MIME_TYPES)
        }

        // Add relative path constraints if targeting specific directories.
        // RELATIVE_PATH values on Android 10+ typically look like "Downloads/", "Documents/", "Books/"
        // The trailing slash is part of the value, so "Download" matches "Downloads/" via %Download%
        val pathSelection = if (includeDownloads && includeDocuments) {
            // Both directories - use OR condition
            val col = MediaStore.Files.FileColumns.RELATIVE_PATH
            " AND ($col LIKE ? OR $col LIKE ? OR $col LIKE ?)"
        } else if (includeDownloads) {
            " AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
        } else if (includeDocuments) {
            " AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
        } else {
            ""
        }

        val fullSelection = selection + pathSelection

        if (includeDownloads) {
            selectionArgs.add("%Download%")
        }
        if (includeDocuments) {
            selectionArgs.add("%Documents%")
        }
        // Also match "Books/" directory when scanning both
        if (includeDownloads && includeDocuments) {
            selectionArgs.add("%Books%")
        }

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                MEDIA_STORE_URI,
                projection,
                fullSelection,
                selectionArgs.toTypedArray(),
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val modCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (c.moveToNext()) {
                    // DATA may be null/empty on scoped storage; fall back to content URI
                    val filePath = c.getString(dataCol)?.takeIf { it.isNotBlank() }
                        ?: contentResolver.getFilePathFromUri(
                            ContentUris.withAppendedId(MEDIA_STORE_URI, c.getLong(idCol))
                        )
                        ?: continue
                    val fileName = c.getString(nameCol) ?: continue
                    val mimeType = c.getString(mimeCol) ?: continue
                    val fileSize = c.getLong(sizeCol)
                    val dateModified = c.getLong(modCol)

                    val extension = File(filePath).extension.lowercase()
                    if (extension !in SUPPORTED_EXTENSIONS) continue

                    results.add(
                        PendingBook(
                            filePath = filePath,
                            fileName = fileName,
                            format = extension.uppercase(),
                            fileSize = fileSize,
                            dateModified = dateModified
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // MediaStore query can fail on some devices; fall through to SAF
        }

        return results
    }

    /**
     * Resolves a content:// URI to a filesystem path.
     * Used as fallback when DATA column is null on scoped storage.
     */
    private fun ContentResolver.getFilePathFromUri(uri: Uri): String? {
        return try {
            query(uri, arrayOf(MediaStore.Files.FileColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Recursively scans a directory for ebook files using filesystem traversal.
     * Used as a fallback for directories not indexed by MediaStore.
     *
     * @param directory The root directory to scan
     * @return List of discovered [PendingBook] entries
     */
    private fun scanDirectory(directory: File): List<PendingBook> {
        val results = mutableListOf<PendingBook>()

        try {
            directory.walkTopDown()
                .maxDepth(3) // Shallow scan: limit depth to avoid ANR on deep trees
                .filter { it.isFile }
                .filter { it.extension.lowercase() in SUPPORTED_EXTENSIONS }
                .forEach { file ->
                    results.add(
                        PendingBook(
                            filePath = file.absolutePath,
                            fileName = file.name,
                            format = file.extension.uppercase(),
                            fileSize = file.length(),
                            dateModified = file.lastModified() / 1000 // Convert to seconds
                        )
                    )
                }
        } catch (e: SecurityException) {
            // Permission denied for this directory; skip silently
        } catch (e: Exception) {
            // Other errors; skip this directory
        }

        return results
    }

    /**
     * Inserts new files into the database and triggers lazy metadata extraction
     * in a background coroutine pool (2-3 concurrent workers).
     */
    private suspend fun extractMetadataForNewFiles(newFiles: List<PendingBook>) {
        coroutineScope {
            // Process in batches to avoid overwhelming the system
            val batches = newFiles.chunked(BATCH_SIZE)

            for (batch in batches) {
                // Insert batch into DB as PENDING_METADATA
                val insertedIds = mutableListOf<Pair<Long, PendingBook>>()

                for (pending in batch) {
                    try {
                        val bookEntity = BookEntity(
                            filePath = pending.filePath,
                            title = pending.fileName,
                            // Placeholder; will be updated by metadata extractor
                            author = null,
                            coverPath = null,
                            format = pending.format,
                            fileSize = pending.fileSize,
                            dateAdded = System.currentTimeMillis(),
                            dateModified = pending.dateModified * 1000, // Convert to millis
                            source = "LOCAL"
                        )
                        val id = bookDao.insertBook(bookEntity)
                        insertedIds.add(id to pending)
                    } catch (e: Exception) {
                        // Skip files that fail to insert
                    }
                }

                // Launch metadata extraction with limited concurrency
                val semaphore = kotlinx.coroutines.sync.Semaphore(METADATA_CONCURRENCY)
                val jobs = insertedIds.map { (bookId, pending) ->
                    async {
                        semaphore.acquire()
                        try {
                            metadataExtractor.extractAndUpdate(bookId, pending)
                        } finally {
                            semaphore.release()
                        }
                    }
                }
                jobs.awaitAll()

                // Update scan state progress
                val current = _scanState.value
                if (current is ScanState.Scanning) {
                    _scanState.value = current.copy(
                        filesProcessed = current.filesProcessed + batch.size
                    )
                }
            }
        }
    }

    /**
     * Resets the scan state to Idle. Useful for retrying after an error.
     */
    fun reset() {
        _scanState.value = ScanState.Idle
    }

    // ---- Public API for LibraryRepository ----

    /**
     * Scan a directory (via SAF Uri) for ebook files and return pending book data.
     * Does NOT insert into the database -- callers are responsible for dedup + insert.
     *
     * @param uri The SAF Uri of the directory to scan
     * @return List of PendingBook objects for discovered files
     */
    suspend fun scanDirectory(uri: Uri): List<PendingBook> = withContext(Dispatchers.IO) {
        val path = uri.path ?: return@withContext emptyList()
        // SAF Uris may not map directly to filesystem paths; attempt best-effort resolution
        val file = try {
            File(path)
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        if (file.exists() && file.isDirectory) {
            scanDirectory(file)
        } else {
            emptyList()
        }
    }

    /**
     * Scan a single file and return it as a BookEntity, or null if unsupported.
     *
     * @param filePath Absolute path to the file
     * @return BookEntity for the file, or null if not a supported format
     */
    suspend fun scanFile(filePath: String): BookEntity? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return@withContext null

        val extension = file.extension.lowercase()
        if (extension !in SUPPORTED_EXTENSIONS) return@withContext null

        val pendingBook = PendingBook(
            filePath = file.absolutePath,
            fileName = file.name,
            format = extension.uppercase(),
            fileSize = file.length(),
            dateModified = file.lastModified() / 1000
        )

        try {
            val bookEntity = BookEntity(
                filePath = pendingBook.filePath,
                title = pendingBook.fileName,
                author = null,
                coverPath = null,
                format = pendingBook.format,
                fileSize = pendingBook.fileSize,
                dateAdded = System.currentTimeMillis(),
                dateModified = pendingBook.dateModified * 1000,
                source = "LOCAL"
            )
            val id = bookDao.insertBook(bookEntity)
            bookEntity.copy(id = id.toInt())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the set of supported file extensions (lowercase, without dot).
     */
    fun supportedExtensions(): Set<String> = SUPPORTED_EXTENSIONS
}
