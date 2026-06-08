package com.mimiral.app.data.remote.kavita

/**
 * Data models for Kavita Want To Read feature.
 *
 * API endpoints:
 * - POST /api/want-to-read/add-series       — add series to Want To Read
 * - POST /api/want-to-read/remove-series    — remove series from Want To Read
 * - GET  /api/want-to-read/v2               — get Want To Read list with filtering + pagination
 * - GET  /api/Opds/{apiKey}/want-to-read    — OPDS feed route
 * - POST /api/Server/cleanup-want-to-read   — auto-cleanup (remove fully-read series)
 */

/**
 * Request body for adding a series to Want To Read.
 */
data class KavitaWantToReadAddRequest(
    val seriesId: Int
)

/**
 * Request body for removing a series from Want To Read.
 */
data class KavitaWantToReadRemoveRequest(
    val seriesId: Int
)

/**
 * Filter parameters for the Want To Read v2 endpoint.
 */
data class KavitaWantToReadFilter(
    val searchQuery: String? = null,
    val libraryId: Int? = null,
    val sortBy: KavitaWantToReadSort? = null,
    val sortDirection: KavitaSortDirection = KavitaSortDirection.Ascending
)

/**
 * Sort options for Want To Read list.
 */
enum class KavitaWantToReadSort(val value: String) {
    SortName("SortName"),
    CreatedDate("CreatedDate"),
    LastChapterAdded("LastChapterAdded"),
    Pages("Pages")
}

/**
 * Sort direction.
 */
enum class KavitaSortDirection(val value: String) {
    Ascending("Asc"),
    Descending("Desc")
}

/**
 * A series in the Want To Read list.
 * Returned by GET /api/want-to-read/v2.
 */
data class KavitaWantToReadSeries(
    val id: Int,
    val name: String,
    val libraryId: Int,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val format: Int = 0,
    val coverImage: String? = null,
    val localizedName: String? = null,
    val sortName: String? = null,
    val created: String? = null,
    val lastChapterAdded: String? = null
) {
    /** Read progress as a 0..1 fraction. */
    val progressFraction: Float
        get() = if (pages > 0) pagesRead.toFloat() / pages else 0f

    /** Whether the series is fully read. */
    val isFullyRead: Boolean
        get() = pages > 0 && pagesRead >= pages
}

/**
 * Paginated response from GET /api/want-to-read/v2.
 */
data class KavitaWantToReadResponse(
    val series: List<KavitaWantToReadSeries> = emptyList(),
    val totalCount: Int = 0,
    val pageNumber: Int = 0,
    val pageSize: Int = 0
)
