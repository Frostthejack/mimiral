package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.google.gson.Gson
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
 * HTTP client for Kavita mark-read / mark-unread API operations.
 *
 * Handles:
 * - POST /api/Reader/mark-chapter-read — mark a chapter as read
 * - POST /api/Reader/mark-volume-read — mark a volume as read
 * - POST /api/Reader/mark-volume-unread — mark a volume as unread
 * - POST /api/Series/mark-read — mark a series as read
 * - POST /api/Series/mark-unread — mark a series as unread
 * - POST /api/Series/mark-multiple-series-read — bulk mark series read
 * - POST /api/Series/mark-multiple-series-unread — bulk mark series unread
 * - POST /api/Tachiyomi/mark-chapter-until-as-read — catch-up mark
 * - Authentication via API key or JWT token
 * - Error handling with structured [KavitaResult]
 */
class KavitaMarkReadClient(
    private val client: OkHttpClient = defaultClient(),
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "KavitaMarkReadClient"
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
     * @param url The Kavita server base URL
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
     * POST /api/Reader/mark-chapter-read
     * Mark a single chapter as read.
     *
     * @param chapterId The Kavita chapter ID
     * @return [KavitaResult] indicating success or failure
     */
    suspend fun markChapterRead(chapterId: Int): KavitaResult<Unit> =
        post(
            path = "/api/Reader/mark-chapter-read",
            body = KavitaMarkChapterReadRequest(chapterId = chapterId),
            operationName = "mark-chapter-read"
        )

    /**
     * POST /api/Reader/mark-volume-read
     * Mark an entire volume as read.
     *
     * @param volumeId The Kavita volume ID
     * @return [KavitaResult] indicating success or failure
     */
    suspend fun markVolumeRead(volumeId: Int): KavitaResult<Unit> =
        post(
            path = "/api/Reader/mark-volume-read",
            body = KavitaMarkVolumeReadRequest(volumeId = volumeId),
            operationName = "mark-volume-read"
        )

    /**
     * POST /api/Reader/mark-volume-unread
     * Mark an entire volume as unread.
     *
     * @param volumeId The Kavita volume ID
     * @return [KavitaResult] indicating success or failure
     */
    suspend fun markVolumeUnread(volumeId: Int): KavitaResult<Unit> =
        post(
            path = "/api/Reader/mark-volume-unread",
            body = KavitaMarkVolumeReadRequest(volumeId = volumeId),
            operationName = "mark-volume-unread"
        )

    /**
     * POST /api/Series/mark-read
     * Mark an entire series as read.
     *
     * @param seriesId The Kavita series ID
     * @return [KavitaResult] indicating success or failure
     */
    suspend fun markSeriesRead(seriesId: Int): KavitaResult<Unit> =
        post(
            path = "/api/Series/mark-read",
            body = KavitaMarkSeriesReadRequest(seriesId = seriesId),
            operationName = "mark-series-read"
        )

    /**
     * POST /api/Series/mark-unread
     * Mark an entire series as unread.
     *
     * @param seriesId The Kavita series ID
     * @return [KavitaResult] indicating success or failure
     */
    suspend fun markSeriesUnread(seriesId: Int): KavitaResult<Unit> =
        post(
            path = "/api/Series/mark-unread",
            body = KavitaMarkSeriesReadRequest(seriesId = seriesId),
            operationName = "mark-series-unread"
        )

    /**
     * POST /api/Series/mark-multiple-series-read
     * Bulk mark multiple series as read.
     *
     * @param seriesIds List of Kavita series IDs
     * @return [KavitaResult] indicating success or failure
     */
    suspend fun markMultipleSeriesRead(seriesIds: List<Int>): KavitaResult<Unit> =
        post(
            path = "/api/Series/mark-multiple-series-read",
            body = KavitaMarkMultipleSeriesReadRequest(seriesIds = seriesIds),
            operationName = "mark-multiple-series-read"
        )

    /**
     * POST /api/Series/mark-multiple-series-unread
     * Bulk mark multiple series as unread.
     *
     * @param seriesIds List of Kavita series IDs
     * @return [KavitaResult] indicating success or failure
     */
    suspend fun markMultipleSeriesUnread(seriesIds: List<Int>): KavitaResult<Unit> =
        post(
            path = "/api/Series/mark-multiple-series-unread",
            body = KavitaMarkMultipleSeriesReadRequest(seriesIds = seriesIds),
            operationName = "mark-multiple-series-unread"
        )

    /**
     * POST /api/Tachiyomi/mark-chapter-until-as-read
     * Catch-up: mark all chapters up to and including the specified one as read.
     *
     * @param seriesId The Kavita series ID
     * @param chapterId The chapter ID to mark up to (inclusive)
     * @param volumesToInclude Number of volumes to include
     * @return [KavitaResult] indicating success or failure
     */
    suspend fun markChapterUntilRead(
        seriesId: Int,
        chapterId: Int,
        volumesToInclude: Int = 0
    ): KavitaResult<Unit> =
        post(
            path = "/api/Tachiyomi/mark-chapter-until-as-read",
            body = KavitaMarkChapterUntilReadRequest(
                seriesId = seriesId,
                chapterId = chapterId,
                volumesToInclude = volumesToInclude
            ),
            operationName = "mark-chapter-until-read"
        )

    // ---- Internal helpers ----

    /**
     * Generic POST helper for mark-read operations.
     */
    private suspend fun <T> post(
        path: String,
        body: T,
        operationName: String
    ): KavitaResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(body)
            val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl$path")
                .addHeaders()
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(
                    TAG,
                    "$operationName failed: ${response.code} " +
                        "${response.message} - $errorBody"
                )
                return@withContext KavitaResult.Error(
                    message = "HTTP ${response.code}: ${response.message}",
                    code = response.code
                )
            }

            Log.d(TAG, "$operationName succeeded")
            KavitaResult.Success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Network error in $operationName: ${e.message}", e)
            KavitaResult.Error(
                message = "Network error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in $operationName: ${e.message}", e)
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
