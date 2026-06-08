package com.mimiral.app.data.remote.kavita

/**
 * Data models for Kavita Reading List feature.
 *
 * API endpoints:
 * - GET  /api/ReadingList/lists               — browse all reading lists
 * - GET  /api/ReadingList/items?readingListId  — list items with read/unread + progress
 * - GET  /api/ReadingList/next-chapter         — next chapter for ordered reading
 * - POST /api/ReadingList/create               — create a new reading list
 * - POST /api/ReadingList/update-by-series     — add all chapters from a series
 * - POST /api/ReadingList/update-by-multiple   — add multiple items at once
 * - POST /api/ReadingList/update-position      — reorder item position in list
 * - POST /api/ReadingList/remove-read          — remove read items from list
 * - POST /api/ReadingList/update               — update reading list name/summary
 * - POST /api/ReadingList/delete               — delete a reading list
 * - GET  /api/Opds/{apiKey}/reading-list       — OPDS feed route
 */

// ==================== Reading List ====================

/**
 * A Kavita reading list.
 * Returned by GET /api/ReadingList/lists.
 *
 * @param id The reading list ID
 * @param name The reading list name
 * @param summary Optional description/summary
 * @param readingListCount Number of items in the list
 * @param promoted Whether the list is promoted (admin feature)
 * @param created ISO-8601 creation timestamp
 * @param lastModified ISO-8601 last modification timestamp
 */
data class KavitaReadingList(
    val id: Int,
    val name: String,
    val summary: String? = null,
    val readingListCount: Int = 0,
    val promoted: Boolean = false,
    val created: String? = null,
    val lastModified: String? = null
)

/**
 * Request body for creating a reading list.
 * POST /api/ReadingList/create
 *
 * @param name The name for the new reading list
 * @param summary Optional description/summary
 */
data class KavitaReadingListCreateRequest(
    val name: String,
    val summary: String? = null
)

/**
 * Request body for updating a reading list.
 * POST /api/ReadingList/update
 *
 * @param id The reading list ID to update
 * @param name The new name
 * @param summary The new summary
 * @param promoted Whether the list is promoted
 */
data class KavitaReadingListUpdateRequest(
    val id: Int,
    val name: String,
    val summary: String? = null,
    val promoted: Boolean = false
)

/**
 * Request body for deleting a reading list.
 * POST /api/ReadingList/delete
 *
 * @param id The reading list ID to delete
 */
data class KavitaReadingListDeleteRequest(
    val id: Int
)

// ==================== Reading List Items ====================

/**
 * An item (chapter/volume) in a Kavita reading list.
 * Returned by GET /api/ReadingList/items.
 *
 * @param id The reading list item ID
 * @param readingListId The parent reading list ID
 * @param seriesId The series ID this item belongs to
 * @param seriesName The series name for display
 * @param volumeId The volume ID (0 if chapter-level)
 * @param chapterId The chapter ID (0 if volume-level)
 * @param order The position/order in the reading list
 * @param title Display title (volume/chapter name)
 * @param pages Total pages in this item
 * @param pagesRead Pages the user has read
 * @param isRead Whether the user has fully read this item
 * @param libraryId The library ID
 * @param coverImage Cover image URL
 */
data class KavitaReadingListItem(
    val id: Int,
    val readingListId: Int,
    val seriesId: Int,
    val seriesName: String? = null,
    val volumeId: Int = 0,
    val chapterId: Int = 0,
    val order: Int = 0,
    val title: String? = null,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val isRead: Boolean = false,
    val libraryId: Int = 0,
    val coverImage: String? = null
) {
    /** Read progress as a 0..1 fraction. */
    val progressFraction: Float
        get() = if (pages > 0) pagesRead.toFloat() / pages else 0f
}

/**
 * Response from GET /api/ReadingList/items.
 * Contains paginated reading list items.
 *
 * @param items List of reading list items
 * @param totalCount Total items across all pages
 * @param pageNumber Current page (0-based)
 * @param pageSize Items per page
 */
data class KavitaReadingListItemsResponse(
    val items: List<KavitaReadingListItem> = emptyList(),
    val totalCount: Int = 0,
    val pageNumber: Int = 0,
    val pageSize: Int = 0
)

// ==================== Add/Update Items ====================

/**
 * Request body for adding a series to a reading list.
 * POST /api/ReadingList/update-by-series
 *
 * @param readingListId The reading list to add to
 * @param seriesId The series whose chapters to add
 */
data class KavitaReadingListUpdateBySeriesRequest(
    val readingListId: Int,
    val seriesId: Int
)

/**
 * Request body for adding multiple items to a reading list.
 * POST /api/ReadingList/update-by-multiple
 *
 * @param readingListId The reading list to add to
 * @param seriesIds Series IDs to add
 * @param chapterIds Chapter IDs to add
 * @param volumeIds Volume IDs to add
 */
data class KavitaReadingListUpdateByMultipleRequest(
    val readingListId: Int,
    val seriesIds: List<Int> = emptyList(),
    val chapterIds: List<Int> = emptyList(),
    val volumeIds: List<Int> = emptyList()
)

/**
 * Request body for reordering an item in a reading list.
 * POST /api/ReadingList/update-position
 *
 * @param readingListId The reading list
 * @param readingListItemId The item to move
 * @param fromPosition Current position
 * @param toPosition New position
 */
data class KavitaReadingListUpdatePositionRequest(
    val readingListId: Int,
    val readingListItemId: Int,
    val fromPosition: Int,
    val toPosition: Int
)

/**
 * Request body for removing read items from a reading list.
 * POST /api/ReadingList/remove-read
 *
 * @param readingListId The reading list to clean
 */
data class KavitaReadingListRemoveReadRequest(
    val readingListId: Int
)

// ==================== Next Chapter ====================

/**
 * Response from GET /api/ReadingList/next-chapter.
 * Provides the next chapter to read in reading-list order.
 *
 * @param seriesId The series ID of the next chapter
 * @param volumeId The volume ID
 * @param chapterId The chapter ID
 * @param title Display title
 * @param pages Total pages
 */
data class KavitaReadingListNextChapter(
    val seriesId: Int,
    val volumeId: Int = 0,
    val chapterId: Int,
    val title: String? = null,
    val pages: Int = 0
)
