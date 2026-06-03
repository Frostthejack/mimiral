package com.mimiral.app.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.reader.ComicArchive
import com.mimiral.app.data.reader.ComicParser
import com.mimiral.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComicReaderUiState(
    val bookId: Int = 0,
    val bookTitle: String = "",
    val filePath: String = "",
    val format: String = "", // CBZ or CBR
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val progressPercent: Float = 0f,
    val isLoading: Boolean = true,
    val error: String? = null,
    // Zoom & pan
    val zoomLevel: Float = 1f,
    val scrollOffsetX: Float = 0f,
    val scrollOffsetY: Float = 0f,
    val isFitWidth: Boolean = true,
    val isFitHeight: Boolean = false,
    // Two-page spread
    val isTwoPageSpread: Boolean = false,
    // Page display mode: "fit_width", "fit_height", "fit_both"
    val fitMode: String = "fit_width",
    // UI controls visibility
    val isControlsVisible: Boolean = true,
    // Bookmarks
    val bookmarks: List<com.mimiral.app.data.local.entity.BookmarkEntity> = emptyList(),
    val showBookmarkList: Boolean = false,
    // Archive info
    val comicArchive: ComicArchive? = null
)

@HiltViewModel
class ComicReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val comicParser: ComicParser,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComicReaderUiState())
    val uiState: StateFlow<ComicReaderUiState> = _uiState.asStateFlow()

    private val bookId: Int = savedStateHandle.get<Int>("bookId") ?: 0

    init {
        loadBook()
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
                        format = book.format,
                        currentPage = savedPage
                    )

                    // Parse the comic archive
                    parseArchive(book.filePath)
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

    private suspend fun parseArchive(filePath: String) {
        try {
            val archive = comicParser.parse(filePath)
            _uiState.update {
                it.copy(
                    comicArchive = archive,
                    totalPages = archive.pageCount,
                    isLoading = false
                )
            }
            updateProgress(_uiState.value.currentPage, archive.pageCount)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Failed to parse comic: ${e.message}"
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

    // ---- Page navigation ----

    fun onPageChanged(newPage: Int) {
        val total = _uiState.value.totalPages
        val clamped = newPage.coerceIn(0, (total - 1).coerceAtLeast(0))
        _uiState.update {
            it.copy(
                currentPage = clamped,
                progressPercent = calculateProgress(clamped, total)
            )
        }
        if (total > 0) {
            saveProgress(clamped, total)
        }
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

    // ---- Zoom & Pan ----

    fun setZoomLevel(level: Float) {
        val clamped = level.coerceIn(0.5f, 5.0f)
        _uiState.update {
            it.copy(
                zoomLevel = clamped,
                isFitWidth = clamped <= 1.05f && it.fitMode == "fit_width"
            )
        }
    }

    fun setScrollOffset(x: Float, y: Float) {
        _uiState.update { it.copy(scrollOffsetX = x, scrollOffsetY = y) }
    }

    fun setFitMode(mode: String) {
        val fitWidth = mode == "fit_width"
        val fitHeight = mode == "fit_height"
        _uiState.update {
            it.copy(
                fitMode = mode,
                isFitWidth = fitWidth,
                isFitHeight = fitHeight,
                zoomLevel = if (fitWidth || fitHeight) 1.0f else it.zoomLevel,
                scrollOffsetX = 0f,
                scrollOffsetY = 0f
            )
        }
    }

    fun toggleFitWidth() {
        val currentlyFit = _uiState.value.isFitWidth
        if (currentlyFit) {
            setFitMode("free")
            _uiState.update { it.copy(zoomLevel = 2.0f) }
        } else {
            setFitMode("fit_width")
        }
    }

    fun toggleFitHeight() {
        val currentlyFit = _uiState.value.isFitHeight
        if (currentlyFit) {
            setFitMode("free")
            _uiState.update { it.copy(zoomLevel = 2.0f) }
        } else {
            setFitMode("fit_height")
        }
    }

    // ---- Two-page spread ----

    fun setTwoPageSpread(enabled: Boolean) {
        _uiState.update { it.copy(isTwoPageSpread = enabled) }
        // Recalculate current page to show proper spread
        if (enabled) {
            // Align to even page for spread start
            val current = _uiState.value.currentPage
            if (current % 2 != 0) {
                onPageChanged(current - 1)
            }
        }
    }

    fun toggleTwoPageSpread() {
        setTwoPageSpread(!_uiState.value.isTwoPageSpread)
    }

    // Controls visibility

    fun toggleControls() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    // ---- Bookmarks ----

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
            } catch (_: Exception) {}
        }
    }

    fun removeBookmark(bookmark: com.mimiral.app.data.local.entity.BookmarkEntity) {
        viewModelScope.launch {
            try {
                bookRepository.deleteBookmark(bookmark)
            } catch (_: Exception) {}
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

    // ---- Private helpers ----

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
                    totalPages = totalPages
                )
            } catch (_: Exception) {}
        }
    }
}
