package com.mimiral.app.data.remote.kavita

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * OkHttp interceptor that handles Kavita authentication:
 *
 * 1. **Token injection** — Adds `Authorization: Bearer ***` or `X-Api-Key` header
 *    to every outgoing request, based on the current [KavitaAuthState].
 *
 * 2. **401 retry with token refresh** — When a request returns HTTP 401:
 *    - If we have a refresh token, calls POST /api/Account/refresh-token
 *    - On success, stores the new JWT + refresh token and retries the original request
 *    - On failure (or no refresh token), calls [onAuthFailed] to signal logout
 *
 * Thread safety: The interceptor runs on OkHttp's background threads.
 * [KavitaAuthService] uses synchronized access for token state, so
 * concurrent refresh attempts are serialized via [KavitaAuthService.refreshToken].
 *
 * @param authService  The auth service that holds current token state and performs refresh
 * @param onAuthFailed Callback invoked when refresh fails — typically clears auth state
 */
class KavitaAuthInterceptor(
    private val authService: KavitaAuthService,
    private val onAuthFailed: () -> Unit
) : Interceptor {

    companion object {
        private const val TAG = "KavitaAuth"
        private const val HEADER_AUTH = "Authorization"
        private const val HEADER_API_KEY = "X-Api-Key"
        private const val PREFIX_BEARER = "Bearer "
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for login/authenticate/refresh endpoints themselves
        if (isAuthEndpoint(originalRequest)) {
            return chain.proceed(originalRequest)
        }

        // Inject auth headers into the request
        val authedRequest = injectAuthHeaders(originalRequest)
        val response = chain.proceed(authedRequest)

        // If 401 and we can try to refresh, do so
        if (response.code == 401 && authService.canRefresh()) {
            response.close() // Must close before retry

            val refreshed = try {
                runBlocking(Dispatchers.IO) {
                    authService.refreshToken()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh failed: ${e.message}", e)
                false
            }

            if (refreshed) {
                // Backoff before retry to avoid hammering the server
                runBlocking { delay(500L) }

                // Retry the original request with new token
                try {
                    val retryRequest = injectAuthHeaders(originalRequest)
                    val retryResponse = chain.proceed(retryRequest)

                    // If retry also got 401, refresh token was also expired
                    if (retryResponse.code == 401) {
                        Log.w(TAG, "Retry after refresh also got 401 — signaling logout")
                        retryResponse.close()
                        onAuthFailed()
                        // Return a synthetic 401 since we consumed the response
                        return response.newBuilder()
                            .code(401)
                            .message("Unauthorized (refresh token expired)")
                            .build()
                    }

                    return retryResponse
                } catch (e: Exception) {
                    Log.e(TAG, "Retry request failed: ${e.message}", e)
                    onAuthFailed()
                    return response.newBuilder()
                        .code(401)
                        .message("Unauthorized (retry failed: ${e.javaClass.simpleName})")
                        .build()
                }
            } else {
                Log.w(TAG, "Auth refresh failed — signaling logout")
                onAuthFailed()
            }
        }

        return response
    }

    /**
     * Inject authentication headers based on current auth state.
     *
     * Priority:
     * 1. JWT Bearer token if available (preferred for full API access)
     * 2. API key header if available (limited to plugin API scope)
     */
    private fun injectAuthHeaders(request: Request): Request {
        val state = authService.authState

        // Don't overwrite existing Authorization headers (e.g. from refresh-token call)
        if (request.header(HEADER_AUTH) != null || request.header(HEADER_API_KEY) != null) {
            return request
        }

        val builder = request.newBuilder()

        when {
            !state.jwtToken.isNullOrBlank() -> {
                builder.header(HEADER_AUTH, PREFIX_BEARER + state.jwtToken)
                Log.v(TAG, "Injected Bearer token")
            }
            !state.apiKey.isNullOrBlank() -> {
                builder.header(HEADER_API_KEY, state.apiKey)
                Log.v(TAG, "Injected API key")
            }
            else -> {
                Log.v(TAG, "No auth credentials available")
            }
        }

        return builder.build()
    }

    /**
     * Check if this request targets an auth endpoint that should NOT
     * have auth headers injected (to avoid infinite loops).
     */
    private fun isAuthEndpoint(request: Request): Boolean {
        val path = request.url.encodedPath
        return path.contains("/api/Account/login") ||
            path.contains("/api/Auth/login") ||
            path.contains("/api/Plugin/authenticate") ||
            path.contains("/api/Account/refresh-token") ||
            path.contains("/api/Auth/refresh-token")
    }
}
