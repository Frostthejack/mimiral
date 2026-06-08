package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.mimiral.app.data.local.dao.ServerDao
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
    private val serverDao: ServerDao
) {
    companion object {
        private const val TAG = "KavitaStatsRepository"
        private const val NO_SERVER_MSG =
            "No Kavita server configured. Please add a Kavita server in Settings."
    }

    /**
     * Resolve the active server and create a KavitaClient.
     * Returns null if no server is configured.
     */
    private suspend fun createClientFromDb(): KavitaClient? {
        val activeServer = serverDao.getActiveServerByType("KAVITA") ?: return null
        if (activeServer.url.isBlank()) return null
        return KavitaClient.create(
            baseUrl = activeServer.url,
            apiKey = activeServer.apiKey
        )
    }

    /**
     * Get reading activity (pages read per day).
     * GET /api/Stats/reading-activity
     */
    suspend fun getReadingActivity(): KavitaResult<List<KavitaReadingActivity>> {
        val client = createClientFromDb()
            ?: return KavitaResult.Error(message = NO_SERVER_MSG)
        return try {
            client.getReadingActivity()
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
        val client = createClientFromDb()
            ?: return KavitaResult.Error(message = NO_SERVER_MSG)
        return try {
            client.getGenreBreakdown()
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
        val client = createClientFromDb()
            ?: return KavitaResult.Error(message = NO_SERVER_MSG)
        return try {
            client.getPagesPerYear()
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
        val client = createClientFromDb()
            ?: return KavitaResult.Error(message = NO_SERVER_MSG)
        return try {
            client.getReadingPace()
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
        val client = createClientFromDb()
            ?: return KavitaResult.Error(message = NO_SERVER_MSG)
        return try {
            client.getFavoriteAuthors()
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
        val client = createClientFromDb()
            ?: return KavitaResult.Error(message = NO_SERVER_MSG)
        return try {
            client.getSeriesReadingHistory(seriesId)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching series reading history: ${e.message}", e)
            KavitaResult.Error(
                message = "Failed to fetch series reading history: ${e.message}"
            )
        }
    }
}
