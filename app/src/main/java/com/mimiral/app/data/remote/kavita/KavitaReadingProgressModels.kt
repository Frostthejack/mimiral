package com.mimiral.app.data.remote.kavita

import com.google.gson.annotations.SerializedName

// ── Reading Progress DTO models ──

/**
 * Full progress DTO matching Kavita's POST /api/Reader/progress contract.
 * Includes all fields for bidirectional sync with EPUB scroll support.
 *
 * @param volumeId Kavita volume ID
 * @param chapterId Kavita chapter ID
 * @param pageNum Current page number (0-based)
 * @param seriesId Kavita series ID
 * @param libraryId Kavita library ID
 * @param bookScrollId EPUB scroll position identifier (null for non-EPUB formats)
 * @param lastModifiedUtc ISO-8601 UTC timestamp for conflict resolution
 */
data class KavitaProgressDto(
    @SerializedName("volumeId") val volumeId: Int,
    @SerializedName("chapterId") val chapterId: Int,
    @SerializedName("pageNum") val pageNum: Int,
    @SerializedName("seriesId") val seriesId: Int,
    @SerializedName("libraryId") val libraryId: Int,
    @SerializedName("bookScrollId") val bookScrollId: String? = null,
    @SerializedName("lastModifiedUtc") val lastModifiedUtc: String
)

/**
 * Response from GET /api/Reader/get-progress?chapterId={chapterId}.
 * Contains the user's reading progress for a specific chapter.
 */
data class KavitaProgressResponseDto(
    @SerializedName("volumeId") val volumeId: Int = 0,
    @SerializedName("chapterId") val chapterId: Int = 0,
    @SerializedName("pageNum") val pageNum: Int = 0,
    @SerializedName("seriesId") val seriesId: Int = 0,
    @SerializedName("libraryId") val libraryId: Int = 0,
    @SerializedName("bookScrollId") val bookScrollId: String? = null,
    @SerializedName("lastModifiedUtc") val lastModifiedUtc: String? = null,
    @SerializedName("pagesRead") val pagesRead: Int = 0,
    @SerializedName("pagesTotal") val pagesTotal: Int = 0
)

/**
 * Continue Point response from GET /api/Reader/continue-point.
 * Represents where the user left off across all series — used for
 * the "Continue Reading" feature on app start.
 */
data class KavitaContinuePointDto(
    @SerializedName("seriesId") val seriesId: Int = 0,
    @SerializedName("volumeId") val volumeId: Int = 0,
    @SerializedName("chapterId") val chapterId: Int = 0,
    @SerializedName("pageNum") val pageNum: Int = 0,
    @SerializedName("libraryId") val libraryId: Int = 0,
    @SerializedName("bookScrollId") val bookScrollId: String? = null,
    @SerializedName("lastModifiedUtc") val lastModifiedUtc: String? = null,
    @SerializedName("seriesName") val seriesName: String? = null,
    @SerializedName("libraryName") val libraryName: String? = null,
    @SerializedName("coverImage") val coverImage: String? = null,
    @SerializedName("format") val format: Int = 0
)

/**
 * Generic API response wrapper for Kavita Reader endpoints.
 */
data class KavitaReaderResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null
)

// ── Pending Operation (offline queue) ──

/**
 * Types of pending sync operations that need to be flushed when connectivity returns.
 */
enum class PendingOperationType {
    /** Push reading progress to Kavita */
    PUSH_PROGRESS,
    /** Full bidirectional sync */
    FULL_SYNC
}

/**
 * Represents a pending sync operation queued for later execution.
 * Stored in Room database for offline resilience.
 */
data class PendingOperation(
    val id: Int = 0,
    val type: PendingOperationType,
    val seriesId: Int,
    val libraryId: Int,
    val volumeId: Int = 0,
    val chapterId: Int = 0,
    val pageNum: Int = 0,
    val bookScrollId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0
)
