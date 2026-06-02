package com.mimiral.app.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.reader.MarkdownElement
import com.mimiral.app.data.reader.MarkdownParser
import com.mimiral.app.data.reader.TocHeading
import com.mimiral.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MarkdownReaderUiState(
    val bookId: Int = -1,
    val bookTitle: String = "",
    val filePath: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val elements: List<MarkdownElement> = emptyList(),
    val tocHeadings: List<TocHeading> = emptyList(),
    val currentScrollIndex: Int = 0,
    val showToc: Boolean = false,
    val showBookmarks: Boolean = false,
    val isCurrentPageBookmarked: Boolean = false,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val progressPercent: Float = 0f
)

@HiltViewModel
class MarkdownReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: Int = savedStateHandle.get<Int>("bookId") ?: -1

    private val _uiState = MutableStateFlow(MarkdownReaderUiState(bookId = bookId))
    val uiState: StateFlow<MarkdownReaderUiState> = _uiState.asStateFlow()

    private val parser = MarkdownParser()
    private var currentScrollIndex = 0
    private var currentPosition: String? = null

    init {
        if (bookId == -1) {
            _uiState.update { it.copy(isLoading = false, error = "Invalid book ID") }
        }
    }

    /**
     * Load the markdown file and parse its contents.
     */
    fun loadFile(filePath: String, title: String = "") {
        _uiState.update { it.copy(filePath = filePath, bookTitle = title) }

        viewModelScope.launch {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "File not found: $filePath")
                    }
                    return@launch
                }

                val elements = parser.parse(file)
                val tocHeadings = parser.extractTableOfContents(elements)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        elements = elements,
                        tocHeadings = tocHeadings,
                        bookTitle = title.ifEmpty { file.nameWithoutExtension }
                    )
                }

                restoreProgress()
                loadBookmarks()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to parse markdown: ${e.message}")
                }
            }
        }
    }

    /**
     * Restore reading progress from the database.
     */
    private fun restoreProgress() {
        if (bookId == -1) return

        viewModelScope.launch {
            try {
                val savedProgress = bookRepository.getProgressForBook(bookId)
                if (savedProgress != null) {
                    currentScrollIndex = savedProgress.pageNumber
                    _uiState.update {
                        it.copy(
                            currentScrollIndex = savedProgress.pageNumber,
                            progressPercent = savedProgress.progressPercent
                        )
                    }
                }
            } catch (_: Exception) {
                // Non-fatal
            }
        }
    }

    /**
     * Load bookmarks for the current book.
     */
    private fun loadBookmarks() {
        if (bookId == -1) return

        viewModelScope.launch {
            try {
                bookRepository.getBookmarksForBook(bookId).collect { bookmarks ->
                    val isBookmarked = bookmarks.any { bookmark ->
                        bookmark.pageNumber == currentScrollIndex
                    }
                    _uiState.update {
                        it.copy(
                            isCurrentPageBookmarked = isBookmarked,
                            bookmarks = bookmarks
                        )
                    }
                }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    /**
     * Called when the user scrolls to a different element index.
     */
    fun onScrollIndexChanged(elementIndex: Int) {
        currentScrollIndex = elementIndex
        val total = _uiState.value.elements.size
        val percent = if (total > 0) {
            ((elementIndex + 1).toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f)
        } else 0f
        _uiState.update {
            it.copy(currentScrollIndex = elementIndex, progressPercent = percent)
        }
        saveProgress(currentScrollIndex, percent)
        currentPosition = "index:$elementIndex"
    }

    /**
     * Navigate to a TOC heading.
     */
    fun navigateToHeading(elementIndex: Int) {
        _uiState.update {
            it.copy(currentScrollIndex = elementIndex, showToc = false)
        }
        saveProgress(currentScrollIndex, _uiState.value.progressPercent)
    }

    /**
     * Toggle bookmark at the current scroll position.
     */
    fun toggleBookmark() {
        if (bookId == -1) return

        viewModelScope.launch {
            try {
                val existing = bookRepository.getBookmarkAtPosition(
                    bookId,
                    0,
                    currentScrollIndex,
                    currentPosition
                )
                if (existing != null) {
                    bookRepository.deleteBookmark(existing)
                } else {
                    val heading = findNearestHeading(currentScrollIndex)
                    bookRepository.addBookmark(
                        bookId = bookId,
                        chapterIndex = 0,
                        pageNumber = currentScrollIndex,
                        position = currentPosition,
                        title = heading ?: "Section ${currentScrollIndex + 1}",
                        note = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Bookmark error: ${e.message}") }
            }
        }
    }

    /**
     * Navigate to a bookmark.
     */
    fun navigateToBookmark(bookmark: BookmarkEntity) {
        _uiState.update {
            it.copy(
                currentScrollIndex = bookmark.pageNumber,
                showBookmarks = false
            )
        }
    }

    /**
     * Delete a bookmark.
     */
    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            try {
                bookRepository.deleteBookmark(bookmark)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Bookmark error: ${e.message}") }
            }
        }
    }

    // --- TOC dialog ---

    fun showToc() { _uiState.update { it.copy(showToc = true) } }
    fun dismissToc() { _uiState.update { it.copy(showToc = false) } }

    // --- Bookmarks dialog ---

    fun showBookmarks() { _uiState.update { it.copy(showBookmarks = true) } }
    fun dismissBookmarks() { _uiState.update { it.copy(showBookmarks = false) } }

    // --- Helpers ---

    private fun findNearestHeading(elementIndex: Int): String? {
        val headings = _uiState.value.tocHeadings
        val nearest = headings.lastOrNull { it.elementIndex <= elementIndex }
        return nearest?.title
    }

    private fun saveProgress(scrollIndex: Int, percent: Float) {
        if (bookId == -1) return
        viewModelScope.launch {
            try {
                bookRepository.saveProgress(
                    bookId = bookId,
                    chapterIndex = 0,
                    characterOffset = scrollIndex.toLong() * 500L,
                    totalCharacters = _uiState.value.elements.size.toLong() * 500L,
                    pageNumber = scrollIndex,
                    lastReadPosition = "index:$scrollIndex"
                )
            } catch (_: Exception) {
                // Silently fail
            }
        }
    }
}
