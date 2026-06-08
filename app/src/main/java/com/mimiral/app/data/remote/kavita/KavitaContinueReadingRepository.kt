package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.mimiral.app.data.local.dao.ServerDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Continue Reading, Next/Prev Chapter, On Deck, and Time Left features.
 *
 * Provides:
 * - Continue Reading: fetch where user left off, deep link into reader
 * - Next/Prev Chapter: seamless chapter transition at book boundaries
 * - On Deck: home shelf of actively-read series
 * - Time Left: estimated reading time remaining for a series
 */
@Singleton
class KavitaContinueReadingRepository @Inject constructor(
    private val kavitaApi: KavitaApi,
    private val serverDao: ServerDao
) {
    companion object {
        private const val TAG = "KavitaContinueReading"
    }

    // ── Continue Reading point ──

    private val _continuePoint = MutableStateFlow<KavitaContinuePointDto?>(null)
    val continuePoint: StateFlow<KavitaContinuePointDto?> = _continuePoint.asStateFlow()

    // ── On Deck series ──

    private val _onDeckSeries = MutableStateFlow<List<KavitaOnDeckDto>>(emptyList())
    val onDeckSeries: StateFlow<List<KavitaOnDeckDto>> = _onDeckSeries.asStateFlow()

    // ── Time Left cache (per series) ──

    private val _timeLeftCache = MutableStateFlow<Map<Int, KavitaTimeLeftDto>>(emptyMap())
    val timeLeftCache: StateFlow<Map<Int, KavitaTimeLeftDto>> = _timeLeftCache.asStateFlow()

    // ── Server check ──

    private suspend fun hasActiveServer(): Boolean {
        return try {
            serverDao.getActiveServerByType("KAVITA") != null
        } catch (_: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Continue Reading
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch the continue-reading point from Kavita.
     * Returns null if no server is configured or if the request fails.
     */
    suspend fun fetchContinuePoint(): KavitaContinuePointDto? {
        if (!hasActiveServer()) return null

        return try {
            val response = kavitaApi.getContinuePoint()
            if (response.isSuccessful) {
                val point = response.body()
                _continuePoint.value = point
                point
            } else {
                Log.w(TAG, "getContinuePoint failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching continue point: ${e.message}")
            null
        }
    }

    /**
     * Build a continue-reading context from the current continue point.
     * Used to deep-link into the reader at the user's last position.
     */
    fun buildContinueReadingContext(): KavitaContinueReadingContext? {
        val point = _continuePoint.value ?: return null
        if (point.seriesId == 0 || point.chapterId == 0) return null

        return KavitaContinueReadingContext(
            seriesId = point.seriesId,
            volumeId = point.volumeId,
            chapterId = point.chapterId,
            pageNum = point.pageNum,
            libraryId = point.libraryId,
            seriesName = point.seriesName,
            bookScrollId = point.bookScrollId
        )
    }

    // ═══════════════════════════════════════════════════════════
    // Next / Prev Chapter
    // ═══════════════════════════════════════════════════════════

    /**
     * Get the next chapter for seamless end-of-chapter navigation.
     * @return Next chapter info, or null if no next chapter or request failed
     */
    suspend fun getNextChapter(
        seriesId: Int,
        volumeId: Int,
        chapterId: Int
    ): KavitaNextChapterDto? {
        if (!hasActiveServer()) return null

        return try {
            val response = kavitaApi.getNextChapter(seriesId, volumeId, chapterId)
            if (response.isSuccessful) {
                val next = response.body()
                if (next != null && next.id > 0) next else null
            } else {
                Log.w(TAG, "getNextChapter failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting next chapter: ${e.message}")
            null
        }
    }

    /**
     * Get the previous chapter for start-of-chapter navigation.
     * @return Previous chapter info, or null if no previous chapter or request failed
     */
    suspend fun getPrevChapter(
        seriesId: Int,
        volumeId: Int,
        chapterId: Int
    ): KavitaPrevChapterDto? {
        if (!hasActiveServer()) return null

        return try {
            val response = kavitaApi.getPrevChapter(seriesId, volumeId, chapterId)
            if (response.isSuccessful) {
                val prev = response.body()
                if (prev != null && prev.id > 0) prev else null
            } else {
                Log.w(TAG, "getPrevChapter failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting prev chapter: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    // On Deck
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch the On Deck shelf — series the user is actively reading.
     * Updates [onDeckSeries] StateFlow.
     */
    suspend fun fetchOnDeck(): List<KavitaOnDeckDto> {
        if (!hasActiveServer()) return emptyList()

        return try {
            val response = kavitaApi.getOnDeck()
            if (response.isSuccessful) {
                val series = response.body() ?: emptyList()
                _onDeckSeries.value = series
                series
            } else {
                Log.w(TAG, "getOnDeck failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching on-deck: ${e.message}")
            emptyList()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Time Left
    // ═══════════════════════════════════════════════════════════

    /**
     * Get estimated reading time remaining for a series.
     * Caches the result in [timeLeftCache].
     *
     * @param seriesId The series ID
     * @param libraryId The library ID
     * @return Time remaining estimate, or null on failure
     */
    suspend fun getTimeLeft(
        seriesId: Int,
        libraryId: Int
    ): KavitaTimeLeftDto? {
        if (!hasActiveServer()) return null

        return try {
            val response = kavitaApi.getTimeLeft(seriesId, libraryId)
            if (response.isSuccessful) {
                val timeLeft = response.body()
                if (timeLeft != null) {
                    _timeLeftCache.value = _timeLeftCache.value + (seriesId to timeLeft)
                }
                timeLeft
            } else {
                Log.w(TAG, "getTimeLeft failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting time left: ${e.message}")
            null
        }
    }

    /**
     * Get cached time-left for a series (no network call).
     */
    fun getCachedTimeLeft(seriesId: Int): KavitaTimeLeftDto? {
        return _timeLeftCache.value[seriesId]
    }
}
