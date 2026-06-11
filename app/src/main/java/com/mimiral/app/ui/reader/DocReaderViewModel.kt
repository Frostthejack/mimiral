package com.mimiral.app.ui.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.reader.DocParser
import com.mimiral.app.data.reader.DocState
import com.mimiral.app.data.reader.resolveFileToCache
import com.mimiral.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DocChapterInfo(
    val index: Int,
    val title: String,
    val startPage: Int,
    val endPage: Int,
    val text: String
)

data class DocReaderUiState(
    val bookId: Int = -1,
    val title: String = "",
    val author: String? = null,
    val isLoading: Boolean = true,
    val currentChapter: Int = 0,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val chapters: List<DocChapterInfo> = emptyList(),
    val fullText: String = "",
    val showToc: Boolean = false,
    val error: String? = null,
    val isDocx: Boolean = false
)

@HiltViewModel
class DocReaderViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bookRepository: BookRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: Int = savedStateHandle.get<Int>("bookId") ?: -1

    private val _uiState = MutableStateFlow(DocReaderUiState(bookId = bookId))
    val uiState: StateFlow<DocReaderUiState> = _uiState.asStateFlow()

    private var parser: DocParser? = null

    init {
        loadBook()
    }

    private fun loadBook() {
        if (bookId == -1) {
            _uiState.update {
                it.copy(isLoading = false, error = "Invalid book ID")
            }
            return
        }

        viewModelScope.launch {
            try {
                val book = bookRepository.getBookById(bookId)
                if (book == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Book not found")
                    }
                    return@launch
                }

                val docParser = DocParser()
                parser = docParser

                val result = withContext(Dispatchers.IO) {
                    val file = resolveFileToCache(appContext, book.filePath, "doc")
                        ?: return@withContext DocState.Error("Cannot access file: ${book.filePath}")
                    docParser.openFile(file)
                }

                when (result) {
                    is DocState.Loaded -> {
                        val docChapters = docParser.getChapters()
                        val chapterInfos = docChapters.mapIndexed { idx, mc ->
                            val charCount = mc.text.length
                            val pages = (charCount / 500).coerceAtLeast(1)
                            val startPage = docChapters.take(idx).sumOf {
                                (it.text.length / 500).coerceAtLeast(
                                    1
                                )
                            }
                            DocChapterInfo(
                                index = idx,
                                title = mc.title,
                                startPage = startPage,
                                endPage = startPage + pages - 1,
                                text = mc.text
                            )
                        }
                        val fullText = docParser.getFullText()
                        _uiState.update {
                            it.copy(
                                bookId = bookId,
                                title = result.title,
                                author = result.author,
                                isLoading = false,
                                chapters = chapterInfos,
                                fullText = fullText,
                                isDocx = result.isDocx
                            )
                        }
                    }
                    is DocState.Error -> {
                        _uiState.update {
                            it.copy(isLoading = false, error = result.message)
                        }
                    }
                    else -> {
                        _uiState.update {
                            it.copy(isLoading = false, error = "Unknown parser error")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load book: ${e.message}")
                }
            }
        }
    }

    fun navigateToChapter(chapterIndex: Int) {
        val state = _uiState.value
        val chapter = state.chapters.getOrNull(chapterIndex) ?: return
        _uiState.update {
            it.copy(currentChapter = chapterIndex, showToc = false)
        }
    }

    fun nextChapter() {
        val state = _uiState.value
        val nextIndex = state.currentChapter + 1
        if (nextIndex < state.chapters.size) navigateToChapter(nextIndex)
    }

    fun previousChapter() {
        val prevIndex = _uiState.value.currentChapter - 1
        if (prevIndex >= 0) navigateToChapter(prevIndex)
    }

    fun hasNextChapter(): Boolean {
        val state = _uiState.value
        return state.currentChapter + 1 < state.chapters.size
    }

    fun hasPreviousChapter(): Boolean = _uiState.value.currentChapter > 0

    fun showToc() { _uiState.update { it.copy(showToc = true) } }

    fun dismissToc() { _uiState.update { it.copy(showToc = false) } }

    fun setTotalPages(total: Int) { _uiState.update { it.copy(totalPages = total) } }

    fun onPageTurn(pageNumber: Int) { _uiState.update { it.copy(currentPage = pageNumber) } }
}
