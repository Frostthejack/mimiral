package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * HTTP client for Kavita bookmark API operations.
 *
 * Handles:
 * - POST /api/Reader/bookmark — push/create a bookmark
 * - GET /api/Reader/chapter-bookmarks — pull bookmarks for a chapter
 * - DELETE /api/Reader/bookmark — remove a bookmark
 * - Authentication via API key or JWT token
 * - Error handling with structured [KavitaResult]
 */
class KavitaBookmarkClient(
    private val client: OkHttpClient = defaultClient(),
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "KavitaBookmarkClient"
        private const val DEFAULT_TIMEOUT_SECONDS = 15L
        private const val JSON_MEDIA_TYPE = "application/json"

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    private var baseUrl: String = ""
    private var apiKey: String? = null
    private var jwtToken: String? = null
    private var username: String? = null
    private var password: String? = null

    /**
     * Configure the client with server connection details.
     *
     * @param url The Kavita server base URL (resolved from active server config)
     * @param key Optional API key for authentication
     * @param token Optional JWT token for authentication
     * @param user Optional username for HTTP Basic auth
     * @param pass Optional password for HTTP Basic auth
     */
    fun configure(
        url: String,
        key: String? = null,
        token: String? = null,
        user: String? = null,
        pass: String? = null
    ) {
        baseUrl = url.trimEnd('/')
        apiKey = key
        jwtToken = token
        username = user
        password = pass
    }

    /**
     * POST /api/Reader/bookmark
     * Push a bookmark to Kavita.
     *
     * @param request The bookmark data to push
     * @return [KavitaResult] indicating success or failure
     */
    suspend fun pushBookmark(
        request: KavitaBookmarkRequest
    ): KavitaResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(request)
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Reader/bookmark")
                .addHeaders()
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(
                    TAG,
                    "Push bookmark failed: ${response.code} " +
                        "${response.message} - $errorBody"
                )
                return@withContext KavitaResult.Error(
                    message = "HTTP ${response.code}: ${response.message}",
                    code = response.code
                )
            }

            Log.d(
                TAG,
                "Pushed bookmark: chapter=${request.chapterId}, " +
                    "page=${request.page}"
            )
            KavitaResult.Success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Network error pushing bookmark: ${e.message}", e)
            KavitaResult.Error(
                message = "Network error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error pushing bookmark: ${e.message}", e)
            KavitaResult.Error(
                message = "Unexpected error: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * GET /api/Reader/chapter-bookmarks
     * Pull all bookmarks for a chapter from Kavita.
     *
     * @param chapterId The Kavita chapter ID
     * @return [KavitaResult] containing list of chapter bookmarks
     */
    suspend fun pullBookmarks(
        chapterId: Int
    ): KavitaResult<List<KavitaChapterBookmark>> =
        withContext(Dispatchers.IO) {
            try {
                val httpRequest = Request.Builder()
                    .url(
                        "$baseUrl/api/Reader/" +
                            "chapter-bookmarks?chapterId=$chapterId"
                    )
                    .addHeaders()
                    .get()
                    .build()

                val response = client.newCall(httpRequest).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.w(
                        TAG,
                        "Pull bookmarks failed: ${response.code} " +
                            "${response.message} - $errorBody"
                    )
                    return@withContext KavitaResult.Error(
                        message = "HTTP ${response.code}: ${response.message}",
                        code = response.code
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext KavitaResult.Error(
                        message = "Empty response body",
                        code = response.code
                    )

                val type = object : TypeToken<List<KavitaChapterBookmark>>() {}
                    .type
                val bookmarks: List<KavitaChapterBookmark> =
                    gson.fromJson(responseBody, type)

                Log.d(
                    TAG,
                    "Pulled ${bookmarks.size} bookmarks " +
                        "for chapter $chapterId"
                )
                KavitaResult.Success(bookmarks)
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "Network error pulling bookmarks: ${e.message}",
                    e
                )
                KavitaResult.Error(
                    message = "Network error: ${e.message}",
                    cause = e
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Unexpected error pulling bookmarks: ${e.message}",
                    e
                )
                KavitaResult.Error(
                    message = "Unexpected error: ${e.message}",
                    cause = e
                )
            }
        }

    /**
     * DELETE /api/Reader/bookmark
     * Remove a bookmark from Kavita by sending a request with page=-1
     * (Kavita convention for bookmark deletion).
     *
     * @param request The bookmark data to delete
     * @return [KavitaResult] indicating success or failure
     */
    suspend fun deleteBookmark(
        request: KavitaBookmarkRequest
    ): KavitaResult<Unit> = withContext(Dispatchers.IO) {
        try {
            // Kavita uses page=-1 to signal deletion
            val deleteRequest = request.copy(page = -1)
            val jsonBody = gson.toJson(deleteRequest)
            val body = jsonBody.toRequestBody(
                JSON_MEDIA_TYPE.toMediaType()
            )

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Reader/bookmark")
                .addHeaders()
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(
                    TAG,
                    "Delete bookmark failed: ${response.code} " +
                        "${response.message} - $errorBody"
                )
                return@withContext KavitaResult.Error(
                    message = "HTTP ${response.code}: ${response.message}",
                    code = response.code
                )
            }

            Log.d(TAG, "Deleted bookmark: chapter=${request.chapterId}")
            KavitaResult.Success(Unit)
        } catch (e: IOException) {
            Log.e(
                TAG,
                "Network error deleting bookmark: ${e.message}",
                e
            )
            KavitaResult.Error(
                message = "Network error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Unexpected error deleting bookmark: ${e.message}",
                e
            )
            KavitaResult.Error(
                message = "Unexpected error: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Add authentication headers to the request.
     * Priority: API key > JWT token > HTTP Basic auth.
     */
    private fun Request.Builder.addHeaders(): Request.Builder {
        header("User-Agent", "Mimiral/0.1.0")
        header("Accept", "application/json")

        when {
            !apiKey.isNullOrBlank() -> {
                header("X-Api-Key", apiKey!!)
            }
            !jwtToken.isNullOrBlank() -> {
                header("Authorization", "Bearer $jwtToken")
            }
            !username.isNullOrBlank() && !password.isNullOrBlank() -> {
                header(
                    "Authorization",
                    Credentials.basic(username!!, password!!)
                )
            }
        }
        return this
    }
}
