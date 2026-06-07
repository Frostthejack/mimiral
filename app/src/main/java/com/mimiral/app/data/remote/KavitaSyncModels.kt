package com.mimiral.app.data.remote

import com.google.gson.annotations.SerializedName

// ── Progress sync models ──

/**
 * Request body for POST /api/Reader/progress — push reading progress to Kavita.
 * @param seriesId Kavita series ID (volume/chapter grouping)
 * @param libraryId Kavita library ID
 * @param chapterId Kavita chapter ID (0 if not known)
 * @param pageNumber Current page number (0-based)
 * @param lastModified Last modified timestamp in ISO-8601 format
 * @param volumeId Volume ID (same as seriesId for single-volume series)
 */
data class KavitaProgressRequest(
    @SerializedName("seriesId") val seriesId: Int,
    @SerializedName("libraryId") val libraryId: Int,
    @SerializedName("chapterId") val chapterId: Int = 0,
    @SerializedName("pageNumber") val pageNumber: Int,
    @SerializedName("lastModified") val lastModified: String,
    @SerializedName("volumeId") val volumeId: Int = 0
)

/**
 * Response body from POST /api/Reader/progress.
 */
data class KavitaProgressResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null
)

/**
 * Response body from GET /api/Reader/get-progress.
 * @param seriesId Kavita series ID
 * @param libraryId Kavita library ID
 * @param chapterId Kavita chapter ID
 * @param pageNumber Current page (0-based)
 * @param lastModified Last modified timestamp in ISO-8601 format
 * @param volumeId Volume ID
 */
data class KavitaProgressData(
    @SerializedName("seriesId") val seriesId: Int = 0,
    @SerializedName("libraryId") val libraryId: Int = 0,
    @SerializedName("chapterId") val chapterId: Int = 0,
    @SerializedName("pageNumber") val pageNumber: Int = 0,
    @SerializedName("lastModified") val lastModified: String? = null,
    @SerializedName("volumeId") val volumeId: Int = 0
)

/**
 * Represents sync status for the UI indicator.
 */
enum class SyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    ERROR
}

/**
 * Server info response from GET /api/Server/info.
 * Used for connection validation and status display.
 *
 * Single source of truth for server info — replaces the duplicate
 * com.mimiral.app.data.remote.kavita.KavitaServerInfo that was removed
 * to prevent ClassCastException when R8 merges same-named classes.
 */
data class KavitaServerInfo(
    @SerializedName("installId") val installId: String? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("isInstalled") val isInstalled: Boolean = true,
    @SerializedName("totalLibraries") val totalLibraries: Int = 0,
    @SerializedName("isDocker") val isDocker: Boolean = false,
    @SerializedName("allowAnyToken") val allowAnyToken: Boolean = false
)

/**
 * Login request body for Kavita JWT authentication.
 * POST /api/Auth/login
 */
data class KavitaLoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

/**
 * Login response containing the JWT token.
 */
data class KavitaLoginResponse(
    @SerializedName("token") val token: String = "",
    @SerializedName("refreshToken") val refreshToken: String? = null,
    @SerializedName("username") val username: String? = null
)

/**
 * Represents the connection status for UI display.
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

// ── Series / Volume browsing models ──

/**
 * Response from GET /api/Series/series-detail?seriesId={id}
 * DTO for the Series Detail page including volumes, chapters, and specials.
 */
data class SeriesDetailDto(
    @SerializedName("specials") val specials: List<ChapterDto> = emptyList(),
    @SerializedName("chapters") val chapters: List<ChapterDto> = emptyList(),
    @SerializedName("volumes") val volumes: List<VolumeDto> = emptyList(),
    @SerializedName("storylineChapters") val storylineChapters: List<ChapterDto> = emptyList(),
    @SerializedName("unreadCount") val unreadCount: Int = 0,
    @SerializedName("totalCount") val totalCount: Int = 0
)

/**
 * Response from GET /api/Series/volumes?seriesId={id}
 * A single volume with progress information and chapters.
 */
data class VolumeDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("number") val number: Int = 0,
    @SerializedName("pages") val pages: Int = 0,
    @SerializedName("pagesRead") val pagesRead: Int = 0,
    @SerializedName("seriesId") val seriesId: Int = 0,
    @SerializedName("chapters") val chapters: List<ChapterDto> = emptyList(),
    @SerializedName("coverImage") val coverImage: String? = null,
    @SerializedName("primaryColor") val primaryColor: String? = null,
    @SerializedName("secondaryColor") val secondaryColor: String? = null,
    @SerializedName("minHoursToRead") val minHoursToRead: Int = 0,
    @SerializedName("maxHoursToRead") val maxHoursToRead: Int = 0,
    @SerializedName("avgHoursToRead") val avgHoursToRead: Double = 0.0,
    @SerializedName("wordCount") val wordCount: Int = 0,
    @SerializedName("lastModified") val lastModified: String? = null,
    @SerializedName("created") val created: String? = null
)

/**
 * A chapter within a volume.
 */
data class ChapterDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("range") val range: String = "",
    @SerializedName("number") val number: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("pages") val pages: Int = 0,
    @SerializedName("pagesRead") val pagesRead: Int = 0,
    @SerializedName("isSpecial") val isSpecial: Boolean = false,
    @SerializedName("volumeId") val volumeId: Int = 0,
    @SerializedName("coverImage") val coverImage: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("titleName") val titleName: String? = null,
    @SerializedName("lastReadingProgress") val lastReadingProgress: String? = null
)

/**
 * Response from GET /api/Series/{seriesId}
 * Basic series information.
 */
data class SeriesDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("originalName") val originalName: String? = null,
    @SerializedName("localizedName") val localizedName: String? = null,
    @SerializedName("sortName") val sortName: String? = null,
    @SerializedName("pages") val pages: Int = 0,
    @SerializedName("pagesRead") val pagesRead: Int = 0,
    @SerializedName("coverImage") val coverImage: String? = null,
    @SerializedName("primaryColor") val primaryColor: String? = null,
    @SerializedName("secondaryColor") val secondaryColor: String? = null,
    @SerializedName("libraryId") val libraryId: Int = 0,
    @SerializedName("libraryName") val libraryName: String? = null,
    @SerializedName("userRating") val userRating: Double = 0.0,
    @SerializedName("hasUserRated") val hasUserRated: Boolean = false,
    @SerializedName("totalReads") val totalReads: Int = 0,
    @SerializedName("latestReadDate") val latestReadDate: String? = null,
    @SerializedName("minHoursToRead") val minHoursToRead: Int = 0,
    @SerializedName("maxHoursToRead") val maxHoursToRead: Int = 0,
    @SerializedName("avgHoursToRead") val avgHoursToRead: Double = 0.0,
    @SerializedName("wordCount") val wordCount: Int = 0,
    @SerializedName("created") val created: String? = null
)
