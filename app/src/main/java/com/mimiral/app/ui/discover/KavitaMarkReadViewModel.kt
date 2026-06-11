package com.mimiral.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.BrowseResult
import com.mimiral.app.data.remote.KavitaBrowseRepository
import com.mimiral.app.data.remote.kavita.KavitaMarkReadRepository
import com.mimiral.app.data.remote.kavita.KavitaMarkReadUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for mark read/unread operations on Kavita content.
 *
 * Provides functions to mark chapters, volumes, and series as read or unread,
 * and automatically refreshes the series/volume data after marking so that
 * the UI progress indicators update immediately.
 */
@HiltViewModel
class KavitaMarkReadViewModel @Inject constructor(
    private val markReadRepository: KavitaMarkReadRepository,
    private val browseRepository: KavitaBrowseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KavitaMarkReadUiState())
    val uiState: StateFlow<KavitaMarkReadUiState> = _uiState.asStateFlow()

    init {
        // Observe repository state and mirror into local state
        viewModelScope.launch {
            markReadRepository.uiState.collect { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * Mark a chapter as read, then refresh volume data.
     *
     * @param chapterId The Kavita chapter ID
     * @param seriesId The series ID (for refresh)
     */
    fun markChapterRead(chapterId: Int, seriesId: Int) {
        viewModelScope.launch {
            val success = markReadRepository.markChapterRead(chapterId)
            if (success) {
                // Refresh progress indicators
                refreshSeriesVolumes(seriesId)
            }
            // Errors are already reflected in _uiState via repository state mirroring
        }
    }

    /**
     * Mark a volume as read, then refresh volume data.
     *
     * @param volumeId The Kavita volume ID
     * @param seriesId The series ID (for refresh)
     */
    fun markVolumeRead(volumeId: Int, seriesId: Int) {
        viewModelScope.launch {
            val success = markReadRepository.markVolumeRead(volumeId)
            if (success) {
                refreshSeriesVolumes(seriesId)
            }
        }
    }

    /**
     * Mark a volume as unread, then refresh volume data.
     *
     * @param volumeId The Kavita volume ID
     * @param seriesId The series ID (for refresh)
     */
    fun markVolumeUnread(volumeId: Int, seriesId: Int) {
        viewModelScope.launch {
            val success = markReadRepository.markVolumeUnread(volumeId)
            if (success) {
                refreshSeriesVolumes(seriesId)
            }
        }
    }

    /**
     * Mark an entire series as read, then refresh.
     *
     * @param seriesId The Kavita series ID
     */
    fun markSeriesRead(seriesId: Int) {
        viewModelScope.launch {
            val success = markReadRepository.markSeriesRead(seriesId)
            if (success) {
                refreshSeriesVolumes(seriesId)
            }
        }
    }

    /**
     * Mark an entire series as unread, then refresh.
     *
     * @param seriesId The Kavita series ID
     */
    fun markSeriesUnread(seriesId: Int) {
        viewModelScope.launch {
            val success = markReadRepository.markSeriesUnread(seriesId)
            if (success) {
                refreshSeriesVolumes(seriesId)
            }
        }
    }

    /**
     * Bulk mark multiple series as read.
     *
     * @param seriesIds List of Kavita series IDs
     */
    fun markMultipleSeriesRead(seriesIds: List<Int>) {
        viewModelScope.launch {
            val success = markReadRepository.markMultipleSeriesRead(seriesIds)
            if (success) {
                seriesIds.forEach { refreshSeriesVolumes(it) }
            }
        }
    }

    /**
     * Bulk mark multiple series as unread.
     *
     * @param seriesIds List of Kavita series IDs
     */
    fun markMultipleSeriesUnread(seriesIds: List<Int>) {
        viewModelScope.launch {
            val success = markReadRepository.markMultipleSeriesUnread(seriesIds)
            if (success) {
                seriesIds.forEach { refreshSeriesVolumes(it) }
            }
        }
    }

    /**
     * Catch-up: mark all chapters up to and including the specified one as read.
     *
     * @param seriesId The Kavita series ID
     * @param chapterId The chapter ID to mark up to (inclusive)
     * @param volumesToInclude Number of volumes to include
     */
    fun markChapterUntilRead(
        seriesId: Int,
        chapterId: Int,
        volumesToInclude: Int = 0
    ) {
        viewModelScope.launch {
            val success = markReadRepository.markChapterUntilRead(
                seriesId = seriesId,
                chapterId = chapterId,
                volumesToInclude = volumesToInclude
            )
            if (success) {
                refreshSeriesVolumes(seriesId)
            }
        }
    }

    /**
     * Clear the current error message.
     */
    fun clearError() {
        markReadRepository.clearError()
    }

    /**
     * Refresh series volumes after a marking operation.
     * This triggers a re-fetch so the UI shows updated progress.
     */
    private suspend fun refreshSeriesVolumes(seriesId: Int) {
        when (val result = browseRepository.getVolumes(seriesId)) {
            is BrowseResult.Success -> {
                // Volumes refreshed — the KavitaSeriesViewModel will
                // pick up the new progress on its next load
            }
            is BrowseResult.Error -> {
                // Non-critical: marking succeeded, refresh failed
            }
            is BrowseResult.Loading -> {
                // No-op
            }
        }
    }
}
