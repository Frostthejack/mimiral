package com.mimiral.app.ui.discover

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import com.mimiral.app.data.remote.opds.OpdsDownloadManager
import com.mimiral.app.data.remote.opds.OpdsEntry
import com.mimiral.app.data.remote.opds.OpdsFeed
import com.mimiral.app.data.remote.opds.OpdsRepository
import com.mimiral.app.data.remote.opds.OpdsResult
import com.mimiral.app.data.remote.opds.OpdsSearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OpdsSearchUiState(
    val catalogs: List<OpdsCatalogEntity> = emptyList(),
    val selectedCatalog: OpdsCatalogEntity? = null,
    val searchQuery: String = "",
    val searchResults: List<OpdsEntry> = emptyList(),
    val isLoadingCatalogs: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingFeed: Boolean = false,
    val currentFeed: OpdsFeed? = null,
    val errorMessage: String? = null,
    val downloadProgress: Map<String, DownloadProgress> = emptyMap()
)

data class DownloadProgress(
    val entryId: String,
    val title: String,
    val state: DownloadState
)

sealed class DownloadState {
    object Pending : DownloadState()
    data class Downloading(val percent: Int) : DownloadState()
    object Completed : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

@HiltViewModel
class OpdsViewModel @Inject constructor(
    application: Application,
    private val opdsRepository: OpdsRepository,
    private val opdsSearchRepository: OpdsSearchRepository,
    private val opdsDownloadManager: OpdsDownloadManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OpdsSearchUiState())
    val uiState: StateFlow<OpdsSearchUiState> = _uiState.asStateFlow()

    init {
        loadCatalogs()
    }

    private fun loadCatalogs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCatalogs = true)
            try {
                opdsRepository.getSavedCatalogs().collect { catalogs ->
                    _uiState.value = _uiState.value.copy(
                        catalogs = catalogs,
                        isLoadingCatalogs = false,
                        selectedCatalog = _uiState.value.selectedCatalog ?: catalogs.firstOrNull()
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingCatalogs = false,
                    errorMessage = "Failed to load catalogs: ${e.message}"
                )
            }
        }
    }

    fun selectCatalog(catalog: OpdsCatalogEntity) {
        _uiState.value = _uiState.value.copy(selectedCatalog = catalog)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun search() {
        val catalog = _uiState.value.selectedCatalog ?: return
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                errorMessage = null,
                searchResults = emptyList()
            )

            val result = opdsSearchRepository.searchCatalog(
                catalogUrl = catalog.url,
                query = query,
                username = catalog.username,
                password = catalog.password
            )

            result.fold(
                onSuccess = { feed ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        searchResults = feed.entries,
                        currentFeed = feed
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        errorMessage = "Search failed: ${error.message}"
                    )
                }
            )
        }
    }

    fun browseCatalog() {
        val catalog = _uiState.value.selectedCatalog ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingFeed = true,
                errorMessage = null
            )

            val result = opdsRepository.browseCatalog(catalog)

            when (result) {
                is OpdsResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingFeed = false,
                        currentFeed = result.data,
                        searchResults = result.data.entries
                    )
                }
                is OpdsResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingFeed = false,
                        errorMessage = "Failed to load catalog: ${result.message}"
                    )
                }
            }
        }
    }

    fun downloadBook(entry: OpdsEntry) {
        val catalog = _uiState.value.selectedCatalog ?: return
        val acquisitionLink = entry.acquisitionLinks.firstOrNull()
            ?: run {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "No download link available for this book"
                )
                return
            }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                downloadProgress = _uiState.value.downloadProgress + (
                    entry.id to DownloadProgress(entry.id, entry.title, DownloadState.Pending)
                    )
            )

            val result = opdsDownloadManager.downloadBook(
                entry = entry,
                acquisitionLink = acquisitionLink,
                catalogUsername = catalog.username,
                catalogPassword = catalog.password
            )

            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        downloadProgress = _uiState.value.downloadProgress + (
                            entry.id to DownloadProgress(
                                entry.id,
                                entry.title,
                                DownloadState.Completed
                            )
                            )
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        downloadProgress = _uiState.value.downloadProgress + (
                            entry.id to DownloadProgress(
                                entry.id,
                                entry.title,
                                DownloadState.Failed(error.message ?: "Unknown error")
                            )
                            )
                    )
                }
            )
        }
    }

    fun addCatalog(name: String, url: String, username: String? = null, password: String? = null) {
        viewModelScope.launch {
            try {
                opdsRepository.addCatalog(name, url, username, password)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to add catalog: ${e.message}"
                )
            }
        }
    }

    fun removeCatalog(catalog: OpdsCatalogEntity) {
        viewModelScope.launch {
            try {
                opdsRepository.removeCatalog(catalog)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to remove catalog: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
