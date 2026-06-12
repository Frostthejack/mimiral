package com.mimiral.app.ui.opds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.GutenbergCatalogSeeder
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import com.mimiral.app.data.remote.opds.OpdsEntry
import com.mimiral.app.data.remote.opds.OpdsFeed
import com.mimiral.app.data.remote.opds.OpdsLink
import com.mimiral.app.data.repository.OpdsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OpdsBrowserUiState(
    val catalogs: List<OpdsCatalogEntity> = emptyList(),
    val currentFeed: OpdsFeed? = null,
    val selectedCatalog: OpdsCatalogEntity? = null,
    val isLoadingCatalogs: Boolean = true,
    val isLoadingFeed: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: String? = null,
    val breadcrumbs: List<BreadcrumbItem> = emptyList(),
    val errorMessage: String? = null,
    val showAddCatalogDialog: Boolean = false,
    val showCatalogList: Boolean = true,
    val hasNextPage: Boolean = false,
    val nextPageUrl: String? = null
)

data class BreadcrumbItem(
    val title: String,
    val feedUrl: String
)

@HiltViewModel
class OpdsCatalogBrowserViewModel @Inject constructor(
    private val opdsRepository: OpdsRepository,
    private val gutenbergSeeder: GutenbergCatalogSeeder,
    private val credentialStore: com.mimiral.app.data.remote.kavita.KavitaCredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpdsBrowserUiState())
    val uiState: StateFlow<OpdsBrowserUiState> = _uiState.asStateFlow()

    private val feedHistory = mutableListOf<BreadcrumbItem>()

    init {
        loadCatalogs()
    }

    private fun loadCatalogs() {
        viewModelScope.launch {
            try {
                // Seed Gutenberg as default if no catalogs exist
                gutenbergSeeder.seedIfEmpty()

                opdsRepository.getActiveCatalogs().collect { catalogs ->
                    _uiState.value = _uiState.value.copy(
                        catalogs = catalogs,
                        isLoadingCatalogs = false
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
        _uiState.value = _uiState.value.copy(
            selectedCatalog = catalog,
            showCatalogList = false,
            breadcrumbs = emptyList(),
            currentFeed = null
        )
        feedHistory.clear()
        browseFeed(catalog.url, catalog.name, catalog.username, credentialStore.getOpdsPassword(catalog.id))
    }

    fun browseFeed(
        url: String,
        title: String,
        username: String? = null,
        password: String? = null
    ) {
        // If password not provided, try to read from encrypted storage
        val effectivePassword = password
            ?: _uiState.value.selectedCatalog?.let { credentialStore.getOpdsPassword(it.id) }
        _uiState.value = _uiState.value.copy(
            isLoadingFeed = true,
            errorMessage = null
        )

        val existingIdx = feedHistory.indexOfFirst { it.feedUrl == url }
        if (existingIdx >= 0) {
            while (feedHistory.size > existingIdx + 1) {
                feedHistory.removeAt(feedHistory.lastIndex)
            }
        } else {
            feedHistory.add(BreadcrumbItem(title = title, feedUrl = url))
        }

        viewModelScope.launch {
            val result = opdsRepository.fetchFeed(url, username, effectivePassword)
            result.fold(
                onSuccess = { feed ->
                    val nextLink = feed.links.firstOrNull {
                        it.rel == OpdsLink.REL_NEXT
                    }
                    _uiState.value = _uiState.value.copy(
                        currentFeed = feed,
                        isLoadingFeed = false,
                        breadcrumbs = feedHistory.toList(),
                        hasNextPage = nextLink != null,
                        nextPageUrl = nextLink?.href
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingFeed = false,
                        errorMessage = "Failed to load feed: ${e.message}"
                    )
                }
            )
        }
    }

    /**
     * Load the next page of results (pagination).
     * Appends new entries to the current feed.
     */
    fun loadNextPage() {
        val nextUrl = _uiState.value.nextPageUrl ?: return
        val catalog = _uiState.value.selectedCatalog

        _uiState.value = _uiState.value.copy(
            isLoadingNextPage = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val result = opdsRepository.fetchFeed(
                nextUrl,
                catalog?.username,
                if (catalog != null) credentialStore.getOpdsPassword(catalog.id) else null
            )
            result.fold(
                onSuccess = { nextFeed ->
                    val currentFeed = _uiState.value.currentFeed
                    if (currentFeed != null) {
                        val mergedEntries =
                            currentFeed.entries + nextFeed.entries
                        val mergedFeed = currentFeed.copy(
                            entries = mergedEntries,
                            links = nextFeed.links
                        )
                        val nextLink = nextFeed.links.firstOrNull {
                            it.rel == OpdsLink.REL_NEXT
                        }
                        _uiState.value = _uiState.value.copy(
                            currentFeed = mergedFeed,
                            isLoadingNextPage = false,
                            hasNextPage = nextLink != null,
                            nextPageUrl = nextLink?.href
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingNextPage = false,
                        errorMessage = "Failed to load next page: ${e.message}"
                    )
                }
            )
        }
    }

    fun navigateToEntry(entry: OpdsEntry) {
        if (!entry.isAcquisition) {
            val navLink = entry.links.firstOrNull { it.isNavigation }
                ?: entry.links.firstOrNull {
                    it.rel == OpdsLink.REL_SUBSECTION
                }
                ?: return
            val catalog = _uiState.value.selectedCatalog
            browseFeed(
                url = navLink.href,
                title = entry.title,
                username = catalog?.username,
                password = if (catalog != null) credentialStore.getOpdsPassword(catalog.id) else null
            )
        }
    }

    fun downloadEntry(entry: OpdsEntry) {
        val downloadLink = entry.preferredAcquisitionLink
            ?: entry.acquisitionLinks.firstOrNull()
        if (downloadLink == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "No download link available for \"${entry.title}\""
            )
            return
        }

        val catalog = _uiState.value.selectedCatalog ?: return
        val extension = downloadLink.fileExtension ?: ".epub"
        val fileName =
            entry.title.replace(Regex("[^a-zA-Z0-9._-]"), "_") + extension
        val destPath =
            "${android.os.Environment.DIRECTORY_DOWNLOADS}/$fileName"

        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            downloadProgress = "Downloading \"${entry.title}\"...\"",
            errorMessage = null
        )

        viewModelScope.launch {
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
                        downloadProgress = "Downloaded \"${entry.title}\""
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        errorMessage = "Download failed: ${e.message}"
                    )
                }
            )
        }
    }

    fun navigateBack() {
        if (feedHistory.size > 1) {
            feedHistory.removeAt(feedHistory.lastIndex)
            val previous = feedHistory.last()
            val catalog = _uiState.value.selectedCatalog
            browseFeed(
                url = previous.feedUrl,
                title = previous.title,
                username = catalog?.username,
                password = if (catalog != null) credentialStore.getOpdsPassword(catalog.id) else null
            )
        } else {
            showCatalogList()
        }
    }

    fun showCatalogList() {
        feedHistory.clear()
        _uiState.value = _uiState.value.copy(
            showCatalogList = true,
            currentFeed = null,
            selectedCatalog = null,
            breadcrumbs = emptyList(),
            errorMessage = null,
            hasNextPage = false,
            nextPageUrl = null
        )
    }

    fun showAddCatalogDialog() {
        _uiState.value = _uiState.value.copy(showAddCatalogDialog = true)
    }

    fun dismissAddCatalogDialog() {
        _uiState.value = _uiState.value.copy(showAddCatalogDialog = false)
    }

    fun addCatalog(name: String, url: String, username: String?, password: String?) {
        viewModelScope.launch {
            try {
                val catalog = OpdsCatalogEntity(
                    name = name,
                    url = url,
                    username = username?.ifBlank { null },
                    isActive = true
                )
                val id = opdsRepository.insertCatalog(catalog)
                // Save sensitive credentials to encrypted storage
                if (id > 0) {
                    credentialStore.saveOpdsCredentials(id.toInt(), password?.ifBlank { null }, null)
                }
                _uiState.value =
                    _uiState.value.copy(showAddCatalogDialog = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to add catalog: ${e.message}",
                    showAddCatalogDialog = false
                )
            }
        }
    }

    fun deleteCatalog(catalog: OpdsCatalogEntity) {
        viewModelScope.launch {
            try {
                opdsRepository.deleteCatalog(catalog)
                if (_uiState.value.selectedCatalog?.id == catalog.id) {
                    showCatalogList()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete catalog: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
