package com.mimiral.app.data.remote.kavita

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Kavita server statistics.
 *
 * Provides access to the Kavita Stats API endpoints:
 * - reading-activity: pages read per day
 * - genre-breakdown: pages read per genre
 * - pages-per-year: annual reading volume
 * - reading-pace: monthly pace trend
 * - favorite-authors: top authors by pages read
 * - reading-history/series/{id}: series-specific history
 *
 * All calls resolve the active Kavita server from the database per request
 * so they work with whichever server is currently configured.
 */
@Singleton
class KavitaStatsRepository @Inject constructor(
    private val kavitaApi: KavitaApi
) {
    companion object {
        private const val TAG = "KavitaStatsRepository"
    }

    /**
     * Get reading activity (pages read per day).
     * GET /api/Stats/reading-activity
     */
    suspend fun getReadingActivity(): KavitaResult<List<KavitaReadingActivity>> {
        return try {
            val response = kavitaApi.getReadingActivity()
            if (response.isSuccessful) {
                KavitaResult.Success(response.body() ?: emptyList())
            } else {
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: ${response.message()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching reading activity: ${e.message}", e)
            KavitaResult.Error(message = "Failed to fetch reading activity: ${e.message}")
        }
    }

    /**
     * Get genre breakdown (pages read per genre).
     * GET /api/Stats/genre-breakdown
     */
    suspend fun getGenreBreakdown(): KavitaResult<List<KavitaGenreBreakdown>> {
        return try {
            val response = kavitaApi.getGenreBreakdown()
            if (response.isSuccessful) {
                KavitaResult.Success(response.body() ?: emptyList())
            } else {
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: ${response.message()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching genre breakdown: ${e.message}", e)
            KavitaResult.Error(message = "Failed to fetch genre breakdown: ${e.message}")
        }
    }

    /**
     * Get pages read per year.
     * GET /api/Stats/pages-per-year
     */
    suspend fun getPagesPerYear(): KavitaResult<List<KavitaPagesPerYear>> {
        return try {
            val response = kavitaApi.getPagesPerYear()
            if (response.isSuccessful) {
                KavitaResult.Success(response.body() ?: emptyList())
            } else {
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: ${response.message()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching pages per year: ${e.message}", e)
            KavitaResult.Error(message = "Failed to fetch pages per year: ${e.message}")
        }
    }

    /**
     * Get reading pace trend (monthly rolling average).
     * GET /api/Stats/reading-pace
     */
    suspend fun getReadingPace(): KavitaResult<List<KavitaReadingPace>> {
        return try {
            val response = kavitaApi.getReadingPace()
            if (response.isSuccessful) {
                KavitaResult.Success(response.body() ?: emptyList())
            } else {
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: ${response.message()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching reading pace: ${e.message}", e)
            KavitaResult.Error(message = "Failed to fetch reading pace: ${e.message}")
        }
    }

    /**
     * Get favorite authors ordered by pages read.
     * GET /api/Stats/favorite-authors
     */
    suspend fun getFavoriteAuthors(): KavitaResult<List<KavitaFavoriteAuthor>> {
        return try {
            val response = kavitaApi.getFavoriteAuthors()
            if (response.isSuccessful) {
                KavitaResult.Success(response.body() ?: emptyList())
            } else {
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: ${response.message()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching favorite authors: ${e.message}", e)
            KavitaResult.Error(message = "Failed to fetch favorite authors: ${e.message}")
        }
    }

    /**
     * Get series-specific reading history.
     * GET /api/Stats/reading-history/series/{seriesId}
     */
    suspend fun getSeriesReadingHistory(
        seriesId: Int
    ): KavitaResult<KavitaSeriesReadingHistory> {
        return try {
            val response = kavitaApi.getSeriesReadingHistory(seriesId)
            if (response.isSuccessful) {
                KavitaResult.Success(
                    response.body() ?: KavitaSeriesReadingHistory(
                        seriesId = seriesId,
                        totalPagesRead = 0
                    )
                )
            } else {
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: ${response.message()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching series reading history: ${e.message}", e)
            KavitaResult.Error(message = "Failed to fetch series reading history: ${e.message}")
        }
    }
}
