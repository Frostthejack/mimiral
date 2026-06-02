package com.mimiral.app.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Fb2ReaderUiState(
    val bookId: Int = -1,
    val isLoading: Boolean = true,
    val currentChapter: Int = 0,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val progress: ReaderProgress = ReaderProgress(),
    val chapters: List<EpubChapter> = emptyList(),
    val showToc: Boolean = false,
    val showBookmarks: Boolean = false,
    val isCurrentPageBookmarked: Boolean = false,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class Fb2ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: Int = savedStateHandle.get<Int>("bookId") ?: -1

    private val _uiState = MutableStateFlow(Fb2ReaderUiState(bookId = bookId))
    val uiState: StateFlow<Fb2ReaderUiState> = _uiState.asStateFlow()

    private var currentChapterIndex = 0
    private var currentPageNumber = 0
    private var currentPosition: String? = null

    init {
        restoreProgress()
        loadToc()
    }

    private fun loadToc() {
        val sampleChapters = listOf(
            EpubChapter(0, "Cover", 0, 0),
            EpubChapter(1, "Chapter 1", 1, 2),
            EpubChapter(2, "Chapter 2", 3, 4),
            EpubChapter(3, "Chapter 3", 5, 5)
        )
        _uiState.update { it.copy(chapters = sampleChapters) }
    }

    private fun restoreProgress() {
        if (bookId == -1) {
            _uiState.update { it.copy(isLoading = false, error = "Invalid book ID") }
            return
        }

        viewModelScope.launch {
            try {
                val savedProgress = bookRepository.getProgressForBook(bookId)
                if (savedProgress != null) {
                    currentChapterIndex = savedProgress.chapterIndex
                    currentPageNumber = savedProgress.pageNumber
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentChapter = savedProgress.chapterIndex,
                            currentPage = savedProgress.pageNumber,
                            progress = ReaderProgress(
                                chapterIndex = savedProgress.chapterIndex,
                                characterOffset = savedProgress.characterOffset,
                                totalCharacters = savedProgress.totalCharacters,
                                pageNumber = savedProgress.pageNumber,
                                progressPercent = savedProgress.progressPercent,
                                lastReadPosition = savedProgress.lastReadPosition
                            )
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
                loadBookmarks()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            try {
                bookRepository.getBookmarksForBook(bookId).collect { bookmarks ->
                    val isBookmarked = bookmarks.any { bookmark ->
                        bookmark.chapterIndex == currentChapterIndex &&
                            bookmark.pageNumber == currentPageNumber
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

    fun onPageTurn(
        chapterIndex: Int,
        characterOffset: Long,
        totalCharacters: Long,
        pageNumber: Int,
        lastReadPosition: String? = null
    ) {
        currentChapterIndex = chapterIndex
        currentPageNumber = pageNumber
        currentPosition = lastReadPosition

        _uiState.update {
            val progressPercent = calculateProgress(characterOffset, totalCharacters)
            it.copy(
                currentChapter = chapterIndex,
                currentPage = pageNumber,
                progress = ReaderProgress(
                    chapterIndex = chapterIndex,
                    characterOffset = characterOffset,
                    totalCharacters = totalCharacters,
                    pageNumber = pageNumber,
                    progressPercent = progressPercent,
                    lastReadPosition = lastReadPosition
                )
            )
        }

        saveProgress(
            bookId = bookId,
            chapterIndex = chapterIndex,
            characterOffset = characterOffset,
            totalCharacters = totalCharacters,
            pageNumber = pageNumber,
            lastReadPosition = lastReadPosition
        )
    }

    fun navigateToChapter(chapterIndex: Int) {
        val chapter = _uiState.value.chapters.getOrNull(chapterIndex) ?: return
        val page = chapter.startPage

        _uiState.update {
            it.copy(
                currentChapter = chapterIndex,
                currentPage = page,
                showToc = false
            )
        }

        onPageTurn(
            chapterIndex = chapterIndex,
            characterOffset = page.toLong() * 500L,
            totalCharacters = _uiState.value.totalPages.toLong() * 500L,
            pageNumber = page,
            lastReadPosition = "chapter:$chapterIndex:page:$page"
        )
    }

    fun nextChapter() {
        val state = _uiState.value
        val nextIndex = state.currentChapter + 1
        if (nextIndex < state.chapters.size) {
            navigateToChapter(nextIndex)
        }
    }

    fun previousChapter() {
        val prevIndex = _uiState.value.currentChapter - 1
        if (prevIndex >= 0) {
            navigateToChapter(prevIndex)
        }
    }

    fun hasNextChapter(): Boolean {
        val state = _uiState.value
        return state.currentChapter + 1 < state.chapters.size
    }

    fun hasPreviousChapter(): Boolean {
        return _uiState.value.currentChapter > 0
    }

    fun toggleBookmark() {
        val state = _uiState.value
        if (bookId == -1) return

        viewModelScope.launch {
            try {
                val existing = bookRepository.getBookmarkAtPosition(
                    bookId,
                    currentChapterIndex,
                    currentPageNumber,
                    currentPosition
                )
                if (existing != null) {
                    bookRepository.deleteBookmark(existing)
                } else {
                    val chapterTitle = state.chapters.getOrNull(currentChapterIndex)?.title
                        ?: "Chapter ${currentChapterIndex + 1}"
                    bookRepository.addBookmark(
                        bookId = bookId,
                        chapterIndex = currentChapterIndex,
                        pageNumber = currentPageNumber,
                        position = currentPosition,
                        title = chapterTitle,
                        note = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Bookmark error: ${e.message}") }
            }
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            try {
                bookRepository.deleteBookmark(bookmark)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Bookmark error: ${e.message}") }
            }
        }
    }

    fun showToc() { _uiState.update { it.copy(showToc = true) } }
    fun dismissToc() { _uiState.update { it.copy(showToc = false) } }
    fun showBookmarks() { _uiState.update { it.copy(showBookmarks = true) } }
    fun dismissBookmarks() { _uiState.update { it.copy(showBookmarks = false) } }

    fun navigateToBookmark(chapterIndex: Int, pageNumber: Int) {
        _uiState.update {
            it.copy(
                currentChapter = chapterIndex,
                currentPage = pageNumber,
                showBookmarks = false
            )
        }
        onPageTurn(
            chapterIndex = chapterIndex,
            characterOffset = pageNumber.toLong() * 500L,
            totalCharacters = _uiState.value.totalPages.toLong() * 500L,
            pageNumber = pageNumber,
            lastReadPosition = "chapter:$chapterIndex:page:$pageNumber"
        )
    }

    fun setTotalPages(total: Int) {
        _uiState.update { it.copy(totalPages = total) }
    }

    fun saveCurrentProgress() {
        val state = _uiState.value
        if (bookId == -1) return
        saveProgress(
            bookId = bookId,
            chapterIndex = state.progress.chapterIndex,
            characterOffset = state.progress.characterOffset,
            totalCharacters = state.progress.totalCharacters,
            pageNumber = state.progress.pageNumber,
            lastReadPosition = state.progress.lastReadPosition
        )
    }

    private fun calculateProgress(characterOffset: Long, totalCharacters: Long): Float {
        return if (totalCharacters > 0) {
            (characterOffset.toFloat() / totalCharacters.toFloat() * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }
    }

    private fun saveProgress(
        bookId: Int,
        chapterIndex: Int,
        characterOffset: Long,
        totalCharacters: Long,
        pageNumber: Int,
        lastReadPosition: String?
    ) {
        viewModelScope.launch {
            try {
                bookRepository.saveProgress(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    characterOffset = characterOffset,
                    totalCharacters = totalCharacters,
                    pageNumber = pageNumber,
                    lastReadPosition = lastReadPosition
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save progress: ${e.message}") }
            }
        }
    }
}
