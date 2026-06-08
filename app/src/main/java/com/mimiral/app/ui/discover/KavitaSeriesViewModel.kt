package com.mimiral.app.ui.discover

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.BrowseResult
import com.mimiral.app.data.remote.KavitaBrowseRepository
import com.mimiral.app.data.remote.SeriesDetailDto
import com.mimiral.app.data.remote.SeriesDto
import com.mimiral.app.data.remote.VolumeDto
import com.mimiral.app.data.remote.kavita.KavitaMarkReadRepository
import com.mimiral.app.data.remote.kavita.KavitaRepository
import com.mimiral.app.data.remote.kavita.MarkReadOperation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KavitaSeriesUiState(
    val isLoading: Boolean = false,
    val seriesDetail: SeriesDetailDto? = null,
    val volumes: List<VolumeDto> = emptyList(),
    val selectedSeries: SeriesDto? = null,
    val selectedVolume: VolumeDto? = null,
    val errorMessage: String? = null,
    val lastMarkReadOperation: MarkReadOperation? = null
)

@HiltViewModel
class KavitaSeriesViewModel @Inject constructor(
    private val browseRepository: KavitaBrowseRepository,
    private val kavitaRepository: KavitaRepository,
    private val markReadRepository: KavitaMarkReadRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val seriesId: Int = savedStateHandle.get<Int>("seriesId") ?: 0

    private val _uiState = MutableStateFlow(KavitaSeriesUiState())
    val uiState: StateFlow<KavitaSeriesUiState> = _uiState.asStateFlow()

    init {
        if (seriesId > 0) {
            loadSeriesDetail(seriesId)
        }
        // Observe mark-read operations to trigger refresh
        viewModelScope.launch {
            markReadRepository.uiState.collect { state ->
                if (state.lastOperation != null && _uiState.value.lastMarkReadOperation != state.lastOperation) {
                    _uiState.value = _uiState.value.copy(
                        lastMarkReadOperation = state.lastOperation
                    )
                    // Refresh volumes after any mark read/unread operation
                    reloadVolumes()
                }
            }
        }
    }

    fun loadSeriesDetail(seriesId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // First get the series detail (specials, counts, etc.)
            when (val detailResult = browseRepository.getSeriesDetail(seriesId)) {
                is BrowseResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = true, // still loading volumes
                        seriesDetail = detailResult.data
                    )
                }
                is BrowseResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = detailResult.message
                    )
                    return@launch
                }
                is BrowseResult.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }

            // Then get volumes separately (series-detail may return empty volumes)
            when (val volumesResult = browseRepository.getVolumes(seriesId)) {
                is BrowseResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        volumes = volumesResult.data,
                        selectedSeries = SeriesDto(
                            id = seriesId,
                            name = "",
                            pages = volumesResult.data.sumOf { it.pages },
                            pagesRead = volumesResult.data.sumOf { it.pagesRead }
                        )
                    )
                }
                is BrowseResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = volumesResult.message
                    )
                }
                is BrowseResult.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }

    fun reloadVolumes() {
        if (seriesId == 0) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = browseRepository.getVolumes(seriesId)) {
                is BrowseResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        volumes = result.data
                    )
                }
                is BrowseResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is BrowseResult.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }

    fun selectVolume(volume: VolumeDto) {
        _uiState.value = _uiState.value.copy(selectedVolume = volume)
    }

    fun clearVolumeSelection() {
        _uiState.value = _uiState.value.copy(selectedVolume = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Download a chapter and return the local book ID and format for reading.
     */
    fun downloadChapter(
        chapterId: Int,
        title: String,
        seriesId: Int,
        onResult: (Int?, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = kavitaRepository.downloadBook(
                chapterId = chapterId,
                title = title,
                seriesId = seriesId
            )
            when (result) {
                is com.mimiral.app.data.remote.kavita.KavitaResult.Success -> {
                    // Look up the book's format from the database
                    val bookId = result.data
                    val format = try {
                        kavitaRepository.getBookFormat(bookId)
                    } catch (_: Exception) {
                        null
                    }
                    onResult(bookId, format)
                }
                is com.mimiral.app.data.remote.kavita.KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Download failed: ${result.message}"
                    )
                    onResult(null, null)
                }
            }
        }
    }
}
