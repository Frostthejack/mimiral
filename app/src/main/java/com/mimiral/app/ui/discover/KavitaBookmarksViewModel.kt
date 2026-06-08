package com.mimiral.app.ui.discover

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.kavita.KavitaBookmarkDto
import com.mimiral.app.data.remote.kavita.KavitaBookmarkGroup
import com.mimiral.app.data.remote.kavita.KavitaBookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KavitaBookmarksUiState(
    val isLoading: Boolean = false,
    val bookmarks: List<KavitaBookmarkDto> = emptyList(),
    val groupedBookmarks: List<KavitaBookmarkGroup> = emptyList(),
    val expandedSeries: Set<Int> = emptySet(),
    val expandedVolume: Set<String> = emptySet(), // "seriesId_volumeId" key
    val selectedBookmark: KavitaBookmarkDto? = null,
    val isExporting: Boolean = false,
    val exportSuccess: Boolean = false,
    val exportPath: String? = null,
    val errorMessage: String? = null,
    val filterQuery: String = ""
)

/**
 * ViewModel for the Kavita Bookmarks viewer screen.
 *
 * Supports:
 * - Loading all bookmarks grouped by series/volume/chapter
 * - Expand/collapse navigation of bookmark groups
 * - Filtering bookmarks by search query
 * - Bookmark removal (unbookmark) via the API
 * - Export all bookmarks to a file
 */
@HiltViewModel
class KavitaBookmarksViewModel @Inject constructor(
    private val bookmarkRepository: KavitaBookmarkRepository
) : ViewModel() {

    companion object {
        private const val TAG = "KavitaBookmarksVM"
    }

    private val _uiState = MutableStateFlow(KavitaBookmarksUiState())
    val uiState: StateFlow<KavitaBookmarksUiState> = _uiState.asStateFlow()

    init {
        loadAllBookmarks()
    }

    /**
     * Fetch all bookmarks from Kavita and group them for display.
     */
    fun loadAllBookmarks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val bookmarks = bookmarkRepository.getAllBookmarks()
                val grouped = bookmarkRepository.groupBookmarks(bookmarks)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    bookmarks = bookmarks,
                    groupedBookmarks = grouped
                )
                Log.d(TAG, "Loaded ${bookmarks.size} bookmarks in ${grouped.size} series groups")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading bookmarks: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load bookmarks: ${e.message}"
                )
            }
        }
    }

    /**
     * Toggle expansion of a series group.
     */
    fun toggleSeriesExpanded(seriesId: Int) {
        val current = _uiState.value.expandedSeries
        _uiState.value = _uiState.value.copy(
            expandedSeries = if (seriesId in current) current - seriesId else current + seriesId
        )
    }

    /**
     * Toggle expansion of a volume group within a series.
     */
    fun toggleVolumeExpanded(seriesId: Int, volumeId: Int) {
        val key = "${seriesId}_$volumeId"
        val current = _uiState.value.expandedVolume
        _uiState.value = _uiState.value.copy(
            expandedVolume = if (key in current) current - key else current + key
        )
    }

    /**
     * Expand all groups.
     */
    fun expandAll() {
        val state = _uiState.value
        val seriesIds = state.groupedBookmarks.map { it.seriesId }.toSet()
        val volumeKeys = state.groupedBookmarks.flatMap { series ->
            series.volumes.map { "${series.seriesId}_${it.volumeId}" }
        }.toSet()
        _uiState.value = state.copy(
            expandedSeries = seriesIds,
            expandedVolume = volumeKeys
        )
    }

    /**
     * Collapse all groups.
     */
    fun collapseAll() {
        _uiState.value = _uiState.value.copy(
            expandedSeries = emptySet(),
            expandedVolume = emptySet()
        )
    }

    /**
     * Select a bookmark for detail view.
     */
    fun selectBookmark(bookmark: KavitaBookmarkDto?) {
        _uiState.value = _uiState.value.copy(selectedBookmark = bookmark)
    }

    /**
     * Remove (unbookmark) a bookmark from Kavita.
     */
    fun removeBookmark(bookmark: KavitaBookmarkDto) {
        viewModelScope.launch {
            val success = bookmarkRepository.removeBookmark(
                page = bookmark.page,
                chapterId = bookmark.chapterId,
                seriesId = bookmark.seriesId,
                libraryId = bookmark.libraryId
            )
            if (success) {
                // Refresh the list
                loadAllBookmarks()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to remove bookmark"
                )
            }
        }
    }

    /**
     * Export all bookmarks to a file.
     *
     * @param exportDir Directory to save the exported file
     */
    fun exportBookmarks(exportDir: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportSuccess = false)
            try {
                val result = bookmarkRepository.exportBookmarks()
                if (result != null) {
                    val (bytes, filename) = result
                    val file = withContext(Dispatchers.IO) {
                        val exportFile = File(exportDir, filename)
                        exportFile.writeBytes(bytes)
                        exportFile
                    }
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportSuccess = true,
                        exportPath = file.absolutePath
                    )
                    Log.d(TAG, "Exported bookmarks to ${file.absolutePath}")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = "Export returned no data"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting bookmarks: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    errorMessage = "Export failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Filter bookmarks by search query.
     * Filters across series name, volume name, and chapter name.
     */
    fun setFilterQuery(query: String) {
        val state = _uiState.value
        val newFiltered = if (query.isBlank()) {
            state.bookmarks
        } else {
            val lowerQuery = query.lowercase()
            state.bookmarks.filter { bm ->
                (bm.seriesName?.lowercase()?.contains(lowerQuery) == true) ||
                    (bm.volumeName?.lowercase()?.contains(lowerQuery) == true) ||
                    (bm.chapterName?.lowercase()?.contains(lowerQuery) == true)
            }
        }
        val grouped = bookmarkRepository.groupBookmarks(newFiltered)
        _uiState.value = state.copy(
            filterQuery = query,
            groupedBookmarks = grouped
        )
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Reset export state.
     */
    fun resetExportState() {
        _uiState.value = _uiState.value.copy(
            isExporting = false,
            exportSuccess = false,
            exportPath = null
        )
    }
}
