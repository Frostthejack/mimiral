package com.mimiral.app.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.local.settings.FilterOption
import com.mimiral.app.data.local.settings.LibrarySettingsRepository
import com.mimiral.app.data.local.settings.SortOption
import com.mimiral.app.data.local.settings.ViewMode
import com.mimiral.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

data class LibraryUiState(
    val books: List<BookWithProgress> = emptyList(),
    val recentBooks: List<BookWithProgress> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.RECENT,
    val filterOption: FilterOption = FilterOption.ALL,
    val viewMode: ViewMode = ViewMode.GRID,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val bookRepository: BookRepository
) : AndroidViewModel(application) {

    private val settingsRepository = LibrarySettingsRepository(application)

    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.RECENT)
    private val _filterOption = MutableStateFlow(FilterOption.ALL)
    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    private val _isRefreshing = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _sortOption.value = settings.sortOption
                _filterOption.value = settings.filterOption
                _viewMode.value = settings.viewMode
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
        Pair(sorted, sort)
    }.combine(_filterOption) { (books, sort), filter ->
        Pair(books, filter)
    }.combine(_viewMode) { (books, filter), viewMode ->
        LibraryUiState(
            books = books,
            recentBooks = emptyList(),
            isLoading = false,
            isRefreshing = _isRefreshing.value,
            searchQuery = _searchQuery.value,
            sortOption = _sortOption.value,
            filterOption = filter,
            viewMode = viewMode
        )
    }.combine(_isRefreshing) { state, refreshing ->
        state.copy(isRefreshing = refreshing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    private fun loadRecentBooks() {
        viewModelScope.launch {
            try {
                bookRepository.getRecentProgress().collect { recentProgress ->
                    val recent = recentProgress.mapNotNull { progress ->
                        val book = bookRepository.getBookById(progress.bookId)
                        book?.let { BookWithProgress(book = it, progress = progress) }
                    }
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
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Trigger a re-scan by re-collecting the flow
                // The Flow from Room will automatically emit new data
                // when the underlying data changes
                bookRepository.getBookCount() // Force DB wake
            } catch (_: Exception) {
                // Non-critical
            } finally {
                _isRefreshing.value = false
            }
        }
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
