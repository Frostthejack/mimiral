package com.mimiral.app.data.remote.kavita

import android.content.Context
import android.util.Log
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.entity.ServerEntity
import com.mimiral.app.data.remote.KavitaServerInfo
import com.mimiral.app.data.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Represents the state of a book download operation.
 */
sealed class DownloadState {
    /** No download in progress. */
    object Idle : DownloadState()

    /** Download is in progress. */
    data class Downloading(
        val bookTitle: String,
        val bytesDownloaded: Long = 0,
        val totalBytes: Long = 0
    ) : DownloadState()

    /** Download completed successfully. */
    data class Completed(
        val bookTitle: String,
        val filePath: String
    ) : DownloadState()

    /** Download failed. */
    data class Failed(
        val bookTitle: String,
        val error: String
    ) : DownloadState()
}

/**
 * Format detection from Kavita file content.
 */
object BookFormatDetector {
    private val FORMAT_SIGNATURES = mapOf(
        "EPUB" to byteArrayOf(0x50, 0x4B, 0x03, 0x04), // ZIP signature
        "PDF" to byteArrayOf(0x25, 0x50, 0x44, 0x46), // %PDF
        "CBZ" to byteArrayOf(0x50, 0x4B, 0x03, 0x04), // ZIP
        "CBR" to byteArrayOf(0x52, 0x61, 0x72, 0x21), // Rar!
        "RAR" to byteArrayOf(0x52, 0x61, 0x72, 0x21), // Rar!
        "PNG" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
        "JPEG" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    )

    private val SIGNATURE_MAX_LEN = 4

    /**
     * Detect ebook format from file header bytes.
     * Returns the format string or "UNKNOWN".
     */
    fun detectFormat(data: ByteArray): String {
        if (data.size < SIGNATURE_MAX_LEN) return "UNKNOWN"
        for ((format, signature) in FORMAT_SIGNATURES) {
            if (data.size >= signature.size) {
                var match = true
                for (i in signature.indices) {
                    if (data[i] != signature[i]) {
                        match = false
                        break
                    }
                }
                if (match) return format
            }
        }
        return "UNKNOWN"
    }

    /**
     * Map a Kavita format code to file extension.
     */
    fun formatToExtension(format: String): String = when {
        format.contains("epub", ignoreCase = true) -> ".epub"
        format.contains("pdf", ignoreCase = true) -> ".pdf"
        format.contains("cbz", ignoreCase = true) -> ".cbz"
        format.contains("cbr", ignoreCase = true) -> ".cbr"
        format.contains("rar", ignoreCase = true) -> ".cbr"
        format.contains("png", ignoreCase = true) -> ".png"
        format.contains("jpeg", ignoreCase = true) ||
            format.contains("jpg", ignoreCase = true) -> ".jpg"
        else -> ".bin"
    }
}

/**
 * Repository for Kavita server book operations.
 *
 * Orchestrates:
 * - Book download (via KavitaClient)
 * - File storage (to app's internal files directory)
 * - Database insertion (via BookRepository)
 * - Cover image download and storage
 * - Reading progress sync (push and pull)
 * - Bookmark sync (push and pull)
 */
