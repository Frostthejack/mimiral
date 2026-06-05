package com.mimiral.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.ReadingSessionEntity
import com.mimiral.app.data.repository.BookRepository
import com.mimiral.app.data.repository.ReadingStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DailyStat(
    val date: String,
    val totalSeconds: Long,
    val totalPages: Int,
    val sessionCount: Int
)

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val totalBooksRead: Int = 0,
    val totalPagesRead: Int = 0,
    val totalReadingTimeSeconds: Long = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val todayPages: Int = 0,
    val todayMinutes: Long = 0,
    val weekPages: Int = 0,
    val weekMinutes: Long = 0,
    val monthPages: Int = 0,
    val monthMinutes: Long = 0,
    val dailyStats: List<DailyStat> = emptyList(),
    val recentSessions: List<ReadingSessionEntity> = emptyList(),
    val isExporting: Boolean = false,
    val exportSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val readingStatsRepository: ReadingStatsRepository,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                val todayEpochDay = LocalDate.now().toEpochDay()
                val todayString = ReadingStatsRepository.todayString()
                val weekStart = ReadingStatsRepository.thisWeekStart()
                val monthStart = ReadingStatsRepository.thisMonthStart()

                // Collect sessions for different time ranges
                combine(
                    readingStatsRepository.getAllSessions(),
                    readingStatsRepository.getSessionsBetweenDates(weekStart, todayString),
                    readingStatsRepository.getSessionsBetweenDates(monthStart, todayString)
                ) { allSessions, weekSessions, monthSessions ->
                    // Today's stats
                    val todaySessions = allSessions.filter { it.sessionDate == todayEpochDay }
                    val todayPages = todaySessions.sumOf { it.pagesRead }
                    val todayMs = todaySessions.sumOf { it.durationMs }

                    // Week stats
                    val weekPages = weekSessions.sumOf { it.pagesRead }
                    val weekMs = weekSessions.sumOf { it.durationMs }

                    // Month stats
                    val monthPages = monthSessions.sumOf { it.pagesRead }
                    val monthMs = monthSessions.sumOf { it.durationMs }

                    // Total stats
                    val totalPages = allSessions.sumOf { it.pagesRead }
                    val totalMs = allSessions.sumOf { it.durationMs }

                    // Books completed (progress >= 99%)
                    var booksCompleted = 0
                    bookRepository.getAllProgress().collect { progressList ->
                        booksCompleted = progressList.count { it.progressPercent >= 99f }
                    }

                    // Daily stats for the last 30 days
                    val last30Days = (0..29).map { daysAgo ->
                        val date = LocalDate.now().minusDays(daysAgo.toLong())
                        val daySessions = allSessions.filter { it.sessionDate == date.toEpochDay() }
                        DailyStat(
                            date = date.format(dateFormatter),
                            totalSeconds = daySessions.sumOf { it.durationMs },
                            totalPages = daySessions.sumOf { it.pagesRead },
                            sessionCount = daySessions.size
                        )
                    }.reversed()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        totalBooksRead = booksCompleted,
                        totalPagesRead = totalPages,
                        totalReadingTimeSeconds = totalMs,
                        todayPages = todayPages,
                        todayMinutes = todayMs / 60000,
                        weekPages = weekPages,
                        weekMinutes = weekMs / 60000,
                        monthPages = monthPages,
                        monthMinutes = monthMs / 60000,
                        dailyStats = last30Days,
                        recentSessions = allSessions.take(10)
                    )
                }.collect { }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load statistics: ${e.message}"
                )
            }
        }

        // Load streak separately
        viewModelScope.launch {
            try {
                val streak = readingStatsRepository.getReadingStreak()
                _uiState.value = _uiState.value.copy(currentStreak = streak)
            } catch (_: Exception) { }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun exportStatistics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportSuccess = false)
            try {
                val sessions = readingStatsRepository.getAllSessionsList()
                val booksCompleted = readingStatsRepository.getTotalBooksCompleted()
                val streak = readingStatsRepository.getReadingStreak()

                val exporter = ReadingStatsExporter(context)
                val file = exporter.exportStatsToFile(
                    sessions = sessions,
                    totalBooksRead = booksCompleted,
                    currentStreak = streak
                )

                if (file != null) {
                    exporter.shareExportedFile(file)
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportSuccess = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        error = "No reading statistics to export"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    error = "Failed to export statistics: ${e.message}"
                )
            }
        }
    }

    fun clearExportSuccess() {
        _uiState.value = _uiState.value.copy(exportSuccess = false)
    }
}
