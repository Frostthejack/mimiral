package com.mimiral.app.ui.opds

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * UI state for direct OPDS feed browsing (no catalog DB involvement).
 */
data class KavitaOpdsBrowseUiState(
    val feedTitle: String = "",
    val currentFeed: OpdsFeed? = null,
    val isLoading: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val isDownloading: Boolean = false,
    val hasNextPage: Boolean = false,
    val nextPageUrl: String? = null,
    val errorMessage: String? = null,
    val breadcrumbs: List<OpdsBrowseBreadcrumb> = emptyList()
)

data class OpdsBrowseBreadcrumb(
    val title: String,
    val feedUrl: String
)

/**
 * ViewModel for browsing a single Kavita OPDS feed URL directly.
 * Unlike OpdsCatalogBrowserViewModel, this doesn't use the catalog DB —
 * it fetches the feed at the given URL and allows navigating within it.
 *
 * Navigation args: "feedUrl" and "feedTitle" (passed via SavedStateHandle)
 */
@HiltViewModel
class KavitaOpdsBrowseViewModel @Inject constructor(
    private val opdsRepository: OpdsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val feedUrl: String = savedStateHandle["feedUrl"] ?: ""
    private val feedTitle: String = savedStateHandle["feedTitle"] ?: "Kavita Feed"

    private val _uiState = MutableStateFlow(KavitaOpdsBrowseUiState(feedTitle = feedTitle))
    val uiState: StateFlow<KavitaOpdsBrowseUiState> = _uiState.asStateFlow()

    private val feedHistory = mutableListOf<OpdsBrowseBreadcrumb>()

    init {
        if (feedUrl.isNotBlank()) {
            browseFeed(feedUrl, feedTitle)
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "No feed URL provided"
            )
        }
    }

    fun browseFeed(url: String, title: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        val existingIdx = feedHistory.indexOfFirst { it.feedUrl == url }
        if (existingIdx >= 0) {
            while (feedHistory.size > existingIdx + 1) {
                feedHistory.removeAt(feedHistory.lastIndex)
            }
        } else {
            feedHistory.add(OpdsBrowseBreadcrumb(title = title, feedUrl = url))
        }

        viewModelScope.launch {
            val result = opdsRepository.fetchFeed(url)
            result.fold(
                onSuccess = { feed ->
                    val nextLink = feed.links.firstOrNull {
                        it.rel == OpdsLink.REL_NEXT
                    }
                    _uiState.value = _uiState.value.copy(
                        currentFeed = feed,
                        feedTitle = feed.title ?: title,
                        isLoading = false,
                        breadcrumbs = feedHistory.toList(),
                        hasNextPage = nextLink != null,
                        nextPageUrl = nextLink?.href
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load feed: ${e.message}"
                    )
                }
            )
        }
    }

    fun loadNextPage() {
        val nextUrl = _uiState.value.nextPageUrl ?: return

        _uiState.value = _uiState.value.copy(
            isLoadingNextPage = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val result = opdsRepository.fetchFeed(nextUrl)
            result.fold(
                onSuccess = { nextFeed ->
                    val currentFeed = _uiState.value.currentFeed
                    if (currentFeed != null) {
                        val mergedFeed = currentFeed.copy(
                            entries = currentFeed.entries + nextFeed.entries,
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
                ?: entry.links.firstOrNull { it.rel == OpdsLink.REL_SUBSECTION }
                ?: return
            browseFeed(url = navLink.href, title = entry.title)
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

        val extension = downloadLink.fileExtension ?: ".epub"
        val fileName = entry.title.replace(Regex("[^a-zA-Z0-9._-]"), "_") + extension
        val destPath = "${android.os.Environment.DIRECTORY_DOWNLOADS}/$fileName"

        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val result = opdsRepository.downloadBook(
                downloadUrl = downloadLink.href,
                destinationPath = destPath
            )
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isDownloading = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        errorMessage = "Download failed: ${e.message}"
                    )
                }
            )
        }
    }

    fun navigateBack(): Boolean {
        if (feedHistory.size > 1) {
            feedHistory.removeAt(feedHistory.lastIndex)
            val previous = feedHistory.last()
            browseFeed(url = previous.feedUrl, title = previous.title)
            return true
        }
        return false
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}