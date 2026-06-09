package com.mimiral.app.data.remote.kavita

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Repository for Kavita Want To Read feature.
 *
 * Provides:
 * - Toggle series in/out of Want To Read list
 * - Paginated listing with search, library filter, and sort
 * - Auto-cleanup of fully-read series
 * - In-memory cache of the current page for UI state
 */
@Singleton
class KavitaWantToReadRepository @Inject constructor(
    private val kavitaApi: KavitaApi
) {
    companion object {
        private const val TAG = "KavitaWantToRead"
        private const val DEFAULT_PAGE_SIZE = 20
    }

    // ── UI State ──

    private val _seriesList = MutableStateFlow<List<KavitaWantToReadSeries>>(emptyList())
    val seriesList: StateFlow<List<KavitaWantToReadSeries>> = _seriesList.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Track which series are in the WTR list for toggle chip state
    private val _wantToReadSeriesIds = MutableStateFlow<Set<Int>>(emptySet())
    val wantToReadSeriesIds: StateFlow<Set<Int>> = _wantToReadSeriesIds.asStateFlow()

    // ── API Operations ──

    /**
     * Load the Want To Read list for a given page.
     *
     * @param pageNumber 0-based page index
     * @param pageSize Items per page
     * @param filter Optional filter parameters
     */
    suspend fun loadWantToRead(
        pageNumber: Int = 0,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        filter: KavitaWantToReadFilter = KavitaWantToReadFilter()
    ) = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val response = kavitaApi.getWantToRead(
                pageNumber = pageNumber,
                pageSize = pageSize,
                searchQuery = filter.searchQuery,
                libraryId = filter.libraryId,
                sortBy = filter.sortBy?.value,
                sortDirection = filter.sortDirection.value
            )
            if (response.isSuccessful) {
                val body = response.body()!!
                _seriesList.value = body.series
                _totalCount.value = body.totalCount
                _currentPage.value = body.pageNumber
                // Update tracked IDs
                _wantToReadSeriesIds.value = _wantToReadSeriesIds.value
                    .plus(body.series.map { it.id }.toSet())
                Log.d(
                    TAG,
                    "Loaded ${body.series.size} WTR series (page " +
                        "$pageNumber, total ${body.totalCount})"
                )
            } else {
                val msg = "Failed to load Want To Read: HTTP ${response.code()}"
                _errorMessage.value = msg
                Log.e(TAG, msg)
            }
        } catch (e: Exception) {
            val msg = "Error loading Want To Read: ${e.message}"
            _errorMessage.value = msg
            Log.e(TAG, msg, e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Add a series to the Want To Read list.
     *
     * @param seriesId The series ID to add
     * @return true if successful
     */
    suspend fun addToWantToRead(seriesId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = kavitaApi.addToWantToRead(KavitaWantToReadAddRequest(seriesId))
            if (response.isSuccessful) {
                _wantToReadSeriesIds.value = _wantToReadSeriesIds.value + seriesId
                Log.d(TAG, "Added series $seriesId to Want To Read")
                true
            } else {
                Log.e(TAG, "Failed to add series $seriesId: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding series $seriesId to WTR: ${e.message}", e)
            false
        }
    }

    /**
     * Remove a series from the Want To Read list.
     *
     * @param seriesId The series ID to remove
     * @return true if successful
     */
    suspend fun removeFromWantToRead(seriesId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = kavitaApi.removeFromWantToRead(KavitaWantToReadRemoveRequest(seriesId))
            if (response.isSuccessful) {
                _wantToReadSeriesIds.value = _wantToReadSeriesIds.value - seriesId
                // Also remove from current list if present
                _seriesList.value = _seriesList.value.filter { it.id != seriesId }
                _totalCount.value = maxOf(0, _totalCount.value - 1)
                Log.d(TAG, "Removed series $seriesId from Want To Read")
                true
            } else {
                Log.e(TAG, "Failed to remove series $seriesId: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing series $seriesId from WTR: ${e.message}", e)
            false
        }
    }

    /**
     * Toggle a series in/out of the Want To Read list.
     *
     * @param seriesId The series ID to toggle
     * @return true if the series is now in the list, false if not
     */
    suspend fun toggleWantToRead(seriesId: Int): Boolean {
        val isInList = _wantToReadSeriesIds.value.contains(seriesId)
        return if (isInList) {
            removeFromWantToRead(seriesId)
            // Now not in list
            false
        } else {
            val success = addToWantToRead(seriesId)
            success
        }
    }

    /**
     * Check if a series is in the Want To Read list.
     */
    fun isInWantToRead(seriesId: Int): Boolean =
        _wantToReadSeriesIds.value.contains(seriesId)

    /**
     * Run auto-cleanup: remove fully-read series from Want To Read.
     *
     * @return true if cleanup was successful
     */
    suspend fun cleanupWantToRead(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = kavitaApi.cleanupWantToRead()
            if (response.isSuccessful) {
                Log.d(TAG, "Want To Read cleanup completed")
                true
            } else {
                Log.e(TAG, "WTR cleanup failed: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "WTR cleanup error: ${e.message}", e)
            false
        }
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Reset pagination state.
     */
    fun reset() {
        _seriesList.value = emptyList()
        _totalCount.value = 0
        _currentPage.value = 0
        _errorMessage.value = null
    }
}
