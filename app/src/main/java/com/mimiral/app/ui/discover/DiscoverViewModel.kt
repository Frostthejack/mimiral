package com.mimiral.app.ui.discover

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.kavita.KavitaLibrary
import com.mimiral.app.data.remote.kavita.KavitaRepository
import com.mimiral.app.data.remote.kavita.KavitaResult
import com.mimiral.app.data.remote.kavita.KavitaSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the Discover screen (Kavita library browsing).
 */
data class DiscoverUiState(
    val libraries: List<KavitaLibrary> = emptyList(),
    val selectedLibrary: KavitaLibrary? = null,
    val series: List<SeriesItem> = emptyList(),
    val selectedSeries: SeriesDetail? = null,
    val isLoadingLibraries: Boolean = false,
    val isLoadingSeries: Boolean = false,
    val isLoadingMetadata: Boolean = false,
    val error: String? = null,
    val navigationStack: List<DiscoverNavItem> = emptyList()
)

/**
 * Series item for the list view, with resolved cover URL.
 */
data class SeriesItem(
    val id: Int,
    val name: String,
    val description: String? = null,
    val pages: Int,
    val format: String,
    val formatLabel: String,
    val coverUrl: String?
)

/**
 * Series detail with volumes and resolved cover URLs.
 */
data class SeriesDetail(
    val id: Int,
    val name: String,
    val description: String? = null,
    val pages: Int,
    val format: String,
    val formatLabel: String,
    val coverUrl: String?,
    val volumes: List<VolumeItem> = emptyList()
)

/**
 * Volume item with resolved cover URL.
 */
data class VolumeItem(
    val id: Int,
    val name: String,
    val number: Int,
    val pages: Int,
    val chapterCount: Int,
    val coverUrl: String?
)

/**
 * Represents a navigation position in the discover browser.
 */
sealed class DiscoverNavItem {
    object Libraries : DiscoverNavItem()
    data class SeriesInLibrary(val library: KavitaLibrary) : DiscoverNavItem()
    data class SeriesDetail(val library: KavitaLibrary, val seriesName: String) :
        DiscoverNavItem()
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    application: Application,
    private val kavitaRepository: KavitaRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        loadLibraries()
    }

    /**
     * Load all Kavita libraries.
     */
    fun loadLibraries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingLibraries = true,
                error = null,
                selectedLibrary = null,
                series = emptyList(),
                selectedSeries = null,
                navigationStack = listOf(DiscoverNavItem.Libraries)
            )

            when (val result = kavitaRepository.getLibraries()) {
                is KavitaResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        libraries = result.data,
                        isLoadingLibraries = false
                    )
                }
                is KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingLibraries = false,
                        error = result.message
                    )
                }
            }
        }
    }

    /**
     * Select a library and load its series.
     */
    fun selectLibrary(library: KavitaLibrary) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedLibrary = library,
                isLoadingSeries = true,
                error = null,
                series = emptyList(),
                selectedSeries = null,
                navigationStack = listOf(
                    DiscoverNavItem.Libraries,
                    DiscoverNavItem.SeriesInLibrary(library)
                )
            )

            when (val result = kavitaRepository.getSeriesForLibrary(library.id)) {
                is KavitaResult.Success -> {
                    val items = result.data.map { it.toSeriesItem() }
                    _uiState.value = _uiState.value.copy(
                        series = items,
                        isLoadingSeries = false
                    )
                }
                is KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingSeries = false,
                        error = result.message
                    )
                }
            }
        }
    }

    /**
     * Select a series and load its metadata (volumes, chapters).
     */
    fun selectSeries(seriesItem: SeriesItem) {
        val library = _uiState.value.selectedLibrary ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingMetadata = true,
                error = null,
                navigationStack = listOf(
                    DiscoverNavItem.Libraries,
                    DiscoverNavItem.SeriesInLibrary(library),
                    DiscoverNavItem.SeriesDetail(library, seriesItem.name)
                )
            )

            when (val result = kavitaRepository.getSeriesMetadata(seriesItem.id)) {
                is KavitaResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedSeries = result.data.toSeriesDetail(),
                        isLoadingMetadata = false
                    )
                }
                is KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMetadata = false,
                        error = result.message
                    )
                }
            }
        }
    }

    /**
     * Navigate back in the discovery stack.
     */
    fun navigateBack() {
        val stack = _uiState.value.navigationStack
        if (stack.size <= 1) return

        val newStack = stack.dropLast(1)
        val previous = newStack.last()

        _uiState.value = _uiState.value.copy(
            navigationStack = newStack,
            selectedSeries = null
        )

        when (previous) {
            is DiscoverNavItem.Libraries -> {
                _uiState.value = _uiState.value.copy(
                    selectedLibrary = null,
                    series = emptyList()
                )
            }
            is DiscoverNavItem.SeriesInLibrary -> {
                selectLibrary(previous.library)
            }
            is DiscoverNavItem.SeriesDetail -> {
                // Find the series item and re-select it
                val seriesItem = _uiState.value.series.find {
                    it.name == previous.seriesName
                }
                if (seriesItem != null) {
                    selectSeries(seriesItem)
                }
            }
        }
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // --- Mapping helpers ---

    private fun KavitaSeries.toSeriesItem(): SeriesItem {
        return SeriesItem(
            id = id,
            name = name,
            description = description,
            pages = pages,
            format = format.toString(),
            formatLabel = formatLabel,
            coverUrl = kavitaRepository.resolveCoverImageUrl(coverImage)
        )
    }

    private fun KavitaSeries.toSeriesDetail(): SeriesDetail {
        return SeriesDetail(
            id = id,
            name = name,
            description = description,
            pages = pages,
            format = format.toString(),
            formatLabel = formatLabel,
            coverUrl = kavitaRepository.resolveCoverImageUrl(coverImage),
            volumes = volumes.map { v ->
                VolumeItem(
                    id = v.id,
                    name = v.name,
                    number = v.number,
                    pages = v.pages,
                    chapterCount = v.chapters.size,
                    coverUrl = kavitaRepository.resolveCoverImageUrl(v.coverImage)
                )
            }
        )
    }
}
