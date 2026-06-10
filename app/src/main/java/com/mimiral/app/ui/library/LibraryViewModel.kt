package com.mimiral.app.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.local.scanner.ScanState
import com.mimiral.app.data.local.settings.FilterOption
import com.mimiral.app.data.local.settings.LibrarySettingsRepository
import com.mimiral.app.data.local.settings.SortOption
import com.mimiral.app.data.local.settings.ViewMode
import com.mimiral.app.data.repository.BookRepository
import com.mimiral.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookWithProgress(
    val book: BookEntity,
    val progress: ReadingProgressEntity?
) {
    val progressPercent: Float get() = progress?.progressPercent ?: 0f
    val currentPage: Int get() = progress?.pageNumber ?: 0
    val totalPages: Int get() = progress?.totalPages ?: 0
    val lastReadTime: Long get() = progress?.lastReadTime ?: 0L
    val isReading: Boolean get() = (progress?.progressPercent ?: 0f) > 0f
    val isFinished: Boolean get() = (progress?.progressPercent ?: 0f) >= 99f
}

data class SeriesGroup(
    val name: String,
    val books: List<BookWithProgress>
)

data class LibraryUiState(
    val books: List<BookWithProgress> = emptyList(),
    val recentBooks: List<BookWithProgress> = emptyList(),
    val seriesGroups: List<SeriesGroup> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val scanState: ScanState = ScanState.Idle,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.RECENT,
    val filterOption: FilterOption = FilterOption.ALL,
    val viewMode: ViewMode = ViewMode.GRID,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val bookRepository: BookRepository,
    private val libraryRepository: LibraryRepository
) : AndroidViewModel(application) {

    private val settingsRepository = LibrarySettingsRepository(application)

    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.RECENT)
    private val _filterOption = MutableStateFlow(FilterOption.ALL)
    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    private val _isRefreshing = MutableStateFlow(false)
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    private val _scanDirectories = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _sortOption.value = settings.sortOption
                _filterOption.value = settings.filterOption
                _viewMode.value = settings.viewMode
                _scanDirectories.clear()
                _scanDirectories.addAll(settings.scanDirectories)
            }
        }
        viewModelScope.launch {
            libraryRepository.scanState.collect { state ->
                _scanState.value = state
                _isRefreshing.value = state is ScanState.Scanning
            }
        }
        loadRecentBooks()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = combine(
        _searchQuery,
        _sortOption,
        _filterOption
    ) { query, sort, filter ->
        Triple(query, sort, filter)
    }.flatMapLatest { (query, sort, filter) ->
        combine(
            bookRepository.getBooks(sort, filter, query),
            bookRepository.getAllProgress()
        ) { books, progressList ->
            val progressMap = progressList.associateBy { it.bookId }
            books.map { book ->
                BookWithProgress(
                    book = book,
                    progress = progressMap[book.id]
                )
            }
        }
    }.combine(_sortOption) { books, sort ->
        val sorted = when (sort) {
            SortOption.PROGRESS -> books.sortedByDescending { it.progressPercent }
            SortOption.RATING -> books
            else -> books
        }
        val groups = if (sort == SortOption.SERIES) {
            val grouped = sorted.groupBy { it.book.seriesName ?: it.book.title }
            grouped.map { (name, booksInGroup) ->
                SeriesGroup(name = name, books = booksInGroup)
            }.sortedBy { it.name.lowercase() }
        } else {
            emptyList()
        }
        Triple(sorted, sort, groups)
    }.combine(_filterOption) { (books, sort, groups), filter ->
        Triple(books, sort, groups)
    }.combine(_viewMode) { (books, sort, groups), viewMode ->
        LibraryUiState(
            books = books,
            recentBooks = emptyList(),
            seriesGroups = groups,
            isLoading = false,
            isRefreshing = _isRefreshing.value,
            scanState = _scanState.value,
            searchQuery = _searchQuery.value,
            sortOption = _sortOption.value,
            filterOption = _filterOption.value,
            viewMode = viewMode
        )
    }.combine(_isRefreshing) { state, refreshing ->
        state.copy(isRefreshing = refreshing)
    }.combine(_scanState) { state, scanState ->
        state.copy(scanState = scanState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    private fun loadRecentBooks() {
        viewModelScope.launch {
            try {
                // Combine recent progress with all books to avoid N+1 queries.
                // flowOn(IO) ensures the collection and mapping happen off the main thread,
                // preventing ANR during drawer animation.
                combine(
                    bookRepository.getRecentProgress(),
                    bookRepository.getAllBooks()
                ) { recentProgress, allBooks ->
                    val bookMap = allBooks.associateBy { it.id }
                    recentProgress.mapNotNull { progress ->
                        bookMap[progress.bookId]?.let { book ->
                            BookWithProgress(book = book, progress = progress)
                        }
                    }
                }.flowOn(Dispatchers.IO)
                    .collect { recent ->
                        _recentBooks.value = recent
                    }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    private val _recentBooks = MutableStateFlow<List<BookWithProgress>>(emptyList())
    val recentBooks: StateFlow<List<BookWithProgress>> = _recentBooks.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        viewModelScope.launch { settingsRepository.setSortOption(option) }
    }

    fun setFilterOption(option: FilterOption) {
        _filterOption.value = option
        viewModelScope.launch { settingsRepository.setFilterOption(option) }
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        viewModelScope.launch { settingsRepository.setViewMode(mode) }
    }

    fun toggleViewMode() {
        val newMode = if (_viewMode.value == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
        setViewMode(newMode)
    }

    fun refreshLibrary() {
        // Build additional roots from user-configured scan directories
        val additionalRoots = _scanDirectories.mapNotNull { path ->
            try {
                val file = java.io.File(path)
                if (file.exists() && file.isDirectory) file else null
            } catch (_: Exception) {
                null
            }
        }
        libraryRepository.refreshLibrary(additionalRoots)
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            try {
                bookRepository.deleteBook(book)
            } catch (_: Exception) {
                // Error handling could be added
            }
        }
    }
}
