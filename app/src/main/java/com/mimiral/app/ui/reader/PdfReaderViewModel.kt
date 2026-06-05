package com.mimiral.app.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.reader.MarginCrop
import com.mimiral.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PdfReaderUiState(
    val bookId: Int = 0,
    val bookTitle: String = "",
    val filePath: String = "",
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
    // UI controls visibility
    val isControlsVisible: Boolean = true,
    // Bookmarks
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val showBookmarkList: Boolean = false,
    // Text selection
    val selectedText: String = "",
    val isTextSelected: Boolean = false,
    val hasTextContent: Boolean = false,
    val showSelectionMenu: Boolean = false,
    // PDF crop settings
    val cropMargins: MarginCrop = MarginCrop.NONE,
    val showCropSettings: Boolean = false,
    val autoDetecting: Boolean = false,
    val suggestedCrop: MarginCrop? = null,
    /** Sync status indicator for Kavita progress sync */
    val syncStatus: com.mimiral.app.data.remote.SyncStatus =
        com.mimiral.app.data.remote.SyncStatus.IDLE
)

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val kavitaSyncRepository: com.mimiral.app.data.remote.KavitaSyncRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()

    private val bookId: Int = savedStateHandle.get<Int>("bookId") ?: 0

    // Session tracking
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var sessionStartPage: Int = 0
    private var sessionPagesTurned: Int = 0

    init {
        loadBook()
    }

    override fun onCleared() {
        super.onCleared()
        // Record reading session when ViewModel is cleared (user leaves reader)
        if (bookId != 0 && sessionPagesTurned > 0) {
            viewModelScope.launch {
                try {
                    bookRepository.recordReadingSession(
                        bookId = bookId,
                        startTime = sessionStartTime,
                        endTime = System.currentTimeMillis(),
                        pagesRead = sessionPagesTurned
                    )
                } catch (_: Exception) {
                    // Non-critical: session recording failure should not crash
                }
            }
        }
    }

    private fun loadBook() {
        viewModelScope.launch {
            try {
                val book = bookRepository.getBookById(bookId)
                if (book != null) {
                    val savedPage = bookRepository.getSavedPage(bookId)
                    val pdfSettings = bookRepository.getPdfSettings(bookId)
                    val cropMargins = pdfSettings?.let {
                        MarginCrop(
                            left = it.cropLeft,
                            top = it.cropTop,
                            right = it.cropRight,
                            bottom = it.cropBottom
                        )
                    } ?: MarginCrop.NONE

                    _uiState.value = _uiState.value.copy(
                        bookId = book.id,
                        bookTitle = book.title,
                        filePath = book.filePath,
                        currentPage = savedPage,
                        cropMargins = cropMargins
                    )
                    sessionStartPage = savedPage
                    loadBookmarks()
                    autoSyncOnOpen()
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

    /**
     * Initialize the reader with explicit parameters.
     */
    fun initialize(bId: Int, fPath: String) {
        _uiState.update {
            it.copy(bookId = bId, filePath = fPath)
        }
    }

    /**
     * Called when the PDF document is loaded and total page count is known.
     */
    fun onDocumentLoaded(totalPages: Int) {
        _uiState.update { it.copy(totalPages = totalPages, isLoading = false) }
        updateProgress(_uiState.value.currentPage, totalPages)
    }

    /**
     * Set total pages (called from composable when PdfRenderer reports page count).
     */
    fun setTotalPages(total: Int) {
        _uiState.update { it.copy(totalPages = total, isLoading = false) }
        updateProgress(_uiState.value.currentPage, total)
    }

    /**
     * Called on each page turn.
     */
    fun onPageChanged(newPage: Int) {
        val total = _uiState.value.totalPages
        val clamped = newPage.coerceIn(0, (total - 1).coerceAtLeast(0))

        // Track pages turned for session stats
        val pagesDelta = kotlin.math.abs(clamped - sessionStartPage)
        if (pagesDelta > sessionPagesTurned) {
            sessionPagesTurned = pagesDelta
        }

        _uiState.update {
            it.copy(
                currentPage = clamped,
                progressPercent = calculateProgress(clamped, total)
            )
        }
        saveProgress(clamped, total)
        pushProgressToKavita(clamped)
        clearSelection()
    }

    /**
     * Jump to a specific page (e.g., from a seek bar or bookmark navigation).
     */
    fun goToPage(page: Int) {
        val total = _uiState.value.totalPages
        val clampedPage = page.coerceIn(0, (total - 1).coerceAtLeast(0))
        onPageChanged(clampedPage)
    }

    /**
     * Navigate to the next page.
     */
    fun nextPage() {
        val current = _uiState.value.currentPage
        val total = _uiState.value.totalPages
        if (current < total - 1) {
            onPageChanged(current + 1)
        }
    }

    /**
     * Navigate to the previous page.
     */
    fun previousPage() {
        val current = _uiState.value.currentPage
        if (current > 0) {
            onPageChanged(current - 1)
        }
    }

    /**
     * Restore the saved page position when reopening a PDF.
     */
    fun getSavedPage(): Int = _uiState.value.currentPage

    // --- Zoom & Pan ---

    fun setZoomLevel(level: Float) {
        val clamped = level.coerceIn(0.5f, 5.0f)
        _uiState.update {
            it.copy(
                zoomLevel = clamped,
                isFitWidth = clamped <= 1.05f
            )
        }
    }

    fun setScrollOffset(x: Float, y: Float) {
        _uiState.update { it.copy(scrollOffsetX = x, scrollOffsetY = y) }
    }

    fun toggleFitWidth() {
        val currentlyFit = _uiState.value.isFitWidth
        if (currentlyFit) {
            _uiState.update { it.copy(zoomLevel = 2.0f, isFitWidth = false) }
        } else {
            _uiState.update {
                it.copy(
                    zoomLevel = 1.0f,
                    isFitWidth = true,
                    scrollOffsetX = 0f,
                    scrollOffsetY = 0f
                )
            }
        }
    }

    // --- Controls visibility ---

    fun toggleControls() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
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

    // --- Text selection ---

    /**
     * Set the selected text from a text selection gesture.
     */
    fun setTextSelection(text: String) {
        _uiState.update {
            it.copy(
                selectedText = text,
                isTextSelected = text.isNotBlank(),
                showSelectionMenu = text.isNotBlank()
            )
        }
    }

    /**
     * Clear the current text selection.
     */
    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedText = "",
                isTextSelected = false,
                showSelectionMenu = false
            )
        }
    }

    /**
     * Set whether the current page has text content (for enabling/disabling selection).
     */
    fun setHasTextContent(hasText: Boolean) {
        _uiState.update { it.copy(hasTextContent = hasText) }
    }

    /**
     * Dismiss the selection context menu.
     */
    fun dismissSelectionMenu() {
        _uiState.update { it.copy(showSelectionMenu = false) }
    }

    // --- PDF Crop Settings ---

    /**
     * Set crop margins and persist to storage.
     */
    fun setCropMargins(crop: MarginCrop) {
        _uiState.update { it.copy(cropMargins = crop) }
        persistCropSettings(crop)
    }

    /**
     * Set uniform crop (same value for all sides).
     */
    fun setUniformCrop(percent: Int) {
        val crop = MarginCrop.uniform(percent)
        setCropMargins(crop)
    }

    /**
     * Reset crop to no cropping.
     */
    fun resetCrop() {
        setCropMargins(MarginCrop.NONE)
    }

    /**
     * Show the crop settings panel.
     */
    fun showCropSettings() {
        _uiState.update { it.copy(showCropSettings = true) }
    }

    /**
     * Hide the crop settings panel.
     */
    fun hideCropSettings() {
        _uiState.update { it.copy(showCropSettings = false) }
    }

    /**
     * Toggle the crop settings panel.
     */
    fun toggleCropSettings() {
        _uiState.update { it.copy(showCropSettings = !it.showCropSettings) }
    }

    /**
     * Set the suggested crop (from auto-detect).
     */
    fun setSuggestedCrop(crop: MarginCrop) {
        _uiState.update { it.copy(suggestedCrop = crop) }
    }

    /**
     * Apply the suggested crop to the current settings.
     */
    fun applySuggestedCrop() {
        val suggested = _uiState.value.suggestedCrop ?: return
        setCropMargins(suggested)
        _uiState.update { it.copy(suggestedCrop = null) }
    }

    /**
     * Set the auto-detecting state.
     */
    fun setAutoDetecting(detecting: Boolean) {
        _uiState.update { it.copy(autoDetecting = detecting) }
    }

    // --- Private helpers ---

    private fun persistCropSettings(crop: MarginCrop) {
        viewModelScope.launch {
            try {
                bookRepository.savePdfSettings(
                    com.mimiral.app.data.local.entity.PdfSettingsEntity(
                        bookId = bookId,
                        cropLeft = crop.left,
                        cropTop = crop.top,
                        cropRight = crop.right,
                        cropBottom = crop.bottom,
                        autoDetected = false
                    )
                )
            } catch (_: Exception) {
                // Silently fail -- crop persistence should not disrupt reading
            }
        }
    }

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
            } catch (_: Exception) {
                // Silently fail -- progress save should not disrupt reading
            }
        }
    }

    // ---- Kavita Sync methods ----

    private fun autoSyncOnOpen() {
        if (bookId == 0) return
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(syncStatus = com.mimiral.app.data.remote.SyncStatus.SYNCING)
                }
                when (val result = kavitaSyncRepository.pullProgress(bookId)) {
                    is com.mimiral.app.data.remote.SyncResult.Success -> {
                        _uiState.update {
                            it.copy(
                                syncStatus =
                                com.mimiral.app.data.remote.SyncStatus.SYNCED
                            )
                        }
                    }
                    is com.mimiral.app.data.remote.SyncResult.NoKavitaBook,
                    is com.mimiral.app.data.remote.SyncResult.NoServer -> {
                        _uiState.update {
                            it.copy(
                                syncStatus =
                                com.mimiral.app.data.remote.SyncStatus.IDLE
                            )
                        }
                    }
                    is com.mimiral.app.data.remote.SyncResult.Error -> {
                        _uiState.update {
                            it.copy(
                                syncStatus =
                                com.mimiral.app.data.remote.SyncStatus.ERROR
                            )
                        }
                    }
                    is com.mimiral.app.data.remote.SyncResult.Conflict -> {
                        _uiState.update {
                            it.copy(
                                syncStatus =
                                com.mimiral.app.data.remote.SyncStatus.SYNCED
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        syncStatus = com.mimiral.app.data.remote.SyncStatus.ERROR
                    )
                }
            }
        }
    }

    private fun pushProgressToKavita(pageNumber: Int) {
        if (bookId == 0) return
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(syncStatus = com.mimiral.app.data.remote.SyncStatus.SYNCING)
                }
                when (
                    val result = kavitaSyncRepository.pushProgress(
                        bookId = bookId,
                        pageNumber = pageNumber,
                        chapterIndex = 0
                    )
                ) {
                    is com.mimiral.app.data.remote.SyncResult.Success -> {
                        _uiState.update {
                            it.copy(
                                syncStatus =
                                com.mimiral.app.data.remote.SyncStatus.SYNCED
                            )
                        }
                    }
                    is com.mimiral.app.data.remote.SyncResult.NoKavitaBook,
                    is com.mimiral.app.data.remote.SyncResult.NoServer -> {
                        _uiState.update {
                            it.copy(
                                syncStatus =
                                com.mimiral.app.data.remote.SyncStatus.IDLE
                            )
                        }
                    }
                    is com.mimiral.app.data.remote.SyncResult.Error -> {
                        _uiState.update {
                            it.copy(
                                syncStatus =
                                com.mimiral.app.data.remote.SyncStatus.ERROR
                            )
                        }
                    }
                    is com.mimiral.app.data.remote.SyncResult.Conflict -> {
                        _uiState.update {
                            it.copy(
                                syncStatus =
                                com.mimiral.app.data.remote.SyncStatus.SYNCED
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        syncStatus = com.mimiral.app.data.remote.SyncStatus.ERROR
                    )
                }
            }
        }
    }

    fun manualSync() {
        if (bookId == 0) return
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(syncStatus = com.mimiral.app.data.remote.SyncStatus.SYNCING)
                }
                when (
                    val result = kavitaSyncRepository.syncProgress(
                        bookId = bookId,
                        localPageNumber = _uiState.value.currentPage,
                        localChapterIndex = 0,
                        localTimestamp = System.currentTimeMillis()
                    )
                ) {
                    is com.mimiral.app.data.remote.SyncResult.Success -> {
                        _uiState.update {
                            it.copy(
                                syncStatus =
                                com.mimiral.app.data.remote.SyncStatus.SYNCED
                            )
                        }
                    }
                    is com.mimiral.app.data.remote.SyncResult.NoKavitaBook,
                    is com.mimiral.app.data.remote.SyncResult.NoServer -> {
                        _uiState.update {
                            it.copy(
                                syncStatus =
                                com.mimiral.app.data.remote.SyncStatus.IDLE
                            )
                        }
                    }
                    is com.mimiral.app.data.remote.SyncResult.Error -> {
                        _uiState.update {
                            it.copy(
                                syncStatus =
                                com.mimiral.app.data.remote.SyncStatus.ERROR
                            )
                        }
                    }
                    is com.mimiral.app.data.remote.SyncResult.Conflict -> {
                        _uiState.update {
                            it.copy(
                                syncStatus =
                                com.mimiral.app.data.remote.SyncStatus.SYNCED
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        syncStatus = com.mimiral.app.data.remote.SyncStatus.ERROR
                    )
                }
            }
        }
    }

    fun autoSyncOnClose() {
        pushProgressToKavita(_uiState.value.currentPage)
    }
}
