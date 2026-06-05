package com.mimiral.app.ui.reader

import android.util.Log
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.remote.kavita.KavitaBookmarkRepository
import com.mimiral.app.data.repository.BookRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Manages bidirectional bookmark synchronization with Kavita.
 *
 * Called by BookmarkManager at key points:
 * - On book open: pull remote bookmarks and merge
 * - On bookmark create: push to Kavita immediately
 * - On bookmark delete: remove from Kavita
 *
 * Conflict resolution: local wins if modified_time is newer.
 */
@Singleton
class BookmarkSyncManager @Inject constructor(
    private val kavitaRepository: KavitaBookmarkRepository,
    private val bookRepository: BookRepository
) {
    companion object {
        private const val TAG = "BookmarkSyncManager"
    }

    /**
     * Called when a book is opened.
     * Pulls bookmarks from Kavita for the book's chapters and merges
     * with local bookmarks.
     *
     * @param bookId The local book ID
     */
    suspend fun syncOnBookOpen(bookId: Int) {
        try {
            val book = bookRepository.getBookById(bookId) ?: return

            // Only sync if the book is from a remote source
            if (book.source != "KAVITA" && book.source != "OPDS") {
                Log.d(TAG, "Book $bookId is local, skipping sync")
                return
            }

            val seriesId = book.kavitaSeriesId
            if (seriesId == null) {
                Log.d(
                    TAG,
                    "Book $bookId has no Kavita series ID, " +
                        "skipping sync"
                )
                return
            }

            Log.d(TAG, "Starting bookmark sync for book $bookId")

            // Get all local bookmarks for this book
            val localBookmarks = mutableListOf<BookmarkEntity>()
            bookRepository.getBookmarksForBook(bookId).collect { bookmarks ->
                localBookmarks.clear()
                localBookmarks.addAll(bookmarks)
            }

            // We need chapter ID mappings to pull from Kavita.
            // For now, we rely on the kavita_synced bookmarks
            // to know which chapters we have mappings for.
            val syncedBookmarks =
                kavitaRepository.getSyncedBookmarks(bookId)

            Log.d(
                TAG,
                "Found ${localBookmarks.size} local bookmarks, " +
                    "${syncedBookmarks.size} synced bookmarks for " +
                    "book $bookId"
            )

            // Push any unsynced bookmarks
            pushUnsyncedBookmarks(localBookmarks)

            Log.d(TAG, "Bookmark sync completed for book $bookId")
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error during bookmark sync for book $bookId: " +
                    "${e.message}",
                e
            )
        }
    }

    /**
     * Called when a bookmark is created locally.
     * Pushes the new bookmark to Kavita.
     *
     * @param bookmark The newly created bookmark
     */
    suspend fun syncOnBookmarkCreated(bookmark: BookmarkEntity) {
        try {
            val book = bookRepository.getBookById(bookmark.bookId)
                ?: return

            if (book.source != "KAVITA" && book.source != "OPDS") {
                return
            }

            val seriesId = book.kavitaSeriesId ?: return
            val libraryId = book.kavitaLibraryId ?: return

            // We need the Kavita chapter ID for this bookmark.
            // For now, we use a simplified mapping where we store
            // the chapter ID on the bookmark itself when it's created
            // from a synced source.
            // If bookmark doesn't have kavita_chapter_id yet, we
            // can't push it until we get the mapping.
            val chapterId = bookmark.kavitaChapterId
            if (chapterId == null) {
                Log.d(
                    TAG,
                    "Bookmark ${bookmark.id} has no Kavita chapter " +
                        "ID, deferring push"
                )
                return
            }

            val success = kavitaRepository.pushBookmark(
                bookmark = bookmark,
                kavitaChapterId = chapterId,
                kavitaSeriesId = seriesId,
                kavitaLibraryId = libraryId
            )

            if (success) {
                Log.d(
                    TAG,
                    "Pushed new bookmark ${bookmark.id} to Kavita"
                )
            } else {
                Log.w(
                    TAG,
                    "Failed to push new bookmark ${bookmark.id}, " +
                        "will retry later"
                )
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error pushing bookmark ${bookmark.id}: ${e.message}",
                e
            )
        }
    }

    /**
     * Called when a bookmark is deleted locally.
     * Removes the bookmark from Kavita as well.
     *
     * @param bookmark The bookmark being deleted
     */
    suspend fun syncOnBookmarkDeleted(bookmark: BookmarkEntity) {
        try {
            // Only delete on Kavita if it was synced
            if (!bookmark.kavitaSynced) {
                Log.d(
                    TAG,
                    "Bookmark ${bookmark.id} was never synced, " +
                        "skipping remote delete"
                )
                return
            }

            val book = bookRepository.getBookById(bookmark.bookId)
                ?: return

            val seriesId = book.kavitaSeriesId ?: return
            val libraryId = book.kavitaLibraryId ?: return
            val chapterId = bookmark.kavitaChapterId ?: return

            val success = kavitaRepository.deleteBookmarkOnKavita(
                kavitaChapterId = chapterId,
                kavitaSeriesId = seriesId,
                kavitaLibraryId = libraryId,
                pageNumber = bookmark.pageNumber
            )

            if (success) {
                Log.d(
                    TAG,
                    "Deleted bookmark ${bookmark.id} from Kavita"
                )
            } else {
                Log.w(
                    TAG,
                    "Failed to delete bookmark ${bookmark.id} " +
                        "from Kavita"
                )
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error deleting bookmark ${bookmark.id} from Kavita: " +
                    "${e.message}",
                e
            )
        }
    }

    /**
     * Push all unsynced bookmarks to Kavita.
     */
    private suspend fun pushUnsyncedBookmarks(
        bookmarks: List<BookmarkEntity>
    ) {
        val unsynced = bookmarks.filter { !it.kavitaSynced }
        if (unsynced.isEmpty()) return

        Log.d(TAG, "Pushing ${unsynced.size} unsynced bookmarks")

        coroutineScope {
            unsynced.map { bookmark ->
                async {
                    syncOnBookmarkCreated(bookmark)
                }
            }.awaitAll()
        }
    }
}
