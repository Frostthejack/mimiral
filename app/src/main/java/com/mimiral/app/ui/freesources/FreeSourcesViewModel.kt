package com.mimiral.app.ui.freesources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.opds.FreeSource
import com.mimiral.app.data.remote.opds.OpdsEntry
import com.mimiral.app.data.remote.opds.OpdsFeed
import com.mimiral.app.data.repository.OpdsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FreeSourcesUiState(
    val sources: List<FreeSource> = FreeSource.all(),
    val selectedSource: FreeSource? = null,
    val currentFeed: OpdsFeed? = null,
    val feedStack: List<FeedStackEntry> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<OpdsEntry> = emptyList(),
    val isBrowsing: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingFeed: Boolean = false,
    val errorMessage: String? = null,
    val downloadingIds: Set<String> = emptySet(),
    val downloadedIds: Set<String> = emptySet()
)

data class FeedStackEntry(
    val title: String,
    val url: String,
    val source: FreeSource
)

@HiltViewModel
class FreeSourcesViewModel @Inject constructor(
    private val opdsRepository: OpdsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FreeSourcesUiState())
    val uiState: StateFlow<FreeSourcesUiState> = _uiState.asStateFlow()

    fun selectSource(source: FreeSource) {
        _uiState.value = _uiState.value.copy(
            selectedSource = source,
            currentFeed = null,
            feedStack = emptyList(),
            searchQuery = "",
            searchResults = emptyList(),
            isBrowsing = false,
            errorMessage = null
        )
    }

    fun browseSource() {
        val source = _uiState.value.selectedSource ?: return
        _uiState.value = _uiState.value.copy(
            isBrowsing = true,
            isLoadingFeed = true,
            errorMessage = null,
            searchResults = emptyList()
        )

        viewModelScope.launch {
            val result = opdsRepository.fetchFeed(source.catalogUrl)
            result.fold(
                onSuccess = { feed ->
                    val entry = FeedStackEntry(
                        title = source.displayName,
                        url = source.catalogUrl,
                        source = source
                    )
                    _uiState.value = _uiState.value.copy(
                        currentFeed = feed,
                        feedStack = listOf(entry),
                        isLoadingFeed = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingFeed = false,
                        isBrowsing = false,
                        errorMessage = "Failed to load ${source.displayName}: ${e.message}"
                    )
                }
            )
        }
    }

    fun navigateToEntry(entry: OpdsEntry) {
        if (!entry.isNavigationEntry) return
        val navUrl = entry.navigationLink?.href ?: return
        val source = _uiState.value.selectedSource ?: return

        _uiState.value = _uiState.value.copy(
            isLoadingFeed = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val result = opdsRepository.fetchFeed(navUrl)
            result.fold(
                onSuccess = { feed ->
                    val stackEntry = FeedStackEntry(
                        title = entry.title,
                        url = navUrl,
                        source = source
                    )
                    _uiState.value = _uiState.value.copy(
                        currentFeed = feed,
                        feedStack = _uiState.value.feedStack + stackEntry,
                        isLoadingFeed = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingFeed = false,
                        errorMessage = "Failed to navigate: ${e.message}"
                    )
                }
            )
        }
    }

    fun navigateBack() {
        val stack = _uiState.value.feedStack
        if (stack.size <= 1) {
            _uiState.value = _uiState.value.copy(
                isBrowsing = false,
                currentFeed = null,
                feedStack = emptyList()
            )
            return
        }

        val newStack = stack.dropLast(1)
        val previous = newStack.last()

        _uiState.value = _uiState.value.copy(
            feedStack = newStack,
            isLoadingFeed = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val result = opdsRepository.fetchFeed(previous.url)
            result.fold(
                onSuccess = { feed ->
                    _uiState.value = _uiState.value.copy(
                        currentFeed = feed,
                        isLoadingFeed = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingFeed = false,
                        errorMessage = "Failed to navigate back: ${e.message}"
                    )
                }
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    /**
     * Search by navigating to the source's search URL if available,
     * or by filtering entries locally.
     * For built-in free sources, we use a simple approach:
     * try to append ?q=query to the catalog URL for sources that support it.
     */
    fun search() {
        val source = _uiState.value.selectedSource ?: return
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        _uiState.value = _uiState.value.copy(
            isSearching = true,
            errorMessage = null,
            searchResults = emptyList()
        )

        // Try to construct a search URL for known sources
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = when (source) {
            FreeSource.PROJECT_GUTENBERG ->
                "https://m.gutenberg.org/ebooks/search.opds/?query=$encodedQuery"
            FreeSource.STANDARD_EBOOKS ->
                "https://standardebooks.org/opds/all?query=$encodedQuery"
            FreeSource.OPEN_LIBRARY ->
                "https://openlibrary.org/search/inside.opds/?q=$encodedQuery"
        }

        viewModelScope.launch {
            val result = opdsRepository.fetchFeed(searchUrl)
            result.fold(
                onSuccess = { feed ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        searchResults = feed.entries,
                        currentFeed = feed
                    )
                },
                onFailure = { e ->
                    // Fallback: filter current feed entries locally
                    val currentFeed = _uiState.value.currentFeed
                    if (currentFeed != null) {
                        val filtered = currentFeed.entries.filter { entry ->
                            entry.title.contains(query, ignoreCase = true) ||
                                entry.authors.any { it.name.contains(query, ignoreCase = true) } ||
                                entry.summary?.contains(query, ignoreCase = true) == true
                        }
                        val msg = if (filtered.isEmpty()) {
                            "No results found for \"$query\""
                        } else {
                            null
                        }
                        _uiState.value = _uiState.value.copy(
                            isSearching = false,
                            searchResults = filtered,
                            errorMessage = msg
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isSearching = false,
                            errorMessage = "Search failed: ${e.message}"
                        )
                    }
                }
            )
        }
    }

    fun downloadEntry(entry: OpdsEntry) {
        val downloadLink = entry.downloadLinks.firstOrNull()
            ?: entry.acquisitionLinks.firstOrNull()
            ?: run {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "No download link available for \"${entry.title}\""
                )
                return
            }

        val extension = downloadLink.fileExtension ?: ".epub"
        val fileName = entry.title.replace(Regex("[^a-zA-Z0-9._-]"), "_") + extension
        val destPath = "${android.os.Environment.DIRECTORY_DOWNLOADS}/$fileName"

        _uiState.value = _uiState.value.copy(
            downloadingIds = _uiState.value.downloadingIds + entry.id,
            errorMessage = null
        )

        viewModelScope.launch {
            val result = opdsRepository.downloadBook(
                downloadUrl = downloadLink.href,
                destinationPath = destPath
            )
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        downloadingIds = _uiState.value.downloadingIds - entry.id,
                        downloadedIds = _uiState.value.downloadedIds + entry.id
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        downloadingIds = _uiState.value.downloadingIds - entry.id,
                        errorMessage = "Download failed: ${e.message}"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
