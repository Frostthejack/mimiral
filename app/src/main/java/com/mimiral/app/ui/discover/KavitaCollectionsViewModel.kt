package com.mimiral.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.kavita.KavitaCollection
import com.mimiral.app.data.remote.kavita.KavitaCollectionRepository
import com.mimiral.app.data.remote.kavita.KavitaCollectionSeries
import com.mimiral.app.data.remote.kavita.KavitaResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the Collections screen.
 */
data class KavitaCollectionsUiState(
    val isLoading: Boolean = false,
    val collections: List<KavitaCollection> = emptyList(),
    val selectedCollection: KavitaCollection? = null,
    val collectionSeries: List<KavitaCollectionSeries> = emptyList(),
    val seriesPage: Int = 1,
    val seriesTotalPages: Int = 1,
    val seriesTotalCount: Int = 0,
    val isSeriesLoading: Boolean = false,
    val errorMessage: String? = null,
    val showCreateDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showEditDialog: Boolean = false,
    val createSuccess: Boolean = false,
    val addSeriesLoading: Boolean = false,
    val addSeriesError: String? = null
)

@HiltViewModel
class KavitaCollectionsViewModel @Inject constructor(
    private val collectionRepository: KavitaCollectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KavitaCollectionsUiState())
    val uiState: StateFlow<KavitaCollectionsUiState> = _uiState.asStateFlow()

    init {
        loadCollections()
    }

    /**
     * Load all collections from the server.
     */
    fun loadCollections() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = collectionRepository.getCollections()) {
                is KavitaResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        collections = result.data
                    )
                }
                is KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /**
     * Select a collection and load its series (first page).
     */
    fun selectCollection(collection: KavitaCollection) {
        _uiState.value = _uiState.value.copy(
            selectedCollection = collection,
            collectionSeries = emptyList(),
            seriesPage = 1,
            seriesTotalPages = 1,
            seriesTotalCount = 0
        )
        loadCollectionSeries(collection.id, page = 1)
    }

    /**
     * Clear the selected collection (go back to list).
     */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedCollection = null,
            collectionSeries = emptyList(),
            seriesPage = 1
        )
    }

    /**
     * Load series for the selected collection (paginated).
     */
    fun loadCollectionSeries(collectionId: Int, page: Int = 1) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSeriesLoading = true, errorMessage = null)
            when (val result = collectionRepository.getSeriesByCollection(collectionId, page)) {
                is KavitaResult.Success -> {
                    val pageData = result.data
                    val currentSeries = if (page == 1) {
                        pageData.series
                    } else {
                        _uiState.value.collectionSeries + pageData.series
                    }
                    _uiState.value = _uiState.value.copy(
                        isSeriesLoading = false,
                        collectionSeries = currentSeries,
                        seriesPage = page,
                        seriesTotalCount = pageData.totalCount,
                        seriesTotalPages = if (pageData.pageSize > 0) {
                            (pageData.totalCount + pageData.pageSize - 1) / pageData.pageSize
                        } else {
                            1
                        }
                    )
                }
                is KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSeriesLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /**
     * Load next page of series for the selected collection.
     */
    fun loadMoreSeries() {
        val state = _uiState.value
        val collectionId = state.selectedCollection?.id ?: return
        if (state.isSeriesLoading) return
        if (state.seriesPage >= state.seriesTotalPages) return
        loadCollectionSeries(collectionId, state.seriesPage + 1)
    }

    /**
     * Create a new collection.
     */
    fun createCollection(
        title: String,
        summary: String? = null,
        seriesIds: List<Int> = emptyList(),
        promoted: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (
                val result = collectionRepository.createCollection(
                    title,
                    summary,
                    seriesIds,
                    promoted
                )
            ) {
                is KavitaResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showCreateDialog = false,
                        createSuccess = true
                    )
                    // Reload collections to show the new one
                    loadCollections()
                }
                is KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Create failed: ${result.message}"
                    )
                }
            }
        }
    }

    /**
     * Update collection metadata.
     */
    fun updateCollection(
        collectionId: Int,
        title: String,
        summary: String? = null,
        promoted: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (
                val result = collectionRepository.updateCollection(
                    collectionId,
                    title,
                    summary,
                    promoted
                )
            ) {
                is KavitaResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showEditDialog = false
                    )
                    loadCollections()
                }
                is KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Update failed: ${result.message}"
                    )
                }
            }
        }
    }

    /**
     * Add series to a collection.
     */
    fun addSeriesToCollection(
        collectionId: Int,
        seriesIds: List<Int>
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(addSeriesLoading = true, addSeriesError = null)
            when (
                val result = collectionRepository.addSeriesToCollection(
                    collectionId,
                    seriesIds
                )
            ) {
                is KavitaResult.Success -> {
                    _uiState.value = _uiState.value.copy(addSeriesLoading = false)
                    // Reload to reflect changes
                    loadCollections()
                    _uiState.value.selectedCollection?.let {
                        selectCollection(it)
                    }
                }
                is KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        addSeriesLoading = false,
                        addSeriesError = "Add series failed: ${result.message}"
                    )
                }
            }
        }
    }

    /**
     * Delete a collection.
     */
    fun deleteCollection(collectionId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = collectionRepository.deleteCollection(collectionId)) {
                is KavitaResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showDeleteConfirm = false,
                        selectedCollection = null
                    )
                    loadCollections()
                }
                is KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Delete failed: ${result.message}"
                    )
                }
            }
        }
    }

    // Dialog state management

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true, createSuccess = false)
    }

    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false, createSuccess = false)
    }

    fun showDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = true)
    }

    fun hideDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = false)
    }

    fun showEditDialog() {
        _uiState.value = _uiState.value.copy(showEditDialog = true)
    }

    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(showEditDialog = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearAddSeriesError() {
        _uiState.value = _uiState.value.copy(addSeriesError = null)
    }
}
