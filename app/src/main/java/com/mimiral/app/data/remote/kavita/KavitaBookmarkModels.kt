package com.mimiral.app.data.remote.kavita

import com.google.gson.annotations.SerializedName

// ── Bookmark request / response models for Kavita Reader API ──

/**
 * Request body for creating a bookmark.
 * POST /api/Reader/bookmark
 *
 * @param page The page number (0-based) within the chapter
 * @param chapterId The Kavita chapter ID
 * @param seriesId The Kavita series ID
 * @param libraryId The Kavita library ID
 */
data class KavitaBookmarkRequest(
    @SerializedName("page") val page: Int,
    @SerializedName("chapterId") val chapterId: Int,
    @SerializedName("seriesId") val seriesId: Int,
    @SerializedName("libraryId") val libraryId: Int
)

/**
 * Request body for removing a bookmark.
 * POST /api/Reader/unbookmark
 *
 * @param page The page number (0-based) within the chapter
 * @param chapterId The Kavita chapter ID
 * @param seriesId The Kavita series ID
 * @param libraryId The Kavita library ID
 */
data class KavitaUnbookmarkRequest(
    @SerializedName("page") val page: Int,
    @SerializedName("chapterId") val chapterId: Int,
    @SerializedName("seriesId") val seriesId: Int,
    @SerializedName("libraryId") val libraryId: Int
)

/**
 * Response from GET /api/Reader/chapter-bookmarks
 * Contains all bookmarks for a single chapter.
 *
 * @param chapterId The Kavita chapter ID
 * @param page The page number (0-based)
 * @param seriesId The Kavita series ID
 */
data class KavitaChapterBookmark(
    @SerializedName("chapterId") val chapterId: Int,
    @SerializedName("page") val page: Int,
    @SerializedName("seriesId") val seriesId: Int
)

/**
 * Response from GET /api/Reader/all-bookmarks
 * A single bookmark with full context for grouped display.
 *
 * @param id Bookmark ID
 * @param page Page number (0-based)
 * @param chapterId Chapter ID
 * @param seriesId Series ID
 * @param volumeId Volume ID
 * @param libraryId Library ID
 * @param seriesName Series name for grouping
 * @param volumeName Volume name (nullable for single-volume series)
 * @param chapterName Chapter title/range
 * @param created Timestamp when bookmark was created
 * @param lastModified Last modification timestamp
 */
data class KavitaBookmarkDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("page") val page: Int = 0,
    @SerializedName("chapterId") val chapterId: Int = 0,
    @SerializedName("seriesId") val seriesId: Int = 0,
    @SerializedName("volumeId") val volumeId: Int = 0,
    @SerializedName("libraryId") val libraryId: Int = 0,
    @SerializedName("seriesName") val seriesName: String? = null,
    @SerializedName("volumeName") val volumeName: String? = null,
    @SerializedName("chapterName") val chapterName: String? = null,
    @SerializedName("created") val created: String? = null,
    @SerializedName("lastModified") val lastModified: String? = null
)

/**
 * Grouped bookmarks for display in the bookmark viewer.
 * Bookmarks are organized by series > volume > chapter.
 */
data class KavitaBookmarkGroup(
    val seriesId: Int,
    val seriesName: String,
    val volumes: List<KavitaVolumeBookmarkGroup>
)

data class KavitaVolumeBookmarkGroup(
    val volumeId: Int,
    val volumeName: String,
    val chapters: List<KavitaChapterBookmarkGroup>
)

data class KavitaChapterBookmarkGroup(
    val chapterId: Int,
    val chapterName: String,
    val bookmarks: List<KavitaBookmarkDto>
)

/**
 * Response from GET /api/Reader/series-bookmarks
 * Bookmarks for a specific series.
 */
data class KavitaSeriesBookmarksDto(
    @SerializedName("seriesId") val seriesId: Int = 0,
    @SerializedName("seriesName") val seriesName: String = "",
    @SerializedName("bookmarks") val bookmarks: List<KavitaBookmarkDto> = emptyList()
)

/**
 * Bookmark thumbnail info.
 * GET /api/Image/bookmark returns the bookmark page as an image.
 * Used for thumbnail display — construct URL as:
 *   {baseUrl}/api/Image/bookmark?chapterId={chapterId}&page={page}
 */
data class KavitaBookmarkThumbnail(
    val chapterId: Int,
    val page: Int
) {
    /** Build the full thumbnail URL from the server base URL. */
    fun toUrl(baseUrl: String): String {
        val base = baseUrl.trimEnd('/')
        return "$base/api/Image/bookmark?chapterId=$chapterId&page=$page"
    }
}

/**
 * Represents a Kavita chapter ID mapping.
 * Used to store the mapping between local chapter index and Kavita chapter ID.
 *
 * @param libraryId The Kavita library ID
 * @param seriesId The Kavita series ID
 * @param chapterId The Kavita chapter ID
 */
data class KavitaChapterMapping(
    val libraryId: Int,
    val seriesId: Int,
    val chapterId: Int
)
