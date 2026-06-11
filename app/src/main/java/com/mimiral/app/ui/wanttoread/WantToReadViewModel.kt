package com.mimiral.app.ui.wanttoread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.kavita.KavitaSortDirection
import com.mimiral.app.data.remote.kavita.KavitaWantToReadFilter
import com.mimiral.app.data.remote.kavita.KavitaWantToReadRepository
import com.mimiral.app.data.remote.kavita.KavitaWantToReadSeries
import com.mimiral.app.data.remote.kavita.KavitaWantToReadSort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Want To Read screen.
 */
data class WantToReadUiState(
    val series: List<KavitaWantToReadSeries> = emptyList(),
    val totalCount: Int = 0,
    val currentPage: Int = 0,
    val pageSize: Int = 20,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val selectedLibraryId: Int? = null,
    val sortBy: KavitaWantToReadSort = KavitaWantToReadSort.SortName,
    val sortDirection: KavitaSortDirection = KavitaSortDirection.Ascending,
    val isToggling: Boolean = false
)

/**
 * ViewModel for the Want To Read screen.
 *
 * Manages:
 * - Pagination (page forward/back)
 * - Search query
 * - Library filter
 * - Sort options
 * - Toggle series in/out of Want To Read
 * - Cleanup action
 */
@HiltViewModel
class WantToReadViewModel @Inject constructor(
    private val repository: KavitaWantToReadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WantToReadUiState())
    val uiState: StateFlow<WantToReadUiState> = _uiState.asStateFlow()

    /** Expose the tracked WTR series IDs for the toggle chip in series detail. */
    val wantToReadSeriesIds: StateFlow<Set<Int>> = repository.wantToReadSeriesIds

    init {
        // Load first page on init
        loadFirstPage()

        // Observe repository state
        viewModelScope.launch {
            repository.seriesList.collect { series ->
                _uiState.update { it.copy(series = series) }
            }
        }
        viewModelScope.launch {
            repository.totalCount.collect { count ->
                _uiState.update { it.copy(totalCount = count) }
            }
        }
        viewModelScope.launch {
            repository.currentPage.collect { page ->
                _uiState.update { it.copy(currentPage = page) }
            }
        }
        viewModelScope.launch {
            repository.isLoading.collect { loading ->
                _uiState.update { it.copy(isLoading = loading) }
            }
        }
        viewModelScope.launch {
            repository.errorMessage.collect { error ->
                _uiState.update { it.copy(errorMessage = error) }
            }
        }
    }

    /**
     * Load the first page (reset state).
     */
    fun loadFirstPage() {
        _uiState.update { it.copy(currentPage = 0) }
        loadPage(0)
    }

    /**
     * Load a specific page.
     */
    fun loadPage(page: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            val filter = KavitaWantToReadFilter(
                searchQuery = state.searchQuery.ifBlank { null },
                libraryId = state.selectedLibraryId,
                sortBy = state.sortBy,
                sortDirection = state.sortDirection
            )
            repository.loadWantToRead(
                pageNumber = page,
                pageSize = state.pageSize,
                filter = filter
            )
        }
    }

    /**
     * Load next page.
     */
    fun loadNextPage() {
        val state = _uiState.value
        val totalPages = (state.totalCount + state.pageSize - 1) / state.pageSize
        if (state.currentPage + 1 < totalPages) {
            loadPage(state.currentPage + 1)
        }
    }

    /**
     * Load previous page.
     */
    fun loadPreviousPage() {
        val state = _uiState.value
        if (state.currentPage > 0) {
            loadPage(state.currentPage - 1)
        }
    }

    /**
     * Update search query and reload from first page.
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadPage(0)
    }

    /**
     * Update library filter and reload from first page.
     */
    fun onLibraryFilterChanged(libraryId: Int?) {
        _uiState.update { it.copy(selectedLibraryId = libraryId) }
        loadPage(0)
    }

    /**
     * Update sort criteria and reload from first page.
     */
    fun onSortChanged(sortBy: KavitaWantToReadSort, sortDirection: KavitaSortDirection) {
        _uiState.update { it.copy(sortBy = sortBy, sortDirection = sortDirection) }
        loadPage(0)
    }

    /**
     * Toggle a series in/out of the Want To Read list.
     */
    fun toggleWantToRead(seriesId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isToggling = true) }
            try {
                repository.toggleWantToRead(seriesId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Failed to update Want To Read: ${e.message}"
                    )
                }
            } finally {
                _uiState.update { it.copy(isToggling = false) }
            }
        }
    }

    /**
     * Add a series to Want To Read (from series detail screen).
     */
    fun addToWantToRead(seriesId: Int) {
        viewModelScope.launch {
            try {
                repository.addToWantToRead(seriesId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Failed to add to Want To Read: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Remove a series from Want To Read (from series detail screen).
     */
    fun removeFromWantToRead(seriesId: Int) {
        viewModelScope.launch {
            try {
                repository.removeFromWantToRead(seriesId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Failed to remove from Want To Read: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Check if a series is in the Want To Read list.
     */
    fun isInWantToRead(seriesId: Int): Boolean =
        repository.isInWantToRead(seriesId)

    /**
     * Run auto-cleanup (remove fully-read series).
     */
    fun cleanup() {
        viewModelScope.launch {
            repository.cleanupWantToRead()
            // Reload after cleanup
            loadPage(_uiState.value.currentPage)
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        repository.clearError()
    }

    /**
     * Refresh the current page.
     */
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadPage(_uiState.value.currentPage)
        _uiState.update { it.copy(isRefreshing = false) }
    }
}
