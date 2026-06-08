package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.mimiral.app.data.local.dao.BookmarkDao
import com.mimiral.app.data.local.entity.BookmarkEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for bidirectional bookmark sync with Kavita.
 *
 * Uses the Retrofit-based [KavitaApi] for all network operations.
 * Orchestrates:
 * - Creating/removing bookmarks via POST /api/Reader/bookmark and /unbookmark
 * - Pulling bookmarks per chapter (chapter-bookmarks)
 * - Fetching all bookmarks (all-bookmarks) grouped by series/volume/chapter
 * - Series-specific bookmarks (series-bookmarks)
 * - Bookmark export (Download/bookmarks)
 * - Sync on app start: fetch all, merge (server source of truth), POST local changes
 * - Conflict resolution: server is source of truth
 */
@Singleton
class KavitaBookmarkRepository @Inject constructor(
    private val kavitaApi: KavitaApi,
    private val bookmarkDao: BookmarkDao
) {
    companion object {
        private const val TAG = "KavitaBookmarkSync"
    }

    // ── Bookmark CRUD via Retrofit ──

    /**
     * Create a bookmark on Kavita.
     * POST /api/Reader/bookmark
     *
     * @param page Page number (0-based)
     * @param chapterId Kavita chapter ID
     * @param seriesId Kavita series ID
     * @param libraryId Kavita library ID
     * @return True if created successfully
     */
    suspend fun createBookmark(
        page: Int,
        chapterId: Int,
        seriesId: Int,
        libraryId: Int
    ): Boolean {
        return try {
            val request = KavitaBookmarkRequest(
                page = page,
                chapterId = chapterId,
                seriesId = seriesId,
                libraryId = libraryId
            )
            val response = kavitaApi.createBookmark(request)
            if (response.isSuccessful) {
                Log.d(TAG, "Created bookmark: chapter=$chapterId, page=$page")
                true
            } else {
                Log.w(TAG, "Create bookmark failed: ${response.code()} ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating bookmark: ${e.message}", e)
            false
        }
    }

    /**
     * Remove a bookmark on Kavita.
     * POST /api/Reader/unbookmark
     *
     * @param page Page number (0-based)
     * @param chapterId Kavita chapter ID
     * @param seriesId Kavita series ID
     * @param libraryId Kavita library ID
     * @return True if removed successfully
     */
    suspend fun removeBookmark(
        page: Int,
        chapterId: Int,
        seriesId: Int,
        libraryId: Int
    ): Boolean {
        return try {
            val request = KavitaUnbookmarkRequest(
                page = page,
                chapterId = chapterId,
                seriesId = seriesId,
                libraryId = libraryId
            )
            val response = kavitaApi.removeBookmark(request)
            if (response.isSuccessful) {
                Log.d(TAG, "Removed bookmark: chapter=$chapterId, page=$page")
                true
            } else {
                Log.w(TAG, "Remove bookmark failed: ${response.code()} ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing bookmark: ${e.message}", e)
            false
        }
    }

    // ── Bookmark retrieval ──

    /**
     * Get all bookmarks for a chapter.
     * GET /api/Reader/chapter-bookmarks?chapterId={chapterId}
     */
    suspend fun getChapterBookmarks(chapterId: Int): List<KavitaChapterBookmark> {
        return try {
            val response = kavitaApi.getChapterBookmarks(chapterId)
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                Log.w(TAG, "Get chapter bookmarks failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chapter bookmarks: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get all bookmarks for the current user.
     * GET /api/Reader/all-bookmarks
     * Returns bookmarks with series/volume/chapter context for grouped display.
     */
    suspend fun getAllBookmarks(): List<KavitaBookmarkDto> {
        return try {
            val response = kavitaApi.getAllBookmarks()
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                Log.w(TAG, "Get all bookmarks failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all bookmarks: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get bookmarks for a specific series.
     * GET /api/Reader/series-bookmarks?seriesId={seriesId}
     */
    suspend fun getSeriesBookmarks(seriesId: Int): KavitaSeriesBookmarksDto? {
        return try {
            val response = kavitaApi.getSeriesBookmarks(seriesId)
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.w(TAG, "Get series bookmarks failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting series bookmarks: ${e.message}", e)
            null
        }
    }

    /**
     * Group bookmarks by series > volume > chapter for hierarchical display.
     */
    fun groupBookmarks(bookmarks: List<KavitaBookmarkDto>): List<KavitaBookmarkGroup> {
        val seriesMap = mutableMapOf<Int, MutableList<KavitaBookmarkDto>>()
        for (bm in bookmarks) {
            seriesMap.getOrPut(bm.seriesId) { mutableListOf() }.add(bm)
        }

        return seriesMap.map { (seriesId, seriesBookmarks) ->
            val volumeMap = mutableMapOf<Int, MutableList<KavitaBookmarkDto>>()
            for (bm in seriesBookmarks) {
                volumeMap.getOrPut(bm.volumeId) { mutableListOf() }.add(bm)
            }

            KavitaBookmarkGroup(
                seriesId = seriesId,
                seriesName = seriesBookmarks.firstOrNull()?.seriesName ?: "Series $seriesId",
                volumes = volumeMap.map { (volumeId, volumeBookmarks) ->
                    val chapterMap = mutableMapOf<Int, MutableList<KavitaBookmarkDto>>()
                    for (bm in volumeBookmarks) {
                        chapterMap.getOrPut(bm.chapterId) { mutableListOf() }.add(bm)
                    }

                    KavitaVolumeBookmarkGroup(
                        volumeId = volumeId,
                        volumeName = volumeBookmarks.firstOrNull()?.volumeName
                            ?: "Volume $volumeId",
                        chapters = chapterMap.map { (chapterId, chapterBookmarks) ->
                            KavitaChapterBookmarkGroup(
                                chapterId = chapterId,
                                chapterName = chapterBookmarks.firstOrNull()?.chapterName
                                    ?: "Chapter $chapterId",
                                bookmarks = chapterBookmarks.sortedBy { it.page }
                            )
                        }.sortedBy { it.chapterId }
                    )
                }.sortedBy { it.volumeId }
            )
        }.sortedBy { it.seriesName }
    }

    // ── Export ──

    /**
     * Export all bookmarks.
     * GET /api/Download/bookmarks
     * Returns the raw response body for saving to a file.
     *
     * @return Pair of (byteArray, filename) on success, null on failure
     */
    suspend fun exportBookmarks(): Pair<ByteArray, String>? {
        return try {
            val response = kavitaApi.exportBookmarks()
            if (response.isSuccessful) {
                val body = response.body() ?: return null
                val bytes = withContext(Dispatchers.IO) { body.bytes() }
                val filename = response.headers()["Content-Disposition"]
                    ?.let { disposition ->
                        val match = Regex("filename=\"?([^\"]+)\"?").find(disposition)
                        match?.groupValues?.get(1)
                    } ?: "bookmarks.json"
                bytes to filename
            } else {
                Log.w(TAG, "Export bookmarks failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting bookmarks: ${e.message}", e)
            null
        }
    }

    // ── Sync on app start ──

    /**
     * Perform a full sync at app startup.
     * 1. Fetch all bookmarks from server
     * 2. Get all local bookmarks
     * 3. Server is source of truth — add missing local bookmarks to server,
     *    and mark local bookmarks as synced where they match server
     * 4. Push any local-only (unsynced) bookmarks immediately
     *
     * @param localBookmarks All local bookmarks to consider for sync
     * @param resolveKavitaIds Function to resolve (seriesId, libraryId, chapterId) for a local bookmark
     */
    suspend fun syncOnStartup(
        localBookmarks: List<BookmarkEntity>,
        resolveKavitaIds: (BookmarkEntity) -> Triple<Int, Int, Int>?
    ) {
        try {
            val serverBookmarks = getAllBookmarks()
            Log.d(
                TAG,
                "Startup sync: ${serverBookmarks.size} server, " +
                    "${localBookmarks.size} local"
            )

            // Mark local bookmarks as synced where they match server bookmarks
            for (local in localBookmarks) {
                val ids = resolveKavitaIds(local) ?: continue
                val (seriesId, libraryId, chapterId) = ids

                val matchesServer = serverBookmarks.any { serverBm ->
                    serverBm.seriesId == seriesId &&
                        serverBm.chapterId == chapterId &&
                        serverBm.page == local.pageNumber
                }

                if (matchesServer && !local.kavitaSynced) {
                    bookmarkDao.updateKavitaSync(
                        bookmarkId = local.id,
                        chapterId = chapterId,
                        modifiedTime = System.currentTimeMillis()
                    )
                    Log.d(TAG, "Marked local bookmark ${local.id} as synced (matched server)")
                }
            }

            // Push unsynced local bookmarks
            val unsynced = localBookmarks.filter { !it.kavitaSynced }
            for (bookmark in unsynced) {
                val ids = resolveKavitaIds(bookmark) ?: continue
                val (seriesId, libraryId, chapterId) = ids
                val success = createBookmark(
                    page = bookmark.pageNumber,
                    chapterId = chapterId,
                    seriesId = seriesId,
                    libraryId = libraryId
                )
                if (success) {
                    bookmarkDao.updateKavitaSync(
                        bookmarkId = bookmark.id,
                        chapterId = chapterId,
                        modifiedTime = System.currentTimeMillis()
                    )
                }
            }
            Log.d(TAG, "Startup sync complete: pushed ${unsynced.size} unsynced bookmarks")
        } catch (e: Exception) {
            Log.e(TAG, "Error during startup sync: ${e.message}", e)
        }
    }

    // ── Local database helpers ──

    /**
     * Get all unsynced local bookmarks.
     */
    suspend fun getUnsyncedBookmarks(): List<BookmarkEntity> {
        return bookmarkDao.getUnsyncedBookmarks()
    }

    /**
     * Get all synced bookmarks for a book.
     */
    suspend fun getSyncedBookmarks(bookId: Int): List<BookmarkEntity> {
        return bookmarkDao.getKavitaSyncedBookmarkIds(bookId)
    }

    /**
     * Push a local bookmark to Kavita and mark it as synced.
     */
    suspend fun pushBookmark(
        bookmark: BookmarkEntity,
        kavitaChapterId: Int,
        kavitaSeriesId: Int,
        kavitaLibraryId: Int
    ): Boolean {
        val success = createBookmark(
            page = bookmark.pageNumber,
            chapterId = kavitaChapterId,
            seriesId = kavitaSeriesId,
            libraryId = kavitaLibraryId
        )
        if (success) {
            bookmarkDao.updateKavitaSync(
                bookmarkId = bookmark.id,
                chapterId = kavitaChapterId,
                modifiedTime = System.currentTimeMillis()
            )
            Log.d(TAG, "Pushed bookmark ${bookmark.id} to Kavita")
        }
        return success
    }

    /**
     * Delete a bookmark on Kavita.
     */
    suspend fun deleteBookmarkOnKavita(
        kavitaChapterId: Int,
        kavitaSeriesId: Int,
        kavitaLibraryId: Int,
        pageNumber: Int
    ): Boolean {
        return removeBookmark(
            page = pageNumber,
            chapterId = kavitaChapterId,
            seriesId = kavitaSeriesId,
            libraryId = kavitaLibraryId
        )
    }
}
