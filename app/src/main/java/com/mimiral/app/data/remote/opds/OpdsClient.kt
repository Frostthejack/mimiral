package com.mimiral.app.data.remote.opds

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Result wrapper for OPDS network operations.
 */
sealed class OpdsResult<out T> {
    data class Success<T>(val data: T) : OpdsResult<T>()
    data class Error(
        val message: String,
        val code: Int? = null,
        val cause: Throwable? = null
    ) : OpdsResult<Nothing>()
}

/**
 * HTTP client for fetching OPDS catalog feeds.
 *
 * Handles:
 * - HTTP GET requests with proper headers
 * - HTTP Basic authentication
 * - URL-based token authentication
 * - Error handling and status code reporting
 *
 * Uses a shared OkHttpClient provided by [OpdsModule] with consistent
 * 30s timeouts for connect, read, and write.
 */
@Singleton
class OpdsClient @Inject constructor(
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "OpdsClient"
    }

    /**
     * Fetch an OPDS feed from a URL.
     *
     * @param url The OPDS catalog URL
     * @param username Optional username for HTTP Basic auth
     * @param password Optional password for HTTP Basic auth
     * @return [OpdsResult] containing the raw response body string
     */
    suspend fun fetchFeed(
        url: String,
        username: String? = null,
        password: String? = null
    ): OpdsResult<String> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/atom+xml,application/xml,text/xml,*/*")
                .header("User-Agent", "Mimiral/0.1.0")

            // Add HTTP Basic auth if credentials provided
            if (username != null && password != null) {
                requestBuilder.header("Authorization", Credentials.basic(username, password))
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(
                    TAG,
                    "OPDS request failed: ${response.code} ${response.message} - $errorBody"
                )
                return@withContext OpdsResult.Error(
                    message = "HTTP ${response.code}: ${response.message}",
                    code = response.code
                )
            }

            val body = response.body?.string()
                ?: return@withContext OpdsResult.Error(
                    message = "Empty response body",
                    code = response.code
                )

            Log.d(TAG, "Fetched OPDS feed: ${body.length} bytes from $url")
            OpdsResult.Success(body)
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching OPDS feed: ${e.message}", e)
            OpdsResult.Error(
                message = "Network error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching OPDS feed: ${e.message}", e)
            OpdsResult.Error(
                message = "Unexpected error: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Fetch an OPDS feed and parse it directly.
     *
     * @param url The OPDS catalog URL
     * @param username Optional username for HTTP Basic auth
     * @param password Optional password for HTTP Basic auth
     * @param parser The [OpdsParser] to use for parsing
     * @return [OpdsResult] containing the parsed [OpdsFeed]
     */
    suspend fun fetchAndParseFeed(
        url: String,
        username: String? = null,
        password: String? = null,
        parser: OpdsParser = OpdsParser()
    ): OpdsResult<OpdsFeed> {
        return when (val result = fetchFeed(url, username, password)) {
            is OpdsResult.Success -> {
                try {
                    val feed = parser.parseFeedString(result.data, baseHref = url)
                    OpdsResult.Success(feed)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}", e)
                    OpdsResult.Error(
                        message = "Parse error: ${e.message}",
                        cause = e
                    )
                }
            }
            is OpdsResult.Error -> result
        }
    }

    /**
     * Download a book file from an acquisition URL.
     *
     * @param url The download URL
     * @param username Optional username for HTTP Basic auth
     * @param password Optional password for HTTP Basic auth
     * @return [OpdsResult] containing the raw bytes
     */
    suspend fun downloadBook(
        url: String,
        username: String? = null,
        password: String? = null
    ): OpdsResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mimiral/0.1.0")

            if (username != null && password != null) {
                requestBuilder.header("Authorization", Credentials.basic(username, password))
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext OpdsResult.Error(
                    message = "HTTP ${response.code}: ${response.message}",
                    code = response.code
                )
            }

            val bytes = response.body?.bytes()
                ?: return@withContext OpdsResult.Error(
                    message = "Empty response body",
                    code = response.code
                )

            Log.d(TAG, "Downloaded book: ${bytes.size} bytes from $url")
            OpdsResult.Success(bytes)
        } catch (e: IOException) {
            Log.e(TAG, "Network error downloading book: ${e.message}", e)
            OpdsResult.Error(
                message = "Network error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error downloading book: ${e.message}", e)
            OpdsResult.Error(
                message = "Unexpected error: ${e.message}",
                cause = e
            )
        }
    }
}
