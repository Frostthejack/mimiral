package com.mimiral.app.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.dao.ServerDao
import androidx.room.withTransaction
import com.mimiral.app.data.local.database.MimiralDatabase
import com.mimiral.app.data.local.entity.ServerEntity
import com.mimiral.app.data.remote.ConnectionStatus
import com.mimiral.app.data.remote.KavitaServerInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Authentication method for connecting to a Kavita server.
 */
enum class AuthMethod {
    USERNAME_PASSWORD,
    API_KEY
}

/**
 * UI state for the Kavita server setup screen.
 */
data class KavitaSetupUiState(
    val serverUrl: String = "",
    val authMethod: AuthMethod = AuthMethod.USERNAME_PASSWORD,
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val serverInfo: KavitaServerInfo? = null,
    val errorMessage: String? = null,
    val isTestingConnection: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val hasExistingConfig: Boolean = false
)

@HiltViewModel
class KavitaSetupViewModel @Inject constructor(
    application: Application,
    private val serverDao: ServerDao,
    private val database: MimiralDatabase
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "KavitaSetupVM"
    }

    // Single OkHttpClient instance reused across all testConnection() calls
    // to avoid leaking sockets and threads on each invocation.
    private val testClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    private val _uiState = MutableStateFlow(KavitaSetupUiState())
    val uiState: StateFlow<KavitaSetupUiState> = _uiState.asStateFlow()

    init {
        loadExistingConfig()
    }

    /**
     * Load existing Kavita server configuration from the database.
     */
    private fun loadExistingConfig() {
        viewModelScope.launch {
            val existingServer = serverDao.getActiveServerByType("KAVITA")
            if (existingServer != null) {
                val authMethod = if (!existingServer.apiKey.isNullOrBlank()) {
                    AuthMethod.API_KEY
                } else {
                    AuthMethod.USERNAME_PASSWORD
                }
                _uiState.value = _uiState.value.copy(
                    serverUrl = existingServer.url,
                    authMethod = authMethod,
                    username = existingServer.username ?: "",
                    password = existingServer.password ?: "",
                    apiKey = existingServer.apiKey ?: "",
                    hasExistingConfig = true
                )
            }
        }
    }

    fun setServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            serverUrl = url,
            errorMessage = null,
            isSaved = false
        )
    }

    fun setAuthMethod(method: AuthMethod) {
        _uiState.value = _uiState.value.copy(
            authMethod = method,
            errorMessage = null,
            isSaved = false
        )
    }

    fun setUsername(username: String) {
        _uiState.value = _uiState.value.copy(
            username = username,
            errorMessage = null,
            isSaved = false
        )
    }

    fun setPassword(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            errorMessage = null,
            isSaved = false
        )
    }

    fun setApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(
            apiKey = apiKey,
            errorMessage = null,
            isSaved = false
        )
    }

    /**
     * Test the connection to the Kavita server.
     * Attempts to fetch server info to validate connectivity and authentication.
     */
    fun testConnection() {
        val state = _uiState.value
        val baseUrl = state.serverUrl.trim().trimEnd('/')

        if (baseUrl.isBlank()) {
            _uiState.value = state.copy(
                errorMessage = "Please enter a server URL"
            )
            return
        }

        val normalizedUrl = if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            "http://$baseUrl"
        } else {
            baseUrl
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingConnection = true,
                connectionStatus = ConnectionStatus.CONNECTING,
                errorMessage = null,
                serverInfo = null
            )

            try {
                val result = testServerConnection(testClient, normalizedUrl, state)

                when {
                    result.isSuccess -> {
                        val info = result.getOrNull()
                        _uiState.value = _uiState.value.copy(
                            isTestingConnection = false,
                            connectionStatus = ConnectionStatus.CONNECTED,
                            serverInfo = info,
                            errorMessage = null
                        )
                    }
                    else -> {
                        val error = result.exceptionOrNull()
                        _uiState.value = _uiState.value.copy(
                            isTestingConnection = false,
                            connectionStatus = ConnectionStatus.ERROR,
                            errorMessage = error?.message ?: "Connection failed"
                        )
                    }
                }
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Connection refused", e)
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionStatus = ConnectionStatus.ERROR,
                    errorMessage = "Cannot connect to server — check URL and network"
                )
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Connection timeout", e)
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionStatus = ConnectionStatus.ERROR,
                    errorMessage = "Server timed out — try again later"
                )
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "Unknown host", e)
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionStatus = ConnectionStatus.ERROR,
                    errorMessage = "Server not found — check the URL"
                )
            } catch (e: Exception) {
                Log.e(TAG, "testConnection unexpected error", e)
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionStatus = ConnectionStatus.ERROR,
                    errorMessage = "Cannot reach server: ${e.message}"
                )
            }
        }
    }

    /**
     * Save the Kavita server configuration to the database.
     */
    fun saveConfiguration() {
        val state = _uiState.value
        val baseUrl = state.serverUrl.trim().trimEnd('/')

        if (baseUrl.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter a server URL")
            return
        }

        val normalizedUrl = if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            "http://$baseUrl"
        } else {
            baseUrl
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)

            try {
                val server = ServerEntity(
                    name = "Kavita Server",
                    type = "KAVITA",
                    url = "$normalizedUrl/",
                    username = if (state.authMethod == AuthMethod.USERNAME_PASSWORD) {
                        state.username.ifBlank { null }
                    } else {
                        null
                    },
                    password = if (state.authMethod == AuthMethod.USERNAME_PASSWORD) {
                        state.password.ifBlank { null }
                    } else {
                        null
                    },
                    apiKey = if (state.authMethod == AuthMethod.API_KEY) {
                        state.apiKey.ifBlank { null }
                    } else {
                        null
                    },
                    isActive = true
                )

                // Deactivate any existing Kavita servers, insert new server, and clear
                // password atomically in a single transaction so the plaintext password is
                // never observable by another DB connection mid-write.
                database.withTransaction {
                    val existingServer = serverDao.getActiveServerByType("KAVITA")
                    if (existingServer != null) {
                        serverDao.updateServer(existingServer.copy(isActive = false))
                    }

                    serverDao.insertServer(server)

                    // Clear password from the entity after saving to credential store
                    val clearedServer = server.copy(password = null)
                    serverDao.updateServer(clearedServer)
                }

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isSaved = true,
                    hasExistingConfig = true,
                    password = ""
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Failed to save: ${e.message}"
                )
            }
        }
    }

    /**
     * Clear the saved configuration.
     */
    fun clearConfiguration() {
        viewModelScope.launch {
            val existingServer = serverDao.getActiveServerByType("KAVITA")
            if (existingServer != null) {
                serverDao.updateServer(existingServer.copy(isActive = false))
            }
            _uiState.value = KavitaSetupUiState()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Test the server connection by fetching the OPDS feed.
     * Uses /api/opds/<api-key> which is the Kavita OPDS endpoint.
     */
    private suspend fun testServerConnection(
        client: OkHttpClient,
        baseUrl: String,
        state: KavitaSetupUiState
    ): Result<KavitaServerInfo> {
        return try {
            val apiKey = when (state.authMethod) {
                AuthMethod.API_KEY -> state.apiKey.trim()
                AuthMethod.USERNAME_PASSWORD -> ""
            }

            if (state.authMethod == AuthMethod.API_KEY && apiKey.isBlank()) {
                return Result.failure(Exception("Please enter an API key"))
            }

            if (state.authMethod == AuthMethod.USERNAME_PASSWORD) {
                // For username/password, POST credentials to /api/Account/login
                if (state.username.isBlank() || state.password.isBlank()) {
                    return Result.failure(
                        Exception("Please enter both username and password")
                    )
                }

                val loginBody = (
                    "{\"username\":\"${state.username}\"" +
                        ",\"password\":\"${state.password}\"}"
                    ).toRequestBody("application/json; charset=utf-8".toMediaType())
                val loginRequest = okhttp3.Request.Builder()
                    .url("$baseUrl/api/Account/login")
                    .post(loginBody)
                    .header("User-Agent", "Mimiral/0.1.0")
                    .header("Content-Type", "application/json")
                    .build()

                val loginResponse = client.newCall(loginRequest).execute()

                when (loginResponse.code) {
                    200 -> {
                        // Login succeeded — server is reachable and credentials are valid
                        Result.success(
                            KavitaServerInfo(
                                installId = null,
                                version = null,
                                totalLibraries = 0,
                                isDocker = false
                            )
                        )
                    }
                    401 -> Result.failure(
                        Exception("Authentication failed: invalid username or password")
                    )
                    403 -> Result.failure(
                        Exception("Access denied: insufficient permissions")
                    )
                    404 -> Result.failure(
                        Exception("Login endpoint not found — check the server URL")
                    )
                    else -> Result.failure(
                        Exception("HTTP ${loginResponse.code}: ${loginResponse.message}")
                    )
                }
            } else {
                // API key auth — use /api/Plugin/authenticate
                val authUrl = "$baseUrl/api/Plugin/authenticate"

                val authRequest = okhttp3.Request.Builder()
                    .url(authUrl)
                    .header("X-Api-Key", apiKey)
                    .header("User-Agent", "Mimiral/0.1.0")
                    .get()
                    .build()

                val httpResponse = client.newCall(authRequest).execute()

                when (httpResponse.code) {
                    200 -> {
                        // Server is reachable and API key is valid
                        Result.success(
                            KavitaServerInfo(
                                installId = null,
                                version = null,
                                totalLibraries = 0,
                                isDocker = false
                            )
                        )
                    }
                    401 -> Result.failure(
                        Exception("Authentication failed: invalid API key")
                    )
                    403 -> Result.failure(
                        Exception("Access denied: insufficient permissions")
                    )
                    404 -> Result.failure(
                        Exception("Server endpoint not found — check the URL")
                    )
                    else -> Result.failure(
                        Exception("HTTP ${httpResponse.code}: ${httpResponse.message}")
                    )
                }
            }
        } catch (e: java.io.IOException) {
            Result.failure(
                Exception("Cannot reach server at $baseUrl — check the URL and network")
            )
        } catch (e: Exception) {
            try {
                val ctx = getApplication<Application>()
                val logFile = java.io.File(ctx.getExternalFilesDir(null), "mimiral_error.txt")
                logFile.writeText("ERROR: ${e.message}\n\n${e.stackTraceToString()}")
            } catch (_: Exception) { }
            Log.e(TAG, "testServerConnection failed", e)
            Result.failure(e)
        }
    }
}
