package com.mimiral.app.ui.opds

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import com.mimiral.app.data.remote.opds.OpdsEntry
import com.mimiral.app.data.remote.opds.OpdsFeed
import com.mimiral.app.data.repository.OpdsRepository
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
    private val opdsRepository: OpdsRepository,
    private val credentialStore: com.mimiral.app.data.remote.kavita.KavitaCredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpdsCatalogUiState())
    val uiState: StateFlow<OpdsCatalogUiState> = _uiState.asStateFlow()

    init {
        loadCatalogs()
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
            val result = opdsRepository.fetchFeed(
                feedUrl = catalog.url,
                username = catalog.username,
                password = credentialStore.getOpdsPassword(catalog.id)
            )
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
            val catalog = _uiState.value.selectedCatalog
            val result = opdsRepository.fetchFeed(
                feedUrl = url,
                username = catalog?.username,
                password = if (catalog != null) credentialStore.getOpdsPassword(catalog.id) else null
            )
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
        val downloadLink = entry.preferredAcquisitionLink
            ?: entry.acquisitionLinks.firstOrNull()
        if (downloadLink == null) {
            _uiState.value = _uiState.value.copy(
                error = "No download link for: ${entry.title}"
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadProgress = "Downloading: ${entry.title}",
                error = null
            )
            val extension = downloadLink.fileExtension ?: ".epub"
            val fileName = entry.title.replace(Regex("[^a-zA-Z0-9._-]"), "_") + extension
            val destPath = "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
            val result = opdsRepository.downloadBook(
                downloadUrl = downloadLink.href,
                destinationPath = destPath,
                username = catalog.username,
                password = credentialStore.getOpdsPassword(catalog.id)
            )
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadProgress = "Downloaded: ${entry.title}"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        error = "Download failed: ${e.message}"
                    )
                }
            )
        }
    }

    fun addCatalog(name: String, url: String) {
        viewModelScope.launch {
            try {
                val entity = OpdsCatalogEntity(
                    name = name,
                    url = url,
                    isActive = true
                )
                opdsRepository.insertCatalog(entity)
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
