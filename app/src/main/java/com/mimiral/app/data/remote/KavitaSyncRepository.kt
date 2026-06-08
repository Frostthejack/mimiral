package com.mimiral.app.data.remote

import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.entity.ServerEntity
import com.mimiral.app.data.remote.kavita.KavitaReadingProgressRepository
import com.mimiral.app.di.KavitaApiClient
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of a sync operation.
 */
sealed class SyncResult {
    data class Success(val message: String = "") : SyncResult()
    data class Conflict(val localProgress: Int, val remoteProgress: Int) : SyncResult()
    data class Error(val exception: Exception) : SyncResult()
    data object NoServer : SyncResult()
    data object NoKavitaBook : SyncResult()
}

/**
 * Repository for bidirectional reading progress sync with Kavita.
 *
 * Now delegates to [KavitaReadingProgressRepository] for enhanced sync
 * with debounced push, offline queue, bookScrollId, and continue-point.
 * Maintains backward compatibility with existing reader ViewModels.
 */
@Singleton
class KavitaSyncRepository @Inject constructor(
    @KavitaApiClient private val kavitaApi: KavitaSyncApi,
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val serverDao: ServerDao,
    private val progressSyncRepository: KavitaReadingProgressRepository
) {

    private val dateFormat = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Push local reading progress to Kavita.
     * Delegates to KavitaReadingProgressRepository for enhanced sync
     * with offline queue support.
     */
    suspend fun pushProgress(
        bookId: Int,
        pageNumber: Int,
        chapterIndex: Int = 0
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            // Delegate to enhanced repository (handles offline queue)
            val success = progressSyncRepository.pushProgressForBook(
                bookId = bookId,
                chapterId = chapterIndex,
                pageNum = pageNumber
            )
            if (success) {
                SyncResult.Success("Progress pushed to Kavita")
            } else {
                // Book may not have Kavita IDs — fall back to legacy path
                pushProgressLegacy(bookId, pageNumber, chapterIndex)
            }
        } catch (e: ConnectException) {
            SyncResult.Error(Exception("Cannot connect to Kavita server"))
        } catch (e: SocketTimeoutException) {
            SyncResult.Error(Exception("Kavita server timed out"))
        } catch (e: UnknownHostException) {
            SyncResult.Error(Exception("Kavita server unreachable"))
        } catch (e: Exception) {
            SyncResult.Error(e)
        }
    }

    /**
     * Legacy push path — used when the book doesn't have Kavita IDs
     * in the new repository (backward compatibility).
     */
    private suspend fun pushProgressLegacy(
        bookId: Int,
        pageNumber: Int,
        chapterIndex: Int
    ): SyncResult {
        val book = bookDao.getBookById(bookId)
            ?: return SyncResult.Error(Exception("Book not found: $bookId"))

        val seriesId = book.kavitaSeriesId
            ?: return SyncResult.NoKavitaBook
        val libraryId = book.kavitaLibraryId
            ?: return SyncResult.NoKavitaBook

        val timestamp = dateFormat.format(Date())
        val request = KavitaProgressRequest(
            seriesId = seriesId,
            libraryId = libraryId,
            chapterId = chapterIndex,
            pageNumber = pageNumber,
            lastModified = timestamp,
            volumeId = seriesId
        )

        val response = kavitaApi.pushProgress(request)
        return if (response.isSuccessful && response.body()?.success == true) {
            val progress = readingProgressDao.getProgressForBook(bookId)
            if (progress != null) {
                readingProgressDao.saveProgress(progress.copy(kavitaSynced = true))
            }
            SyncResult.Success("Progress pushed to Kavita")
        } else {
            SyncResult.Error(
                Exception("Push failed: ${response.code()} ${response.message()}")
            )
        }
    }

    /**
     * Pull reading progress from Kavita and resolve conflicts.
     * Conflict resolution: use most recent timestamp.
     * Returns the page number that should be used after conflict resolution.
     */
    suspend fun pullProgress(bookId: Int): SyncResult = withContext(Dispatchers.IO) {
        try {
            val book = bookDao.getBookById(bookId)
                ?: return@withContext SyncResult.Error(
                    Exception("Book not found: $bookId")
                )

            val seriesId = book.kavitaSeriesId
                ?: return@withContext SyncResult.NoKavitaBook
            val libraryId = book.kavitaLibraryId
                ?: return@withContext SyncResult.NoKavitaBook

            val response = kavitaApi.pullProgress(seriesId, libraryId)
            if (response.isSuccessful) {
                val remoteData = response.body()
                if (remoteData != null) {
                    val localProgress = readingProgressDao.getProgressForBook(bookId)
                    val resolvedPage = resolveConflict(
                        localProgress?.pageNumber ?: 0,
                        localProgress?.lastReadTime ?: 0L,
                        remoteData.pageNumber,
                        parseKavitaTimestamp(remoteData.lastModified)
                    )
                    SyncResult.Success(
                        "Progress pulled from Kavita, resolved page: $resolvedPage"
                    )
                } else {
                    SyncResult.Success("No remote progress found")
                }
            } else {
                SyncResult.Error(
                    Exception(
                        "Pull failed: ${response.code()} ${response.message()}"
                    )
                )
            }
        } catch (e: ConnectException) {
            SyncResult.Error(Exception("Cannot connect to Kavita server"))
        } catch (e: SocketTimeoutException) {
            SyncResult.Error(Exception("Kavita server timed out"))
        } catch (e: UnknownHostException) {
            SyncResult.Error(Exception("Kavita server unreachable"))
        } catch (e: Exception) {
            SyncResult.Error(e)
        }
    }

    /**
     * Full bidirectional sync: pull remote progress, resolve conflicts,
     * then push the winning progress back.
     * Returns the resolved page number.
     */
    suspend fun syncProgress(
        bookId: Int,
        localPageNumber: Int,
        localChapterIndex: Int = 0,
        localTimestamp: Long = System.currentTimeMillis()
    ): SyncResult = withContext(Dispatchers.IO) {
        val book = bookDao.getBookById(bookId)
            ?: return@withContext SyncResult.Error(
                Exception("Book not found: $bookId")
            )

        val seriesId = book.kavitaSeriesId
            ?: return@withContext SyncResult.NoKavitaBook
        val libraryId = book.kavitaLibraryId
            ?: return@withContext SyncResult.NoKavitaBook

        try {
            // Step 1: Pull remote progress
            val pullResponse = kavitaApi.pullProgress(seriesId, libraryId)
            if (!pullResponse.isSuccessful) {
                return@withContext SyncResult.Error(
                    Exception(
                        "Pull failed: ${pullResponse.code()} ${pullResponse.message()}"
                    )
                )
            }

            val remoteData = pullResponse.body()
            val resolvedPage: Int

            if (remoteData != null && remoteData.lastModified != null) {
                // Step 2: Conflict resolution — most recent timestamp wins
                val remoteTimestamp = parseKavitaTimestamp(remoteData.lastModified)
                resolvedPage = if (remoteTimestamp > localTimestamp) {
                    remoteData.pageNumber
                } else {
                    localPageNumber
                }
            } else {
                resolvedPage = localPageNumber
            }

            // Step 3: Push resolved progress via enhanced repository
            val pushSuccess = progressSyncRepository.pushProgressForBook(
                bookId = bookId,
                chapterId = localChapterIndex,
                pageNum = resolvedPage
            )
            if (pushSuccess) {
                SyncResult.Success("Sync complete, resolved page: $resolvedPage")
            } else {
                // Fall back to legacy push
                val result = pushProgressLegacy(bookId, resolvedPage, localChapterIndex)
                result
            }
        } catch (e: ConnectException) {
            SyncResult.Error(Exception("Cannot connect to Kavita server"))
        } catch (e: SocketTimeoutException) {
            SyncResult.Error(Exception("Kavita server timed out"))
        } catch (e: UnknownHostException) {
            SyncResult.Error(Exception("Kavita server unreachable"))
        } catch (e: Exception) {
            SyncResult.Error(e)
        }
    }

    /**
     * Resolve conflict between local and remote progress.
     * Uses most recent timestamp to determine winner.
     * Returns the winning page number.
     */
    private fun resolveConflict(
        localPage: Int,
        localTimestamp: Long,
        remotePage: Int,
        remoteTimestamp: Long
    ): Int {
        return if (remoteTimestamp > localTimestamp) remotePage else localPage
    }

    /**
     * Parse Kavita ISO-8601 timestamp to epoch millis.
     * Returns 0 if parsing fails.
     */
    private fun parseKavitaTimestamp(timestamp: String?): Long {
        if (timestamp == null) return 0L
        return try {
            dateFormat.parse(timestamp)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Get the active Kavita server entity.
     */
    suspend fun getKavitaServer(): ServerEntity? =
        withContext(Dispatchers.IO) { serverDao.getActiveServerByType("KAVITA") }

    /**
     * Check if an active Kavita server is configured.
     * Use this before attempting sync to avoid unnecessary network calls.
     */
    suspend fun hasActiveServer(): Boolean =
        withContext(Dispatchers.IO) { serverDao.getActiveServerByType("KAVITA") != null }

    // ── Pass-through to enhanced repository ──

    /**
     * Get the continue-reading point from Kavita.
     * Delegates to KavitaReadingProgressRepository.
     */
    suspend fun fetchContinuePoint() = progressSyncRepository.fetchContinuePoint()

    /**
     * Flush pending offline operations.
     * Delegates to KavitaReadingProgressRepository.
     */
    suspend fun flushPendingOperations() = progressSyncRepository.flushPendingOperations()

    /**
     * Perform full sync on app start.
     * Delegates to KavitaReadingProgressRepository.
     */
    suspend fun fullSyncOnStartup() = progressSyncRepository.fullSyncOnStartup()

    /**
     * Get the count of pending operations.
     * Delegates to KavitaReadingProgressRepository.
     */
    suspend fun pendingOperationCount() = progressSyncRepository.pendingOperationCount()

    /**
     * Access the enhanced repository directly for debounced page-turn sync.
     */
    val progressSync: KavitaReadingProgressRepository get() = progressSyncRepository
}
