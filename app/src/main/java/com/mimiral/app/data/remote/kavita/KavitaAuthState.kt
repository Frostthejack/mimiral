package com.mimiral.app.data.remote.kavita

/**
 * Represents the complete authentication state for a Kavita server connection.
 *
 * This is the single source of truth for auth – consumed by the interceptor,
 * the service, and the UI. All fields are nullable because the user may not
 * have completed setup yet.
 *
 * @param serverUrl      Base URL of the Kavita server (e.g. "https://kavita.example.com")
 * @param jwtToken       Current JWT bearer token (from login or API-key authenticate)
 * @param refreshToken   Refresh token (only from username/password login)
 * @param apiKey         API key for plugin authentication (stored in DB ServerEntity)
 * @param opdsUrl        Auto-derived OPDS feed URL from /api/Account/opds-url
 * @param userId         Kavita user ID (from JWT claims or /api/Account/info)
 * @param username       Kavita username
 * @param tokenExpiry    Epoch millis when the JWT expires (decoded from JWT "exp" claim)
 * @param roles          User roles from the JWT or server
 */
data class KavitaAuthState(
    val serverUrl: String? = null,
    val jwtToken: String? = null,
    val refreshToken: String? = null,
    val apiKey: String? = null,
    val opdsUrl: String? = null,
    val userId: String? = null,
    val username: String? = null,
    val tokenExpiry: Long? = null,
    val roles: List<String> = emptyList()
) {
    /** Whether we have a valid JWT token that hasn't expired. */
    val hasValidJwt: Boolean
        get() = !jwtToken.isNullOrBlank() &&
            (tokenExpiry == null || tokenExpiry > System.currentTimeMillis())

    /** Whether we have a refresh token available. */
    val canRefresh: Boolean
        get() = !refreshToken.isNullOrBlank() && !jwtToken.isNullOrBlank()

    /** Whether we have an API key that can be used for plugin auth. */
    val hasApiKey: Boolean
        get() = !apiKey.isNullOrBlank()

    /** Whether any form of authentication is configured. */
    val isAuthenticated: Boolean
        get() = hasValidJwt || hasApiKey

    /** Whether the server URL is configured. */
    val isConfigured: Boolean
        get() = !serverUrl.isNullOrBlank()
}
