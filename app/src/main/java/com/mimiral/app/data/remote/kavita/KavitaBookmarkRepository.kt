package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.mimiral.app.data.local.dao.BookmarkDao
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.local.entity.ServerEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for bidirectional bookmark sync with Kavita.
 *
 * Orchestrates:
 * - Pushing local bookmarks to Kavita (create/update)
 * - Pulling remote bookmarks from Kavita
 * - Deleting bookmarks on both sides
 * - Conflict resolution: local wins if modified_time is newer
 * - Chapter ID mapping between local and Kavita
 */
@Singleton
class KavitaBookmarkRepository @Inject constructor(
    private val client: KavitaBookmarkClient,
    private val bookmarkDao: BookmarkDao,
    private val serverDao: ServerDao
) {
    companion object {
        private const val TAG = "KavitaBookmarkSync"
    }

    /**
     * Initialize the Kavita client from the active server configuration.
     */
    private suspend fun initClient(): Boolean {
        val server = serverDao.getActiveServerByType("KAVITA") ?: run {
            Log.w(TAG, "No active Kavita server configured")
            return false
        }

        client.configure(
            url = server.url,
            key = server.apiKey,
            token = server.jwtToken,
            user = server.username,
            pass = server.password
        )
        return true
    }

    /**
     * Push a local bookmark to Kavita.
     *
     * @param bookmark The local bookmark to push
     * @param kavitaChapterId The Kavita chapter ID
     * @param kavitaSeriesId The Kavita series ID
     * @param kavitaLibraryId The Kavita library ID
     * @return True if push succeeded, false otherwise
     */
    suspend fun pushBookmark(
        bookmark: BookmarkEntity,
        kavitaChapterId: Int,
        kavitaSeriesId: Int,
        kavitaLibraryId: Int
    ): Boolean {
        if (!initClient()) return false

        val request = KavitaBookmarkRequest(
            page = bookmark.pageNumber,
            chapterId = kavitaChapterId,
            seriesId = kavitaSeriesId,
            libraryId = kavitaLibraryId
        )

        return when (val result = client.pushBookmark(request)) {
            is KavitaResult.Success -> {
                bookmarkDao.updateKavitaSync(
                    bookmarkId = bookmark.id,
                    chapterId = kavitaChapterId,
                    modifiedTime = System.currentTimeMillis()
                )
                Log.d(TAG, "Pushed bookmark ${bookmark.id} to Kavita")
                true
            }
            is KavitaResult.Error -> {
                Log.w(
                    TAG,
                    "Failed to push bookmark ${bookmark.id}: " +
                        "${result.message}"
                )
                false
            }
        }
    }

    /**
     * Pull all bookmarks for a chapter from Kavita.
     *
     * @param kavitaChapterId The Kavita chapter ID
     * @param bookId The local book ID
     * @return List of bookmarks from Kavita (may be empty)
     */
    suspend fun pullBookmarks(
        kavitaChapterId: Int,
        bookId: Int
    ): List<KavitaChapterBookmark> {
        if (!initClient()) return emptyList()

        return when (
            val result = client.pullBookmarks(kavitaChapterId)
        ) {
            is KavitaResult.Success -> {
                Log.d(
                    TAG,
                    "Pulled ${result.data.size} bookmarks for " +
                        "Kavita chapter $kavitaChapterId"
                )
                result.data
            }
            is KavitaResult.Error -> {
                Log.w(
                    TAG,
                    "Failed to pull bookmarks for chapter " +
                        "$kavitaChapterId: ${result.message}"
                )
                emptyList()
            }
        }
    }

    /**
     * Delete a bookmark on Kavita.
     *
     * @param kavitaChapterId The Kavita chapter ID
     * @param kavitaSeriesId The Kavita series ID
     * @param kavitaLibraryId The Kavita library ID
     * @param pageNumber The page number of the bookmark to delete
     * @return True if delete succeeded, false otherwise
     */
    suspend fun deleteBookmarkOnKavita(
        kavitaChapterId: Int,
        kavitaSeriesId: Int,
        kavitaLibraryId: Int,
        pageNumber: Int
    ): Boolean {
        if (!initClient()) return false

        val request = KavitaBookmarkRequest(
            page = pageNumber,
            chapterId = kavitaChapterId,
            seriesId = kavitaSeriesId,
            libraryId = kavitaLibraryId
        )

        return when (val result = client.deleteBookmark(request)) {
            is KavitaResult.Success -> {
                Log.d(
                    TAG,
                    "Deleted bookmark on Kavita: " +
                        "chapter=$kavitaChapterId, page=$pageNumber"
                )
                true
            }
            is KavitaResult.Error -> {
                Log.w(
                    TAG,
                    "Failed to delete bookmark on Kavita: " +
                        "${result.message}"
                )
                false
            }
        }
    }

    /**
     * Get all unsynced local bookmarks that need to be pushed.
     */
    suspend fun getUnsyncedBookmarks(): List<BookmarkEntity> {
        return bookmarkDao.getUnsyncedBookmarks()
    }

    /**
     * Get all bookmarks for a book that have been synced to Kavita.
     */
    suspend fun getSyncedBookmarks(bookId: Int): List<BookmarkEntity> {
        return bookmarkDao.getKavitaSyncedBookmarkIds(bookId)
    }

    /**
     * Resolve conflicts between local and remote bookmarks.
     * Strategy: local wins if modified_time is newer.
     *
     * @param local The local bookmark
     * @param remotePage The page number from Kavita
     * @param remoteTimestamp The timestamp from Kavita (if available)
     * @return True if local should be kept, False if remote wins
     */
    fun resolveConflict(
        local: BookmarkEntity,
        remotePage: Int,
        remoteTimestamp: Long? = null
    ): Boolean {
        // Local wins if it was modified more recently
        val localTime = local.modifiedTime
        val remoteTime = remoteTimestamp ?: 0L
        return localTime >= remoteTime
    }
}
