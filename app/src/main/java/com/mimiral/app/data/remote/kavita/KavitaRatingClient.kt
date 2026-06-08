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
 * HTTP client for Kavita rating and review API operations.
 *
 * Handles:
 * - POST /api/Rating/series — rate a series (1-5 stars)
 * - POST /api/Rating/chapter — rate a chapter (1-5 stars)
 * - GET /api/Rating/overall-series — get community rating for a series
 * - POST /api/Review/series — write a series review
 * - POST /api/Review/chapter — write a chapter review
 * - GET /api/Review/all — get all reviews (optionally filtered)
 * - Authentication via API key or JWT token
 * - Error handling with structured [KavitaResult]
 */
class KavitaRatingClient(
    private val client: OkHttpClient = defaultClient(),
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "KavitaRatingClient"
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

    // ==================== Rating ====================

    /**
     * POST /api/Rating/series
     * Rate a series (1-5 stars).
     *
     * @param seriesId The series ID
     * @param rating The rating (1-5, or 0 to remove)
     * @return [KavitaResult] with the rating response
     */
    suspend fun rateSeries(
        seriesId: Int,
        rating: Int
    ): KavitaResult<KavitaRatingResponse> = withContext(Dispatchers.IO) {
        try {
            val request = KavitaSeriesRatingRequest(
                seriesId = seriesId,
                userRating = rating.coerceIn(0, 5)
            )
            val jsonBody = gson.toJson(request)
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Rating/series")
                .addHeaders()
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Rate series failed: ${response.code} $errorBody")
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

            val ratingResponse = gson.fromJson(
                responseBody,
                KavitaRatingResponse::class.java
            )
            Log.d(TAG, "Rated series $seriesId: $rating stars")
            KavitaResult.Success(ratingResponse)
        } catch (e: IOException) {
            Log.e(TAG, "Network error rating series: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error rating series: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }

    /**
     * POST /api/Rating/chapter
     * Rate a chapter (1-5 stars).
     *
     * @param chapterId The chapter ID
     * @param rating The rating (1-5, or 0 to remove)
     * @return [KavitaResult] with the rating response
     */
    suspend fun rateChapter(
        chapterId: Int,
        rating: Int
    ): KavitaResult<KavitaRatingResponse> = withContext(Dispatchers.IO) {
        try {
            val request = KavitaChapterRatingRequest(
                chapterId = chapterId,
                userRating = rating.coerceIn(0, 5)
            )
            val jsonBody = gson.toJson(request)
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Rating/chapter")
                .addHeaders()
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Rate chapter failed: ${response.code} $errorBody")
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

            val ratingResponse = gson.fromJson(
                responseBody,
                KavitaRatingResponse::class.java
            )
            Log.d(TAG, "Rated chapter $chapterId: $rating stars")
            KavitaResult.Success(ratingResponse)
        } catch (e: IOException) {
            Log.e(TAG, "Network error rating chapter: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error rating chapter: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }

    /**
     * GET /api/Rating/overall-series?seriesId={seriesId}
     * Get the community/overall rating for a series.
     *
     * @param seriesId The series ID
     * @return [KavitaResult] with the overall rating
     */
    suspend fun getOverallSeriesRating(
        seriesId: Int
    ): KavitaResult<KavitaOverallSeriesRating> = withContext(Dispatchers.IO) {
        try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Rating/overall-series?seriesId=$seriesId")
                .addHeaders()
                .get()
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Get overall rating failed: ${response.code} $errorBody")
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

            val overallRating = gson.fromJson(
                responseBody,
                KavitaOverallSeriesRating::class.java
            )
            Log.d(TAG, "Overall rating for series $seriesId: ${overallRating.totalRating}")
            KavitaResult.Success(overallRating)
        } catch (e: IOException) {
            Log.e(TAG, "Network error getting overall rating: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error getting overall rating: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }

    // ==================== Reviews ====================

    /**
     * POST /api/Review/series
     * Write a review for a series.
     *
     * @param seriesId The series ID
     * @param reviewBody The review text
     * @param tagline Optional short tagline
     * @return [KavitaResult] with the created review
     */
    suspend fun reviewSeries(
        seriesId: Int,
        reviewBody: String,
        tagline: String? = null
    ): KavitaResult<KavitaReview> = withContext(Dispatchers.IO) {
        try {
            val request = KavitaReviewRequest(
                seriesId = seriesId,
                reviewBody = reviewBody,
                tagline = tagline
            )
            val jsonBody = gson.toJson(request)
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Review/series")
                .addHeaders()
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Review series failed: ${response.code} $errorBody")
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

            val review = gson.fromJson(responseBody, KavitaReview::class.java)
            Log.d(TAG, "Created review for series $seriesId")
            KavitaResult.Success(review)
        } catch (e: IOException) {
            Log.e(TAG, "Network error creating review: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error creating review: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }

    /**
     * POST /api/Review/chapter
     * Write a review for a chapter.
     *
     * @param chapterId The chapter ID
     * @param reviewBody The review text
     * @param tagline Optional short tagline
     * @return [KavitaResult] with the created review
     */
    suspend fun reviewChapter(
        chapterId: Int,
        reviewBody: String,
        tagline: String? = null
    ): KavitaResult<KavitaReview> = withContext(Dispatchers.IO) {
        try {
            val request = KavitaReviewRequest(
                chapterId = chapterId,
                reviewBody = reviewBody,
                tagline = tagline
            )
            val jsonBody = gson.toJson(request)
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/Review/chapter")
                .addHeaders()
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Review chapter failed: ${response.code} $errorBody")
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

            val review = gson.fromJson(responseBody, KavitaReview::class.java)
            Log.d(TAG, "Created review for chapter $chapterId")
            KavitaResult.Success(review)
        } catch (e: IOException) {
            Log.e(TAG, "Network error creating review: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error creating review: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
        }
    }

    /**
     * GET /api/Review/all?seriesId={seriesId}
     * Get all reviews (optionally filtered by series or chapter).
     *
     * @param seriesId Optional series ID filter
     * @param chapterId Optional chapter ID filter
     * @return [KavitaResult] with list of reviews
     */
    suspend fun getReviews(
        seriesId: Int? = null,
        chapterId: Int? = null
    ): KavitaResult<List<KavitaReview>> = withContext(Dispatchers.IO) {
        try {
            val queryParams = buildString {
                seriesId?.let { append("seriesId=$it&") }
                chapterId?.let { append("chapterId=$it&") }
            }.trimEnd('&')

            val url = if (queryParams.isNotEmpty()) {
                "$baseUrl/api/Review/all?$queryParams"
            } else {
                "$baseUrl/api/Review/all"
            }

            val httpRequest = Request.Builder()
                .url(url)
                .addHeaders()
                .get()
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Get reviews failed: ${response.code} $errorBody")
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

            val type = object : TypeToken<List<KavitaReview>>() {}.type
            val reviews: List<KavitaReview> = gson.fromJson(responseBody, type)
            Log.d(TAG, "Got ${reviews.size} reviews")
            KavitaResult.Success(reviews)
        } catch (e: IOException) {
            Log.e(TAG, "Network error getting reviews: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error getting reviews: ${e.message}", e)
            KavitaResult.Error(message = "Unexpected error: ${e.message}", cause = e)
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
