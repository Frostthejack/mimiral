package com.mimiral.app.data.remote

import com.google.gson.annotations.SerializedName

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
 */
data class KavitaServerInfo(
    @SerializedName("installId") val installId: String? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("totalLibraries") val totalLibraries: Int = 0,
    @SerializedName("isDocker") val isDocker: Boolean = false
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
