package com.mimiral.app.data.local.statistics

import com.mimiral.app.data.local.dao.ReadingSessionDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingStreakCalculatorTest {

    private val mockDao = mock<ReadingSessionDao>()
    private val calculator = ReadingStreakCalculator(mockDao)

    // Helper: create an epoch day from a date string "yyyy-MM-dd"
    private fun date(year: Int, month: Int, day: Int): Long {
        return java.time.LocalDate.of(year, month, day).toEpochDay()
    }

    // --- computeCurrentStreak tests ---

    @Test
    fun `computeCurrentStreak - empty dates returns zero`() {
        assertEquals(0, calculator.computeCurrentStreak(emptyList()))
    }

    @Test
    fun `computeCurrentStreak - single day returns one`() {
        val today = java.time.LocalDate.now().toEpochDay()
        assertEquals(1, calculator.computeCurrentStreak(listOf(today)))
    }

    @Test
    fun `computeCurrentStreak - consecutive days from today`() {
        val today = java.time.LocalDate.now().toEpochDay()
        val dates = listOf(today, today - 1, today - 2, today - 3)
        assertEquals(4, calculator.computeCurrentStreak(dates))
    }

    @Test
    fun `computeCurrentStreak - gap breaks streak at the start`() {
        val today = java.time.LocalDate.now().toEpochDay()
        // Days 0, 1, 2, then gap at 3, then 4
        val dates = listOf(today, today - 1, today - 2, today - 4)
        assertEquals(3, calculator.computeCurrentStreak(dates))
    }

    @Test
    fun `computeCurrentStreak - gap between today and yesterday ends streak`() {
        val today = java.time.LocalDate.now().toEpochDay()
        // Last read was 3 days ago — streak broken
        val dates = listOf(today - 3, today - 4, today - 5)
        assertEquals(0, calculator.computeCurrentStreak(dates))
    }

    @Test
    fun `computeCurrentStreak - yesterday session keeps streak alive`() {
        val today = java.time.LocalDate.now().toEpochDay()
        // User read yesterday and the day before but not yet today
        val dates = listOf(today - 1, today - 2, today - 3)
        assertEquals(3, calculator.computeCurrentStreak(dates))
    }

    @Test
    fun `computeCurrentStreak - single old date with gap returns zero`() {
        val today = java.time.LocalDate.now().toEpochDay()
        val dates = listOf(today - 5)
        assertEquals(0, calculator.computeCurrentStreak(dates))
    }

    // --- computeLongestStreak tests ---

    @Test
    fun `computeLongestStreak - empty dates returns zero`() {
        assertEquals(0, calculator.computeLongestStreak(emptyList()))
    }

    @Test
    fun `computeLongestStreak - single day returns one`() {
        assertEquals(1, calculator.computeLongestStreak(listOf(date(2026, 1, 1))))
    }

    @Test
    fun `computeLongestStreak - consecutive week`() {
        val start = date(2026, 3, 1)
        val dates = (0..6).map { start + it }
        assertEquals(7, calculator.computeLongestStreak(dates))
    }

    @Test
    fun `computeLongestStreak - two streaks picks longest`() {
        val streak1 = (0..4).map { date(2026, 1, 1) + it } // 5 days
        val gap = listOf(date(2026, 2, 1)) // 1 day
        val streak2 = (0..2).map { date(2026, 3, 1) + it } // 3 days
        val dates = (streak1 + gap + streak2).sortedDescending()
        assertEquals(5, calculator.computeLongestStreak(dates))
    }

    @Test
    fun `computeLongestStreak - full month streak`() {
        val start = date(2026, 4, 1)
        val dates = (0 until 30).map { start + it }
        assertEquals(30, calculator.computeLongestStreak(dates))
    }

    @Test
    fun `computeLongestStreak - non-consecutive days each count as 1`() {
        val dates = listOf(
            date(2026, 1, 1),
            date(2026, 1, 3),
            date(2026, 1, 5),
            date(2026, 1, 7)
        )
        assertEquals(1, calculator.computeLongestStreak(dates))
    }

    @Test
    fun `computeLongestStreak - unsorted input handled correctly`() {
        val dates = listOf(
            date(2026, 3, 1),
            date(2026, 1, 1),
            date(2026, 1, 2),
            date(2026, 1, 3),
            date(2026, 2, 1)
        )
        assertEquals(3, calculator.computeLongestStreak(dates))
    }

    // --- computeStreakSummary tests ---

    @Test
    fun `computeStreakSummary - no sessions returns zeros`() = runTest {
        whenever(mockDao.getDistinctReadingDates()).thenReturn(emptyList())
        whenever(mockDao.getDistinctReadingDaysCount()).thenReturn(0)
        whenever(mockDao.getTotalReadingDurationMs()).thenReturn(0L)
        whenever(mockDao.getTotalPagesRead()).thenReturn(0)

        val summary = calculator.computeStreakSummary()

        assertEquals(0, summary.currentStreak)
        assertEquals(0, summary.longestStreak)
        assertEquals(0, summary.totalReadingDays)
        assertEquals(0L, summary.totalReadingTimeMs)
        assertEquals(0, summary.totalPagesRead)
        assertNull(summary.currentStreakStart)
        assertNull(summary.lastReadDate)
    }

    @Test
    fun `computeStreakSummary - today only session`() = runTest {
        val today = java.time.LocalDate.now().toEpochDay()
        whenever(mockDao.getDistinctReadingDates()).thenReturn(listOf(today))
        whenever(mockDao.getDistinctReadingDaysCount()).thenReturn(1)
        whenever(mockDao.getTotalReadingDurationMs()).thenReturn(600_000L) // 10 min
        whenever(mockDao.getTotalPagesRead()).thenReturn(25)

        val summary = calculator.computeStreakSummary()

        assertEquals(1, summary.currentStreak)
        assertEquals(1, summary.longestStreak)
        assertEquals(1, summary.totalReadingDays)
        assertEquals(600_000L, summary.totalReadingTimeMs)
        assertEquals(25, summary.totalPagesRead)
        assertEquals(today, summary.currentStreakStart)
        assertEquals(today, summary.lastReadDate)
    }

    @Test
    fun `computeStreakSummary - week-long streak`() = runTest {
        val today = java.time.LocalDate.now().toEpochDay()
        val dates = (0..6).map { today - it } // today, yesterday, ... 6 days ago
        whenever(mockDao.getDistinctReadingDates()).thenReturn(dates)
        whenever(mockDao.getDistinctReadingDaysCount()).thenReturn(7)
        whenever(mockDao.getTotalReadingDurationMs()).thenReturn(4_200_000L) // 70 min
        whenever(mockDao.getTotalPagesRead()).thenReturn(175)

        val summary = calculator.computeStreakSummary()

        assertEquals(7, summary.currentStreak)
        assertEquals(7, summary.longestStreak)
        assertEquals(7, summary.totalReadingDays)
        assertEquals(4_200_000L, summary.totalReadingTimeMs)
        assertEquals(175, summary.totalPagesRead)
        assertEquals(today - 6, summary.currentStreakStart)
        assertEquals(today, summary.lastReadDate)
    }

    @Test
    fun `computeStreakSummary - broken streak with historical data`() = runTest {
        val today = java.time.LocalDate.now().toEpochDay()
        // Last read 3 days ago, with a 5-day streak ending then
        val dates = listOf(today - 3, today - 4, today - 5, today - 6, today - 7)
        whenever(mockDao.getDistinctReadingDates()).thenReturn(dates)
        whenever(mockDao.getDistinctReadingDaysCount()).thenReturn(5)
        whenever(mockDao.getTotalReadingDurationMs()).thenReturn(3_000_000L)
        whenever(mockDao.getTotalPagesRead()).thenReturn(100)

        val summary = calculator.computeStreakSummary()

        assertEquals(0, summary.currentStreak) // broken (gap > 1 day)
        assertEquals(5, summary.longestStreak)
        assertEquals(5, summary.totalReadingDays)
        assertNull(summary.currentStreakStart)
        assertEquals(today - 3, summary.lastReadDate)
    }

    // --- epochDayToString tests ---

    @Test
    fun `epochDayToString - formats correctly`() {
        val epochDay = date(2026, 6, 5)
        assertEquals("2026-06-05", ReadingStreakCalculator.epochDayToString(epochDay))
    }

    @Test
    fun `epochDayToLocalDate - converts correctly`() {
        val epochDay = date(2026, 12, 25)
        val localDate = ReadingStreakCalculator.epochDayToLocalDate(epochDay)
        assertEquals(2026, localDate.year)
        assertEquals(12, localDate.monthValue)
        assertEquals(25, localDate.dayOfMonth)
    }
}
