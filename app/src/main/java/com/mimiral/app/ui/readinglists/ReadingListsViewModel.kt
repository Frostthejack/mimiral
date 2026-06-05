package com.mimiral.app.ui.readinglists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.ReadingListEntity
import com.mimiral.app.data.repository.ReadingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReadingListWithCount(
    val list: ReadingListEntity,
    val bookCount: Int
)

data class ReadingListsUiState(
    val lists: List<ReadingListWithCount> = emptyList(),
    val isLoading: Boolean = true,
    val editingList: ReadingListEntity? = null,
    val expandedListId: Int? = null,
    val booksInExpanded: List<BookEntity> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ReadingListsViewModel @Inject constructor(
    private val readingListRepository: ReadingListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadingListsUiState())
    val uiState: StateFlow<ReadingListsUiState> = _uiState.asStateFlow()

    init {
        loadReadingLists()
    }

    private fun loadReadingLists() {
        viewModelScope.launch {
            readingListRepository.getAllReadingLists().collect { lists ->
                val withCounts = lists.map { list ->
                    ReadingListWithCount(
                        list = list,
                        bookCount = readingListRepository.getBookCountInReadingList(list.id)
                    )
                }
                _uiState.value = _uiState.value.copy(
                    lists = withCounts,
                    isLoading = false
                )
            }
        }
    }

    fun createReadingList(name: String) {
        viewModelScope.launch {
            try {
                readingListRepository.createReadingList(name)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to create list: ${e.message}"
                )
            }
        }
    }

    fun updateReadingList(list: ReadingListEntity) {
        viewModelScope.launch {
            try {
                readingListRepository.updateReadingList(list)
                _uiState.value = _uiState.value.copy(editingList = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update list: ${e.message}"
                )
            }
        }
    }

    fun deleteReadingList(list: ReadingListEntity) {
        viewModelScope.launch {
            try {
                readingListRepository.deleteReadingList(list)
                if (_uiState.value.expandedListId == list.id) {
                    _uiState.value = _uiState.value.copy(
                        expandedListId = null,
                        booksInExpanded = emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete list: ${e.message}"
                )
            }
        }
    }

    fun startEditing(list: ReadingListEntity) {
        _uiState.value = _uiState.value.copy(editingList = list)
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(editingList = null)
    }

    fun expandList(listId: Int) {
        if (_uiState.value.expandedListId == listId) {
            _uiState.value = _uiState.value.copy(
                expandedListId = null,
                booksInExpanded = emptyList()
            )
        } else {
            _uiState.value = _uiState.value.copy(expandedListId = listId)
            viewModelScope.launch {
                readingListRepository.getBooksInReadingList(listId).collect { books ->
                    _uiState.value = _uiState.value.copy(booksInExpanded = books)
                }
            }
        }
    }

    fun removeBookFromList(bookId: Int, listId: Int) {
        viewModelScope.launch {
            try {
                readingListRepository.removeBookFromReadingList(bookId, listId)
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
}
