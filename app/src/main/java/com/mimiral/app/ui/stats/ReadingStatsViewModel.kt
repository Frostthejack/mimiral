package com.mimiral.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ReadingStatsUiState(
    val pagesToday: Int = 0,
    val pagesThisWeek: Int = 0,
    val pagesThisMonth: Int = 0,
    val totalTimeTodaySeconds: Long = 0,
    val sessionCountToday: Int = 0,
    val dailyPages: List<DailyPageStat> = emptyList(),
    val isLoading: Boolean = true
)

data class DailyPageStat(
    val dateMillis: Long,
    val dateLabel: String,
    val pagesRead: Int
)

@HiltViewModel
class ReadingStatsViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadingStatsUiState())
    val uiState: StateFlow<ReadingStatsUiState> = _uiState.asStateFlow()

    private val dateFormatter = SimpleDateFormat("EEE", Locale.getDefault())

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            try {
                val pagesToday = bookRepository.getPagesReadToday()
                val pagesThisWeek = bookRepository.getPagesReadThisWeek()
                val pagesThisMonth = bookRepository.getPagesReadThisMonth()
                val totalTimeToday = bookRepository.getTotalReadingTimeToday()
                val sessionCountToday = bookRepository.getSessionCountToday()

                // Load last 7 days for the chart
                val dailyData = bookRepository.getDailyPagesForLastDays(7)
                val dailyStats = dailyData.map { daily ->
                    DailyPageStat(
                        dateMillis = daily.startTime,
                        dateLabel = dateFormatter.format(Date(daily.startTime)),
                        pagesRead = daily.totalPages
                    )
                }

                _uiState.update {
                    it.copy(
                        pagesToday = pagesToday,
                        pagesThisWeek = pagesThisWeek,
                        pagesThisMonth = pagesThisMonth,
                        totalTimeTodaySeconds = totalTimeToday,
                        sessionCountToday = sessionCountToday,
                        dailyPages = dailyStats,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
}
