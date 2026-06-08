package com.mimiral.app.data.remote.kavita

/**
 * Scrobble settings from GET /api/Scrobbling/scrobble-settings.
 *
 * Controls which external providers (AniList, MAL, etc.) are enabled
 * and the global scrobbling toggle. Requires Kavita+ license.
 */
data class KavitaScrobblingSettings(
    val isScrobblingEnabled: Boolean = false,
    val isAniListEnabled: Boolean = false,
    val isMalEnabled: Boolean = false,
    val isGoogleBooksEnabled: Boolean = false,
    val aniListToken: String? = null,
    val malToken: String? = null,
    val googleBooksToken: String? = null
)

/**
 * Kavita+ license validation result from GET /api/License/valid-license.
 */
data class KavitaLicenseStatus(
    val isValid: Boolean = false,
    val licenseType: String? = null,
    val expirationDate: String? = null
)

/**
 * A scrobble error entry from GET /api/Scrobbling/scrobble-errors.
 *
 * Represents a failed scrobble attempt that can be retried.
 */
data class KavitaScrobbleError(
    val id: Int,
    val seriesId: Int,
    val seriesName: String? = null,
    val libraryId: Int = 0,
    val scrobbleProvider: String? = null,
    val errorMessage: String? = null,
    val createdDate: String? = null,
    val modifiedDate: String? = null
)

/**
 * Request body for updating scrobble provider token.
 * POST /api/Scrobbling/update-user-scrobble-provider
 */
data class KavitaUpdateScrobbleProviderRequest(
    val provider: String,
    val token: String
)

/**
 * Scrobble hold on a series from GET /api/Scrobbling/holds.
 *
 * A hold prevents scrobbling for a specific series.
 */
data class KavitaScrobbleHold(
    val seriesId: Int,
    val seriesName: String? = null,
    val heldAt: String? = null
)
