package com.mimiral.app.ui.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.reader.RtfParseResult
import com.mimiral.app.data.reader.RtfParser
import com.mimiral.app.data.reader.TxtParseResult
import com.mimiral.app.data.reader.TxtParser
import com.mimiral.app.data.reader.resolveFileToCache
import com.mimiral.app.data.repository.BookRepository
import com.mimiral.app.data.repository.ReadingTimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TxtRtfChapter(
    val index: Int,
    val title: String,
    val startPage: Int,
    val endPage: Int
)

data class TxtRtfReaderProgress(
    val chapterIndex: Int = 0,
    val characterOffset: Long = 0,
    val totalCharacters: Long = 0,
    val pageNumber: Int = 0,
    val progressPercent: Float = 0f,
    val lastReadPosition: String? = null
)

data class TxtRtfUiState(
    val bookId: Int = -1,
    val isLoading: Boolean = true,
    val isPaginating: Boolean = false,
    val currentChapter: Int = 0,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val progress: TxtRtfReaderProgress = TxtRtfReaderProgress(),
    val chapters: List<TxtRtfChapter> = emptyList(),
    val showToc: Boolean = false,
    val showBookmarks: Boolean = false,
    val isCurrentPageBookmarked: Boolean = false,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val error: String? = null,
    val title: String = "",
    val format: String = "TXT"
)

