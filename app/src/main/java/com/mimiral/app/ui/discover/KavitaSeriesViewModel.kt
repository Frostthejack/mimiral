package com.mimiral.app.ui.discover

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.BrowseResult
import com.mimiral.app.data.remote.KavitaBrowseRepository
import com.mimiral.app.data.remote.SeriesDetailDto
import com.mimiral.app.data.remote.SeriesDto
import com.mimiral.app.data.remote.VolumeDto
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
    val errorMessage: String? = null
)

@HiltViewModel
class KavitaSeriesViewModel @Inject constructor(
    private val browseRepository: KavitaBrowseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val seriesId: Int = savedStateHandle.get<Int>("seriesId") ?: 0

    private val _uiState = MutableStateFlow(KavitaSeriesUiState())
    val uiState: StateFlow<KavitaSeriesUiState> = _uiState.asStateFlow()

    init {
        if (seriesId > 0) {
            loadSeriesDetail(seriesId)
        }
    }

    fun loadSeriesDetail(seriesId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = browseRepository.getSeriesDetail(seriesId)) {
                is BrowseResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        seriesDetail = result.data,
                        volumes = result.data.volumes,
                        selectedSeries = SeriesDto(
                            id = seriesId,
                            name = "",
                            pages = result.data.volumes.sumOf { it.pages },
                            pagesRead = result.data.volumes.sumOf { it.pagesRead }
                        )
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
}
