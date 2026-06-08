package com.mimiral.app.ui.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.local.entity.ReadingTimeTracker
import com.mimiral.app.data.reader.ChapterExtractor
import com.mimiral.app.data.reader.ChapterExtractionResult
import com.mimiral.app.data.reader.EpubParser
import com.mimiral.app.data.reader.EpubState
import com.mimiral.app.data.reader.TxtParseResult
import com.mimiral.app.data.reader.TxtParser
import com.mimiral.app.data.repository.BookRepository
import com.mimiral.app.data.repository.ReadingTimeRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A single paragraph within a chapter, suitable for LazyColumn items.
 *
 * @param index Zero-based paragraph index within the chapter.
 * @param text The paragraph text content.
 * @param charOffset Character offset from the start of the chapter.
 */
data class ReadingParagraph(
    val index: Int,
    val text: String,
    val charOffset: Int
)

/**
 * A chapter with its paragraphs extracted for reflowable display.
 *
 * @param index Zero-based chapter index.
 * @param title Chapter title from TOC or derived.
 * @param paragraphs Paragraphs within this chapter.
 * @param totalCharacters Total character count in this chapter.
 */
data class ReadingChapter(
    val index: Int,
    val title: String,
    val paragraphs: List<ReadingParagraph>,
    val totalCharacters: Int
)

/**
 * A single page of paginated text, computed by PaginationEngine.
 *
 * @param pageIndex Zero-based page index across all chapters.
 * @param chapterIndex The chapter this page belongs to.
 * @param text The text content for this page.
 * @param startCharOffset Character offset from the start of the chapter.
 */
data class ReadingPage(
    val pageIndex: Int,
    val chapterIndex: Int,
    val text: String,
    val startCharOffset: Int
)

/**
 * UI state for the Reading Mode screen.
 */
data class ReadingModeUiState(
    val bookId: Int = -1,
    val bookTitle: String = "",
    val filePath: String = "",
    val format: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    // Chapters & TOC
    val chapters: List<ReadingChapter> = emptyList(),
    val chapterTitles: List<String> = emptyList(),
    val currentChapterIndex: Int = 0,
    // Reading content (paragraphs kept for bookmark/progress compat)
    val paragraphs: List<ReadingParagraph> = emptyList(),
    // Paginated pages (filled after PaginationEngine runs)
    val pages: List<ReadingPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val totalPages: Int = 0,
    // Progress
    val progressPercent: Float = 0f,
    val totalCharacters: Long = 0,
    val readCharacters: Long = 0,
    // Scroll position (paragraph index — kept for bookmark compat)
    val scrollToParagraphIndex: Int? = null,
    // Bookmarks
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val isCurrentPositionBookmarked: Boolean = false,
    val showBookmarkList: Boolean = false,
    // TOC dialog
    val showToc: Boolean = false,
    // Text settings panel
    val showTextSettings: Boolean = false,
    // TTS state
    val ttsState: com.mimiral.app.tts.TTSState = com.mimiral.app.tts.TTSState.IDLE,
    /** Currently highlighted sentence during TTS playback (character offsets within full text). */
    val currentTtsSentence: com.mimiral.app.data.reader.Sentence? = null,
    /** Start offset of the currently spoken word (-1 = none). */
    val ttsWordStart: Int = -1,
    /** End offset of the currently spoken word (-1 = none). */
    val ttsWordEnd: Int = -1,
    /** Sync status indicator for Kavita progress sync */
    val syncStatus: com.mimiral.app.data.remote.SyncStatus =
        com.mimiral.app.data.remote.SyncStatus.IDLE
)

