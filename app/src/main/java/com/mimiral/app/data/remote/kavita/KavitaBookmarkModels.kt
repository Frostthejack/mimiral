package com.mimiral.app.data.remote.kavita

/**
 * Result wrapper for Kavita API operations.
 */
sealed class KavitaResult<out T> {
    data class Success<T>(val data: T) : KavitaResult<T>()
    data class Error(
        val message: String,
        val code: Int? = null,
        val cause: Throwable? = null
    ) : KavitaResult<Nothing>()
}

/**
 * Request body for creating/updating a bookmark in Kavita.
 * POST /api/Reader/bookmark
 *
 * @param page The page number (0-based) within the chapter
 * @param chapterId The Kavita chapter ID (series chapter ID)
 * @param seriesId The Kavita series ID
 * @param libraryId The Kavita library ID
 */
data class KavitaBookmarkRequest(
    val page: Int,
    val chapterId: Int,
    val seriesId: Int,
    val libraryId: Int
)

/**
 * Response from GET /api/Reader/chapter-bookmarks
 * Contains all bookmarks for a chapter.
 *
 * @param chapterId The Kavita chapter ID
 * @param page The page number
 * @param seriesId The Kavita series ID
 */
data class KavitaChapterBookmark(
    val chapterId: Int,
    val page: Int,
    val seriesId: Int
)

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
