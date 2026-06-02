package com.mimiral.app.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.reader.Sentence
import com.mimiral.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EpubChapter(
    val index: Int,
    val title: String,
    val startPage: Int,
    val endPage: Int
)

data class ReaderProgress(
    val chapterIndex: Int = 0,
    val characterOffset: Long = 0,
    val totalCharacters: Long = 0,
    val pageNumber: Int = 0,
    val progressPercent: Float = 0f,
    val lastReadPosition: String? = null
)

/**
 * Represents the current TTS sentence being read, using the data-layer Sentence type.
 * Alias for clarity in the reader UI context.
 */
typealias TtsSentence = Sentence

data class ReaderUiState(
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
    val error: String? = null,
    val ttsPlaying: Boolean = false,
    val ttsPaused: Boolean = false,
    /** The sentence currently being read by TTS, or null if no TTS active. */
    val currentTtsSentence: TtsSentence? = null
)

@HiltViewModel
class EpubReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: Int = savedStateHandle.get<Int>("bookId") ?: -1

    private val _uiState = MutableStateFlow(ReaderUiState(bookId = bookId))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // Track reading position for bookmark toggle
    private var currentChapterIndex = 0
    private var currentPageNumber = 0
    private var currentPosition: String? = null

    init {
        restoreProgress()
        loadToc()
    }

    /**
     * Load the table of contents (chapter list) for the book.
     * In production this would parse the EPUB's NCX/nav document.
     * For now, generates sample chapters based on the book.
     */
    private fun loadToc() {
        // Sample TOC — in production, parse from EPUB spine/NCX
        val sampleChapters = listOf(
            EpubChapter(0, "Cover", 0, 0),
            EpubChapter(1, "Chapter 1: The Beginning", 1, 2),
            EpubChapter(2, "Chapter 2: The Journey", 3, 4),
            EpubChapter(3, "Chapter 3: The End", 5, 5)
        )
        _uiState.update { it.copy(chapters = sampleChapters) }
    }

    /**
     * Restore reading progress from the database when opening a book.
     */
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
                // Load bookmarks for this book to check bookmark state
                loadBookmarks()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Load bookmarks for the current book and observe changes.
     */
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
                // Non-critical — bookmark state can default to false
            }
        }
    }

    /**
     * Called on each page turn. Saves the current reading position
     * and recalculates progress percentage.
     */
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

    /**
     * Navigate to a specific chapter from the TOC.
     */
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

        // Trigger page turn to save progress
        onPageTurn(
            chapterIndex = chapterIndex,
            characterOffset = page.toLong() * 500L,
            totalCharacters = _uiState.value.totalPages.toLong() * 500L,
            pageNumber = page,
            lastReadPosition = "chapter:$chapterIndex:page:$page"
        )
    }

    /**
     * Navigate to the next chapter.
     */
    fun nextChapter() {
        val state = _uiState.value
        val nextIndex = state.currentChapter + 1
        if (nextIndex < state.chapters.size) {
            navigateToChapter(nextIndex)
        }
    }

    /**
     * Navigate to the previous chapter.
     */
    fun previousChapter() {
        val prevIndex = _uiState.value.currentChapter - 1
        if (prevIndex >= 0) {
            navigateToChapter(prevIndex)
        }
    }

    /**
     * Check if there is a next chapter available.
     */
    fun hasNextChapter(): Boolean {
        val state = _uiState.value
        return state.currentChapter + 1 < state.chapters.size
    }

    /**
     * Check if there is a previous chapter available.
     */
    fun hasPreviousChapter(): Boolean {
        return _uiState.value.currentChapter > 0
    }

    /**
     * Toggle bookmark at the current reading position.
     */
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
                    val chapterTitle = state.chapters.getOrNull(currentChapterIndex)?.title ?: "Chapter ${currentChapterIndex + 1}"
                    bookRepository.addBookmark(
                        bookId = bookId,
                        chapterIndex = currentChapterIndex,
                        pageNumber = currentPageNumber,
                        position = currentPosition,
                        title = chapterTitle,
                        note = null
                    )
                }
                // Bookmark state will auto-update via the Flow in loadBookmarks()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Bookmark error: ${e.message}") }
            }
        }
    }

    /**
     * Delete a specific bookmark.
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

    /**
     * Show the table of contents dialog.
     */
    fun showToc() {
        _uiState.update { it.copy(showToc = true) }
    }

    /**
     * Dismiss the table of contents dialog.
     */
    fun dismissToc() {
        _uiState.update { it.copy(showToc = false) }
    }

    /**
     * Show the bookmarks dialog.
     */
    fun showBookmarks() {
        _uiState.update { it.copy(showBookmarks = true) }
    }

    /**
     * Dismiss the bookmarks dialog.
     */
    fun dismissBookmarks() {
        _uiState.update { it.copy(showBookmarks = false) }
    }

    /**
     * Navigate to a specific bookmark.
     */
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

    /**
     * Calculate progress percentage from character offset.
     */
    private fun calculateProgress(characterOffset: Long, totalCharacters: Long): Float {
        return if (totalCharacters > 0) {
            (characterOffset.toFloat() / totalCharacters.toFloat() * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }
    }

    /**
     * Persist progress to Room database.
     */
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

    /**
     * Update total pages (called when book is loaded and pagination is computed).
     */
    fun setTotalPages(total: Int) {
        _uiState.update { it.copy(totalPages = total) }
    }

    /**
     * Manually save current progress (e.g., on pause/stop lifecycle events).
     */
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

    // --- TTS state management ---

    fun setTtsPlaying(playing: Boolean) {
        _uiState.update { it.copy(ttsPlaying = playing, ttsPaused = false) }
    }

    fun setTtsPaused(paused: Boolean) {
        _uiState.update { it.copy(ttsPlaying = false, ttsPaused = paused) }
    }

    fun setTtsStopped() {
        _uiState.update { it.copy(ttsPlaying = false, ttsPaused = false) }
    }

    /**
     * Update the current TTS sentence being read.
     * Called from the UI layer when a sentence broadcast is received.
     *
     * @param sentence The active sentence, or null if playback stopped/paused.
     */
    fun onTtsSentenceChanged(sentence: TtsSentence?) {
        _uiState.update { it.copy(currentTtsSentence = sentence) }
    }
}
