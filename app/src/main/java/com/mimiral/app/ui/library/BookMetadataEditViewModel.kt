package com.mimiral.app.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.TagEntity
import com.mimiral.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MetadataEditUiState(
    val book: BookEntity? = null,
    val title: String = "",
    val author: String = "",
    val rating: Float? = null,
    val tags: List<TagEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BookMetadataEditViewModel @Inject constructor(
    application: Application,
    private val bookRepository: BookRepository
) : AndroidViewModel(application) {

    private val _book = MutableStateFlow<BookEntity?>(null)
    private val _title = MutableStateFlow("")
    private val _author = MutableStateFlow("")
    private val _rating = MutableStateFlow<Float?>(null)
    private val _tags = MutableStateFlow<List<TagEntity>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _isSaving = MutableStateFlow(false)
    private val _saveSuccess = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MetadataEditUiState> = combine(
        _book,
        _title,
        _author,
        _rating,
        _tags,
        _isLoading,
        _isSaving,
        _saveSuccess,
        _error
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        MetadataEditUiState(
            book = values[0] as? BookEntity,
            title = values[1] as String,
            author = values[2] as String,
            rating = values[3] as? Float,
            tags = values[4] as List<TagEntity>,
            isLoading = values[5] as Boolean,
            isSaving = values[6] as Boolean,
            saveSuccess = values[7] as Boolean,
            error = values[8] as? String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MetadataEditUiState()
    )

    private var currentBookId: Int? = null

    fun loadBook(bookId: Int) {
        currentBookId = bookId
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val book = bookRepository.getBookById(bookId)
                if (book != null) {
                    _book.value = book
                    _title.value = book.title
                    _author.value = book.author ?: ""
                    _rating.value = book.rating
                    // Load tags
                    val tags = bookRepository.getTagsForBookList(bookId)
                    _tags.value = tags
                } else {
                    _error.value = "Book not found"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load book"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setTitle(title: String) {
        _title.value = title
    }

    fun setAuthor(author: String) {
        _author.value = author
    }

    fun setRating(rating: Float?) {
        _rating.value = rating
        // Persist rating immediately
        val bookId = currentBookId ?: return
        viewModelScope.launch {
            try {
                bookRepository.updateRating(bookId, rating)
            } catch (_: Exception) {
                // Non-critical, will be saved on full save
            }
        }
    }

    fun addTag(tagName: String) {
        val bookId = currentBookId ?: return
        val trimmed = tagName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                bookRepository.addTagToBook(bookId, trimmed)
                _tags.value = bookRepository.getTagsForBookList(bookId)
            } catch (e: Exception) {
                _error.value = "Failed to add tag: ${e.message}"
            }
        }
    }

    fun removeTag(tagId: Int) {
        val bookId = currentBookId ?: return
        viewModelScope.launch {
            try {
                bookRepository.removeTagFromBook(bookId, tagId)
                _tags.value = bookRepository.getTagsForBookList(bookId)
            } catch (e: Exception) {
                _error.value = "Failed to remove tag: ${e.message}"
            }
        }
    }

    fun save() {
        val bookId = currentBookId ?: return
        _isSaving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val book = bookRepository.getBookById(bookId)
                if (book != null) {
                    val updatedBook = book.copy(
                        title = _title.value.trim().ifBlank { book.title },
                        author = _author.value.trim().ifBlank { null }
                    )
                    bookRepository.updateBook(updatedBook)
                    _book.value = updatedBook
                    _saveSuccess.value = true
                } else {
                    _error.value = "Book not found"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to save"
            } finally {
                _isSaving.value = false
            }
        }
    }
}
