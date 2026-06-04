package com.mimiral.app.data.remote.kavita

import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response as OkHttpResponse
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * OkHttp interceptor that injects authentication headers into requests.
 *
 * Supports two modes:
 * - JWT Bearer token: adds "Authorization: Bearer <token>"
 * - API key: adds "X-Api-Key: <apiKey>"
 */
class KavitaAuthInterceptor(
    private var token: String? = null,
    private var apiKey: String? = null
) : Interceptor {

    companion object {
        private const val TAG = "KavitaAuth"
    }

    fun setToken(newToken: String?) {
        token = newToken
        Log.d(TAG, "JWT token ${if (newToken != null) "set" else "cleared"}")
    }

    fun setApiKey(newApiKey: String?) {
        apiKey = newApiKey
        Log.d(TAG, "API key ${if (newApiKey != null) "set" else "cleared"}")
    }

    fun clearAuth() {
        token = null
        apiKey = null
        Log.d(TAG, "All auth cleared")
    }

    override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .header("User-Agent", "Mimiral/0.1.0")
            .header("Accept", "application/json")

        // Prefer JWT token over API key
        token?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        } ?: apiKey?.let {
            requestBuilder.header("X-Api-Key", it)
        }

        return chain.proceed(requestBuilder.build())
    }
}

/**
 * HTTP client for Kavita API operations.
 *
 * Wraps the Retrofit [KavitaApi] service with:
 * - Automatic auth header injection via [KavitaAuthInterceptor]
 * - Connection timeout configuration
 * - Error handling and status code reporting
 * - Token management
 */
class KavitaClient(
    private val api: KavitaApi,
    private val authInterceptor: KavitaAuthInterceptor
) {
    companion object {
        private const val TAG = "KavitaClient"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L

        /**
         * Create a KavitaClient from a base URL.
         *
         * @param baseUrl The Kavita server base URL (e.g., "https://kavita.example.com/")
         * @param token Optional JWT token for Bearer auth
         * @param apiKey Optional API key for X-Api-Key auth
         * @return Configured KavitaClient
         */
        fun create(
            baseUrl: String,
            token: String? = null,
            apiKey: String? = null
        ): KavitaClient {
            val authInterceptor = KavitaAuthInterceptor(token, apiKey)

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val api = retrofit.create(KavitaApi::class.java)
            return KavitaClient(api, authInterceptor)
        }
    }

    // ==================== Auth Management ====================

    fun setToken(token: String?) {
        authInterceptor.setToken(token)
    }

    fun setApiKey(apiKey: String?) {
        authInterceptor.setApiKey(apiKey)
    }

    fun clearAuth() {
        authInterceptor.clearAuth()
    }

    // ==================== Authentication ====================

    /**
     * Authenticate with username and password.
     *
     * @param username The username
     * @param password The password
     * @return [KavitaResult] containing the login response with JWT token
     */
    suspend fun login(
        username: String,
        password: String
    ): KavitaResult<KavitaLoginResponse> = withContext(Dispatchers.IO) {
        try {
            val request = KavitaLoginRequest(username, password)
            val response = api.login(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    // Auto-set the token for subsequent requests
                    setToken(body.token)
                    Log.d(TAG, "Login successful for user: ${body.username}")
                    KavitaResult.Success(body)
                } else {
                    KavitaResult.Error(
                        message = "Empty login response body",
                        code = response.code()
                    )
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                Log.w(TAG, "Login failed: ${response.code()} - $errorBody")
                KavitaResult.Error(
                    message = parseErrorMessage(errorBody, response.code()),
                    code = response.code()
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error during login: ${e.message}", e)
            KavitaResult.Error(
                message = "Network error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during login: ${e.message}", e)
            KavitaResult.Error(
                message = "Unexpected error: ${e.message}",
                cause = e
            )
        }
    }

    // ==================== Connection Validation ====================

    /**
     * Validate the connection to the Kavita server.
     * Attempts to fetch server info to confirm connectivity and auth.
     *
     * @return [KavitaResult] containing server info on success
     */
    suspend fun validateConnection(): KavitaResult<KavitaServerInfo> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getServerInfo()

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Log.d(
                            TAG,
                            "Connection valid: Kavita v${body.version} " +
                                "(${body.totalLibraries} libraries)"
                        )
                        KavitaResult.Success(body)
                    } else {
                        KavitaResult.Error(
                            message = "Empty server info response",
                            code = response.code()
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.w(
                        TAG,
                        "Connection validation failed: ${response.code()} - $errorBody"
                    )
                    KavitaResult.Error(
                        message = parseErrorMessage(errorBody, response.code()),
                        code = response.code()
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error validating connection: ${e.message}", e)
                KavitaResult.Error(
                    message = "Cannot reach server: ${e.message}",
                    cause = e
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error validating connection: ${e.message}", e)
                KavitaResult.Error(
                    message = "Unexpected error: ${e.message}",
                    cause = e
                )
            }
        }

    // ==================== Libraries ====================

    /**
     * Get all libraries from the server.
     *
     * @return [KavitaResult] containing list of libraries
     */
    suspend fun getLibraries(): KavitaResult<List<KavitaLibrary>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getLibraries()

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Log.d(TAG, "Fetched ${body.size} libraries")
                        KavitaResult.Success(body)
                    } else {
                        KavitaResult.Error(
                            message = "Empty libraries response",
                            code = response.code()
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.w(TAG, "Failed to fetch libraries: ${response.code()}")
                    KavitaResult.Error(
                        message = parseErrorMessage(errorBody, response.code()),
                        code = response.code()
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error fetching libraries: ${e.message}", e)
                KavitaResult.Error(
                    message = "Network error: ${e.message}",
                    cause = e
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error fetching libraries: ${e.message}", e)
                KavitaResult.Error(
                    message = "Unexpected error: ${e.message}",
                    cause = e
                )
            }
        }

    // ==================== User Info ====================

    /**
     * Get current user info.
     *
     * @return [KavitaResult] containing user info
     */
    suspend fun getUserInfo(): KavitaResult<KavitaUserInfo> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getUserInfo()

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Log.d(TAG, "User info: ${body.username}")
                        KavitaResult.Success(body)
                    } else {
                        KavitaResult.Error(
                            message = "Empty user info response",
                            code = response.code()
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.w(TAG, "Failed to get user info: ${response.code()}")
                    KavitaResult.Error(
                        message = parseErrorMessage(errorBody, response.code()),
                        code = response.code()
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error getting user info: ${e.message}", e)
                KavitaResult.Error(
                    message = "Network error: ${e.message}",
                    cause = e
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error getting user info: ${e.message}", e)
                KavitaResult.Error(
                    message = "Unexpected error: ${e.message}",
                    cause = e
                )
            }
        }

    // ==================== Helpers ====================

    /**
     * Parse a user-friendly error message from an error response body.
     */
    private fun parseErrorMessage(errorBody: String, code: Int): String {
        return when (code) {
            401 -> "Authentication failed: invalid credentials or expired token"
            403 -> "Access denied: insufficient permissions"
            404 -> "Server endpoint not found — check the URL"
            500 -> "Server error: please try again later"
            502, 503 -> "Server unavailable: please try again later"
            else -> {
                if (errorBody.isNotBlank()) {
                    "HTTP $code: $errorBody"
                } else {
                    "HTTP $code"
                }
            }
        }
    }
}
