package com.mimiral.app.data.remote.kavita

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * OkHttp interceptor that handles Kavita authentication:
 *
 * 1. **Token injection** — Adds `Authorization: Bearer *** or `X-Api-Key` header
 *    to every outgoing request, based on the current [KavitaAuthState].
 *
 * 401 handling is done by [KavitaAuthenticator], which implements OkHttp's
 * [okhttp3.Authenticator] interface. Unlike an interceptor, the Authenticator
 * runs on OkHttp's internal thread pool and does not block the interceptor chain.
 *
 * @param authService  The auth service that holds current token state
 */
class KavitaAuthInterceptor(
    private val authService: KavitaAuthService
) : Interceptor {

    companion object {
        private const val TAG = "KavitaAuth"
        private const val HEADER_AUTH = "Authorization"
        private const val HEADER_API_KEY = "X-Api-Key"
        private const val PREFIX_BEARER = "Bearer "
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Inject auth headers into the request
        val authedRequest = injectAuthHeaders(originalRequest)
        return chain.proceed(authedRequest)
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
}
