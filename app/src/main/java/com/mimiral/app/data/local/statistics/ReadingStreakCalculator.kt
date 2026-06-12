package com.mimiral.app.data.local.statistics

import com.mimiral.app.data.local.dao.ReadingSessionDao
import java.time.LocalDate

import java.time.temporal.ChronoUnit
/**
 * Calculates reading streaks and related statistics from reading session data.
 *
 * A "reading streak" is the number of consecutive calendar days (epoch days) on which
 * the user had at least one reading session. The streak breaks on any day with no sessions.
 *
 * All date calculations use epoch days (days since 1970-01-01 UTC) to match the
 * `session_date` column in the `reading_sessions` table.
 */
class ReadingStreakCalculator(private val sessionDao: ReadingSessionDao) {

    /**
     * Data class representing a complete streak summary.
     */
    data class StreakSummary(
        /** Current streak in days. 0 if no session today or yesterday. */
        val currentStreak: Int,
        /** Longest streak ever recorded in days. */
        val longestStreak: Int,
        /** Total number of distinct days with at least one reading session. */
        val totalReadingDays: Int,
        /** Total reading time across all sessions in milliseconds. */
        val totalReadingTimeMs: Long,
        /** Total pages read across all sessions. */
        val totalPagesRead: Int,
        /** Start date of the current streak as epoch day, or null if current streak is 0. */
        val currentStreakStart: Long?,
        /** Date of the most recent reading session as epoch day, or null if no sessions. */
        val lastReadDate: Long?
    )

    /**
     * Compute the full streak summary.
     * This is a convenience method that calls the individual calculation methods.
     */
    suspend fun computeStreakSummary(): StreakSummary {
        val distinctDates = sessionDao.getDistinctReadingDates()
        val currentStreak = computeCurrentStreak(distinctDates)
        val longestStreak = computeLongestStreak(distinctDates)
        val totalDays = sessionDao.getDistinctReadingDaysCount()
        val totalTime = sessionDao.getTotalReadingDurationMs()
        val totalPages = sessionDao.getTotalPagesRead()

        val currentStreakStart = if (currentStreak > 0 && distinctDates.isNotEmpty()) {
            // The current streak starts at the most recent date minus (currentStreak - 1) days
            distinctDates.first() - (currentStreak - 1)
        } else {
            null
        }

        val lastReadDate = distinctDates.firstOrNull()

        return StreakSummary(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalReadingDays = totalDays,
            totalReadingTimeMs = totalTime,
            totalPagesRead = totalPages,
            currentStreakStart = currentStreakStart,
            lastReadDate = lastReadDate
        )
    }

    /**
     * Compute the current reading streak in days.
     *
     * The streak counts backwards from today. If there is no session today,
     * we check yesterday — if there was a session yesterday, the streak is
     * still active (user might read later today). If neither today nor yesterday
     * has a session, the streak is 0.
     *
     * @param distinctDates List of distinct epoch days with sessions, sorted descending (newest first).
     * @return Current streak length in days.
     */
    fun computeCurrentStreak(distinctDates: List<Long>): Int {
        if (distinctDates.isEmpty()) return 0

        val today = LocalDate.now().toEpochDay()
        val mostRecentDate = distinctDates.first()

        // If the most recent session is more than 1 day ago, streak is broken
        val daysSinceLastRead = today - mostRecentDate
        if (daysSinceLastRead > 1) return 0

        // Count consecutive days backwards from the most recent date
        var streak = 1
        for (i in 1 until distinctDates.size) {
            val expected = mostRecentDate - i
            if (distinctDates[i] == expected) {
                streak++
            } else {
                break
            }
        }
        return streak
    }

    /**
     * Compute the longest reading streak ever recorded.
     *
     * @param distinctDates List of distinct epoch days with sessions, sorted descending (newest first).
     * @return Longest streak length in days.
     */
    fun computeLongestStreak(distinctDates: List<Long>): Int {
        if (distinctDates.isEmpty()) return 0
        if (distinctDates.size == 1) return 1

        // Sort ascending for sequential scan
        val sorted = distinctDates.sorted()
        var longest = 1
        var current = 1

        for (i in 1 until sorted.size) {
            val diff = sorted[i] - sorted[i - 1]
            if (diff == 1L) {
                current++
                if (current > longest) longest = current
            } else if (diff > 1L) {
                current = 1
            }
            // diff == 0 shouldn't happen since we use DISTINCT, but handle gracefully
        }
        return longest
    }

    /**
     * Check if the user has a reading session on the given epoch day.
     */
    suspend fun hasSessionOnDate(epochDay: Long): Boolean {
        return sessionDao.getTotalDurationForDay(epochDay) > 0
    }

    /**
     * Get reading time in milliseconds for a specific date.
     */
    suspend fun getReadingTimeForDate(epochDay: Long): Long {
        return sessionDao.getTotalDurationForDay(epochDay)
    }

    /**
     * Get pages read for a specific date.
     */
    suspend fun getPagesReadForDate(epochDay: Long): Int {
        return sessionDao.getTotalPagesForDay(epochDay)
    }

    companion object {
        /** Convert a [LocalDate] to epoch day. */
        fun localDateToEpochDay(date: LocalDate): Long =
            java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.ofEpochDay(0), date)

        /** Convert epoch day to [LocalDate]. */
        fun epochDayToLocalDate(epochDay: Long): LocalDate =
            LocalDate.ofEpochDay(epochDay)

        /** Get today as epoch day. */
        fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

        /** Convert epoch day to human-readable string (yyyy-MM-dd). */
        fun epochDayToString(epochDay: Long): String =
            LocalDate.ofEpochDay(epochDay).toString()
    }
}
