package com.mimiral.app.data.opds

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * HTTP client for fetching OPDS feeds.
 * Uses OkHttp with reasonable timeouts for mobile network conditions.
 */
class OpdsClient(
    private val okHttpClient: OkHttpClient = defaultClient()
) {

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Fetch and parse an OPDS feed from the given URL.
     */
    suspend fun fetchFeed(
        url: String,
        username: String? = null,
        password: String? = null
    ): Result<OpdsFeed> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/atom+xml, application/xml, text/xml")

            if (username != null && password != null) {
                val credentials = okhttp3.Credentials.basic(username, password)
                requestBuilder.header("Authorization", credentials)
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP ${response.code} for $url")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(
                    IOException("Empty response body for $url")
                )

            val feed = OpdsParser.parse(body, url)
            Result.success(feed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download a file (e.g., book or cover image) from a URL.
     * Returns the raw bytes.
     */
    suspend fun downloadFile(
        url: String,
        username: String? = null,
        password: String? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder().url(url)

            if (username != null && password != null) {
                val credentials = okhttp3.Credentials.basic(username, password)
                requestBuilder.header("Authorization", credentials)
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP ${response.code} for $url")
                )
            }

            val bytes = response.body?.bytes()
                ?: return@withContext Result.failure(
                    IOException("Empty response body for $url")
                )

            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
