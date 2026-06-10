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
 * - Book download (via KavitaApi)
 * - File storage (to app's internal files directory)
 * - Database insertion (via BookRepository)
 * - Cover image download and storage
 * - Reading progress sync (push and pull)
 * - Bookmark sync (push and pull)
 */
@Singleton
class KavitaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kavitaApi: KavitaApi,
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
     * List all Kavita libraries.
     */
    suspend fun getLibraries(): KavitaResult<List<KavitaLibrary>> {
        return try {
            val response = kavitaApi.getLibraries()
            if (response.isSuccessful) {
                KavitaResult.Success(response.body() ?: emptyList())
            } else {
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: ${response.message()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching libraries: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
    }

    /**
     * Get all series in a library.
     */
    suspend fun getSeriesForLibrary(libraryId: Int): KavitaResult<List<KavitaSeries>> {
        return try {
            val response = kavitaApi.getSeries(libraryId = libraryId)
            if (response.isSuccessful) {
                KavitaResult.Success(response.body()?.items ?: emptyList())
            } else {
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: ${response.message()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching series: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
    }

    /**
     * Get detailed series metadata including volumes.
     */
    suspend fun getSeriesMetadata(seriesId: Int): KavitaResult<KavitaSeries> {
        return try {
            val volumesResponse = kavitaApi.getVolumes(seriesId)
            if (!volumesResponse.isSuccessful) {
                return KavitaResult.Error(
                    message = "HTTP ${volumesResponse.code()}: ${volumesResponse.message()}",
                    code = volumesResponse.code()
                )
            }
            val volumes = volumesResponse.body() ?: emptyList()
            KavitaResult.Success(
                KavitaSeries(
                    id = seriesId,
                    name = "",
                    libraryId = 0,
                    volumes = volumes
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching series metadata: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
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
        // For testConnection, we use the KavitaApi (which resolves server URL from DB)
        // but for testing a specific server URL, we need the base URL check first.
        // The KavitaApi uses KavitaBaseUrlInterceptor which resolves from DB,
        // so we rely on getServerInfo() which tests the configured server.
        return try {
            val response = kavitaApi.getServerInfo()
            if (response.isSuccessful) {
                val info = response.body() ?: KavitaServerInfo()
                KavitaResult.Success(info)
            } else {
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: ${response.message()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error testing connection: ${e.message}", e)
            KavitaResult.Error(message = "Connection error: ${e.message}", cause = e)
        }
    }

    /**
     * Authenticate with username/password for a specific server.
     * Returns the JWT token on success.
     */
    suspend fun loginWithCredentials(
        server: ServerEntity,
        username: String,
        password: String
    ): KavitaResult<String> {
        return try {
            val request = KavitaLoginRequest(username = username, password = password)
            val response = kavitaApi.login(request)
            if (response.isSuccessful) {
                val loginResponse = response.body()
                val token = loginResponse?.token ?: ""
                if (token.isNotBlank()) {
                    // Update stored token
                    serverDao.updateServer(server.copy(jwtToken = token))
                    KavitaResult.Success(token)
                } else {
                    KavitaResult.Error(message = "Empty token in login response")
                }
            } else {
                KavitaResult.Error(
                    message = "Login failed: HTTP ${response.code()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
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
        _downloadState.value = DownloadState.Downloading(bookTitle = title)

        try {
            // Step 1: Download bytes
            val downloadResponse = kavitaApi.downloadBook(chapterId)
            if (!downloadResponse.isSuccessful) {
                _downloadState.value = DownloadState.Failed(
                    title,
                    "HTTP ${downloadResponse.code()}"
                )
                return@withContext KavitaResult.Error(
                    message = "Download failed: HTTP ${downloadResponse.code()}",
                    code = downloadResponse.code()
                )
            }

            val bytes = downloadResponse.body()?.bytes() ?: run {
                _downloadState.value = DownloadState.Failed(title, "Empty download response")
                return@withContext KavitaResult.Error(message = "Empty download response")
            }
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
     */
    suspend fun downloadAndSaveCover(
        seriesId: Int,
        bookFilePath: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val response = kavitaApi.downloadSeriesCover(seriesId)
            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to download cover for series $seriesId: HTTP ${response.code()}")
                return@withContext Result.success(null)
            }

            val bytes = response.body()?.bytes() ?: return@withContext Result.success(null)
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

            Log.d(TAG, "Saved cover to: ${coverFile.absolutePath} (${bytes.size} bytes)")
            Result.success(coverFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cover: ${e.message}", e)
            Result.success(null)
        }
    }

    /**
     * Load a cover image from a local file path into a format
     * suitable for Coil image loading.
     */
    fun loadCoverFile(coverPath: String?): File? {
        if (coverPath == null) return null
        val file = File(coverPath)
        return if (file.exists()) file else null
    }

    /**
     * Push reading progress to Kavita.
     */
    suspend fun pushProgress(
        chapterId: Int,
        pageNum: Int,
        seriesId: Int,
        volumeId: Int,
        libraryId: Int
    ): KavitaResult<Unit> {
        return try {
            val progress = KavitaProgressDto(
                chapterId = chapterId,
                pageNum = pageNum,
                seriesId = seriesId,
                volumeId = volumeId,
                libraryId = libraryId,
                lastModifiedUtc = java.time.Instant.now().toString()
            )
            val response = kavitaApi.pushProgressDto(progress)
            if (response.isSuccessful) {
                KavitaResult.Success(Unit)
            } else {
                KavitaResult.Error(
                    message = "Progress push failed: HTTP ${response.code()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing progress: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
    }

    /**
     * Pull reading progress from Kavita for a series.
     */
    suspend fun pullProgress(
        seriesId: Int
    ): KavitaResult<List<KavitaProgress>> {
        return try {
            val response = kavitaApi.getProgressDto(seriesId)
            if (response.isSuccessful) {
                // The response is a single progress item per chapter; wrap in list
                val progress = response.body()?.let { listOfNotNull(it) } ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                KavitaResult.Success(progress as List<KavitaProgress>)
            } else {
                KavitaResult.Error(
                    message = "Progress pull failed: HTTP ${response.code()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling progress: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
    }

    /**
     * Get bookmarks for a series from Kavita.
     */
    suspend fun getBookmarks(
        seriesId: Int
    ): KavitaResult<List<KavitaBookmark>> {
        return try {
            val response = kavitaApi.getSeriesBookmarks(seriesId)
            if (response.isSuccessful) {
                val bookmarks = response.body()?.bookmarks ?: emptyList()
                // Convert KavitaBookmarkDto to KavitaBookmark
                val converted = bookmarks.map { dto ->
                    KavitaBookmark(
                        id = dto.id,
                        chapterId = dto.chapterId,
                        pageNum = dto.page,
                        seriesId = dto.seriesId,
                        volumeId = dto.volumeId,
                        libraryId = dto.libraryId
                    )
                }
                KavitaResult.Success(converted)
            } else {
                KavitaResult.Error(
                    message = "Bookmarks fetch failed: HTTP ${response.code()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching bookmarks: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
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
