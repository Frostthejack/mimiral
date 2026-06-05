package com.mimiral.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookmarkUiState(
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val isCurrentPositionBookmarked: Boolean = false,
    val showBookmarkList: Boolean = false
)

@HiltViewModel
class BookmarkManager @Inject constructor(
    private val bookRepository: BookRepository,
    private val syncManager: BookmarkSyncManager
) : ViewModel() {

    private val _currentBookId = MutableStateFlow<Int?>(null)

    private val _uiState = MutableStateFlow(BookmarkUiState())
    val uiState: StateFlow<BookmarkUiState> = _uiState.asStateFlow()

    // Current reading position tracked for bookmark indicator
    private val _currentChapterIndex = MutableStateFlow(0)
    private val _currentPageNumber = MutableStateFlow(0)
    private val _currentPosition = MutableStateFlow<String?>(null)

    /**
     * Call when the reader opens a book. Loads all bookmarks for that book
     * and triggers a sync with Kavita if the book is from a remote source.
     */
    fun loadBook(bookId: Int) {
        _currentBookId.value = bookId
        viewModelScope.launch {
            bookRepository.getBookmarksForBook(bookId)
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    emptyList()
                )
                .collect { bookmarks ->
                    _uiState.value = _uiState.value.copy(
                        bookmarks = bookmarks
                    )
                    checkIfCurrentPositionBookmarked()
                }
        }
        // Trigger sync with Kavita in background
        viewModelScope.launch {
            syncManager.syncOnBookOpen(bookId)
        }
    }

    /**
     * Call when the reader navigates to a new position.
     * Updates the bookmark indicator state.
     */
    fun updatePosition(
        chapterIndex: Int,
        pageNumber: Int,
        position: String? = null
    ) {
        _currentChapterIndex.value = chapterIndex
        _currentPageNumber.value = pageNumber
        _currentPosition.value = position
        checkIfCurrentPositionBookmarked()
    }

    /**
     * Toggle bookmark at the current reading position.
     * If bookmarked, remove it. If not, add it.
     * Syncs changes to Kavita.
     */
    fun toggleBookmarkAtCurrentPosition() {
        val bookId = _currentBookId.value ?: return
        val chapter = _currentChapterIndex.value
        val page = _currentPageNumber.value
        val position = _currentPosition.value

        viewModelScope.launch {
            val existing = bookRepository.getBookmarkAtPosition(
                bookId,
                chapter,
                page,
                position
            )
            if (existing != null) {
                // Delete bookmark and sync removal to Kavita
                bookRepository.deleteBookmark(existing)
                syncManager.syncOnBookmarkDeleted(existing)
            } else {
                val title = "Chapter ${chapter + 1}"
                val bookmarkId = bookRepository.addBookmark(
                    bookId = bookId,
                    chapterIndex = chapter,
                    pageNumber = page,
                    position = position,
                    title = title,
                    note = null
                )
                // Push new bookmark to Kavita
                val newBookmark = BookmarkEntity(
                    id = bookmarkId.toInt(),
                    bookId = bookId,
                    chapterIndex = chapter,
                    pageNumber = page,
                    position = position,
                    title = title,
                    note = null,
                    createdTime = System.currentTimeMillis(),
                    modifiedTime = System.currentTimeMillis()
                )
                syncManager.syncOnBookmarkCreated(newBookmark)
            }
            // State will auto-update via the Flow collection in loadBook()
        }
    }

    /**
     * Delete a specific bookmark.
     * Syncs the deletion to Kavita.
     */
    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            bookRepository.deleteBookmark(bookmark)
            syncManager.syncOnBookmarkDeleted(bookmark)
        }
    }

    /**
     * Show the bookmark list dialog.
     */
    fun showBookmarkList() {
        _uiState.value = _uiState.value.copy(showBookmarkList = true)
    }

    /**
     * Dismiss the bookmark list dialog.
     */
    fun dismissBookmarkList() {
        _uiState.value = _uiState.value.copy(showBookmarkList = false)
    }

    /**
     * Check if the current reading position has a bookmark.
     */
    private fun checkIfCurrentPositionBookmarked() {
        val bookId = _currentBookId.value ?: return
        val chapter = _currentChapterIndex.value
        val page = _currentPageNumber.value
        val position = _currentPosition.value

        viewModelScope.launch {
            val isBookmarked = bookRepository.isBookmarkedAtPosition(
                bookId,
                chapter,
                page,
                position
            )
            _uiState.value = _uiState.value.copy(
                isCurrentPositionBookmarked = isBookmarked
            )
        }
    }
}
