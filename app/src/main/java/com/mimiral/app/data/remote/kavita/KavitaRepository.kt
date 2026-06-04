package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.entity.ServerEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Kavita server operations.
 *
 * Combines the Kavita HTTP client, secure credential storage, and local
 * database access to provide:
 * - Server connection setup and validation
 * - JWT and API key authentication
 * - Credential persistence
 * - Connection error handling with user-friendly messages
 */
@Singleton
class KavitaRepository @Inject constructor(
    private val credentialStore: KavitaCredentialStore,
    private val serverDao: ServerDao
) {
    companion object {
        private const val TAG = "KavitaRepository"
        private const val SERVER_TYPE_KAVITA = "KAVITA"
    }

    /**
     * The current active Kavita client instance.
     * Created on demand when connecting to a server.
     */
    private var client: KavitaClient? = null

    /**
     * Get all saved Kavita servers from the local database.
     */
    fun getSavedServers(): Flow<List<ServerEntity>> =
        serverDao.getActiveServers()

    /**
     * Connect to a Kavita server using username/password (JWT auth).
     *
     * Flow:
     * 1. Create a KavitaClient for the server URL
     * 2. Attempt login to get a JWT token
     * 3. Save credentials and token securely
     * 4. Validate the connection
     * 5. Save server info to local database
     *
     * @param serverUrl The Kavita server base URL
     * @param username The username
     * @param password The password
     * @return [KavitaResult] containing server info on success
     */
    suspend fun connectWithPassword(
        serverUrl: String,
        username: String,
        password: String
    ): KavitaResult<KavitaServerInfo> {
        Log.d(TAG, "Connecting to $serverUrl as $username")

        // Normalize URL (ensure trailing slash for Retrofit)
        val normalizedUrl = normalizeUrl(serverUrl)

        // Create a fresh client (no auth yet for login)
        val kavitaClient = KavitaClient.create(baseUrl = normalizedUrl)
        client = kavitaClient

        // Step 1: Login to get JWT token
        val loginResult = kavitaClient.login(username, password)
        if (loginResult is KavitaResult.Error) {
            client = null
            return KavitaResult.Error(
                message = "Login failed: ${loginResult.message}",
                code = loginResult.code,
                cause = loginResult.cause
            )
        }

        val loginResponse = (loginResult as KavitaResult.Success).data

        // Step 2: Save credentials securely
        credentialStore.saveConnectionInfo(
            serverUrl = normalizedUrl,
            username = username,
            password = password,
            token = loginResponse.token,
            refreshToken = loginResponse.refreshToken
        )

        // Step 3: Validate connection by fetching server info
        val validationResult = kavitaClient.validateConnection()
        if (validationResult is KavitaResult.Error) {
            return validationResult
        }

        val serverInfo = (validationResult as KavitaResult.Success).data

        // Step 4: Save to local database
        val serverEntity = ServerEntity(
            name = serverInfo.installId ?: "Kavita Server",
            type = SERVER_TYPE_KAVITA,
            url = normalizedUrl,
            username = username,
            password = null, // Don't store password in plain DB
            apiKey = null,
            jwtToken = loginResponse.token,
            isActive = true,
            lastSyncTime = System.currentTimeMillis()
        )
        serverDao.insertServer(serverEntity)

        Log.d(
            TAG,
            "Connected to Kavita v${serverInfo.version} " +
                "(${serverInfo.totalLibraries} libraries)"
        )
        return KavitaResult.Success(serverInfo)
    }

    /**
     * Connect to a Kavita server using an API key.
     *
     * Flow:
     * 1. Create a KavitaClient with the API key
     * 2. Validate the connection
     * 3. Save the API key securely
     * 4. Save server info to local database
     *
     * @param serverUrl The Kavita server base URL
     * @param apiKey The API key
     * @return [KavitaResult] containing server info on success
     */
    suspend fun connectWithApiKey(
        serverUrl: String,
        apiKey: String
    ): KavitaResult<KavitaServerInfo> {
        Log.d(TAG, "Connecting to $serverUrl with API key")

        val normalizedUrl = normalizeUrl(serverUrl)

        // Create client with API key
        val kavitaClient = KavitaClient.create(baseUrl = normalizedUrl, apiKey = apiKey)
        client = kavitaClient

        // Save API key securely
        credentialStore.saveServerUrl(normalizedUrl)
        credentialStore.saveApiKey(apiKey)

        // Validate connection
        val validationResult = kavitaClient.validateConnection()
        if (validationResult is KavitaResult.Error) {
            client = null
            return KavitaResult.Error(
                message = "Connection failed: ${validationResult.message}",
                code = validationResult.code,
                cause = validationResult.cause
            )
        }

        val serverInfo = (validationResult as KavitaResult.Success).data

        // Save to local database
        val serverEntity = ServerEntity(
            name = serverInfo.installId ?: "Kavita Server",
            type = SERVER_TYPE_KAVITA,
            url = normalizedUrl,
            username = null,
            password = null,
            apiKey = apiKey,
            jwtToken = null,
            isActive = true,
            lastSyncTime = System.currentTimeMillis()
        )
        serverDao.insertServer(serverEntity)

        Log.d(TAG, "Connected to Kavita with API key (${serverInfo.totalLibraries} libraries)")
        return KavitaResult.Success(serverInfo)
    }

    /**
     * Restore a connection to a previously saved server.
     * Loads stored credentials/tokens and attempts to validate the connection.
     *
     * @return [KavitaResult] containing server info on success, or error
     */
    suspend fun restoreConnection(): KavitaResult<KavitaServerInfo> {
        val serverUrl = credentialStore.getServerUrl()
        if (serverUrl.isNullOrBlank()) {
            return KavitaResult.Error("No saved server URL found")
        }

        Log.d(TAG, "Restoring connection to $serverUrl")

        // Try JWT token first
        val token = credentialStore.getJwtToken()
        if (!token.isNullOrBlank()) {
            val kavitaClient = KavitaClient.create(baseUrl = serverUrl, token = token)
            client = kavitaClient

            val result = kavitaClient.validateConnection()
            if (result is KavitaResult.Success) {
                return result
            }
            // Token may be expired, fall through to other methods
            Log.d(TAG, "JWT token validation failed, trying other methods")
        }

        // Try API key
        val apiKey = credentialStore.getApiKey()
        if (!apiKey.isNullOrBlank()) {
            return connectWithApiKey(serverUrl, apiKey)
        }

        // Try username/password
        val username = credentialStore.getUsername()
        val password = credentialStore.getPassword()
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            return connectWithPassword(serverUrl, username, password)
        }

        return KavitaResult.Error(
            "No valid credentials found. Please reconnect to the server."
        )
    }

    /**
     * Validate the current connection is still active.
     *
     * @return [KavitaResult] containing server info if connection is valid
     */
    suspend fun validateCurrentConnection(): KavitaResult<KavitaServerInfo> {
        val kavitaClient = client
        if (kavitaClient == null) {
            // Try to restore from stored credentials
            return restoreConnection()
        }
        return kavitaClient.validateConnection()
    }

    /**
     * Get libraries from the connected server.
     *
     * @return [KavitaResult] containing list of libraries
     */
    suspend fun getLibraries(): KavitaResult<List<KavitaLibrary>> {
        val kavitaClient = client
            ?: return KavitaResult.Error("Not connected to a server")
        return kavitaClient.getLibraries()
    }

    /**
     * Get the current active client instance.
     * Returns null if not connected.
     */
    fun getClient(): KavitaClient? = client

    /**
     * Disconnect from the current server.
     * Clears the in-memory client and stored tokens (keeps server URL).
     */
    fun disconnect() {
        client?.clearAuth()
        client = null
        credentialStore.clearTokens()
        Log.d(TAG, "Disconnected from Kavita server")
    }

    /**
     * Remove a server from the saved servers list.
     */
    suspend fun removeServer(server: ServerEntity) {
        if (client != null) {
            disconnect()
        }
        credentialStore.clearAll()
        serverDao.updateServer(server.copy(isActive = false))
        Log.d(TAG, "Server removed: ${server.name}")
    }

    // ==================== Helpers ====================

    /**
     * Normalize a URL for use with Retrofit.
     * Ensures the URL ends with a trailing slash.
     */
    private fun normalizeUrl(url: String): String {
        val trimmed = url.trimEnd('/')
        return "$trimmed/"
    }
}