@HiltViewModel
class TxtRtfReaderViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bookRepository: BookRepository,
    private val readingTimeRepository: ReadingTimeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: Int = savedStateHandle.get<Int>("bookId") ?: -1

    private val _uiState = MutableStateFlow(TxtRtfUiState(bookId = bookId))
    val uiState: StateFlow<TxtRtfUiState> = _uiState.asStateFlow()

    private var currentChapterIndex = 0
    private var currentPageNumber = 0
    private var currentPosition: String? = null

    private val txtParser = TxtParser()
    private val rtfParser = RtfParser()

    // Full parsed text and chapter breaks
    private var fullText: String = ""
    private var chapterBreaks: List<Int> = listOf(0)
    private var chapterTexts: MutableList<String> = mutableListOf()

    // Reading time tracker — accumulates time across pause/resume cycles
    private val readingTimeTracker = com.mimiral.app.data.local.entity.ReadingTimeTracker()

    init {
        loadBook()
        readingTimeTracker.startSession()
    }

    private fun loadBook() {
        if (bookId == -1) {
            _uiState.update { it.copy(isLoading = false, error = "Invalid book ID") }
            return
        }

        viewModelScope.launch {
            try {
                val book = bookRepository.getBookById(bookId)
                if (book == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Book not found") }
                    return@launch
                }

                val file = resolveFileToCache(appContext, book.filePath, "txt")
                    ?: run {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Cannot access file: ${book.filePath}"
                            )
                        }
                        return@launch
                    }
                val format = book.format.uppercase()

                // Parse the file based on format
                val parseResult = when (format) {
                    "TXT" -> txtParser.parse(file)
                    "RTF" -> rtfParser.parse(file)
                    else -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Unsupported format: " + format
                            )
                        }
                        return@launch
                    }
                }

                when (parseResult) {
                    is TxtParseResult.Success -> {
                        fullText = parseResult.text
                        chapterBreaks = parseResult.chapterBreaks
                        buildChapters(parseResult.title, format)
                        restoreProgress()
                        loadBookmarks()
                    }
                    is TxtParseResult.Error -> {
                        _uiState.update { it.copy(isLoading = false, error = parseResult.message) }
                    }
                    is RtfParseResult.Success -> {
                        fullText = parseResult.text
                        chapterBreaks = parseResult.chapterBreaks
                        buildChapters(parseResult.title, format)
                        restoreProgress()
                        loadBookmarks()
                    }
                    is RtfParseResult.Error -> {
                        _uiState.update { it.copy(isLoading = false, error = parseResult.message) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load book: " + e.message
                    )
                }
            }
        }
    }

    private fun buildChapters(bookTitle: String, format: String) {
        // Build chapter list from chapter breaks
        val chaptersList = mutableListOf<TxtRtfChapter>()
        chapterTexts = mutableListOf<String>()

        for (i in chapterBreaks.indices) {
            val start = chapterBreaks[i]
            val end = if (i + 1 < chapterBreaks.size) chapterBreaks[i + 1] else fullText.length
            val chapterText = if (start < fullText.length) {
                fullText.substring(
                    start,
                    end.coerceAtMost(fullText.length)
                )
            } else {
                ""
            }
            chapterTexts.add(chapterText)

            // Extract chapter title from first line
            val firstLine = chapterText.trimStart().substringBefore("\n").trim()
            val chapterTitle = if (firstLine.isNotEmpty() && firstLine.length <= 80) {
                firstLine
            } else {
                "Chapter " + (i + 1)
            }

            chaptersList.add(
                TxtRtfChapter(
                    index = i,
                    title = chapterTitle,
                    startPage = 0,
                    endPage = 0
                )
            )
        }

        if (chaptersList.isEmpty()) {
            chaptersList.add(TxtRtfChapter(0, bookTitle, 0, 0))
            chapterTexts.add(fullText)
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                title = bookTitle,
                format = format,
                chapters = chaptersList
            )
        }
    }

    fun getChapterText(chapterIndex: Int): String {
        return if (chapterIndex in chapterTexts.indices) chapterTexts[chapterIndex] else ""
    }

    fun getFullText(): String = fullText

    fun getChapterCount(): Int = chapterTexts.size

    private fun restoreProgress() {
        viewModelScope.launch {
            try {
                val savedProgress = bookRepository.getProgressForBook(bookId)
                if (savedProgress != null) {
                    currentChapterIndex = savedProgress.chapterIndex
                    currentPageNumber = savedProgress.pageNumber
                    _uiState.update {
                        it.copy(
                            currentChapter = savedProgress.chapterIndex,
                            currentPage = savedProgress.pageNumber,
                            progress = TxtRtfReaderProgress(
                                chapterIndex = savedProgress.chapterIndex,
                                characterOffset = savedProgress.characterOffset,
                                totalCharacters = savedProgress.totalCharacters,
                                pageNumber = savedProgress.pageNumber,
                                progressPercent = savedProgress.progressPercent,
                                lastReadPosition = savedProgress.lastReadPosition
                            )
                        )
                    }
                }
            } catch (_: Exception) { }
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
            } catch (_: Exception) { }
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
                progress = TxtRtfReaderProgress(
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
            bookId,
            chapterIndex,
            characterOffset,
            totalCharacters,
            pageNumber,
            lastReadPosition,
            readingTimeTracker.accumulatedMs()
        )
    }

    fun navigateToChapter(chapterIndex: Int) {
        val chapter = _uiState.value.chapters.getOrNull(chapterIndex) ?: return
        _uiState.update {
            it.copy(
                currentChapter = chapterIndex,
                currentPage = 0,
                showToc = false
            )
        }
        onPageTurn(
            chapterIndex,
            0,
            fullText.length.toLong(),
            0,
            "chapter:" + chapterIndex + ":page:0"
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
                        ?: "Chapter " + (currentChapterIndex + 1)
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
                _uiState.update { it.copy(error = "Bookmark error: " + e.message) }
            }
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            try {
                bookRepository.deleteBookmark(bookmark)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Bookmark error: " + e.message) }
            }
        }
    }

    fun showToc() { _uiState.update { it.copy(showToc = true) } }
    fun dismissToc() { _uiState.update { it.copy(showToc = false) } }
    fun showBookmarks() { _uiState.update { it.copy(showBookmarks = true) } }
    fun dismissBookmarks() { _uiState.update { it.copy(showBookmarks = false) } }

    fun navigateToBookmark(chapterIndex: Int, pageNumber: Int) {
        _uiState.update {
            it.copy(currentChapter = chapterIndex, currentPage = pageNumber, showBookmarks = false)
        }
        onPageTurn(
            chapterIndex,
            pageNumber.toLong() * 500L,
            fullText.length.toLong(),
            pageNumber,
            "chapter:" + chapterIndex + ":page:" + pageNumber
        )
    }

    fun setTotalPages(total: Int) {
        _uiState.update { it.copy(totalPages = total) }
    }

    fun saveCurrentProgress() {
        val state = _uiState.value
        if (bookId == -1) return

        // Pause the timer and record elapsed time
        readingTimeTracker.stopSession()
        val elapsedMs = readingTimeTracker.accumulatedMs()
        if (elapsedMs > 0) {
            viewModelScope.launch {
                try {
                    readingTimeRepository.recordReadingTime(bookId, elapsedMs)
                } catch (_: Exception) {
                    // Non-critical
                }
            }
        }

        saveProgress(
            bookId,
            state.progress.chapterIndex,
            state.progress.characterOffset,
            state.progress.totalCharacters,
            state.progress.pageNumber,
            state.progress.lastReadPosition
        )

        // Resume timer for next session segment
        readingTimeTracker.reset()
        readingTimeTracker.startSession()
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
        lastReadPosition: String?,
        sessionTimeDeltaMs: Long = 0L
    ) {
        viewModelScope.launch {
            try {
                bookRepository.saveProgress(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    characterOffset = characterOffset,
                    totalCharacters = totalCharacters,
                    pageNumber = pageNumber,
                    lastReadPosition = lastReadPosition,
                    sessionTimeDeltaMs = sessionTimeDeltaMs
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save progress: " + e.message) }
            }
        }
    }
}
