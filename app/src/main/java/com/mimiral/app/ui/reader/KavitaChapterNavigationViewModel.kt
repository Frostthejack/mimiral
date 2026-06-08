package com.mimiral.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.kavita.KavitaContinueReadingRepository
import com.mimiral.app.data.remote.kavita.KavitaNextChapterDto
import com.mimiral.app.data.remote.kavita.KavitaPrevChapterDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for next/prev chapter navigation.
 */
data class ChapterNavigationState(
    /** Next chapter (null if at end of series or not fetched yet) */
    val nextChapter: KavitaNextChapterDto? = null,
    /** Previous chapter (null if at start of series or not fetched yet) */
    val prevChapter: KavitaPrevChapterDto? = null,
    /** Whether we're currently fetching next/prev chapter info */
    val isLoading: Boolean = false,
    /** Current series context for chapter navigation queries */
    val seriesId: Int = 0,
    /** Current volume context */
    val volumeId: Int = 0,
    /** Current chapter context */
    val chapterId: Int = 0
) {
    /** Whether a next chapter is available */
    val hasNextChapter: Boolean get() = nextChapter != null && nextChapter.id > 0
    /** Whether a previous chapter is available */
    val hasPrevChapter: Boolean get() = prevChapter != null && prevChapter.id > 0
}

/**
 * Shared ViewModel for Kavita next/prev chapter navigation.
 *
 * Attach to any reader screen to provide chapter boundary navigation.
 * The reader sets the current chapter context via [setChapterContext],
 * and this VM fetches the next/prev chapter info from the Kavita API.
 */
@HiltViewModel
class KavitaChapterNavigationViewModel @Inject constructor(
    private val continueReadingRepository: KavitaContinueReadingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChapterNavigationState())
    val state: StateFlow<ChapterNavigationState> = _state.asStateFlow()

    /**
     * Set the current chapter context and fetch next/prev chapter info.
     * Call this when a reader opens a chapter or when the user navigates
     * to a new chapter.
     *
     * @param seriesId Current series ID
     * @param volumeId Current volume ID
     * @param chapterId Current chapter ID
     */
    fun setChapterContext(seriesId: Int, volumeId: Int, chapterId: Int) {
        // Skip if already set to the same context
        val current = _state.value
        if (current.seriesId == seriesId &&
            current.volumeId == volumeId &&
            current.chapterId == chapterId
        ) {
            return
        }

        _state.value = ChapterNavigationState(
            seriesId = seriesId,
            volumeId = volumeId,
            chapterId = chapterId,
            isLoading = true
        )

        viewModelScope.launch {
            val next = continueReadingRepository.getNextChapter(seriesId, volumeId, chapterId)
            _state.value = _state.value.copy(nextChapter = next)
        }
        viewModelScope.launch {
            val prev = continueReadingRepository.getPrevChapter(seriesId, volumeId, chapterId)
            _state.value = _state.value.copy(prevChapter = prev, isLoading = false)
        }
    }

    /**
     * Clear the chapter context (e.g. when the reader closes).
     */
    fun clearContext() {
        _state.value = ChapterNavigationState()
    }
}
