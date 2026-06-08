package com.mimiral.app.ui.nowreading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.remote.kavita.KavitaContinuePointDto
import com.mimiral.app.data.remote.kavita.KavitaContinueReadingRepository
import com.mimiral.app.data.remote.kavita.KavitaOnDeckDto
import com.mimiral.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NowReadingBook(
    val book: BookEntity,
    val progress: ReadingProgressEntity?
) {
    val progressPercent: Float get() = progress?.progressPercent ?: 0f
    val currentPage: Int get() = progress?.pageNumber ?: 0
    val totalPages: Int get() = progress?.totalPages ?: 0
    val lastReadTime: Long get() = progress?.lastReadTime ?: 0L
}

data class NowReadingUiState(
    val books: List<NowReadingBook> = emptyList(),
    val isLoading: Boolean = true,
    /** On Deck series from Kavita (server-side reading status) */
    val onDeckSeries: List<KavitaOnDeckDto> = emptyList(),
    /** Continue-reading point across all series */
    val continuePoint: KavitaContinuePointDto? = null
)

@HiltViewModel
class NowReadingViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val continueReadingRepository: KavitaContinueReadingRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<NowReadingUiState> = combine(
        bookRepository.getAllBooks(),
        bookRepository.getAllProgress()
    ) { books, progressList ->
        val progressMap = progressList.associateBy { it.bookId }
        books.mapNotNull { book ->
            val progress = progressMap[book.id]
            // Include books that are currently being read (1%..98%)
            if (progress != null && progress.progressPercent in 1f..98f) {
                NowReadingBook(book = book, progress = progress)
            } else {
                null
            }
        }.sortedByDescending { it.lastReadTime }
    }.combine(_isLoading) { books, loading ->
        NowReadingUiState(books = books, isLoading = loading)
    }.combine(continueReadingRepository.onDeckSeries) { state, onDeck ->
        state.copy(onDeckSeries = onDeck)
    }.combine(continueReadingRepository.continuePoint) { state, point ->
        state.copy(continuePoint = point)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NowReadingUiState()
    )

    init {
        viewModelScope.launch {
            // Mark loading as false once the first emission arrives
            bookRepository.getAllBooks().collect {
                _isLoading.value = false
            }
        }
        // Fetch On Deck and continue point from Kavita
        viewModelScope.launch {
            continueReadingRepository.fetchOnDeck()
            continueReadingRepository.fetchContinuePoint()
        }
    }

    /**
     * Refresh On Deck shelf and continue point.
     */
    fun refreshKavita() {
        viewModelScope.launch {
            continueReadingRepository.fetchOnDeck()
            continueReadingRepository.fetchContinuePoint()
        }
    }
}
