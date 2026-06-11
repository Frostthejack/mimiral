package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.google.gson.Gson
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.entity.ServerEntity
import com.mimiral.app.di.ApplicationScope
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Service that manages Kavita authentication lifecycle.
 *
 * Responsibilities:
 * - **JWT login** via POST /api/Account/login (username+password → JWT+refresh)
 * - **API key auth** via GET /api/Plugin/authenticate (x-api-key → JWT)
 * - **Token refresh** via POST /api/Account/refresh-token (expired JWT + refresh → new JWT)
 * - **OPDS URL derivation** via GET /api/Account/opds-url
 * - **Token persistence** in EncryptedSharedPreferences via [KavitaCredentialStore]
 * - **Server DB persistence** updating ServerEntity with JWT token
 * - **Auth state exposure** as [KavitaAuthState] for the interceptor and UI
 *
 * This service uses its own lightweight [OkHttpClient] (without the auth interceptor)
 * for auth API calls, breaking the circular dependency:
 *   OkHttpClient → KavitaAuthInterceptor → KavitaAuthService → KavitaApi → OkHttpClient
 *
 * Thread safety: All token mutations are serialized via [Mutex] to prevent
 * concurrent refresh races.
 */
@Singleton
class KavitaAuthService @Inject constructor(
    private val credentialStore: KavitaCredentialStore,
    private val serverDao: ServerDao,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "KavitaAuthService"
        private const val BASE_URL_HEADER = "X-Kavita-Base-Url"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val mutex = Mutex()

    /**
     * Lightweight HTTP client for auth operations only.
     * This avoids circular dependency with the main Kavita OkHttpClient
     * (which has KavitaAuthInterceptor → KavitaAuthService).
     */
    private val authClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val gson = Gson()

    /** Current authentication state — thread-safe read via mutex. */
    @Volatile
    private var _authState: KavitaAuthState = KavitaAuthState()

    /** Public read-only access to the current auth state. */
    val authState: KavitaAuthState get() = _authState

    /**
     * Whether a token refresh is possible (we have both JWT and refresh token).
     */
    fun canRefresh(): Boolean = _authState.canRefresh

    /**
     * Initialize auth state from persistent storage.
     * Called once at app startup to restore previously saved tokens.
     */
    suspend fun initialize() = mutex.withLock {
        try {
            val server = withContext(Dispatchers.IO) {
                serverDao.getActiveServerByType("KAVITA")
            }

            if (server != null) {
                // Check encrypted store first (more up-to-date tokens)
                val jwt = credentialStore.getJwtToken() ?: server.jwtToken
                val refresh = credentialStore.getRefreshToken()
                val apiKey = credentialStore.getApiKey() ?: server.apiKey

                _authState = KavitaAuthState(
                    serverUrl = server.url,
                    jwtToken = jwt,
                    refreshToken = refresh,
                    apiKey = apiKey,
                    username = server.username,
                    userId = null
                )

                Log.d(
                    TAG,
                    "Initialized auth: server=${server.url}, " +
                        "hasJwt=${!jwt.isNullOrBlank()}, hasRefresh=${!refresh.isNullOrBlank()}, " +
                        "hasApiKey=${!apiKey.isNullOrBlank()}"
                )
            } else {
                _authState = KavitaAuthState()
                Log.d(TAG, "No Kavita server configured")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize auth state: ${e.message}", e)
            _authState = KavitaAuthState()
        }
    }

    /**
     * Authenticate with username and password via POST /api/Account/login.
     *
     * On success, stores JWT + refresh token in both EncryptedSharedPreferences
     * and the ServerEntity, then updates the in-memory auth state.
     *
     * @param serverUrl  The Kavita server base URL
     * @param username   Kavita username
     * @param password   Kavita password
     * @return Result with the JWT token on success, or error on failure
     */
    suspend fun login(
        serverUrl: String,
        username: String,
        password: String
    ): Result<String> = mutex.withLock {
        try {
            val baseUrl = serverUrl.trimEnd('/')
            val request = KavitaLoginRequest(username = username, password = password)
            val body = gson.toJson(request).toRequestBody(JSON_MEDIA_TYPE)

            // Try v0.7+ endpoint first, fall back to legacy
            val response = try {
                executePost("$baseUrl/api/Account/login", body)
            } catch (e: Exception) {
                Log.w(TAG, "Account/login failed, trying Auth/login: ${e.message}")
                executePost("$baseUrl/api/Auth/login", body)
            }

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.w(TAG, "Login failed: HTTP ${response.code} — $errorBody")
                return@withLock Result.failure(
                    KavitaAuthException("Login failed: HTTP ${response.code}", response.code)
                )
            }

            val responseBody = response.body?.string()
                ?: return@withLock Result.failure(
                    KavitaAuthException("Empty login response")
                )

            val loginResponse = gson.fromJson(responseBody, KavitaLoginResponse::class.java)

            if (loginResponse.token.isBlank()) {
                return@withLock Result.failure(
                    KavitaAuthException("Login returned empty token")
                )
            }

            // Update auth state
            _authState = _authState.copy(
                serverUrl = serverUrl,
                jwtToken = loginResponse.token,
                refreshToken = loginResponse.refreshToken,
                apiKey = loginResponse.apiKey ?: _authState.apiKey,
                username = loginResponse.username ?: username
            )

            // Persist tokens
            credentialStore.saveConnectionInfo(
                serverUrl = serverUrl,
                username = username,
                password = password,
                token = loginResponse.token,
                refreshToken = loginResponse.refreshToken
            )

            // Update server entity
            updateServerEntity(
                serverUrl = serverUrl,
                username = username,
                password = password,
                jwtToken = loginResponse.token,
                apiKey = loginResponse.apiKey
            )

            // Derive OPDS URL in background (non-blocking)
            launchOpdsUrlDerivation(baseUrl, loginResponse.token)

            Log.d(TAG, "Login successful for user: ${loginResponse.username}")
            Result.success(loginResponse.token)
        } catch (e: Exception) {
            Log.e(TAG, "Login error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Authenticate via API key through GET /api/Plugin/authenticate.
     *
     * The API key is sent as the X-Api-Key header. The server returns
     * a JWT token in the response body, giving full API access.
     *
     * @param serverUrl  The Kavita server base URL
     * @param apiKey     The plugin API key
     * @return Result with the JWT token on success, or error on failure
     */
    suspend fun authenticateWithApiKey(
        serverUrl: String,
        apiKey: String
    ): Result<String> = mutex.withLock {
        try {
            val baseUrl = serverUrl.trimEnd('/')
            val request = Request.Builder()
                .url("$baseUrl/api/Plugin/authenticate")
                .header("X-Api-Key", apiKey)
                .header("User-Agent", "Mimiral/0.1.0")
                .get()
                .build()

            val response = withContext(Dispatchers.IO) {
                authClient.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.w(TAG, "API key auth failed: HTTP ${response.code} — $errorBody")
                return@withLock Result.failure(
                    KavitaAuthException(
                        "API key auth failed: HTTP ${response.code}",
                        response.code
                    )
                )
            }

            val jwtToken = response.body?.string()
                ?: return@withLock Result.failure(
                    KavitaAuthException("Empty authenticate response")
                )

            if (jwtToken.isBlank()) {
                return@withLock Result.failure(
                    KavitaAuthException("Authenticate returned empty token")
                )
            }

            // Update auth state
            _authState = _authState.copy(
                serverUrl = serverUrl,
                jwtToken = jwtToken,
                refreshToken = null,
                apiKey = apiKey
            )

            // Persist
            credentialStore.saveServerUrl(serverUrl)
            credentialStore.saveApiKey(apiKey)
            credentialStore.saveTokens(jwtToken, null)

            // Update server entity
            updateServerEntity(
                serverUrl = serverUrl,
                username = null,
                password = null,
                jwtToken = jwtToken,
                apiKey = apiKey
            )

            // Derive OPDS URL
            launchOpdsUrlDerivation(baseUrl, jwtToken)

            Log.d(TAG, "API key auth successful, got JWT")
            Result.success(jwtToken)
        } catch (e: Exception) {
            Log.e(TAG, "API key auth error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Refresh the JWT token using the stored refresh token.
     *
     * Called by [KavitaAuthInterceptor] on 401 responses.
     * Returns true if refresh succeeded, false otherwise.
     *
     * Thread-safe: serialized via mutex to prevent concurrent refresh races.
     */
    suspend fun refreshToken(): Boolean = mutex.withLock {
        try {
            val currentToken = _authState.jwtToken
            val currentRefresh = _authState.refreshToken
            val baseUrl = _authState.serverUrl?.trimEnd('/')

            if (currentToken.isNullOrBlank() ||
                currentRefresh.isNullOrBlank() ||
                baseUrl.isNullOrBlank()
            ) {
                Log.w(TAG, "Cannot refresh: missing token, refresh token, or server URL")
                return@withLock false
            }

            val request = Request.Builder()
                .url("$baseUrl/api/Account/refresh-token")
                .header("Authorization", "Bearer $currentToken")
                .header("Refresh-Token", currentRefresh)
                .header("User-Agent", "Mimiral/0.1.0")
                .post("".toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = withContext(Dispatchers.IO) {
                authClient.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                Log.w(TAG, "Token refresh failed: HTTP ${response.code}")
                return@withLock false
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                Log.w(TAG, "Token refresh returned empty body")
                return@withLock false
            }

            val loginResponse = gson.fromJson(responseBody, KavitaLoginResponse::class.java)
            if (loginResponse.token.isBlank()) {
                Log.w(TAG, "Token refresh returned empty token")
                return@withLock false
            }

            // Update auth state
            _authState = _authState.copy(
                jwtToken = loginResponse.token,
                refreshToken = loginResponse.refreshToken ?: currentRefresh
            )

            // Persist new tokens
            credentialStore.saveTokens(
                loginResponse.token,
                loginResponse.refreshToken ?: currentRefresh
            )

            // Update server entity JWT
            withContext(Dispatchers.IO) {
                val server = serverDao.getActiveServerByType("KAVITA")
                if (server != null) {
                    serverDao.updateServer(server.copy(jwtToken = loginResponse.token))
                }
            }

            Log.d(TAG, "Token refresh successful")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error: ${e.message}", e)
            false
        }
    }

    /**
     * Derive the OPDS feed URL from GET /api/Account/opds-url.
     *
     * Stores the result in auth state. Called automatically after login/authenticate.
     */
    suspend fun deriveOpdsUrl(): Result<String> {
        return try {
            val baseUrl = _authState.serverUrl?.trimEnd('/')
            val jwt = _authState.jwtToken

            if (baseUrl.isNullOrBlank() || jwt.isNullOrBlank()) {
                return Result.failure(KavitaAuthException("Not authenticated"))
            }

            val request = Request.Builder()
                .url("$baseUrl/api/Account/opds-url")
                .header("Authorization", "Bearer $jwt")
                .header("User-Agent", "Mimiral/0.1.0")
                .get()
                .build()

            val response = withContext(Dispatchers.IO) {
                authClient.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to get OPDS URL: HTTP ${response.code}")
                return Result.failure(
                    KavitaAuthException("OPDS URL fetch failed: HTTP ${response.code}")
                )
            }

            val opdsUrl = response.body?.string()
            if (opdsUrl.isNullOrBlank()) {
                Log.w(TAG, "Empty OPDS URL response")
                return Result.failure(KavitaAuthException("Empty OPDS URL"))
            }

            _authState = _authState.copy(opdsUrl = opdsUrl)
            Log.d(TAG, "OPDS URL derived: $opdsUrl")
            Result.success(opdsUrl)
        } catch (e: Exception) {
            Log.e(TAG, "OPDS URL derivation error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Clear all auth state and persisted credentials.
     * Called on logout or when refresh fails permanently.
     */
    suspend fun clearAuth() = mutex.withLock {
        _authState = KavitaAuthState()
        credentialStore.clearAll()

        // Deactivate server in DB
        try {
            withContext(Dispatchers.IO) {
                val server = serverDao.getActiveServerByType("KAVITA")
                if (server != null) {
                    serverDao.updateServer(server.copy(isActive = false))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deactivate server: ${e.message}", e)
        }

        Log.d(TAG, "Auth state cleared")
    }

    /**
     * Clear only in-memory tokens (keep server config for re-login).
     * Used when interceptor signals auth failure but user hasn't explicitly logged out.
     */
    fun clearTokens() {
        _authState = _authState.copy(
            jwtToken = null,
            refreshToken = null,
            tokenExpiry = null,
            opdsUrl = null
        )
        credentialStore.clearTokens()
        Log.d(TAG, "Auth tokens cleared (server config retained)")
    }

    // ==================== Internal helpers ====================

    private suspend fun executePost(url: String, body: okhttp3.RequestBody): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", "Mimiral/0.1.0")
            .header("Content-Type", "application/json")
            .build()
        return withContext(Dispatchers.IO) {
            authClient.newCall(request).execute()
        }
    }

    /**
     * Derive OPDS URL in background (fire-and-forget).
     * Failures are logged but don't block the auth flow.
     */
    fun launchOpdsUrlDerivation(baseUrl: String, jwtToken: String) {
        scope.launch {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/Account/opds-url")
                    .header("Authorization", "Bearer $jwtToken")
                    .header("User-Agent", "Mimiral/0.1.0")
                    .get()
                    .build()

                val response = authClient.newCall(request).execute()
                response.use {
                    if (it.isSuccessful) {
                        val opdsUrl = it.body?.string()
                        if (!opdsUrl.isNullOrBlank()) {
                            _authState = _authState.copy(opdsUrl = opdsUrl)
                            Log.d(TAG, "OPDS URL derived: $opdsUrl")
                        }
                    } else {
                        Log.w(TAG, "OPDS URL fetch failed: HTTP ${it.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "OPDS URL derivation failed: ${e.message}")
            }
        }
    }

    /**
     * Update the ServerEntity in the database with the current auth info.
     */
    private suspend fun updateServerEntity(
        serverUrl: String,
        username: String?,
        password: String?,
        jwtToken: String?,
        apiKey: String?
    ) {
        withContext(Dispatchers.IO) {
            try {
                val existing = serverDao.getActiveServerByType("KAVITA")

                if (existing != null) {
                    serverDao.updateServer(
                        existing.copy(
                            url = serverUrl,
                            username = username,
                            password = password,
                            jwtToken = jwtToken,
                            apiKey = apiKey,
                            isActive = true
                        )
                    )
                } else {
                    serverDao.insertServer(
                        ServerEntity(
                            name = "Kavita Server",
                            type = "KAVITA",
                            url = serverUrl,
                            username = username,
                            password = password,
                            jwtToken = jwtToken,
                            apiKey = apiKey,
                            isActive = true
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update server entity: ${e.message}", e)
            }
        }
    }
}

/**
 * Exception for Kavita auth failures with HTTP status code.
 */
class KavitaAuthException(
    message: String,
    val statusCode: Int? = null
) : Exception(message)
