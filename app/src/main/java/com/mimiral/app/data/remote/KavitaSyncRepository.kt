package com.mimiral.app.data.remote

import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.entity.ServerEntity
import com.mimiral.app.di.KavitaApiClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

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
 * Handles push/pull logic with conflict resolution (most recent timestamp wins).
 */
@Singleton
class KavitaSyncRepository @Inject constructor(
    @KavitaApiClient private val kavitaApi: KavitaSyncApi,
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val serverDao: ServerDao
) {

    private val dateFormat = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Push local reading progress to Kavita.
     * Reads the book's kavitaSeriesId and kavitaLibraryId from the local DB.
     */
    suspend fun pushProgress(
        bookId: Int,
        pageNumber: Int,
        chapterIndex: Int = 0
    ): SyncResult {
        return try {
            val book = bookDao.getBookById(bookId)
                ?: return SyncResult.Error(
                    Exception("Book not found: $bookId")
                )

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
            if (response.isSuccessful && response.body()?.success == true) {
                // Mark local progress as synced
                val progress = readingProgressDao.getProgressForBook(bookId)
                if (progress != null) {
                    readingProgressDao.saveProgress(
                        progress.copy(kavitaSynced = true)
                    )
                }
                SyncResult.Success("Progress pushed to Kavita")
            } else {
                SyncResult.Error(
                    Exception(
                        "Push failed: ${response.code()} ${response.message()}"
                    )
                )
            }
        } catch (e: Exception) {
            SyncResult.Error(e)
        }
    }

    /**
     * Pull reading progress from Kavita and resolve conflicts.
     * Conflict resolution: use most recent timestamp.
     * Returns the page number that should be used after conflict resolution.
     */
    suspend fun pullProgress(bookId: Int): SyncResult {
        return try {
            val book = bookDao.getBookById(bookId)
                ?: return SyncResult.Error(
                    Exception("Book not found: $bookId")
                )

            val seriesId = book.kavitaSeriesId
                ?: return SyncResult.NoKavitaBook
            val libraryId = book.kavitaLibraryId
                ?: return SyncResult.NoKavitaBook

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
    ): SyncResult {
        val book = bookDao.getBookById(bookId)
            ?: return SyncResult.Error(
                Exception("Book not found: $bookId")
            )

        val seriesId = book.kavitaSeriesId
            ?: return SyncResult.NoKavitaBook
        val libraryId = book.kavitaLibraryId
            ?: return SyncResult.NoKavitaBook

        return try {
            // Step 1: Pull remote progress
            val pullResponse = kavitaApi.pullProgress(seriesId, libraryId)
            if (!pullResponse.isSuccessful) {
                return SyncResult.Error(
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

            // Step 3: Push resolved progress to Kavita
            val timestamp = dateFormat.format(Date())
            val pushRequest = KavitaProgressRequest(
                seriesId = seriesId,
                libraryId = libraryId,
                chapterId = localChapterIndex,
                pageNumber = resolvedPage,
                lastModified = timestamp,
                volumeId = seriesId
            )
            val pushResponse = kavitaApi.pushProgress(pushRequest)
            if (pushResponse.isSuccessful && pushResponse.body()?.success == true) {
                // Mark local progress as synced
                val progress = readingProgressDao.getProgressForBook(bookId)
                if (progress != null) {
                    readingProgressDao.saveProgress(
                        progress.copy(kavitaSynced = true)
                    )
                }
                SyncResult.Success(
                    "Sync complete, resolved page: $resolvedPage"
                )
            } else {
                SyncResult.Error(
                    Exception(
                        "Push failed: ${pushResponse.code()} ${pushResponse.message()}"
                    )
                )
            }
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
    suspend fun getKavitaServer(): ServerEntity? {
        return serverDao.getActiveServerByType("KAVITA")
    }
}
