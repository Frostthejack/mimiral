package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for Kavita REST API.
 *
 * Supports:
 * - GET /api/Library — list all libraries
 * - GET /api/Series/all?libraryId={id} — all series in a library
 * - GET /api/Series/metadata?seriesId={id} — series metadata with volumes
 * - GET /api/Image/cover?coverImage={coverImageId} — download cover image
 * - GET /api/Home — test authentication
 *
 * Authentication: API key (preferred) via X-Api-Key header,
 * or HTTP Basic auth with username/password.
 */
class KavitaApiClient(
    private val baseUrl: String,
    private val apiKey: String? = null,
    private val username: String? = null,
    private val password: String? = null,
    private val client: OkHttpClient = defaultClient()
) {
    companion object {
        private const val TAG = "KavitaApiClient"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L

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

    private val gson = Gson()
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    /**
     * List all Kavita libraries.
     * GET /api/Library
     */
    suspend fun getLibraries(): KavitaResult<List<KavitaLibrary>> = withContext(Dispatchers.IO) {
        try {
            val url = "$normalizedBaseUrl/api/Library"
            val response = executeRequest(url)
            if (response is KavitaResult.Error) return@withContext response

            @Suppress("UNCHECKED_CAST")
            val body = (response as KavitaResult.Success<String>).data
            val type = object : TypeToken<List<KavitaLibrary>>() {}.type
            val libraries: List<KavitaLibrary> = gson.fromJson(body, type)
            Log.d(TAG, "Fetched ${libraries.size} libraries")
            KavitaResult.Success(libraries)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching libraries: ${e.message}", e)
            KavitaResult.Error("Error fetching libraries: ${e.message}", cause = e)
        }
    }

    /**
     * Get all series in a library.
     * GET /api/Series/all?libraryId={id}
     *
     * Returns series list with basic info (id, name, format, pages, coverImage).
     */
    suspend fun getSeriesForLibrary(
        libraryId: Int
    ): KavitaResult<List<KavitaSeries>> = withContext(Dispatchers.IO) {
        try {
            val url = "$normalizedBaseUrl/api/Series/all?libraryId=$libraryId"
            val response = executeRequest(url)
            if (response is KavitaResult.Error) return@withContext response

            @Suppress("UNCHECKED_CAST")
            val body = (response as KavitaResult.Success<String>).data
            val type = object : TypeToken<List<KavitaSeries>>() {}.type
            val series: List<KavitaSeries> = gson.fromJson(body, type)
            Log.d(TAG, "Fetched ${series.size} series for library $libraryId")
            KavitaResult.Success(series)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching series: ${e.message}", e)
            KavitaResult.Error("Error fetching series: ${e.message}", cause = e)
        }
    }

    /**
     * Get detailed series metadata including volumes and chapters.
     * GET /api/Series/metadata?seriesId={id}
     */
    suspend fun getSeriesMetadata(
        seriesId: Int
    ): KavitaResult<KavitaSeries> = withContext(Dispatchers.IO) {
        try {
            val url = "$normalizedBaseUrl/api/Series/metadata?seriesId=$seriesId"
            val response = executeRequest(url)
            if (response is KavitaResult.Error) return@withContext response

            @Suppress("UNCHECKED_CAST")
            val body = (response as KavitaResult.Success<String>).data
            val series: KavitaSeries = gson.fromJson(body, KavitaSeries::class.java)
            Log.d(TAG, "Fetched metadata for series ${series.name}")
            KavitaResult.Success(series)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching series metadata: ${e.message}", e)
            KavitaResult.Error(
                "Error fetching series metadata: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Download a cover image.
     * GET /api/Image/cover?coverImage={coverImageId}
     *
     * @param coverImageId The cover image ID from series/volume/chapter
     * @return Raw bytes of the cover image (JPEG/PNG/WebP)
     */
    suspend fun getCoverImage(
        coverImageId: String
    ): KavitaResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$normalizedBaseUrl/api/Image/cover?coverImage=$coverImageId"
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mimiral/0.1.0")

            addAuthHeaders(requestBuilder)

            val request = requestBuilder.build()
            val okResponse = client.newCall(request).execute()

            if (!okResponse.isSuccessful) {
                return@withContext KavitaResult.Error(
                    "HTTP ${okResponse.code}: ${okResponse.message}",
                    code = okResponse.code
                )
            }

            val bytes = okResponse.body?.bytes()
                ?: return@withContext KavitaResult.Error("Empty cover image response")

            Log.d(TAG, "Downloaded cover image: ${bytes.size} bytes")
            KavitaResult.Success(bytes)
        } catch (e: IOException) {
            Log.e(TAG, "Network error downloading cover: ${e.message}", e)
            KavitaResult.Error("Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading cover: ${e.message}", e)
            KavitaResult.Error("Error downloading cover: ${e.message}", cause = e)
        }
    }

    /**
     * Resolve a cover image URL for use with Coil image loader.
     * Kavita cover images are UUIDs that need /api/Image/cover.
     * External URLs are full HTTP(S) URLs.
     */
    fun resolveCoverImageUrl(coverImage: String?): String? {
        if (coverImage.isNullOrBlank()) return null
        if (coverImage.startsWith("http://") || coverImage.startsWith("https://")) {
            return coverImage
        }
        return "$normalizedBaseUrl/api/Image/cover?coverImage=$coverImage"
    }

    /**
     * Test authentication with the Kavita API.
     * GET /api/Home — lightweight endpoint to verify credentials.
     */
    suspend fun testConnection(): KavitaResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "$normalizedBaseUrl/api/Home"
            val response = executeRequest(url)
            when (response) {
                is KavitaResult.Success -> KavitaResult.Success(true)
                is KavitaResult.Error -> response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed: ${e.message}", e)
            KavitaResult.Error("Connection test failed: ${e.message}", cause = e)
        }
    }

    private fun executeRequest(url: String): KavitaResult<String> {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Mimiral/0.1.0")

        addAuthHeaders(requestBuilder)

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            Log.w(TAG, "Kavita API error: ${response.code} $errorBody")
            return KavitaResult.Error(
                "HTTP ${response.code}: ${response.message}",
                code = response.code
            )
        }

        val body = response.body?.string()
            ?: return KavitaResult.Error("Empty response body")

        return KavitaResult.Success(body)
    }

    private fun addAuthHeaders(requestBuilder: Request.Builder) {
        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("X-Api-Key", apiKey)
        } else if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            requestBuilder.header("Authorization", Credentials.basic(username, password))
        }
    }
}
