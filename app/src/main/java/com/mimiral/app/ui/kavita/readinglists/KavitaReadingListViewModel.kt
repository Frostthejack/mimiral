package com.mimiral.app.ui.kavita.readinglists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.kavita.KavitaReadingList
import com.mimiral.app.data.remote.kavita.KavitaReadingListItem
import com.mimiral.app.data.remote.kavita.KavitaReadingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Kavita Reading Lists screen.
 */
data class KavitaReadingListsUiState(
    val readingLists: List<KavitaReadingList> = emptyList(),
    val selectedListId: Int? = null,
    val selectedListName: String = "",
    val items: List<KavitaReadingListItem> = emptyList(),
    val itemTotalCount: Int = 0,
    val itemCurrentPage: Int = 0,
    val pageSize: Int = 20,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingItems: Boolean = false,
    val errorMessage: String? = null,
    val showingCreateDialog: Boolean = false,
    val showingEditDialog: Boolean = false,
    val editingList: KavitaReadingList? = null
)

/**
 * ViewModel for Kavita Reading Lists.
 *
 * Manages:
 * - Browsing all reading lists
 * - Selecting a list to view its items
 * - Paginated item listing with read/unread state
 * - Create, update, delete reading lists
 * - Add series to reading lists
 * - Remove read items (cleanup)
 * - Next chapter for ordered reading
 */
@HiltViewModel
class KavitaReadingListViewModel @Inject constructor(
    private val repository: KavitaReadingListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KavitaReadingListsUiState())
    val uiState: StateFlow<KavitaReadingListsUiState> = _uiState.asStateFlow()

    init {
        loadReadingLists()

        // Observe repository flows
        viewModelScope.launch {
            repository.readingLists.collect { lists ->
                _uiState.update { it.copy(readingLists = lists) }
            }
        }
        viewModelScope.launch {
            repository.currentItems.collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
        viewModelScope.launch {
            repository.itemTotalCount.collect { count ->
                _uiState.update { it.copy(itemTotalCount = count) }
            }
        }
        viewModelScope.launch {
            repository.itemCurrentPage.collect { page ->
                _uiState.update { it.copy(itemCurrentPage = page) }
            }
        }
        viewModelScope.launch {
            repository.isLoading.collect { loading ->
                _uiState.update { it.copy(isLoading = loading) }
            }
        }
        viewModelScope.launch {
            repository.errorMessage.collect { error ->
                _uiState.update { it.copy(errorMessage = error) }
            }
        }
    }

    // ── Reading Lists ──

    /**
     * Load all reading lists.
     */
    fun loadReadingLists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.loadReadingLists()
        }
    }

    /**
     * Select a reading list and load its first page of items.
     */
    fun selectReadingList(listId: Int) {
        val list = _uiState.value.readingLists.find { it.id == listId }
        _uiState.update {
            it.copy(
                selectedListId = listId,
                selectedListName = list?.name ?: ""
            )
        }
        loadListItems(listId, pageNumber = 0)
    }

    /**
     * Clear the selected list (go back to list overview).
     */
    fun clearSelectedList() {
        _uiState.update {
            it.copy(
                selectedListId = null,
                selectedListName = "",
                items = emptyList()
            )
        }
        repository.resetItems()
    }

    // ── Items ──

    /**
     * Load items for the selected reading list.
     */
    fun loadListItems(listId: Int, pageNumber: Int = 0) {
        viewModelScope.launch {
            repository.loadReadingListItems(
                readingListId = listId,
                pageNumber = pageNumber,
                pageSize = _uiState.value.pageSize
            )
        }
    }

    /**
     * Load next page of items.
     */
    fun loadNextPage() {
        val state = _uiState.value
        val listId = state.selectedListId ?: return
        val totalPages = (state.itemTotalCount + state.pageSize - 1) / state.pageSize
        if (state.itemCurrentPage + 1 < totalPages) {
            loadListItems(listId, state.itemCurrentPage + 1)
        }
    }

    /**
     * Load previous page of items.
     */
    fun loadPreviousPage() {
        val state = _uiState.value
        val listId = state.selectedListId ?: return
        if (state.itemCurrentPage > 0) {
            loadListItems(listId, state.itemCurrentPage - 1)
        }
    }

    // ── Create ──

    /**
     * Show the create dialog.
     */
    fun showCreateDialog() {
        _uiState.update { it.copy(showingCreateDialog = true) }
    }

    /**
     * Dismiss the create dialog.
     */
    fun dismissCreateDialog() {
        _uiState.update { it.copy(showingCreateDialog = false) }
    }

    /**
     * Create a new reading list.
     */
    fun createReadingList(name: String, summary: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val list = repository.createReadingList(name, summary)
            if (list != null) {
                _uiState.update { it.copy(showingCreateDialog = false) }
            }
        }
    }

    // ── Update ──

    /**
     * Show the edit dialog for a reading list.
     */
    fun showEditDialog(list: KavitaReadingList) {
        _uiState.update { it.copy(showingEditDialog = true, editingList = list) }
    }

    /**
     * Dismiss the edit dialog.
     */
    fun dismissEditDialog() {
        _uiState.update { it.copy(showingEditDialog = false, editingList = null) }
    }

    /**
     * Update a reading list.
     */
    fun updateReadingList(id: Int, name: String, summary: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val success = repository.updateReadingList(
                id = id,
                name = name,
                summary = summary
            )
            if (success) {
                _uiState.update {
                    it.copy(
                        showingEditDialog = false,
                        editingList = null,
                        selectedListName = name
                    )
                }
            }
        }
    }

    // ── Delete ──

    /**
     * Delete a reading list.
     */
    fun deleteReadingList(id: Int) {
        viewModelScope.launch {
            val success = repository.deleteReadingList(id)
            if (success && _uiState.value.selectedListId == id) {
                clearSelectedList()
            }
        }
    }

    // ── Add Items ──

    /**
     * Add a series to the selected reading list.
     */
    fun addSeriesToList(seriesId: Int) {
        val listId = _uiState.value.selectedListId ?: return
        viewModelScope.launch {
            repository.addSeriesToReadingList(listId, seriesId)
            // Refresh items after adding
            loadListItems(listId, _uiState.value.itemCurrentPage)
        }
    }

    // ── Cleanup ──

    /**
     * Remove read items from the selected reading list.
     */
    fun removeReadItems() {
        val listId = _uiState.value.selectedListId ?: return
        viewModelScope.launch {
            repository.removeReadItems(listId)
            // Refresh items after cleanup
            loadListItems(listId, 0)
        }
    }

    // ── Utility ──

    /**
     * Clear error message.
     */
    fun clearError() {
        repository.clearError()
    }

    /**
     * Refresh the current view.
     */
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        val state = _uiState.value
        if (state.selectedListId != null) {
            loadListItems(state.selectedListId, state.itemCurrentPage)
        } else {
            loadReadingLists()
        }
        _uiState.update { it.copy(isRefreshing = false) }
    }
}
