package com.mimiral.app.data.repository

import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.dao.ReadingSessionDao
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.local.entity.ReadingSessionEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ReadingStatsRepositoryTest {

    private lateinit var readingSessionDao: ReadingSessionDao
    private lateinit var readingProgressDao: ReadingProgressDao
    private lateinit var bookDao: BookDao
    private lateinit var repository: ReadingStatsRepository

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    @Before
    fun setup() {
        readingSessionDao = mock()
        readingProgressDao = mock()
        bookDao = mock()
        repository = ReadingStatsRepository(readingSessionDao, readingProgressDao, bookDao)
    }

    // ---- recordSession ----

    @Test
    fun `recordSession - calculates duration and inserts session`() = runTest {
        repository.recordSession(
            bookId = 1,
            startTime = 1000000L,
            endTime = 1060000L,
            pagesRead = 10
        )

        verify(readingSessionDao).insertSession(any())
    }

    @Test
    fun `recordSession - duration is computed correctly`() = runTest {
        val captured = mutableListOf<ReadingSessionEntity>()
        whenever(readingSessionDao.insertSession(any())).thenAnswer { invocation ->
            captured.add(invocation.arguments[0] as ReadingSessionEntity)
            Unit
        }

        repository.recordSession(
            bookId = 2,
            startTime = 0L,
            endTime = 120000L,
            pagesRead = 5
        )

        assertEquals(1, captured.size)
        assertEquals(120L, captured[0].durationSeconds)
        assertEquals(2, captured[0].bookId)
        assertEquals(5, captured[0].pagesRead)
    }

    @Test
    fun `recordSession - date is today`() = runTest {
        val captured = mutableListOf<ReadingSessionEntity>()
        whenever(readingSessionDao.insertSession(any())).thenAnswer { invocation ->
            captured.add(invocation.arguments[0] as ReadingSessionEntity)
            Unit
        }

        repository.recordSession(
            bookId = 1,
            startTime = 0L,
            endTime = 60000L,
            pagesRead = 1
        )

        val today = LocalDate.now().format(dateFormatter)
        assertEquals(today, captured[0].date)
    }

    // ---- getAllSessions ----

    @Test
    fun `getAllSessions - delegates to dao`() {
        val sessions = listOf(
            ReadingSessionEntity(
                bookId = 1,
                startTime = 0,
                endTime = 60000,
                durationSeconds = 60,
                pagesRead = 5,
                date = "2026-06-01"
            ),
            ReadingSessionEntity(
                bookId = 1,
                startTime = 120000,
                endTime = 180000,
                durationSeconds = 60,
                pagesRead = 3,
                date = "2026-06-02"
            )
        )
        whenever(readingSessionDao.getAllSessions()).thenReturn(flowOf(sessions))

        val result = repository.getAllSessions()
        assertNotNull(result)
    }

    // ---- getReadingStreak ----

    @Test
    fun `getReadingStreak - empty dates returns zero`() = runTest {
        whenever(readingSessionDao.getDistinctDatesDesc()).thenReturn(emptyList())
        assertEquals(0, repository.getReadingStreak())
    }

    @Test
    fun `getReadingStreak - single day streak`() = runTest {
        val today = LocalDate.now().format(dateFormatter)
        whenever(readingSessionDao.getDistinctDatesDesc()).thenReturn(listOf(today))
        assertEquals(1, repository.getReadingStreak())
    }

    @Test
    fun `getReadingStreak - three day streak`() = runTest {
        val today = LocalDate.now()
        val dates = (0..2).map { today.minusDays(it.toLong()).format(dateFormatter) }
        whenever(readingSessionDao.getDistinctDatesDesc()).thenReturn(dates)
        assertEquals(3, repository.getReadingStreak())
    }

    @Test
    fun `getReadingStreak - streak broken returns partial`() = runTest {
        val today = LocalDate.now()
        val dates = listOf(
            today.format(dateFormatter),
            today.minusDays(1).format(dateFormatter),
            // gap at minusDays(2)
            today.minusDays(3).format(dateFormatter)
        )
        whenever(readingSessionDao.getDistinctDatesDesc()).thenReturn(dates)
        assertEquals(2, repository.getReadingStreak())
    }

    @Test
    fun `getReadingStreak - yesterday only counts as streak of 1`() = runTest {
        val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)
        whenever(readingSessionDao.getDistinctDatesDesc()).thenReturn(listOf(yesterday))
        assertEquals(1, repository.getReadingStreak())
    }

    @Test
    fun `getReadingStreak - two days ago only returns zero`() = runTest {
        val twoDaysAgo = LocalDate.now().minusDays(2).format(dateFormatter)
        whenever(readingSessionDao.getDistinctDatesDesc()).thenReturn(listOf(twoDaysAgo))
        assertEquals(0, repository.getReadingStreak())
    }

    @Test
    fun `getReadingStreak - five day streak`() = runTest {
        val today = LocalDate.now()
        val dates = (0..4).map { today.minusDays(it.toLong()).format(dateFormatter) }
        whenever(readingSessionDao.getDistinctDatesDesc()).thenReturn(dates)
        assertEquals(5, repository.getReadingStreak())
    }

    // ---- getTotalBooksCompleted ----

    @Test
    fun `getTotalBooksCompleted - counts books with 99 percent or more progress`() = runTest {
        val progressList = listOf(
            ReadingProgressEntity(bookId = 1, progressPercent = 100f),
            ReadingProgressEntity(bookId = 2, progressPercent = 99f),
            ReadingProgressEntity(bookId = 3, progressPercent = 50f),
            ReadingProgressEntity(bookId = 4, progressPercent = 0f)
        )
        whenever(readingProgressDao.getAllProgress()).thenReturn(flowOf(progressList))

        assertEquals(2, repository.getTotalBooksCompleted())
    }

    @Test
    fun `getTotalBooksCompleted - empty list returns zero`() = runTest {
        whenever(readingProgressDao.getAllProgress()).thenReturn(flowOf(emptyList()))
        assertEquals(0, repository.getTotalBooksCompleted())
    }

    // ---- Date helpers ----

    @Test
    fun `todayString - returns today in ISO format`() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertEquals(today, ReadingStatsRepository.todayString())
    }

    @Test
    fun `daysAgoString - returns correct date`() {
        val expected = LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertEquals(expected, ReadingStatsRepository.daysAgoString(7))
    }

    @Test
    fun `daysAgoString - zero days returns today`() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertEquals(today, ReadingStatsRepository.daysAgoString(0))
    }

    @Test
    fun `thisWeekStart - returns Monday of current week`() {
        val expected = LocalDate.now().minusDays(
            (LocalDate.now().dayOfWeek.value - 1).toLong()
        ).format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertEquals(expected, ReadingStatsRepository.thisWeekStart())
    }

    @Test
    fun `thisMonthStart - returns first day of current month`() {
        val expected = LocalDate.now().withDayOfMonth(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertEquals(expected, ReadingStatsRepository.thisMonthStart())
    }
}
