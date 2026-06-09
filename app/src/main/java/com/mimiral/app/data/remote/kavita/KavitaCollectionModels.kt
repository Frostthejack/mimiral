package com.mimiral.app.data.remote.kavita

/**
 * A Kavita collection (user-defined grouping of series).
 * GET /api/Collection returns a list of these.
 */
data class KavitaCollection(
    val id: Int,
    val title: String,
    val summary: String? = null,
    val coverImage: String? = null,
    val coverImageLocked: Boolean = false,
    val promoted: Boolean = false,
    val totalSeriesCount: Int = 0,
    val childSeriesIds: List<Int> = emptyList()
)

/**
 * Series item within a collection context.
 * GET /api/Series/series-by-collection returns paginated series.
 */
data class KavitaCollectionSeries(
    val id: Int,
    val name: String,
    val libraryId: Int,
    val pages: Int = 0,
    val format: Int = 0,
    val coverImage: String? = null,
    val pagesRead: Int = 0
)

/**
 * Request body for creating a new collection.
 * POST /api/Collection/update-series with tagId=0 creates a new collection.
 *
 * @param title The collection name
 * @param summary Optional description
 * @param tagId 0 for new collections, existing ID for updates
 * @param seriesIds Series IDs to include in the collection
 * @param promoted Whether the collection is promoted (featured)
 */
data class KavitaCollectionUpdateRequest(
    val title: String,
    val summary: String? = null,
    val tagId: Int = 0,
    val seriesIds: List<Int> = emptyList(),
    val promoted: Boolean = false
)

/**
 * Request body for adding/removing series to/from a collection.
 * POST /api/Collection/update-series
 *
 * @param tagId The collection ID
 * @param seriesIds Series IDs to add/remove
 */
data class KavitaCollectionSeriesRequest(
    val tagId: Int,
    val seriesIds: List<Int>
)

/**
 * Request body for updating collection metadata.
 * POST /api/Collection/update
 *
 * @param tagId The collection ID
 * @param title New title
 * @param summary New summary
 * @param promoted Whether promoted
 * @param coverImageLocked Whether cover image is locked
 */
data class KavitaCollectionEditRequest(
    val tagId: Int,
    val title: String,
    val summary: String? = null,
    val promoted: Boolean = false,
    val coverImageLocked: Boolean = false
)

/**
 * Paginated response for series-by-collection endpoint.
 * GET /api/Series/series-by-collection?collectionId={id}&pageNumber={n}&pageSize={s}
 */
data class KavitaCollectionSeriesPage(
    val series: List<KavitaCollectionSeries> = emptyList(),
    val totalCount: Int = 0,
    val pageNumber: Int = 1,
    val pageSize: Int = 20
)
