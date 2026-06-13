package com.mimiral.app.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.CollectionEntity
import com.mimiral.app.data.local.entity.CollectionWithBookCount
import com.mimiral.app.data.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CollectionWithCount(
    val collection: CollectionEntity,
    val bookCount: Int
)

data class CollectionsUiState(
    val collections: List<CollectionWithCount> = emptyList(),
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val editingCollection: CollectionEntity? = null,
    val expandedCollectionId: Int? = null,
    val booksInExpanded: List<BookEntity> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    private val _collections = MutableStateFlow<List<CollectionWithCount>>(emptyList())

    init {
        loadCollections()
    }

    private fun loadCollections() {
        viewModelScope.launch {
            collectionRepository.getAllCollectionsWithBookCount().collect { withCounts ->
                _collections.value = withCounts.map { it.toCollectionWithCount() }
                _uiState.value = _uiState.value.copy(
                    collections = withCounts.map { it.toCollectionWithCount() },
                    isLoading = false
                )
            }
        }
    }

    fun createCollection(name: String, description: String?) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isCreating = true)
                collectionRepository.createCollection(name, description)
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    editingCollection = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = "Failed to create collection: ${e.message}"
                )
            }
        }
    }

    fun updateCollection(collection: CollectionEntity) {
        viewModelScope.launch {
            try {
                collectionRepository.updateCollection(collection)
                _uiState.value = _uiState.value.copy(editingCollection = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update collection: ${e.message}"
                )
            }
        }
    }

    fun deleteCollection(collection: CollectionEntity) {
        viewModelScope.launch {
            try {
                collectionRepository.deleteCollection(collection)
                // Collapse if this was the expanded collection
                if (_uiState.value.expandedCollectionId == collection.id) {
                    _uiState.value = _uiState.value.copy(
                        expandedCollectionId = null,
                        booksInExpanded = emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete collection: ${e.message}"
                )
            }
        }
    }

    fun startEditing(collection: CollectionEntity) {
        _uiState.value = _uiState.value.copy(editingCollection = collection)
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(editingCollection = null)
    }

    fun expandCollection(collectionId: Int) {
        if (_uiState.value.expandedCollectionId == collectionId) {
            // Collapse
            _uiState.value = _uiState.value.copy(
                expandedCollectionId = null,
                booksInExpanded = emptyList()
            )
        } else {
            // Expand and load books
            _uiState.value = _uiState.value.copy(expandedCollectionId = collectionId)
            viewModelScope.launch {
                collectionRepository.getBooksInCollection(collectionId).collect { books ->
                    _uiState.value = _uiState.value.copy(booksInExpanded = books)
                }
            }
        }
    }

    fun removeBookFromCollection(bookId: Int, collectionId: Int) {
        viewModelScope.launch {
            try {
                collectionRepository.removeBookFromCollection(bookId, collectionId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to remove book: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun CollectionWithBookCount.toCollectionWithCount(): CollectionWithCount =
        CollectionWithCount(
            collection = CollectionEntity(
                id = id,
                name = name,
                description = description,
                createdTime = createdTime,
                sortOrder = sortOrder
            ),
            bookCount = bookCount
        )
}
