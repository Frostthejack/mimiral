package com.mimiral.app.data.repository

import com.mimiral.app.data.local.dao.ReadingTimeDao
import com.mimiral.app.data.local.entity.ReadingTimeEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository for reading time tracking.
 * Provides per-book, per-day, per-week, and per-month aggregation.
 */
@Singleton
class ReadingTimeRepository @Inject constructor(
    private val readingTimeDao: ReadingTimeDao
) {

    /** Record reading time for a book on today's date. */
    suspend fun recordReadingTime(bookId: Int, timeMs: Long) {
        val today = todayEpochDay()
        val existing = readingTimeDao.getReadingTime(bookId, today)
        val updated = ReadingTimeEntity(
            bookId = bookId,
            date = today,
            timeReadMs = (existing?.timeReadMs ?: 0L) + timeMs,
            lastSessionStart = System.currentTimeMillis()
        )
        readingTimeDao.saveReadingTime(updated)
    }

    /** Get total reading time for a book across all dates. */
    suspend fun getTotalTimeForBook(bookId: Int): Long =
        readingTimeDao.getTotalTimeForBook(bookId)

    /** Get total reading time across all books. */
    suspend fun getTotalReadingTime(): Long =
        readingTimeDao.getTotalReadingTime()

    /** Get total reading time for a specific date across all books. */
    suspend fun getTotalTimeForDate(dateEpochDay: Long): Long =
        readingTimeDao.getTotalTimeForDate(dateEpochDay)

    /** Get reading time entries for a book, ordered by date descending. */
    fun getReadingTimeForBook(bookId: Int): Flow<List<ReadingTimeEntity>> =
        readingTimeDao.getReadingTimeForBook(bookId)

    /** Get reading time for a book within a date range. */
    fun getReadingTimeForBookInRange(
        bookId: Int,
        startDate: Long,
        endDate: Long
    ): Flow<List<ReadingTimeEntity>> =
        readingTimeDao.getReadingTimeForBookInRange(bookId, startDate, endDate)

    /** Get all dates on which any reading occurred. */
    fun getReadingDates(): Flow<List<Long>> =
        readingTimeDao.getReadingDates()

    /** Get reading dates for a specific book. */
    fun getReadingDatesForBook(bookId: Int): Flow<List<Long>> =
        readingTimeDao.getReadingDatesForBook(bookId)

    /** Get the most recent reading date, or null if no reading recorded. */
    suspend fun getLastReadingDate(): Long? =
        readingTimeDao.getLastReadingDate()

    /** Delete all reading time entries for a book. */
    suspend fun deleteForBook(bookId: Int) =
        readingTimeDao.deleteForBook(bookId)

    /** Get today as epoch day. */
    companion object {
        fun todayEpochDay(): Long =
            LocalDate.now().toEpochDay()
    }
}
