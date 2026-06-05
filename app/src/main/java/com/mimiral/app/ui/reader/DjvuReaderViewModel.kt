package com.mimiral.app.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.reader.DjvuPageText
import com.mimiral.app.data.repository.BookRepository
import com.mimiral.app.data.repository.ReadingTimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for DJVU reader screen.
 */
data class DjvuReaderUiState(
    val bookId: Int = 0,
    val bookTitle: String = "",
    val filePath: String = "",
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val progressPercent: Float = 0f,
    val isLoading: Boolean = true,
    val error: String? = null,
    // UI controls visibility
    val isControlsVisible: Boolean = true,
    // Bookmarks
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val showBookmarkList: Boolean = false,
    // Text content
    val extractedText: String = "",
    val hasTextContent: Boolean = false,
    val showTextPanel: Boolean = false,
    // Page rendering
    val isPageLoading: Boolean = false
)

@HiltViewModel
class DjvuReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val readingTimeRepository: ReadingTimeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(DjvuReaderUiState())
    val uiState: StateFlow<DjvuReaderUiState> = _uiState.asStateFlow()

    private val bookId: Int = savedStateHandle.get<Int>("bookId") ?: 0

    // Reading time tracker — accumulates time across pause/resume cycles
    private val readingTimeTracker = com.mimiral.app.data.local.entity.ReadingTimeTracker()

    init {
        loadBook()
        readingTimeTracker.startSession()
    }

    private fun loadBook() {
        viewModelScope.launch {
            try {
                val book = bookRepository.getBookById(bookId)
                if (book != null) {
                    val savedPage = bookRepository.getSavedPage(bookId)

                    _uiState.value = _uiState.value.copy(
                        bookId = book.id,
                        bookTitle = book.title,
                        filePath = book.filePath,
                        currentPage = savedPage,
                        hasTextContent = false // Will be set after checking text layer
                    )
                    loadBookmarks()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Book not found"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load book"
                )
            }
        }
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            try {
                bookRepository.getBookmarksForBook(bookId).collect { bookmarks ->
                    _uiState.update { it.copy(bookmarks = bookmarks) }
                }
            } catch (_: Exception) {
                // Bookmark load failure should not disrupt reading
            }
        }
    }

    fun setTotalPages(total: Int) {
        _uiState.update { it.copy(totalPages = total, isLoading = false) }
        updateProgress(_uiState.value.currentPage, total)
    }

    fun onPageChanged(newPage: Int) {
        val total = _uiState.value.totalPages
        val clamped = newPage.coerceIn(0, (total - 1).coerceAtLeast(0))
        _uiState.update {
            it.copy(
                currentPage = clamped,
                progressPercent = calculateProgress(clamped, total),
                isPageLoading = false
            )
        }
        saveProgress(clamped, total)
    }

    fun goToPage(page: Int) {
        val total = _uiState.value.totalPages
        val clampedPage = page.coerceIn(0, (total - 1).coerceAtLeast(0))
        onPageChanged(clampedPage)
    }

    fun nextPage() {
        val current = _uiState.value.currentPage
        val total = _uiState.value.totalPages
        if (current < total - 1) {
            onPageChanged(current + 1)
        }
    }

    fun previousPage() {
        val current = _uiState.value.currentPage
        if (current > 0) {
            onPageChanged(current - 1)
        }
    }

    fun getSavedPage(): Int = _uiState.value.currentPage

    fun toggleControls() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    fun setPageLoading(loading: Boolean) {
        _uiState.update { it.copy(isPageLoading = loading) }
    }

    // --- Bookmark integration ---

    fun isPageBookmarked(page: Int): Boolean {
        return _uiState.value.bookmarks.any { it.pageNumber == page }
    }

    fun addBookmark(page: Int) {
        viewModelScope.launch {
            try {
                bookRepository.addBookmark(
                    bookId = bookId,
                    chapterIndex = 0,
                    pageNumber = page,
                    position = "page:$page",
                    title = "Page ${page + 1}",
                    note = null
                )
            } catch (_: Exception) {
                // Silently fail
            }
        }
    }

    fun removeBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            try {
                bookRepository.deleteBookmark(bookmark)
            } catch (_: Exception) {
                // Silently fail
            }
        }
    }

    fun toggleBookmarkAtCurrentPage() {
        val page = _uiState.value.currentPage
        if (isPageBookmarked(page)) {
            val bookmark = _uiState.value.bookmarks.find { it.pageNumber == page }
            bookmark?.let { removeBookmark(it) }
        } else {
            addBookmark(page)
        }
    }

    fun showBookmarkList() {
        _uiState.update { it.copy(showBookmarkList = true) }
    }

    fun dismissBookmarkList() {
        _uiState.update { it.copy(showBookmarkList = false) }
    }

    // --- Text content ---

    fun setExtractedText(pageText: DjvuPageText) {
        _uiState.update {
            it.copy(
                extractedText = pageText.text,
                hasTextContent = pageText.hasText
            )
        }
    }

    fun showTextPanel() {
        _uiState.update { it.copy(showTextPanel = true) }
    }

    fun hideTextPanel() {
        _uiState.update { it.copy(showTextPanel = false) }
    }

    // --- Private helpers ---

    private fun updateProgress(currentPage: Int, totalPages: Int) {
        _uiState.update {
            it.copy(progressPercent = calculateProgress(currentPage, totalPages))
        }
    }

    private fun calculateProgress(currentPage: Int, totalPages: Int): Float {
        return if (totalPages > 0) {
            ((currentPage + 1).toFloat() / totalPages.toFloat()) * 100f
        } else {
            0f
        }
    }

    private fun saveProgress(currentPage: Int, totalPages: Int) {
        viewModelScope.launch {
            try {
                bookRepository.saveProgress(
                    bookId = bookId,
                    pageNumber = currentPage,
                    totalPages = totalPages,
                    sessionTimeDeltaMs = readingTimeTracker.accumulatedMs()
                )
            } catch (_: Exception) {
                // Silently fail
            }
        }
    }

    /** Called when the reader is closed. Persists reading time. */
    fun onReaderClosed() {
        readingTimeTracker.stopSession()
        val totalMs = readingTimeTracker.accumulatedMs()
        if (totalMs > 0) {
            viewModelScope.launch {
                try {
                    readingTimeRepository.recordReadingTime(bookId, totalMs)
                } catch (_: Exception) {}
            }
        }
    }
}