@HiltViewModel
class ReadingModeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bookRepository: BookRepository,
    private val readingTimeRepository: ReadingTimeRepository,
    private val kavitaSyncRepository: com.mimiral.app.data.remote.KavitaSyncRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: Int = savedStateHandle.get<Int>("bookId") ?: -1

    private val _uiState = MutableStateFlow(ReadingModeUiState(bookId = bookId))
    val uiState: StateFlow<ReadingModeUiState> = _uiState.asStateFlow()

    // Session tracking
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var sessionPagesTurned: Int = 0

    // Reading time tracker
    private val readingTimeTracker = ReadingTimeTracker()

    // Current scroll position for progress/bookmark tracking
    private var currentParagraphIndex: Int = 0
    private var currentCharOffsetInChapter: Int = 0

    // Accumulated characters in previous chapters for progress calculation
    private var charsInPreviousChapters: Long = 0

    init {
        loadBook()
        readingTimeTracker.startSession()
    }

    override fun onCleared() {
        super.onCleared()
        saveProgress()
        if (bookId != -1 && sessionPagesTurned > 0) {
            viewModelScope.launch {
                try {
                    bookRepository.recordReadingSession(
                        bookId = bookId,
                        startTime = sessionStartTime,
                        endTime = System.currentTimeMillis(),
                        pagesRead = sessionPagesTurned
                    )
                } catch (_: Exception) {
                    // Non-critical
                }
            }
        }
        readingTimeTracker.stopSession()
    }

    // ---- Public API ----

    /**
     * Called when the HorizontalPager page changes.
     * Updates current chapter, progress, and bookmark state.
     */
    fun onPageChanged(pageIndex: Int) {
        val pages = _uiState.value.pages
        if (pageIndex !in pages.indices) return

        val page = pages[pageIndex]
        _uiState.update {
            it.copy(
                currentPageIndex = pageIndex,
                currentChapterIndex = page.chapterIndex
            )
        }
        currentParagraphIndex = pageIndex
        currentCharOffsetInChapter = page.startCharOffset
        charsInPreviousChapters = calculateCharsInPreviousChapters(page.chapterIndex)
        updateProgress()
        updateBookmarkState()
        sessionPagesTurned++
    }

    /**
     * Set the paginated pages (called from Screen after PaginationEngine computes them).
     */
    fun setPages(pages: List<ReadingPage>) {
        _uiState.update {
            it.copy(
                pages = pages,
                totalPages = pages.size
            )
        }
    }

    /**
     * Navigate to a chapter by index. Returns the page index to scroll to.
     */
    fun navigateToChapter(chapterIndex: Int): Int {
        val chapters = _uiState.value.chapters
        if (chapterIndex !in chapters.indices) return -1

        val pages = _uiState.value.pages
        val targetPage = pages.indexOfFirst { it.chapterIndex == chapterIndex }
        if (targetPage >= 0) {
            _uiState.update {
                it.copy(
                    currentChapterIndex = chapterIndex,
                    currentPageIndex = targetPage
                )
            }
            currentParagraphIndex = targetPage
            currentCharOffsetInChapter = 0
            charsInPreviousChapters = calculateCharsInPreviousChapters(chapterIndex)
            updateProgress()
            updateBookmarkState()
            sessionPagesTurned++
            return targetPage
        }

        // Fallback: no pages computed yet, just update chapter
        val chapter = chapters[chapterIndex]
        _uiState.update {
            it.copy(
                currentChapterIndex = chapterIndex,
                paragraphs = chapter.paragraphs
            )
        }
        currentParagraphIndex = 0
        currentCharOffsetInChapter = 0
        charsInPreviousChapters = calculateCharsInPreviousChapters(chapterIndex)
        updateProgress()
        updateBookmarkState()
        sessionPagesTurned++
        return -1
    }

    /**
     * Track scroll position changes from the LazyColumn.
     */
    fun onParagraphVisible(paragraphIndex: Int) {
        if (paragraphIndex == currentParagraphIndex) return
        currentParagraphIndex = paragraphIndex
        val paragraphs = _uiState.value.paragraphs
        if (paragraphIndex in paragraphs.indices) {
            currentCharOffsetInChapter = paragraphs[paragraphIndex].charOffset
        }
        updateProgress()
    }

    /**
     * Toggle the TOC dialog visibility.
     */
    fun toggleToc() {
        _uiState.update { it.copy(showToc = !it.showToc) }
    }

    /**
     * Toggle the bookmark list dialog.
     */
    fun toggleBookmarkList() {
        _uiState.update { it.copy(showBookmarkList = !it.showBookmarkList) }
    }

    /**
     * Toggle text settings panel.
     */
    fun toggleTextSettings() {
        _uiState.update { it.copy(showTextSettings = !it.showTextSettings) }
    }

    /**
     * Toggle bookmark at the current reading position.
     */
    fun toggleBookmark() {
        val isBookmarked = _uiState.value.isCurrentPositionBookmarked
        viewModelScope.launch {
            try {
                if (isBookmarked) {
                    val existing = bookRepository.getBookmarkAtPosition(
                        bookId = bookId,
                        chapterIndex = _uiState.value.currentChapterIndex,
                        pageNumber = currentParagraphIndex,
                        position = "paragraph:$currentParagraphIndex"
                    )
                    if (existing != null) {
                        bookRepository.deleteBookmark(existing)
                    }
                } else {
                    val chapterTitle = _uiState.value.chapterTitles.getOrNull(
                        _uiState.value.currentChapterIndex
                    )
                    bookRepository.addBookmark(
                        bookId = bookId,
                        chapterIndex = _uiState.value.currentChapterIndex,
                        pageNumber = currentParagraphIndex,
                        position = "paragraph:$currentParagraphIndex",
                        title = chapterTitle,
                        note = null
                    )
                }
                updateBookmarkState()
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    /**
     * Navigate to a bookmark's position. Returns the page index to scroll to.
     */
    fun navigateToBookmark(bookmark: BookmarkEntity): Int {
        val targetPage = navigateToChapter(bookmark.chapterIndex)
        _uiState.update { it.copy(showBookmarkList = false) }
        return targetPage
    }

    /**
     * Clear the scroll-to target after the LazyColumn has scrolled.
     */
    fun clearScrollTarget() {
        _uiState.update { it.copy(scrollToParagraphIndex = null) }
    }

    // ---- Private implementation ----

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

                _uiState.update {
                    it.copy(
                        bookTitle = book.title,
                        filePath = book.filePath,
                        format = book.format
                    )
                }

                // Load text based on format
                val chapters = when (book.format.uppercase()) {
                    "EPUB" -> loadEpubChapters(book.filePath)
                    "PDF" -> loadPdfChapters(book.filePath)
                    "TXT", "RTF" -> loadTxtChapters(book.filePath)
                    else -> loadTxtChapters(book.filePath) // Fallback
                }

                if (chapters.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "No text content could be extracted"
                        )
                    }
                    return@launch
                }

                val chapterTitles = chapters.map { it.title }
                val totalChars = chapters.sumOf { it.totalCharacters.toLong() }

                // Restore saved progress
                val savedProgress = bookRepository.getProgressForBook(bookId)
                val startChapter = savedProgress?.chapterIndex ?: 0
                val startParagraph = savedProgress?.pageNumber ?: 0

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        chapters = chapters,
                        chapterTitles = chapterTitles,
                        currentChapterIndex = startChapter,
                        paragraphs = chapters.getOrNull(startChapter)?.paragraphs
                            ?: chapters.first().paragraphs,
                        totalCharacters = totalChars,
                        scrollToParagraphIndex = startParagraph
                    )
                }

                charsInPreviousChapters = calculateCharsInPreviousChapters(startChapter)
                currentParagraphIndex = startParagraph

                // Load bookmarks
                loadBookmarks()

                // Restore progress percent
                if (savedProgress != null) {
                    _uiState.update {
                        it.copy(progressPercent = savedProgress.progressPercent)
                    }
                }

                updateProgress()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Extract chapters from an EPUB file using the existing EpubParser + ChapterExtractor.
     */
    private suspend fun loadEpubChapters(filePath: String): List<ReadingChapter> =
        withContext(Dispatchers.IO) {
            try {
                val parser = EpubParser(appContext)
                val state = parser.openFile(File(filePath))
                if (state !is EpubState.Loaded) {
                    parser.close()
                    return@withContext emptyList<ReadingChapter>()
                }

                val epubChapters = parser.getChapters()
                if (epubChapters.isEmpty()) {
                    parser.close()
                    return@withContext emptyList<ReadingChapter>()
                }

                val extractor = ChapterExtractor(parser)
                val result = mutableListOf<ReadingChapter>()

                for (i in epubChapters.indices) {
                    val chapterResult = extractor.getChapter(i)
                    when (chapterResult) {
                        is ChapterExtractionResult.Success -> {
                            val paragraphs = splitIntoParagraphs(
                                chapterResult.text,
                                chapterResult.chapterIndex
                            )
                            result.add(
                                ReadingChapter(
                                    index = i,
                                    title = chapterResult.chapterTitle,
                                    paragraphs = paragraphs,
                                    totalCharacters = chapterResult.characterCount
                                )
                            )
                        }
                        is ChapterExtractionResult.Error -> {
                            result.add(
                                ReadingChapter(
                                    index = i,
                                    title = epubChapters[i].title,
                                    paragraphs = emptyList(),
                                    totalCharacters = 0
                                )
                            )
                        }
                    }
                }

                extractor.clearPool()
                parser.close()
                result
            } catch (_: Exception) {
                emptyList<ReadingChapter>()
            }
        }

    /**
     * Extract chapters from a PDF file using PDFBox text extraction.
     * Pages are grouped into sections for comfortable reading.
     */
    private suspend fun loadPdfChapters(filePath: String): List<ReadingChapter> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext emptyList<ReadingChapter>()

                val chapters = mutableListOf<ReadingChapter>()
                val document = PDDocument.load(file)

                try {
                    val totalPages = document.numberOfPages
                    if (totalPages == 0) return@withContext emptyList<ReadingChapter>()

                    // Group pages into sections (every N pages)
                    val pagesPerChapter = (totalPages / maxOf(1, totalPages / 10))
                        .coerceIn(1, 20)
                    var chapterIndex = 0
                    var pageStart = 0

                    while (pageStart < totalPages) {
                        val pageEnd = minOf(pageStart + pagesPerChapter, totalPages)
                        val stripper = PDFTextStripper()
                        stripper.startPage = pageStart + 1 // 1-indexed
                        stripper.endPage = pageEnd
                        val text = stripper.getText(document).trim()

                        if (text.isNotEmpty()) {
                            val paragraphs = splitIntoParagraphs(text, chapterIndex)
                            chapters.add(
                                ReadingChapter(
                                    index = chapterIndex,
                                    title = if (totalPages <= 20) "Page ${pageStart + 1}"
                                    else "Section ${chapterIndex + 1}",
                                    paragraphs = paragraphs,
                                    totalCharacters = text.length
                                )
                            )
                            chapterIndex++
                        }
                        pageStart = pageEnd
                    }
                } finally {
                    document.close()
                }

                chapters
            } catch (_: Exception) {
                emptyList<ReadingChapter>()
            }
        }

    /**
     * Extract chapters from a TXT/RTF file using the existing TxtParser.
     */
    private suspend fun loadTxtChapters(filePath: String): List<ReadingChapter> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext emptyList<ReadingChapter>()

                val parser = TxtParser()
                val result = parser.parse(file)

                when (result) {
                    is TxtParseResult.Success -> {
                        val text = result.text
                        if (text.isBlank()) return@withContext emptyList<ReadingChapter>()

                        val breaks = result.chapterBreaks
                        if (breaks.isEmpty() || breaks.size <= 1) {
                            val paragraphs = splitIntoParagraphs(text, 0)
                            listOf(
                                ReadingChapter(
                                    index = 0,
                                    title = result.title.ifBlank { "Chapter 1" },
                                    paragraphs = paragraphs,
                                    totalCharacters = text.length
                                )
                            )
                        } else {
                            val chapters = mutableListOf<ReadingChapter>()
                            for (i in breaks.indices) {
                                val start = breaks[i]
                                val end = if (i + 1 < breaks.size) breaks[i + 1]
                                else text.length
                                val chapterText = text.substring(start, end).trim()
                                if (chapterText.isNotEmpty()) {
                                    val paragraphs = splitIntoParagraphs(chapterText, i)
                                    val firstLine = chapterText.lines()
                                        .firstOrNull { it.isNotBlank() }
                                        ?.trim()
                                        ?.take(80)
                                        ?: "Chapter ${i + 1}"
                                    chapters.add(
                                        ReadingChapter(
                                            index = i,
                                            title = firstLine,
                                            paragraphs = paragraphs,
                                            totalCharacters = chapterText.length
                                        )
                                    )
                                }
                            }
                            chapters
                        }
                    }
                    is TxtParseResult.Error -> emptyList<ReadingChapter>()
                }
            } catch (_: Exception) {
                emptyList<ReadingChapter>()
            }
        }

    /**
     * Split text into paragraphs. A paragraph is delimited by double newlines
     * or single newlines after sentences (heuristic).
     */
    private fun splitIntoParagraphs(text: String, chapterIndex: Int): List<ReadingParagraph> {
        if (text.isBlank()) return emptyList()

        val paragraphs = mutableListOf<ReadingParagraph>()
        var charOffset = 0
        var paragraphIndex = 0

        // Split on double newlines (blank line between text blocks)
        val blocks = text.split(Regex("\\n\\s*\\n"))

        for (block in blocks) {
            val trimmed = block.trim()
            if (trimmed.isEmpty()) {
                charOffset += block.length + 2
                continue
            }

            // Further split very long blocks at single newlines
            if (trimmed.length > 2000) {
                val subBlocks = trimmed.split("\n")
                var currentSubBlock = StringBuilder()
                for (line in subBlocks) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.isEmpty() && currentSubBlock.isNotEmpty()) {
                        val paragraphText = currentSubBlock.toString().trim()
                        if (paragraphText.isNotEmpty()) {
                            paragraphs.add(
                                ReadingParagraph(
                                    index = paragraphIndex++,
                                    text = paragraphText,
                                    charOffset = charOffset
                                )
                            )
                            charOffset += paragraphText.length + 1
                        }
                        currentSubBlock = StringBuilder()
                    } else {
                        if (currentSubBlock.isNotEmpty()) currentSubBlock.append(' ')
                        currentSubBlock.append(trimmedLine)
                    }
                }
                if (currentSubBlock.isNotEmpty()) {
                    val paragraphText = currentSubBlock.toString().trim()
                    if (paragraphText.isNotEmpty()) {
                        paragraphs.add(
                            ReadingParagraph(
                                index = paragraphIndex++,
                                text = paragraphText,
                                charOffset = charOffset
                            )
                        )
                        charOffset += paragraphText.length + 1
                    }
                }
            } else {
                val normalized = trimmed.replace(Regex("\\n+"), " ")
                paragraphs.add(
                    ReadingParagraph(
                        index = paragraphIndex++,
                        text = normalized,
                        charOffset = charOffset
                    )
                )
                charOffset += trimmed.length + 2
            }
        }

        return paragraphs
    }

    private fun calculateCharsInPreviousChapters(chapterIndex: Int): Long {
        val chapters = _uiState.value.chapters
        var total = 0L
        for (i in 0 until chapterIndex) {
            if (i < chapters.size) {
                total += chapters[i].totalCharacters
            }
        }
        return total
    }

    private fun updateProgress() {
        val paragraphs = _uiState.value.paragraphs
        val totalCharacters = _uiState.value.totalCharacters
        if (totalCharacters == 0L) return

        var charsInCurrentChapter = 0
        if (currentParagraphIndex in paragraphs.indices) {
            val paragraph = paragraphs[currentParagraphIndex]
            charsInCurrentChapter = paragraph.charOffset + paragraph.text.length
        } else if (paragraphs.isNotEmpty()) {
            charsInCurrentChapter = paragraphs.last().charOffset + paragraphs.last().text.length
        }

        val readChars = charsInPreviousChapters + charsInCurrentChapter
        val percent = (readChars.toFloat() / totalCharacters.toFloat())
            .coerceIn(0f, 1f) * 100f

        _uiState.update {
            it.copy(
                readCharacters = readChars,
                progressPercent = percent
            )
        }
    }

    private fun saveProgress() {
        if (bookId == -1) return
        viewModelScope.launch {
            try {
                val state = _uiState.value
                bookRepository.saveProgress(
                    bookId = bookId,
                    chapterIndex = state.currentChapterIndex,
                    characterOffset = state.readCharacters,
                    totalCharacters = state.totalCharacters,
                    pageNumber = state.currentPageIndex,
                    totalPages = state.totalPages,
                    lastReadPosition = "page:${state.currentPageIndex}"
                )
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    private fun loadBookmarks() {
        if (bookId == -1) return
        viewModelScope.launch {
            try {
                bookRepository.getBookmarksForBook(bookId).collect { bookmarks ->
                    _uiState.update { it.copy(bookmarks = bookmarks) }
                    updateBookmarkState()
                }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    private fun updateBookmarkState() {
        val bookmarks = _uiState.value.bookmarks
        val chapterIdx = _uiState.value.currentChapterIndex
        val isBookmarked = bookmarks.any {
            it.chapterIndex == chapterIdx && it.pageNumber == currentParagraphIndex
        }
        _uiState.update { it.copy(isCurrentPositionBookmarked = isBookmarked) }
    }

    // ---- TTS handlers ----

    /**
     * Called when the TTS engine state changes (PLAYING, PAUSED, READY, IDLE, etc.).
     */
    fun onTtsStateChanged(stateName: String) {
        val newState = try {
            com.mimiral.app.tts.TTSState.valueOf(stateName)
        } catch (_: IllegalArgumentException) {
            com.mimiral.app.tts.TTSState.IDLE
        }
        _uiState.update { it.copy(ttsState = newState) }
    }

    /**
     * Called when the TTS sentence changes during playback.
     */
    fun onTtsSentenceChanged(sentence: com.mimiral.app.data.reader.Sentence?) {
        _uiState.update { it.copy(currentTtsSentence = sentence) }
    }

    /**
     * Called when the TTS word boundary changes during playback.
     */
    fun onTtsWordChanged(start: Int, end: Int) {
        _uiState.update { it.copy(ttsWordStart = start, ttsWordEnd = end) }
    }

    /**
     * Called when the TTS word highlight should be cleared.
     */
    fun onTtsWordCleared() {
        _uiState.update { it.copy(ttsWordStart = -1, ttsWordEnd = -1) }
    }

    /**
     * Get the full text of the current chapter for TTS.
     */
    fun getFullText(): String {
        return _uiState.value.paragraphs.joinToString("\n\n") { it.text }
    }

    companion object {
        private const val TAG = "ReadingModeViewModel"
    }
}
