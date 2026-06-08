package com.mimiral.app.data.remote.kavita

import com.google.gson.annotations.SerializedName

// ── Continue Reading + Next/Prev Chapter + On Deck + Time Left ──

/**
 * Response from GET /api/Reader/next-chapter?seriesId={seriesId}&volumeId={volumeId}&chapterId={chapterId}.
 * Returns the next chapter's ID for seamless end-of-chapter navigation.
 * Returns 0 or -1 if there is no next chapter (end of series).
 */
data class KavitaNextChapterDto(
    @SerializedName("id") val id: Int = -1,
    @SerializedName("volumeId") val volumeId: Int = 0,
    @SerializedName("chapterId") val chapterId: Int = 0,
    /** Human-readable title for UI display (e.g. "Chapter 5") */
    @SerializedName("title") val title: String? = null,
    /** Total pages in this chapter */
    @SerializedName("pages") val pages: Int = 0
)

/**
 * Response from GET /api/Reader/prev-chapter?seriesId={seriesId}&volumeId={volumeId}&chapterId={chapterId}.
 * Returns the previous chapter's ID for start-of-chapter navigation.
 * Returns 0 or -1 if there is no previous chapter (beginning of series).
 */
data class KavitaPrevChapterDto(
    @SerializedName("id") val id: Int = -1,
    @SerializedName("volumeId") val volumeId: Int = 0,
    @SerializedName("chapterId") val chapterId: Int = 0,
    /** Human-readable title for UI display (e.g. "Chapter 3") */
    @SerializedName("title") val title: String? = null,
    /** Total pages in this chapter */
    @SerializedName("pages") val pages: Int = 0
)

/**
 * Response from GET /api/Reader/time-left?seriesId={seriesId}&libraryId={libraryId}.
 * Provides estimated reading time remaining for a series.
 */
data class KavitaTimeLeftDto(
    @SerializedName("seriesId") val seriesId: Int = 0,
    /** Hours of estimated reading time remaining */
    @SerializedName("hours") val hours: Int = 0,
    /** Additional minutes past the hours */
    @SerializedName("minutes") val minutes: Int = 0,
    /** Total pages remaining across all chapters */
    @SerializedName("pagesLeft") val pagesLeft: Int = 0,
    /** Total pages in the series */
    @SerializedName("totalPages") val totalPages: Int = 0,
    /** Average reading speed in pages per hour (server-calculated) */
    @SerializedName("avgPagesPerHour") val avgPagesPerHour: Double = 0.0
) {
    /** Human-readable time estimate, e.g. "2h 30m" or "45m" */
    val formattedTime: String
        get() = when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }

    /** Progress percentage (0..100) */
    val progressPercent: Float
        get() = if (totalPages > 0) {
            ((totalPages - pagesLeft).toFloat() / totalPages) * 100f
        } else 0f
}

/**
 * Series entry in the On Deck shelf.
 * Returned by GET /api/Series/on-deck — series the user is actively reading
 * sorted by most recently accessed.
 */
data class KavitaOnDeckDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("libraryId") val libraryId: Int = 0,
    @SerializedName("pages") val pages: Int = 0,
    @SerializedName("pagesRead") val pagesRead: Int = 0,
    /** Format enum: 0=Archive, 1=Epub, 2=Pdf, 3=Image, etc. */
    @SerializedName("format") val format: Int = 0,
    @SerializedName("coverImage") val coverImage: String? = null,
    /** ISO-8601 date the series was last read */
    @SerializedName("latestReadDate") val latestReadDate: String? = null
) {
    /** Progress percentage (0..100) */
    val progressPercent: Float
        get() = if (pages > 0) (pagesRead.toFloat() / pages) * 100f else 0f
}

/**
 * Continue-reading context for deep-linking from On Deck / Continue Reading
 * into the reader. Bundles the chapter ID and page number for the reader
 * to jump directly to the user's last position.
 */
data class KavitaContinueReadingContext(
    val seriesId: Int,
    val volumeId: Int,
    val chapterId: Int,
    val pageNum: Int,
    val libraryId: Int,
    val seriesName: String?,
    val bookScrollId: String? = null
)
