package com.mimiral.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.CollectionEntity
import com.mimiral.app.data.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CollectionPickerUiState(
    val collections: List<CollectionEntity> = emptyList(),
    val selectedCollectionIds: Set<Int> = emptySet(),
    val bookIds: List<Int> = emptyList(),
    val isSingleBook: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CollectionPickerViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _selectedCollectionIds = MutableStateFlow<Set<Int>>(emptySet())
    private val _bookIds = MutableStateFlow<List<Int>>(emptyList())
    private val _isSaving = MutableStateFlow(false)
    private val _isSaved = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CollectionPickerUiState> = combine(
        collectionRepository.getAllCollections(),
        _selectedCollectionIds,
        _bookIds,
        _isSaving,
        _isSaved,
        _error
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        CollectionPickerUiState(
            collections = values[0] as List<CollectionEntity>,
            selectedCollectionIds = values[1] as Set<Int>,
            bookIds = values[2] as List<Int>,
            isSingleBook = (values[2] as List<Int>).size == 1,
            isSaving = values[3] as Boolean,
            isSaved = values[4] as Boolean,
            error = values[5] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CollectionPickerUiState()
    )

    /**
     * Initialize the picker with the given book IDs.
     * For a single book, pre-selects the collections the book already belongs to.
     * For multiple books, shows all collections unselected.
     */
    fun initWithBooks(bookIds: List<Int>) {
        _bookIds.value = bookIds
        if (bookIds.size == 1) {
            loadExistingSelections(bookIds.first())
        }
    }

    private fun loadExistingSelections(bookId: Int) {
        viewModelScope.launch {
            try {
                val collectionIds = collectionRepository.getCollectionIdsForBook(bookId)
                _selectedCollectionIds.value = collectionIds.toSet()
            } catch (_: Exception) {
                // Non-critical, start with empty selection
            }
        }
    }

    fun toggleCollection(collectionId: Int) {
        val current = _selectedCollectionIds.value
        _selectedCollectionIds.value = if (collectionId in current) {
            current - collectionId
        } else {
            current + collectionId
        }
    }

    fun selectAll(collectionIds: List<Int>) {
        _selectedCollectionIds.value = collectionIds.toSet()
    }

    fun selectNone() {
        _selectedCollectionIds.value = emptySet()
    }

    /**
     * Save the current selection. For a single book, sets exact collection membership.
     * For multiple books, adds all selected books to all selected collections.
     */
    fun saveSelection() {
        val selectedCollectionIds = _selectedCollectionIds.value
        if (selectedCollectionIds.isEmpty()) return

        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                val collectionIdsList = selectedCollectionIds.toList()
                val targetBookIds = _bookIds.value

                if (targetBookIds.size == 1) {
                    // Single book: set exact membership
                    collectionRepository.setCollectionsForBook(
                        targetBookIds.first(),
                        collectionIdsList
                    )
                } else {
                    // Multiple books: add all books to all selected collections
                    if (collectionIdsList.isNotEmpty()) {
                        collectionRepository.addBooksToCollections(
                            targetBookIds,
                            collectionIdsList
                        )
                    }
                }
                _isSaved.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to save collection assignment"
            } finally {
                _isSaving.value = false
            }
        }
    }
}
