package com.mimiral.app.data.remote.kavita

/**
 * Result wrapper for Kavita API network operations.
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
 * Login request body for Kavita JWT authentication.
 * POST /api/Auth/login
 */
data class KavitaLoginRequest(
    val username: String,
    val password: String
)

/**
 * Login response containing the JWT token.
 * The token is used as a Bearer token in subsequent requests.
 */
data class KavitaLoginResponse(
    val token: String,
    val refreshToken: String? = null,
    val username: String? = null,
    val email: String? = null,
    val tokenExpiration: String? = null
)

/**
 * Server info response from GET /api/Library.
 * Used for connection validation.
 */
data class KavitaLibraryInfo(
    val id: Int,
    val name: String,
    val type: Int? = null,
    val lastScanned: String? = null
)

/**
 * Kavita library (server-side collection of series).
 */
data class KavitaLibrary(
    val id: Int,
    val name: String,
    val type: Int? = null,
    val lastScanned: String? = null,
    val coverImage: String? = null,
    val items: List<KavitaSeries> = emptyList()
)

/**
 * Series within a Kavita library.
 */
data class KavitaSeries(
    val id: Int,
    val name: String,
    val format: String? = null,
    val coverImage: String? = null,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val libraryId: Int,
    val libraryName: String? = null,
    val description: String? = null
)

/**
 * Volume/chapter information within a series.
 */
data class KavitaVolume(
    val id: Int,
    val name: String,
    val number: Int = 0,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val minNumber: Double = 0.0,
    val maxNumber: Double = 0.0,
    val seriesId: Int
)

/**
 * Individual chapter/issue within a volume.
 */
data class KavitaChapter(
    val id: Int,
    val name: String,
    val number: String? = null,
    val volumeId: Int? = null,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val lastReadingProgress: String? = null
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
 * Server configuration and health check response.
 */
data class KavitaServerInfo(
    val installId: String? = null,
    val isDocker: Boolean = false,
    val architecture: String? = null,
    val os: String? = null,
    val version: String? = null,
    val isNewAccount: Boolean = false,
    val hasManga: Boolean = false,
    val hasComic: Boolean = false,
    val hasBooks: Boolean = false,
    val hasLightNovels: Boolean = false,
    val hasSpecialDirs: Boolean = false,
    val hasBookmarks: Boolean = false,
    val totalLibraries: Int = 0
)

/**
 * Paginated response wrapper for Kavita list endpoints.
 */
data class KavitaPaginatedResponse<T>(
    val items: List<T> = emptyList(),
    val totalItems: Int = 0,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val pageSize: Int = 0
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
