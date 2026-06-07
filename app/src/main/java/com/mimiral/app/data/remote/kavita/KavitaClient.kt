package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mimiral.app.data.remote.KavitaServerInfo
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Result wrapper for Kavita network operations.
 */
sealed class KavitaResult<out T> {
    data class Success<T>(val data: T) : KavitaResult<T>()
    data class Error(
        val message: String,
        val code: Int? = null,
        val cause: Throwable? = null
    ) : KavitaResult<Nothing>()
}

/**
 * HTTP client for Kavita server API.
 *
 * Handles:
 * - JWT authentication (login endpoint)
 * - API key authentication (header-based)
 * - Server info and library listing
 * - Book download with progress
 * - Cover image download
 * - Reading progress push/pull
 * - Bookmark push/pull
 *
 * All endpoints are relative to the configured server base URL.
 * Auth mode (JWT or API key) is selected at client creation time.
 */
class KavitaClient(
    private val baseUrl: String,
    private val apiKey: String? = null,
    private var jwtToken: String? = null,
    private val client: OkHttpClient = defaultClient()
) {
    /** Exposes the configured base URL for validation by repositories. */
    val serverUrl: String get() = baseUrl

    companion object {
        private const val TAG = "KavitaClient"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private const val DOWNLOAD_TIMEOUT_SECONDS = 120L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val gson = Gson()

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        private fun downloadClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        /** Create a KavitaClient with base URL only (no auth). */
        fun create(baseUrl: String): KavitaClient {
            return KavitaClient(baseUrl = baseUrl)
        }

        /** Create a KavitaClient with authentication. */
        fun create(
            baseUrl: String,
            token: String? = null,
            apiKey: String? = null
        ): KavitaClient {
            return KavitaClient(
                baseUrl = baseUrl,
                jwtToken = token,
                apiKey = apiKey
            )
        }
    }

    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    /**
     * Authenticate with username/password and obtain a JWT token.
     * The token is stored internally for subsequent requests.
     *
     * @param username Kavita username
     * @param password Kavita password
     * @return KavitaResult with the JWT token string
     */
    suspend fun login(
        username: String,
        password: String
    ): KavitaResult<String> = withContext(Dispatchers.IO) {
        try {
            val loginRequest = KavitaLoginRequest(
                username = username,
                password = password
            )
            val body = gson.toJson(loginRequest).toRequestBody(JSON_MEDIA_TYPE)

            val requestBuilder = Request.Builder()
                .url("$normalizedBaseUrl/api/account/login")
                .post(body)
                .header("User-Agent", "Mimiral/0.1.0")

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext KavitaResult.Error(
                    message = "Login failed: HTTP ${response.code}",
                    code = response.code
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext KavitaResult.Error(
                    message = "Empty login response",
                    code = response.code
                )

            val loginResponse = gson.fromJson(
                responseBody,
                KavitaLoginResponse::class.java
            )
            val token = loginResponse.token
            jwtToken = token
            Log.d(TAG, "Login successful for user: ${loginResponse.username}")
            KavitaResult.Success(token)
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

    /**
     * Get server info to verify connectivity and check auth capabilities.
     */
    suspend fun getServerInfo(): KavitaResult<KavitaServerInfo> =
        get("/api/server/info", object : TypeToken<KavitaServerInfo>() {})

    /**
     * List all libraries on the server.
     */
    suspend fun getLibraries(): KavitaResult<List<KavitaLibrary>> =
        getList("/api/library", object : TypeToken<List<KavitaLibrary>>() {})

    /**
     * List all series across all libraries.
     *
     * @param libraryId Optional library ID filter
     * @param pageNumber Page number (1-based)
     * @param pageSize Items per page
     */
    suspend fun getSeries(
        libraryId: Int? = null,
        pageNumber: Int = 1,
        pageSize: Int = 20
    ): KavitaResult<KavitaPagedResponse<KavitaSeries>> {
        val params = mutableListOf(
            "pageNumber=$pageNumber",
            "pageSize=$pageSize"
        )
        if (libraryId != null) {
            params.add("libraryId=$libraryId")
        }
        val query = params.joinToString("&")
        return get(
            "/api/series/all?$query",
            object : TypeToken<KavitaPagedResponse<KavitaSeries>>() {}
        )
    }

    /**
     * Get all volumes (books) in a series.
     */
    suspend fun getVolumes(seriesId: Int): KavitaResult<List<KavitaVolume>> =
        getList(
            "/api/series/volume?$seriesId=$seriesId",
            object : TypeToken<List<KavitaVolume>>() {}
        )

    /**
     * Get all chapters in a volume (contains the actual files).
     */
    suspend fun getChapters(volumeId: Int): KavitaResult<List<KavitaChapter>> =
        getList(
            "/api/series/chapter?$volumeId=$volumeId",
            object : TypeToken<List<KavitaChapter>>() {}
        )

    /**
     * Download a book file from Kavita.
     *
     * Supports two download methods:
     * - /api/download/book?chapterId={chapterId} for direct book download
     * - /api/download/scanlator-file?chapterId={chapterId} as fallback
     *
     * @param chapterId The chapter ID to download
     * @return KavitaResult containing the raw bytes
     */
    suspend fun downloadBook(
        chapterId: Int
    ): KavitaResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("$normalizedBaseUrl/api/download/book?chapterId=$chapterId")
                .header("User-Agent", "Mimiral/0.1.0")

            addAuthHeader(requestBuilder)

            val request = requestBuilder.build()
            val dClient = downloadClient()
            val response = dClient.newCall(request).execute()

            if (!response.isSuccessful) {
                // Fallback: try scanlator-file endpoint
                val fallbackRequest = Request.Builder()
                    .url(
                        "$normalizedBaseUrl/api/download" +
                            "/scanlator-file?chapterId=$chapterId"
                    )
                    .header("User-Agent", "Mimiral/0.1.0")
                    .also { addAuthHeader(it) }
                    .build()
                val fallbackResponse = dClient.newCall(fallbackRequest).execute()
                if (!fallbackResponse.isSuccessful) {
                    return@withContext KavitaResult.Error(
                        message = "Download failed: HTTP ${response.code}",
                        code = response.code
                    )
                }
                val bytes = fallbackResponse.body?.bytes()
                    ?: return@withContext KavitaResult.Error(
                        message = "Empty download response",
                        code = fallbackResponse.code
                    )
                Log.d(
                    TAG,
                    "Downloaded book (fallback): ${bytes.size} bytes " +
                        "for chapter $chapterId"
                )
                return@withContext KavitaResult.Success(bytes)
            }

            val bytes = response.body?.bytes()
                ?: return@withContext KavitaResult.Error(
                    message = "Empty download response",
                    code = response.code
                )
            Log.d(TAG, "Downloaded book: ${bytes.size} bytes for chapter $chapterId")
            KavitaResult.Success(bytes)
        } catch (e: IOException) {
            Log.e(TAG, "Network error downloading book: ${e.message}", e)
            KavitaResult.Error(
                message = "Network error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error downloading book: ${e.message}", e)
            KavitaResult.Error(
                message = "Unexpected error: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Download a cover image from Kavita.
     *
     * @param seriesId The series ID for the cover
     * @return KavitaResult containing the raw image bytes
     */
    suspend fun downloadSeriesCover(
        seriesId: Int
    ): KavitaResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(
                    "$normalizedBaseUrl/api/image" +
                        "/series-cover?seriesId=$seriesId"
                )
                .header("User-Agent", "Mimiral/0.1.0")

            addAuthHeader(requestBuilder)

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext KavitaResult.Error(
                    message = "Cover download failed: HTTP ${response.code}",
                    code = response.code
                )
            }

            val bytes = response.body?.bytes()
                ?: return@withContext KavitaResult.Error(
                    message = "Empty cover response",
                    code = response.code
                )
            Log.d(
                TAG,
                "Downloaded cover: ${bytes.size} bytes for series $seriesId"
            )
            KavitaResult.Success(bytes)
        } catch (e: IOException) {
            Log.e(TAG, "Network error downloading cover: ${e.message}", e)
            KavitaResult.Error(
                message = "Network error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error downloading cover: ${e.message}", e)
            KavitaResult.Error(
                message = "Unexpected error: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Download a book-specific cover image.
     *
     * @param chapterId The chapter ID whose cover to download
     * @return KavitaResult containing the raw image bytes
     */
    suspend fun downloadBookCover(
        chapterId: Int
    ): KavitaResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(
                    "$normalizedBaseUrl/api/image" +
                        "/book-cover?chapterId=$chapterId"
                )
                .header("User-Agent", "Mimiral/0.1.0")

            addAuthHeader(requestBuilder)

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext KavitaResult.Error(
                    message = "Book cover download failed: HTTP ${response.code}",
                    code = response.code
                )
            }

            val bytes = response.body?.bytes()
                ?: return@withContext KavitaResult.Error(
                    message = "Empty cover response",
                    code = response.code
                )
            Log.d(
                TAG,
                "Downloaded book cover: ${bytes.size} bytes " +
                    "for chapter $chapterId"
            )
            KavitaResult.Success(bytes)
        } catch (e: IOException) {
            Log.e(TAG, "Network error downloading book cover: ${e.message}", e)
            KavitaResult.Error(
                message = "Network error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Unexpected error downloading book cover: ${e.message}",
                e
            )
            KavitaResult.Error(
                message = "Unexpected error: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Push reading progress to Kavita.
     *
     * @param chapterId The chapter ID
     * @param pageNum The current page number
     * @param seriesId The series ID
     * @param volumeId The volume ID
     * @param libraryId The library ID
     * @param bookScrollId Optional scroll position identifier
     */
    suspend fun pushProgress(
        chapterId: Int,
        pageNum: Int,
        seriesId: Int,
        volumeId: Int,
        libraryId: Int,
        bookScrollId: String? = null
    ): KavitaResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val progress = KavitaProgress(
                chapterId = chapterId,
                pageNum = pageNum,
                seriesId = seriesId,
                volumeId = volumeId,
                libraryId = libraryId,
                bookScrollId = bookScrollId
            )
            val body = gson.toJson(progress).toRequestBody(JSON_MEDIA_TYPE)

            val requestBuilder = Request.Builder()
                .url("$normalizedBaseUrl/api/reader/progress")
                .post(body)
                .header("User-Agent", "Mimiral/0.1.0")

            addAuthHeader(requestBuilder)

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext KavitaResult.Error(
                    message = "Progress push failed: HTTP ${response.code}",
                    code = response.code
                )
            }
            Log.d(TAG, "Pushed progress: chapter=$chapterId page=$pageNum")
            KavitaResult.Success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Network error pushing progress: ${e.message}", e)
            KavitaResult.Error(
                message = "Network error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error pushing progress: ${e.message}", e)
            KavitaResult.Error(
                message = "Unexpected error: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Pull reading progress from Kavita for a series.
     *
     * @param seriesId The series ID
     */
    suspend fun pullProgress(
        seriesId: Int
    ): KavitaResult<List<KavitaProgress>> =
        getList(
            "/api/reader/get-progress?seriesId=$seriesId",
            object : TypeToken<List<KavitaProgress>>() {}
        )

    /**
     * Get bookmarks for a series from Kavita.
     *
     * @param seriesId The series ID
     */
    suspend fun getBookmarks(
        seriesId: Int
    ): KavitaResult<List<KavitaBookmark>> =
        getList(
            "/api/reader/chapter-bookmarks?seriesId=$seriesId",
            object : TypeToken<List<KavitaBookmark>>() {}
        )

    /**
     * Push a bookmark to Kavita.
     */
    suspend fun pushBookmark(
        chapterId: Int,
        pageNum: Int,
        seriesId: Int,
        volumeId: Int,
        libraryId: Int,
        bookScrollId: String? = null
    ): KavitaResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val bookmark = KavitaBookmark(
                chapterId = chapterId,
                pageNum = pageNum,
                seriesId = seriesId,
                volumeId = volumeId,
                libraryId = libraryId,
                bookScrollId = bookScrollId
            )
            val body = gson.toJson(bookmark).toRequestBody(JSON_MEDIA_TYPE)

            val requestBuilder = Request.Builder()
                .url("$normalizedBaseUrl/api/reader/bookmark")
                .post(body)
                .header("User-Agent", "Mimiral/0.1.0")

            addAuthHeader(requestBuilder)

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext KavitaResult.Error(
                    message = "Bookmark push failed: HTTP ${response.code}",
                    code = response.code
                )
            }
            Log.d(TAG, "Pushed bookmark: chapter=$chapterId page=$pageNum")
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
     * Remove a bookmark from Kavita.
     */
    suspend fun removeBookmark(
        seriesId: Int,
        chapterId: Int,
        pageNum: Int
    ): KavitaResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(
                    "$normalizedBaseUrl/api/reader/bookmark" +
                        "?seriesId=$seriesId" +
                        "&chapterId=$chapterId" +
                        "&pageNum=$pageNum"
                )
                .delete()
                .header("User-Agent", "Mimiral/0.1.0")

            addAuthHeader(requestBuilder)

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext KavitaResult.Error(
                    message = "Bookmark removal failed: HTTP ${response.code}",
                    code = response.code
                )
            }
            Log.d(
                TAG,
                "Removed bookmark: series=$seriesId " +
                    "chapter=$chapterId page=$pageNum"
            )
            KavitaResult.Success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Network error removing bookmark: ${e.message}", e)
            KavitaResult.Error(
                message = "Network error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Unexpected error removing bookmark: ${e.message}",
                e
            )
            KavitaResult.Error(
                message = "Unexpected error: ${e.message}",
                cause = e
            )
        }
    }

    // ---- Internal helpers ----

    private fun addAuthHeader(requestBuilder: Request.Builder) {
        val token = jwtToken
        val key = apiKey
        when {
            token != null -> requestBuilder.header("Authorization", "Bearer $token")
            key != null -> requestBuilder.header("ApiKey", key)
        }
    }

    private suspend fun <T> get(
        path: String,
        clazz: Class<T>
    ): KavitaResult<T> = fetch(path) { body ->
        gson.fromJson(body, clazz)
    }

    private suspend fun <T> get(
        path: String,
        typeToken: TypeToken<T>
    ): KavitaResult<T> = fetch(path) { body ->
        gson.fromJson<T>(body, typeToken.type)
    }

    private suspend fun <T> getList(
        path: String,
        typeToken: TypeToken<T>
    ): KavitaResult<T> = fetch(path) { body ->
        gson.fromJson<T>(body, typeToken.type)
    }

    private suspend inline fun <T> fetch(
        path: String,
        crossinline parser: (String) -> T
    ): KavitaResult<T> {
        val requestBuilder = Request.Builder()
            .url("$normalizedBaseUrl$path")
            .header("Accept", "application/json")
            .header("User-Agent", "Mimiral/0.1.0")

        addAuthHeader(requestBuilder)

        val request = requestBuilder.build()
        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            return KavitaResult.Error(
                message = "HTTP ${response.code}: ${response.message}",
                code = response.code
            )
        }

        val body = response.body?.string()
            ?: return KavitaResult.Error(
                message = "Empty response body",
                code = response.code
            )

        return try {
            KavitaResult.Success(parser(body))
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}", e)
            KavitaResult.Error(
                message = "Parse error: ${e.message}",
                cause = e
            )
        }
    }
}
