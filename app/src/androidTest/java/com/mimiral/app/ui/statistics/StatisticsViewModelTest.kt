package com.mimiral.app.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsViewModelTest {

    @Test
    fun `StatisticsUiState - default values are sensible`() {
        val state = StatisticsUiState()

        assertTrue(state.isLoading)
        assertEquals(0, state.totalBooksRead)
        assertEquals(0, state.totalPagesRead)
        assertEquals(0L, state.totalReadingTimeSeconds)
        assertEquals(0, state.currentStreak)
        assertEquals(0, state.longestStreak)
        assertEquals(0, state.todayPages)
        assertEquals(0L, state.todayMinutes)
        assertEquals(0, state.weekPages)
        assertEquals(0L, state.weekMinutes)
        assertEquals(0, state.monthPages)
        assertEquals(0L, state.monthMinutes)
        assertTrue(state.dailyStats.isEmpty())
        assertTrue(state.recentSessions.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun `DailyStat - holds correct data`() {
        val stat = DailyStat(
            date = "2026-06-05",
            totalSeconds = 3600,
            totalPages = 25,
            sessionCount = 3
        )

        assertEquals("2026-06-05", stat.date)
        assertEquals(3600L, stat.totalSeconds)
        assertEquals(25, stat.totalPages)
        assertEquals(3, stat.sessionCount)
    }

    @Test
    fun `StatisticsUiState - copy with new values`() {
        val original = StatisticsUiState()
        val updated = original.copy(
            isLoading = false,
            totalBooksRead = 5,
            totalPagesRead = 1200,
            currentStreak = 7
        )

        assertFalse(updated.isLoading)
        assertEquals(5, updated.totalBooksRead)
        assertEquals(1200, updated.totalPagesRead)
        assertEquals(7, updated.currentStreak)
        // Unchanged fields preserved
        assertEquals(0, updated.todayPages)
        assertEquals(0L, updated.todayMinutes)
    }
}