@Singleton
class KavitaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kavitaClient: KavitaClient,
    private val serverDao: ServerDao,
    private val bookRepository: BookRepository
) {
    companion object {
        private const val TAG = "KavitaRepository"
        private const val BOOKS_DIR = "kavita_books"
        private const val COVERS_DIR = "kavita_covers"
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /**
     * Get the active Kavita server configuration.
     */
    suspend fun getActiveServer(): ServerEntity? =
        serverDao.getActiveServerByType("KAVITA")

    /**
     * Create a KavitaClient from the active server configuration in the database.
     * Returns null if no server is configured.
     */
    private suspend fun createClientFromDb(): KavitaClient? {
        val activeServer = serverDao.getActiveServerByType("KAVITA") ?: return null
        if (activeServer.url.isBlank()) return null
        return KavitaClient.create(
            baseUrl = activeServer.url,
            apiKey = activeServer.apiKey
        )
    }

    /**
     * List all Kavita libraries.
     */
    suspend fun getLibraries(): KavitaResult<List<KavitaLibrary>> {
        val client = createClientFromDb() ?: return KavitaResult.Error(
            message = "No Kavita server configured. Please add a Kavita server in Settings."
        )
        return client.getLibraries()
    }

    /**
     * Get all series in a library.
     */
    suspend fun getSeriesForLibrary(libraryId: Int): KavitaResult<List<KavitaSeries>> {
        val client = createClientFromDb() ?: return KavitaResult.Error(
            message = "No Kavita server configured. Please add a Kavita server in Settings."
        )
        val result = client.getSeries(libraryId = libraryId)
        return when (result) {
            is KavitaResult.Success -> KavitaResult.Success(result.data)
            is KavitaResult.Error -> result
        }
    }

    /**
     * Get detailed series metadata including volumes.
     */
    suspend fun getSeriesMetadata(seriesId: Int): KavitaResult<KavitaSeries> {
        val client = createClientFromDb() ?: return KavitaResult.Error(
            message = "No Kavita server configured. Please add a Kavita server in Settings."
        )
        val volumesResult = client.getVolumes(seriesId)
        if (volumesResult is KavitaResult.Error) return volumesResult
        val volumes = (volumesResult as KavitaResult.Success).data
        // Return a KavitaSeries with the volumes populated
        // We need basic series info; use a minimal series with volumes
        return KavitaResult.Success(
            KavitaSeries(
                id = seriesId,
                name = "",
                libraryId = 0,
                volumes = volumes
            )
        )
    }

    /**
     * Resolve a cover image URL from a cover image path.
     */
    suspend fun resolveCoverImageUrl(coverImage: String?): String? {
        if (coverImage.isNullOrBlank()) return null
        val server = withContext(Dispatchers.IO) {
            serverDao.getActiveServerByType("KAVITA")
        } ?: return null
        val base = server.url.trimEnd('/')
        val apiKey = server.apiKey ?: return null
        // coverImage is a filename like "v1567_c1724.png" — extract the volumeId and chapterId
        // Format: v{volumeId}_c{chapterId}.png
        val regex = Regex("v(\\d+)_c(\\d+)")
        val match = regex.find(coverImage)
        return if (match != null) {
            val volumeId = match.groupValues[1]
            "$base/api/Image/volume-cover?volumeId=$volumeId&apiKey=$apiKey"
        } else {
            null
        }
    }

    /**
     * Connect to the Kavita server using stored credentials.
     * Tests connectivity and returns the server info.
     */
    suspend fun testConnection(server: ServerEntity): KavitaResult<KavitaServerInfo> {
        val testClient = KavitaClient(
            baseUrl = server.url,
            apiKey = server.apiKey,
            jwtToken = server.jwtToken
        )
        if (server.apiKey.isNullOrBlank() && server.jwtToken.isNullOrBlank()) {
            // Try username/password login
            if (!server.username.isNullOrBlank() && !server.password.isNullOrBlank()) {
                when (
                    val loginResult = testClient.login(
                        server.username,
                        server.password
                    )
                ) {
                    is KavitaResult.Success -> {
                        // Update stored token
                        serverDao.updateServer(
                            server.copy(jwtToken = loginResult.data)
                        )
                        return testClient.getServerInfo()
                    }
                    is KavitaResult.Error -> return loginResult
                }
            }
        }
        return testClient.getServerInfo()
    }

    /**
     * Download a book from Kavita and save it locally.
     *
     * Steps:
     * 1. Download raw bytes from Kavita via chapterId
     * 2. Detect format from file header
     * 3. Save to internal storage
     * 4. Insert book record into local database
     *
     * @param chapterId The Kavita chapter ID to download
     * @param title Book title for display and file naming
     * @param seriesId Optional series ID for cover loading
     * @param author Optional author name
     * @return KavitaResult with the local file path, or error
     */
    suspend fun downloadBook(
        chapterId: Int,
        title: String,
        seriesId: Int? = null,
        author: String? = null
    ): KavitaResult<Int> = withContext(Dispatchers.IO) {
        val client = createClientFromDb()
        if (client == null) {
            _downloadState.value = DownloadState.Failed(title, "No Kavita server configured")
            return@withContext KavitaResult.Error(message = "No Kavita server configured")
        }
        _downloadState.value = DownloadState.Downloading(bookTitle = title)

        try {
            // Step 1: Download bytes
            val downloadResult = client.downloadBook(chapterId)
            if (downloadResult is KavitaResult.Error) {
                _downloadState.value = DownloadState.Failed(title, downloadResult.message)
                return@withContext KavitaResult.Error(
                    message = downloadResult.message,
                    code = downloadResult.code,
                    cause = downloadResult.cause
                )
            }

            val bytes = (downloadResult as KavitaResult.Success).data
            Log.d(TAG, "Downloaded ${bytes.size} bytes for: $title")

            // Step 2: Detect format
            val detectedFormat = BookFormatDetector.detectFormat(bytes)
            val extension = BookFormatDetector.formatToExtension(detectedFormat)
            val mimeType = when {
                extension == ".epub" -> "EPUB"
                extension == ".pdf" -> "PDF"
                extension == ".cbz" -> "CBZ"
                extension == ".cbr" -> "CBR"
                else -> detectedFormat
            }

            // Step 3: Save to internal storage
            val booksDir = File(context.filesDir, BOOKS_DIR)
            if (!booksDir.exists()) {
                booksDir.mkdirs()
            }
            val sanitizedTitle = title.replace(
                Regex("[^a-zA-Z0-9._\\-]"),
                "_"
            )
            val fileName = "$sanitizedTitle$extension"
            val file = File(booksDir, fileName)

            // Handle duplicate filenames
            val finalFile = if (file.exists()) {
                val nameWithoutExt = fileName.substringBeforeLast('.')
                val ext = fileName.substringAfterLast('.')
                var counter = 1
                var candidate: File
                do {
                    candidate = File(booksDir, "$nameWithoutExt ($counter).$ext")
                    counter++
                } while (candidate.exists())
                candidate
            } else {
                file
            }

            FileOutputStream(finalFile).use { fos ->
                fos.write(bytes)
            }
            Log.d(TAG, "Saved book to: ${finalFile.absolutePath}")

            // Step 4: Insert into database
            val bookEntity = com.mimiral.app.data.local.entity.BookEntity(
                filePath = finalFile.absolutePath,
                title = title,
                author = author,
                coverPath = null,
                format = mimeType,
                fileSize = bytes.size.toLong(),
                dateAdded = System.currentTimeMillis(),
                dateModified = System.currentTimeMillis(),
                isDownloaded = true,
                source = "KAVITA",
                sourceId = chapterId.toString(),
                kavitaSeriesId = seriesId
            )

            val bookId = bookRepository.insertBook(bookEntity)

            // Step 5: Try to download cover
            if (seriesId != null) {
                downloadAndSaveCover(seriesId, finalFile.absolutePath)
                    .onSuccess { coverPath ->
                        if (coverPath != null) {
                            bookRepository.updateBook(
                                bookEntity.copy(
                                    id = bookId.toInt(),
                                    coverPath = coverPath
                                )
                            )
                        }
                    }
            }

            _downloadState.value = DownloadState.Completed(
                bookTitle = title,
                filePath = finalFile.absolutePath
            )

            KavitaResult.Success(bookId.toInt())
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading book: ${e.message}", e)
            _downloadState.value = DownloadState.Failed(
                title,
                e.message ?: "Unknown error"
            )
            KavitaResult.Error(
                message = "Download error: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Download and save a series cover image.
     *
     * Downloads the cover from Kavita, saves it to internal storage,
     * and returns the local file path.
     *
     * @param seriesId The Kavita series ID
     * @param bookFilePath The book file path to associate cover with
     * @return Result with local cover path, or null if download failed
     */
    suspend fun downloadAndSaveCover(
        seriesId: Int,
        bookFilePath: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val client = createClientFromDb() ?: return@withContext Result.success(null)
            val result = client.downloadSeriesCover(seriesId)
            when (result) {
                is KavitaResult.Success -> {
                    val bytes = result.data
                    val extension = BookFormatDetector.formatToExtension(
                        BookFormatDetector.detectFormat(bytes)
                    )

                    val coversDir = File(context.filesDir, COVERS_DIR)
                    if (!coversDir.exists()) {
                        coversDir.mkdirs()
                    }

                    val coverFileName = "series_${seriesId}_cover$extension"
                    val coverFile = File(coversDir, coverFileName)

                    FileOutputStream(coverFile).use { fos ->
                        fos.write(bytes)
                    }

                    Log.d(
                        TAG,
                        "Saved cover to: ${coverFile.absolutePath} " +
                            "(${bytes.size} bytes)"
                    )
                    Result.success(coverFile.absolutePath)
                }
                is KavitaResult.Error -> {
                    Log.w(
                        TAG,
                        "Failed to download cover for series $seriesId: " +
                            result.message
                    )
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cover: ${e.message}", e)
            Result.success(null)
        }
    }

    /**
     * Load a cover image from a local file path into a format
     * suitable for Coil image loading.
     *
     * Returns a File that can be used with Coil's AsyncImage
     * via the imageUrl parameter.
     *
     * @param coverPath Local file path of the cover image
     * @return The cover File if it exists, null otherwise
     */
    fun loadCoverFile(coverPath: String?): File? {
        if (coverPath == null) return null
        val file = File(coverPath)
        return if (file.exists()) file else null
    }

    /**
     * Push reading progress to Kavita.
     *
     * @param chapterId The chapter ID
     * @param pageNum Current page number
     * @param seriesId The series ID
     * @param volumeId The volume ID
     * @param libraryId The library ID
     */
    suspend fun pushProgress(
        chapterId: Int,
        pageNum: Int,
        seriesId: Int,
        volumeId: Int,
        libraryId: Int
    ): KavitaResult<Unit> {
        val client = createClientFromDb() ?: return KavitaResult.Error(
            message = "No Kavita server configured. Please add a Kavita server in Settings."
        )
        return client.pushProgress(
            chapterId = chapterId,
            pageNum = pageNum,
            seriesId = seriesId,
            volumeId = volumeId,
            libraryId = libraryId
        )
    }

    /**
     * Pull reading progress from Kavita for a series.
     */
    suspend fun pullProgress(
        seriesId: Int
    ): KavitaResult<List<KavitaProgress>> {
        val client = createClientFromDb() ?: return KavitaResult.Error(
            message = "No Kavita server configured. Please add a Kavita server in Settings."
        )
        return client.pullProgress(seriesId)
    }

    /**
     * Get bookmarks for a series from Kavita.
     */
    suspend fun getBookmarks(
        seriesId: Int
    ): KavitaResult<List<KavitaBookmark>> {
        val client = createClientFromDb() ?: return KavitaResult.Error(
            message = "No Kavita server configured. Please add a Kavita server in Settings."
        )
        return client.getBookmarks(seriesId)
    }

    /**
     * Get the format of a downloaded book by its database ID.
     */
    suspend fun getBookFormat(bookId: Int): String? {
        return try {
            bookRepository.getBookById(bookId)?.format
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reset download state to Idle.
     */
    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }
}
