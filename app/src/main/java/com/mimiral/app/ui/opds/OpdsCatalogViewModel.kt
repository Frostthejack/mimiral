package com.mimiral.app.ui.opds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import com.mimiral.app.data.opds.OpdsEntry
import com.mimiral.app.data.opds.OpdsFeed
import com.mimiral.app.data.opds.OpdsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OpdsCatalogUiState(
    val catalogs: List<OpdsCatalogEntity> = emptyList(),
    val selectedCatalog: OpdsCatalogEntity? = null,
    val currentFeed: OpdsFeed? = null,
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: String? = null,
    val error: String? = null,
    val feedStack: List<OpdsFeed> = emptyList()
)

@HiltViewModel
class OpdsCatalogViewModel @Inject constructor(
    private val opdsRepository: OpdsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpdsCatalogUiState())
    val uiState: StateFlow<OpdsCatalogUiState> = _uiState.asStateFlow()

    init {
        loadCatalogs()
        seedDefaults()
    }

    private fun seedDefaults() {
        viewModelScope.launch {
            try {
                opdsRepository.seedDefaultCatalogs()
            } catch (_: Exception) {
                // Ignore seeding errors
            }
        }
    }

    fun loadCatalogs() {
        viewModelScope.launch {
            opdsRepository.getActiveCatalogs().collect { catalogs ->
                _uiState.value = _uiState.value.copy(catalogs = catalogs)
            }
        }
    }

    fun selectCatalog(catalog: OpdsCatalogEntity) {
        _uiState.value = _uiState.value.copy(
            selectedCatalog = catalog,
            feedStack = emptyList()
        )
        loadFeed(catalog)
    }

    fun loadFeed(catalog: OpdsCatalogEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
            val result = opdsRepository.fetchCatalogFeed(catalog)
            result.fold(
                onSuccess = { feed ->
                    _uiState.value = _uiState.value.copy(
                        currentFeed = feed,
                        isLoading = false,
                        feedStack = listOf(feed)
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load catalog"
                    )
                }
            )
        }
    }

    fun navigateToFeed(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
            val result = opdsRepository.fetchFeedByUrl(url)
            result.fold(
                onSuccess = { feed ->
                    val newStack = _uiState.value.feedStack + feed
                    _uiState.value = _uiState.value.copy(
                        currentFeed = feed,
                        isLoading = false,
                        feedStack = newStack
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load feed"
                    )
                }
            )
        }
    }

    fun navigateBack() {
        val stack = _uiState.value.feedStack
        if (stack.size > 1) {
            val newStack = stack.dropLast(1)
            _uiState.value = _uiState.value.copy(
                currentFeed = newStack.last(),
                feedStack = newStack
            )
        }
    }

    fun canNavigateBack(): Boolean = _uiState.value.feedStack.size > 1

    fun downloadBook(entry: OpdsEntry) {
        val catalog = _uiState.value.selectedCatalog ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadProgress = "Downloading: ${entry.title}",
                error = null
            )
            try {
                val bookId = opdsRepository.downloadBook(
                    entry = entry,
                    catalogName = catalog.name
                )
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadProgress = "Downloaded: ${entry.title} (ID: $bookId)"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    error = "Download failed: ${e.message}"
                )
            }
        }
    }

    fun addCatalog(name: String, url: String) {
        viewModelScope.launch {
            try {
                opdsRepository.addCatalog(name = name, url = url)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to add catalog: ${e.message}"
                )
            }
        }
    }

    fun deleteCatalog(catalog: OpdsCatalogEntity) {
        viewModelScope.launch {
            try {
                opdsRepository.deleteCatalog(catalog)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete catalog: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
