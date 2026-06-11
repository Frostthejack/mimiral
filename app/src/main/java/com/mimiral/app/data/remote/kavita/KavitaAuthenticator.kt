package com.mimiral.app.data.remote.kavita

import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp [Authenticator] that handles Kavita 401 responses by refreshing the JWT token.
 *
 * Unlike an [okhttp3.Interceptor], OkHttp calls [Authenticator.authenticate] on its
 * own internal thread pool — it does NOT block the calling interceptor chain.
 * This means a slow token refresh will not exhaust OkHttp's dispatcher threads
 * and cause cascading timeouts for other in-flight requests.
 *
 * On 401:
 * 1. If a refresh token is available, synchronously calls [KavitaAuthService.refreshToken].
 * 2. On success, returns a new request with the updated auth headers.
 * 3. On failure (or no refresh token), calls [onAuthFailed] and returns null
 *    (telling OkHttp to stop retrying).
 *
 * Thread safety: [KavitaAuthService.refreshToken] is serialized via [kotlinx.coroutines.sync.Mutex],
 * so concurrent 401s will queue for the refresh rather than racing.
 *
 * @param authService  The auth service that holds current token state and performs refresh
 * @param onAuthFailed Callback invoked when refresh fails — typically clears auth state
 */
class KavitaAuthenticator(
    private val authService: KavitaAuthService,
    private val onAuthFailed: () -> Unit
) : Authenticator {

    companion object {
        private const val TAG = "KavitaAuth"
        private const val HEADER_AUTH = "Authorization"
        private const val HEADER_API_KEY = "X-Api-Key"
        private const val PREFIX_BEARER = "Bearer "
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        // Only handle 401 Unauthorized
        if (response.code != 401) {
            return null
        }

        // Don't retry auth endpoints themselves
        val path = response.request.url.encodedPath
        if (path.contains("/api/Account/login") ||
            path.contains("/api/Auth/login") ||
            path.contains("/api/Plugin/authenticate") ||
            path.contains("/api/Account/refresh-token") ||
            path.contains("/api/Auth/refresh-token")
        ) {
            Log.w(TAG, "Auth endpoint returned 401 — not retrying")
            onAuthFailed()
            return null
        }

        // Check if we can attempt a refresh
        if (!authService.canRefresh()) {
            Log.w(TAG, "Cannot refresh — no refresh token available")
            onAuthFailed()
            return null
        }

        // Synchronously refresh the token.
        // This is safe because Authenticator.authenticate() is already a blocking
        // call on OkHttp's internal thread pool — it does not block the
        // interceptor chain's dispatcher threads.
        val refreshed = try {
            runBlocking {
                authService.refreshToken()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed: ${e.message}", e)
            false
        }

        if (!refreshed) {
            Log.w(TAG, "Auth refresh failed — signaling logout")
            onAuthFailed()
            return null
        }

        // Build a new request with the updated auth headers
        val state = authService.authState
        val builder = response.request.newBuilder()

        when {
            !state.jwtToken.isNullOrBlank() -> {
                builder.header(HEADER_AUTH, PREFIX_BEARER + state.jwtToken)
                Log.v(TAG, "Authenticator: injected new Bearer token for retry")
            }
            !state.apiKey.isNullOrBlank() -> {
                builder.header(HEADER_API_KEY, state.apiKey)
                Log.v(TAG, "Authenticator: injected API key for retry")
            }
            else -> {
                Log.w(TAG, "Authenticator: no credentials after refresh — giving up")
                onAuthFailed()
                return null
            }
        }

        return builder.build()
    }
}
