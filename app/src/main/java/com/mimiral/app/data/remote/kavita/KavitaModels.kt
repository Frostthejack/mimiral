package com.mimiral.app.data.remote.kavita

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
