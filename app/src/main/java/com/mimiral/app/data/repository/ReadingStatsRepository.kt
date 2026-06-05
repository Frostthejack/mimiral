package com.mimiral.app.data.repository

import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.dao.ReadingSessionDao
import com.mimiral.app.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingStatsRepository @Inject constructor(
    private val readingSessionDao: ReadingSessionDao,
    private val readingProgressDao: ReadingProgressDao,
    private val bookDao: BookDao
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
        val duration = (endTime - startTime) / 1000
        val date = LocalDate.now().format(dateFormatter)
        val session = ReadingSessionEntity(
            bookId = bookId,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = duration,
            pagesRead = pagesRead,
            date = date
        )
        readingSessionDao.insertSession(session)
    }

    fun getAllSessions(): Flow<List<ReadingSessionEntity>> =
        readingSessionDao.getAllSessions()

    fun getSessionsForBook(bookId: Int): Flow<List<ReadingSessionEntity>> =
        readingSessionDao.getSessionsForBook(bookId)

    fun getSessionsForDate(date: String): Flow<List<ReadingSessionEntity>> =
        readingSessionDao.getSessionsForDate(date)

    fun getSessionsBetweenDates(startDate: String, endDate: String): Flow<List<ReadingSessionEntity>> =
        readingSessionDao.getSessionsBetweenDates(startDate, endDate)

    fun getAllReadingDates(): Flow<List<String>> =
        readingSessionDao.getAllReadingDates()

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
     */
    suspend fun getReadingStreak(): Int {
        val dates = readingSessionDao.getDistinctDatesDesc()
        if (dates.isEmpty()) return 0

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        // Check if today or yesterday has activity
        val latestDate = LocalDate.parse(dates.first(), dateFormatter)
        if (latestDate != today && latestDate != yesterday) return 0

        var streak = 1
        var currentDate = latestDate

        for (i in 1 until dates.size) {
            val prevDate = LocalDate.parse(dates[i], dateFormatter)
            if (currentDate.minusDays(1) == prevDate) {
                streak++
                currentDate = prevDate
            } else {
                break
            }
        }

        return streak
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
