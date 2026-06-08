package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.mimiral.app.data.local.dao.ServerDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Kavita rating and review operations.
 *
 * Orchestrates:
 * - Rating series (1-5 stars) and retrieving community ratings
 * - Rating chapters (1-5 stars)
 * - Writing reviews for series and chapters
 * - Fetching reviews (filtered by series or chapter)
 * - Server configuration initialization from active server
 */
@Singleton
class KavitaRatingRepository @Inject constructor(
    private val client: KavitaRatingClient,
    private val serverDao: ServerDao
) {
    companion object {
        private const val TAG = "KavitaRating"
    }

    /**
     * Initialize the Kavita client from the active server configuration.
     */
    private suspend fun initClient(): Boolean {
        val server = serverDao.getActiveServerByType("KAVITA") ?: run {
            Log.w(TAG, "No active Kavita server configured")
            return false
        }

        client.configure(
            url = server.url,
            key = server.apiKey,
            token = server.jwtToken,
            user = server.username,
            pass = server.password
        )
        return true
    }

    // ==================== Rating ====================

    /**
     * Rate a series (1-5 stars).
     *
     * @param seriesId The series ID
     * @param rating The rating (1-5, or 0 to remove)
     * @return The rating response, or null on failure
     */
    suspend fun rateSeries(
        seriesId: Int,
        rating: Int
    ): KavitaRatingResponse? {
        if (!initClient()) return null

        return when (val result = client.rateSeries(seriesId, rating)) {
            is KavitaResult.Success -> {
                Log.d(TAG, "Rated series $seriesId: $rating stars")
                result.data
            }
            is KavitaResult.Error -> {
                Log.w(TAG, "Failed to rate series $seriesId: ${result.message}")
                null
            }
        }
    }

    /**
     * Rate a chapter (1-5 stars).
     *
     * @param chapterId The chapter ID
     * @param rating The rating (1-5, or 0 to remove)
     * @return The rating response, or null on failure
     */
    suspend fun rateChapter(
        chapterId: Int,
        rating: Int
    ): KavitaRatingResponse? {
        if (!initClient()) return null

        return when (val result = client.rateChapter(chapterId, rating)) {
            is KavitaResult.Success -> {
                Log.d(TAG, "Rated chapter $chapterId: $rating stars")
                result.data
            }
            is KavitaResult.Error -> {
                Log.w(TAG, "Failed to rate chapter $chapterId: ${result.message}")
                null
            }
        }
    }

    /**
     * Get the community/overall rating for a series.
     *
     * @param seriesId The series ID
     * @return The overall rating, or null on failure
     */
    suspend fun getOverallSeriesRating(
        seriesId: Int
    ): KavitaOverallSeriesRating? {
        if (!initClient()) return null

        return when (val result = client.getOverallSeriesRating(seriesId)) {
            is KavitaResult.Success -> {
                Log.d(TAG, "Overall rating for series $seriesId: ${result.data.totalRating}")
                result.data
            }
            is KavitaResult.Error -> {
                Log.w(TAG, "Failed to get overall rating for series $seriesId: ${result.message}")
                null
            }
        }
    }

    // ==================== Reviews ====================

    /**
     * Write a review for a series.
     *
     * @param seriesId The series ID
     * @param reviewBody The review text
     * @param tagline Optional short tagline/summary
     * @return The created review, or null on failure
     */
    suspend fun reviewSeries(
        seriesId: Int,
        reviewBody: String,
        tagline: String? = null
    ): KavitaReview? {
        if (!initClient()) return null

        return when (val result = client.reviewSeries(seriesId, reviewBody, tagline)) {
            is KavitaResult.Success -> {
                Log.d(TAG, "Created review for series $seriesId")
                result.data
            }
            is KavitaResult.Error -> {
                Log.w(TAG, "Failed to review series $seriesId: ${result.message}")
                null
            }
        }
    }

    /**
     * Write a review for a chapter.
     *
     * @param chapterId The chapter ID
     * @param reviewBody The review text
     * @param tagline Optional short tagline/summary
     * @return The created review, or null on failure
     */
    suspend fun reviewChapter(
        chapterId: Int,
        reviewBody: String,
        tagline: String? = null
    ): KavitaReview? {
        if (!initClient()) return null

        return when (val result = client.reviewChapter(chapterId, reviewBody, tagline)) {
            is KavitaResult.Success -> {
                Log.d(TAG, "Created review for chapter $chapterId")
                result.data
            }
            is KavitaResult.Error -> {
                Log.w(TAG, "Failed to review chapter $chapterId: ${result.message}")
                null
            }
        }
    }

    /**
     * Get reviews (optionally filtered by series or chapter).
     *
     * @param seriesId Optional series ID filter
     * @param chapterId Optional chapter ID filter
     * @return List of reviews (may be empty)
     */
    suspend fun getReviews(
        seriesId: Int? = null,
        chapterId: Int? = null
    ): List<KavitaReview> {
        if (!initClient()) return emptyList()

        return when (val result = client.getReviews(seriesId, chapterId)) {
            is KavitaResult.Success -> {
                Log.d(TAG, "Got ${result.data.size} reviews")
                result.data
            }
            is KavitaResult.Error -> {
                Log.w(TAG, "Failed to get reviews: ${result.message}")
                emptyList()
            }
        }
    }
}
