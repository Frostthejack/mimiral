package com.mimiral.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.kavita.KavitaFavoriteAuthor
import com.mimiral.app.data.remote.kavita.KavitaGenreBreakdown
import com.mimiral.app.data.remote.kavita.KavitaPagesPerYear
import com.mimiral.app.data.remote.kavita.KavitaReadingActivity
import com.mimiral.app.data.remote.kavita.KavitaReadingPace
import com.mimiral.app.data.remote.kavita.KavitaResult
import com.mimiral.app.data.remote.kavita.KavitaStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsDashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val readingActivity: List<KavitaReadingActivity> = emptyList(),
    val genreBreakdown: List<KavitaGenreBreakdown> = emptyList(),
    val pagesPerYear: List<KavitaPagesPerYear> = emptyList(),
    val readingPace: List<KavitaReadingPace> = emptyList(),
    val favoriteAuthors: List<KavitaFavoriteAuthor> = emptyList(),
    val hasKavitaServer: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel for the Kavita Stats Dashboard.
 *
 * Loads all stats in parallel on init and exposes them as a single UI state.
 * The dashboard shows:
 * - Reading activity bar chart (pages/day)
 * - Genre breakdown pie chart
 * - Pages-per-year bar chart
 * - Reading pace trend line chart
 * - Favorite authors list
 */
@HiltViewModel
class StatsDashboardViewModel @Inject constructor(
    private val statsRepository: KavitaStatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsDashboardUiState())
    val uiState: StateFlow<StatsDashboardUiState> = _uiState.asStateFlow()

    init {
        loadAllStats()
    }

    fun loadAllStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            var hasError = false
            var errorMsg: String? = null
            var hasServer = true

            // Load all stats in parallel
            val activityResult = statsRepository.getReadingActivity()
            val genreResult = statsRepository.getGenreBreakdown()
            val pagesResult = statsRepository.getPagesPerYear()
            val paceResult = statsRepository.getReadingPace()
            val authorsResult = statsRepository.getFavoriteAuthors()

            val activity = when (activityResult) {
                is KavitaResult.Success -> activityResult.data
                is KavitaResult.Error -> {
                    if (activityResult.message.contains("No Kavita server")) {
                        hasServer = false
                    }
                    hasError = true
                    errorMsg = activityResult.message
                    emptyList()
                }
            }

            val genres = when (genreResult) {
                is KavitaResult.Success -> genreResult.data
                is KavitaResult.Error -> {
                    if (!hasError) {
                        hasError = true
                        errorMsg = genreResult.message
                    }
                    emptyList()
                }
            }

            val pages = when (pagesResult) {
                is KavitaResult.Success -> pagesResult.data
                is KavitaResult.Error -> {
                    if (!hasError) {
                        hasError = true
                        errorMsg = pagesResult.message
                    }
                    emptyList()
                }
            }

            val pace = when (paceResult) {
                is KavitaResult.Success -> paceResult.data
                is KavitaResult.Error -> {
                    if (!hasError) {
                        hasError = true
                        errorMsg = paceResult.message
                    }
                    emptyList()
                }
            }

            val authors = when (authorsResult) {
                is KavitaResult.Success -> authorsResult.data
                is KavitaResult.Error -> {
                    if (!hasError) {
                        hasError = true
                        errorMsg = authorsResult.message
                    }
                    emptyList()
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    readingActivity = activity,
                    genreBreakdown = genres,
                    pagesPerYear = pages,
                    readingPace = pace,
                    favoriteAuthors = authors,
                    hasKavitaServer = hasServer,
                    error = if (hasError) errorMsg else null
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            loadAllStats()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
