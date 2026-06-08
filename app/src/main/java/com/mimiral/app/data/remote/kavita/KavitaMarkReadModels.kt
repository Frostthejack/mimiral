package com.mimiral.app.data.remote.kavita

/**
 * Request body for marking a chapter as read.
 * POST /api/Reader/mark-chapter-read
 *
 * @param chapterId The Kavita chapter ID
 */
data class KavitaMarkChapterReadRequest(
    val chapterId: Int
)

/**
 * Request body for marking a volume as read or unread.
 * POST /api/Reader/mark-volume-read
 * POST /api/Reader/mark-volume-unread
 *
 * @param volumeId The Kavita volume ID
 */
data class KavitaMarkVolumeReadRequest(
    val volumeId: Int
)

/**
 * Request body for marking a series as read or unread.
 * POST /api/Series/mark-read
 * POST /api/Series/mark-unread
 *
 * @param seriesId The Kavita series ID
 */
data class KavitaMarkSeriesReadRequest(
    val seriesId: Int
)

/**
 * Request body for bulk marking multiple series as read or unread.
 * POST /api/Series/mark-multiple-series-read
 * POST /api/Series/mark-multiple-series-unread
 *
 * @param seriesIds List of Kavita series IDs
 */
data class KavitaMarkMultipleSeriesReadRequest(
    val seriesIds: List<Int>
)

/**
 * Request body for the catch-up (mark-until) endpoint.
 * POST /api/Tachiyomi/mark-chapter-until-as-read
 *
 * Marks all chapters up to and including the specified chapter as read.
 *
 * @param seriesId The Kavita series ID
 * @param chapterId The chapter ID to mark up to (inclusive)
 * @param volumesToInclude Number of volumes to include (for volume grouping)
 */
data class KavitaMarkChapterUntilReadRequest(
    val seriesId: Int,
    val chapterId: Int,
    val volumesToInclude: Int = 0
)

/**
 * UI state for mark read/unread operations.
 */
data class KavitaMarkReadUiState(
    val isMarking: Boolean = false,
    val lastOperation: MarkReadOperation? = null,
    val errorMessage: String? = null
)

/**
 * Represents a mark read/unread operation for UI feedback.
 */
sealed class MarkReadOperation {
    data class ChapterMarkedRead(val chapterId: Int) : MarkReadOperation()
    data class VolumeMarkedRead(val volumeId: Int) : MarkReadOperation()
    data class VolumeMarkedUnread(val volumeId: Int) : MarkReadOperation()
    data class SeriesMarkedRead(val seriesId: Int) : MarkReadOperation()
    data class SeriesMarkedUnread(val seriesId: Int) : MarkReadOperation()
    data class BulkSeriesMarkedRead(val count: Int) : MarkReadOperation()
    data class BulkSeriesMarkedUnread(val count: Int) : MarkReadOperation()
    data class CatchUpMarkedRead(val seriesId: Int, val upToChapterId: Int) : MarkReadOperation()
}
