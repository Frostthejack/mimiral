package com.mimiral.app.data.remote.kavita

/**
 * Kavita server information returned by /api/server/info.
 */
data class KavitaServerInfo(
    val installId: String,
    val isInstalled: Boolean,
    val version: String,
    val allowAnyToken: Boolean = false
)

/**
 * Request body for Kavita JWT login.
 */
data class KavitaLoginRequest(
    val username: String,
    val password: String
)

/**
 * Response from Kavita JWT login.
 */
data class KavitaLoginResponse(
    val username: String?,
    val token: String,
    val refreshToken: String?,
    val tokenDuration: String?,
    val apiKey: String?
)

/**
 * A Kavita library entry.
 */
data class KavitaLibrary(
    val id: Int,
    val name: String,
    val type: Int,
    val lastScanned: String? = null,
    val fileExtTypes: List<String>? = null
) {
    companion object {
        const val TYPE_BOOK = 0
        const val TYPE_COMIC = 1
        const val TYPE_MANGA = 2
        const val TYPE_IMAGE = 3
        const val TYPE_PDF = 4
    }

    val typeLabel: String
        get() = when (type) {
            TYPE_BOOK -> "Books"
            TYPE_COMIC -> "Comics"
            TYPE_MANGA -> "Manga"
            TYPE_IMAGE -> "Images"
            TYPE_PDF -> "PDFs"
            else -> "Library"
        }
}

/**
 * A Kavita series (logical grouping of books/volumes).
 */
data class KavitaSeries(
    val id: Int,
    val name: String,
    val libraryId: Int,
    val pages: Int = 0,
    val format: Int = 0,
    val coverImageLocked: Boolean = false,
    val coverImage: String? = null,
    val pagesRead: Int = 0,
    val latestReadDate: String? = null,
    val description: String? = null,
    val formatLabel: String? = null,
    val volumes: List<KavitaVolume>? = null
)

/**
 * A Kavita volume within a series.
 */
data class KavitaVolume(
    val id: Int,
    val name: String,
    val number: Int,
    val minNumber: Int = 0,
    val maxNumber: Int = 0,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val lastModified: String? = null,
    val files: List<KavitaBookFile>? = null,
    val chapters: List<KavitaChapter>? = null,
    val coverImage: String? = null
)

/**
 * A Kavita chapter within a volume.
 */
data class KavitaChapter(
    val id: Int,
    val range: String? = null,
    val number: String? = null,
    val minNumber: Int = 0,
    val maxNumber: Int = 0,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val isSpecial: Boolean = false,
    val title: String? = null,
    val files: List<KavitaBookFile>? = null
)

/**
 * A file entry in a Kavita chapter or volume.
 */
data class KavitaBookFile(
    val id: Int,
    val filePath: String,
    val pages: Int = 0,
    val format: String? = null,
    val created: String? = null,
    val lastModified: String? = null,
    val size: Long = 0
)

/**
 * Detailed book info from Kavita API.
 */
data class KavitaBook(
    val id: Int,
    val seriesId: Int,
    val name: String,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val seriesName: String? = null,
    val libraryId: Int = 0,
    val libraryType: Int = 0,
    val format: Int = 0,
    val created: String? = null,
    val lastModified: String? = null,
    val coverImageLocked: Boolean = false,
    val coverImage: String? = null,
    val path: String? = null,
    val files: List<KavitaBookFile>? = null
)

/**
 * Simplified book reference for download operations.
 */
data class KavitaBookRef(
    val bookId: Int,
    val title: String,
    val format: String,
    val seriesId: Int? = null,
    val libraryId: Int,
    val volumeName: String? = null,
    val chapterNumber: String? = null
)

/**
 * Progress bookmark from Kavita.
 */
data class KavitaBookmark(
    val id: Int = 0,
    val chapterId: Int,
    val pageNum: Int,
    val seriesId: Int,
    val volumeId: Int,
    val libraryId: Int,
    val bookScrollId: String? = null,
    val created: String? = null,
    val lastModified: String? = null
)

/**
 * Reading progress for syncing with Kavita.
 */
data class KavitaProgress(
    val id: Int = 0,
    val chapterId: Int,
    val pageNum: Int,
    val seriesId: Int,
    val volumeId: Int,
    val libraryId: Int,
    val bookScrollId: String? = null,
    val lastModified: String? = null
)

/**
 * Wrapper for paginated API responses.
 */
data class KavitaPagedResponse<T>(
    val items: List<T>,
    val totalItems: Int,
    val currentPage: Int,
    val totalPages: Int
)

/**
 * Paginated response wrapper for Kavita list endpoints.
 * Used by KavitaApi for endpoints returning paginated results.
 */
data class KavitaPaginatedResponse<T>(
    val items: List<T> = emptyList(),
    val totalItems: Int = 0,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val pageSize: Int = 0
)

/**
 * User-specific reading progress for a chapter.
 */
data class KavitaReadingProgress(
    val id: Int = 0,
    val chapterId: Int,
    val pagesRead: Int,
    val lastModified: String? = null,
    val volumeId: Int? = null,
    val seriesId: Int? = null,
    val libraryId: Int? = null
)

/**
 * User info from the Kavita server.
 */
data class KavitaUserInfo(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val apiKey: String? = null,
    val ageRestriction: Int = 0,
    val isLocked: Boolean = false
)

// ==================== Stats Models ====================

/**
 * Reading activity data point from GET /api/Stats/reading-activity.
 * Represents pages read per day over a time range.
 */
data class KavitaReadingActivity(
    val date: String,
    val pagesRead: Int,
    val chaptersRead: Int = 0
)

/**
 * Genre breakdown from GET /api/Stats/genre-breakdown.
 * Each entry is a genre and its associated page count.
 */
data class KavitaGenreBreakdown(
    val genre: String,
    val pagesRead: Int,
    val seriesCount: Int = 0
)

/**
 * Pages-per-year data point from GET /api/Stats/pages-per-year.
 * Used for the bar chart showing reading volume by year.
 */
data class KavitaPagesPerYear(
    val year: Int,
    val pagesRead: Int,
    val booksRead: Int = 0
)

/**
 * Reading pace trend from GET /api/Stats/reading-pace.
 * Monthly rolling average of pages per day.
 */
data class KavitaReadingPace(
    val month: String,
    val pagesPerDay: Float,
    val avgSessionMinutes: Float = 0f
)

/**
 * Favorite author entry from GET /api/Stats/favorite-authors.
 * Ordered by pages read descending.
 */
data class KavitaFavoriteAuthor(
    val author: String,
    val pagesRead: Int,
    val seriesCount: Int = 0,
    val booksCount: Int = 0
)

/**
 * Series-specific reading history from GET /api/Stats/reading-history/series/{seriesId}.
 */
data class KavitaSeriesReadingHistory(
    val seriesId: Int,
    val seriesName: String? = null,
    val totalPagesRead: Int,
    val lastReadDate: String? = null,
    val readingSessions: Int = 0,
    val avgPagesPerSession: Float = 0f
)
