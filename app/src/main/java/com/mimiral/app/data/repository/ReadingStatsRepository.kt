package com.mimiral.app.data.repository

import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.dao.ReadingSessionDao
import com.mimiral.app.data.local.entity.ReadingSessionEntity
import com.mimiral.app.data.local.statistics.ReadingStreakCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class ReadingStatsRepository @Inject constructor(
    private val readingSessionDao: ReadingSessionDao,
    private val readingProgressDao: ReadingProgressDao,
    private val bookDao: BookDao,
    private val streakCalculator: ReadingStreakCalculator
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Record a completed reading session.
     */
    suspend fun recordSession(
        bookId: Int,
        startTime: Long,
        endTime: Long,
        pagesRead: Int
    ) {
        val durationMs = endTime - startTime
        val sessionDate = LocalDate.now().toEpochDay()
        val session = ReadingSessionEntity(
            bookId = bookId,
            sessionDate = sessionDate,
            durationMs = durationMs,
            pagesRead = pagesRead
        )
        readingSessionDao.insertSession(session)
    }

    fun getAllSessions(): Flow<List<ReadingSessionEntity>> =
        readingSessionDao.getAllSessions()

    suspend fun getAllSessionsList(): List<ReadingSessionEntity> =
        readingSessionDao.getAllSessions().first()

    fun getSessionsForBook(bookId: Int): Flow<List<ReadingSessionEntity>> =
        readingSessionDao.getSessionsForBook(bookId)

    fun getSessionsBetweenDates(
        startDate: String,
        endDate: String
    ): Flow<List<ReadingSessionEntity>> {
        val parsedStart = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val startEpochDay = parsedStart.toEpochDay()
        val parsedEnd = LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val endEpochDay = parsedEnd.toEpochDay()
        return readingSessionDao.getSessionsInRange(startEpochDay, endEpochDay)
    }

    suspend fun getAllReadingDates(): List<String> =
        readingSessionDao.getAllSessions().first().let { sessions ->
            val dates = sessions.map { it.sessionDate }.distinct().sortedDescending()
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE
            dates.map { epochDay ->
                LocalDate.ofEpochDay(epochDay).format(formatter)
            }
        }

    fun getRecentSessions(): Flow<List<ReadingSessionEntity>> =
        readingSessionDao.getAllSessions()

    suspend fun getTotalBooksCompleted(): Int =
        readingProgressDao.getAllProgress().let { flow ->
            var count = 0
            flow.collect { list ->
                count = list.count { it.progressPercent >= 99f }
            }
            count
        }

    /**
     * Calculate the current reading streak (consecutive days with reading activity).
     * Returns 0 if no reading sessions exist or today has no activity yet.
     *
     * Delegates to [ReadingStreakCalculator] for the calculation.
     */
    suspend fun getReadingStreak(): Int {
        val dates = readingSessionDao.getDistinctReadingDates()
        return streakCalculator.computeCurrentStreak(dates)
    }

    /**
     * Calculate the longest reading streak (maximum consecutive days with reading activity).
     *
     * Delegates to [ReadingStreakCalculator] for the calculation.
     */
    suspend fun getLongestStreak(): Int {
        val dates = readingSessionDao.getDistinctReadingDates()
        return streakCalculator.computeLongestStreak(dates)
    }

    /**
     * Date helpers
     */
    companion object {
        fun todayString(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        fun daysAgoString(days: Int): String =
            LocalDate.now().minusDays(days.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)

        fun thisWeekStart(): String =
            LocalDate.now().minusDays(LocalDate.now().dayOfWeek.value.toLong() - 1)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)

        fun thisMonthStart(): String =
            LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
