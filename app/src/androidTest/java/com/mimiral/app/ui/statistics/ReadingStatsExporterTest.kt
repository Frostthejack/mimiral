package com.mimiral.app.ui.statistics

import android.content.Context
import com.mimiral.app.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ReadingStatsExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var exporter: ReadingStatsExporter

    @Before
    fun setup() {
        context = mock()
        val cacheDir = tempFolder.root
        whenever(context.cacheDir).thenReturn(cacheDir)
        exporter = ReadingStatsExporter(context)
    }

    @Test
    fun `export with empty sessions returns null`() = runBlocking {
        val result = exporter.exportStatsToFile(
            sessions = emptyList(),
            totalBooksRead = 0,
            currentStreak = 0
        )
        assertNull("Should return null for empty sessions", result)
    }

    @Test
    fun `export creates valid CSV file`() = runBlocking {
        val sessions = listOf(
            ReadingSessionEntity(
                id = 1,
                bookId = 10,
                sessionDate = 20605L,
                durationMs = 3600000L,
                pagesRead = 30
            ),
            ReadingSessionEntity(
                id = 2,
                bookId = 10,
                sessionDate = 20606L,
                durationMs = 3600000L,
                pagesRead = 25
            ),
            ReadingSessionEntity(
                id = 3,
                bookId = 20,
                sessionDate = 20607L,
                durationMs = 1800000L,
                pagesRead = 15
            )
        )

        val file = exporter.exportStatsToFile(
            sessions = sessions,
            totalBooksRead = 2,
            currentStreak = 3
        )

        assertNotNull("File should not be null", file)
        assertTrue("File should exist", file!!.exists())
        assertTrue("File should have content", file.length() > 0)

        val content = file.readText()

        // Check summary section
        val headerComment = "# Mimiral Reading Statistics Export"
        assertTrue("Should contain header comment", content.contains(headerComment))
        assertTrue("Should contain total sessions", content.contains("# Total sessions: 3"))
        assertTrue("Should contain total pages", content.contains("# Total pages read: 70"))
        val timeStr = "# Total reading time (ms): 9000000"
        assertTrue("Should contain total reading time", content.contains(timeStr))
        assertTrue("Should contain books completed", content.contains("# Books completed: 2"))
        assertTrue("Should contain current streak", content.contains("# Current streak (days): 3"))

        // Check CSV header
        val csvHeader = "session_date,book_id,duration_ms,pages_read"
        assertTrue("Should contain CSV header", content.contains(csvHeader))

        // Check CSV data rows (sorted by sessionDate ascending)
        val lines = content.lines().filter { line ->
            line.isNotBlank() && !line.startsWith("#") && !line.startsWith("session_date")
        }
        assertEquals("Should have 3 data rows", 3, lines.size)

        // First row: epoch day 20605, book 10
        val row1Prefix = "20605,10,"
        assertTrue("First row should contain epoch day 20605 data", lines[0].startsWith(row1Prefix))
        // Second row: epoch day 20606, book 10
        val row2Prefix = "20606,10,"
        assertTrue(
            "Second row should contain epoch day 20606 data",
            lines[1].startsWith(row2Prefix)
        )
        // Third row: epoch day 20607, book 20
        val row3Prefix = "20607,20,"
        assertTrue("Third row should contain epoch day 20607 data", lines[2].startsWith(row3Prefix))
    }

    @Test
    fun `exported file has csv extension`() = runBlocking {
        val sessions = listOf(
            ReadingSessionEntity(
                id = 1,
                bookId = 1,
                sessionDate = 20605L,
                durationMs = 3600000L,
                pagesRead = 10
            )
        )

        val file = exporter.exportStatsToFile(
            sessions = sessions,
            totalBooksRead = 1,
            currentStreak = 1
        )

        assertNotNull(file)
        assertTrue("File should have .csv extension", file!!.name.endsWith(".csv"))
        val prefix = "mimiral_statistics_"
        assertTrue("File name should start with $prefix", file.name.startsWith(prefix))
    }

    @Test
    fun `exported file is in cache exports directory`() = runBlocking {
        val sessions = listOf(
            ReadingSessionEntity(
                id = 1,
                bookId = 1,
                sessionDate = 20605L,
                durationMs = 3600000L,
                pagesRead = 10
            )
        )

        val file = exporter.exportStatsToFile(
            sessions = sessions,
            totalBooksRead = 0,
            currentStreak = 0
        )

        assertNotNull(file)
        assertTrue(
            "File should be in cache/exports directory",
            file!!.parentFile.name == "exports"
        )
    }

    @Test
    fun `CSV data rows are chronologically sorted`() = runBlocking {
        val sessions = listOf(
            ReadingSessionEntity(
                id = 3,
                bookId = 1,
                sessionDate = 20607L, // June 3
                durationMs = 1800000L,
                pagesRead = 5
            ),
            ReadingSessionEntity(
                id = 1,
                bookId = 1,
                sessionDate = 20605L, // June 1
                durationMs = 3600000L,
                pagesRead = 10
            ),
            ReadingSessionEntity(
                id = 2,
                bookId = 1,
                sessionDate = 20606L, // June 2
                durationMs = 3600000L,
                pagesRead = 8
            )
        )

        val file = exporter.exportStatsToFile(
            sessions = sessions,
            totalBooksRead = 0,
            currentStreak = 0
        )

        assertNotNull(file)
        val content = file!!.readText()
        val lines = content.lines().filter {
            it.isNotBlank() && !it.startsWith("#") && !it.startsWith("session_date")
        }

        // Should be sorted by sessionDate ascending: June 1, June 2, June 3
        assertTrue("First row should be epoch day 20605", lines[0].startsWith("20605"))
        assertTrue("Second row should be epoch day 20606", lines[1].startsWith("20606"))
        assertTrue("Third row should be epoch day 20607", lines[2].startsWith("20607"))
    }

    @Test
    fun `CSV values are correctly formatted`() = runBlocking {
        val sessions = listOf(
            ReadingSessionEntity(
                id = 1,
                bookId = 42,
                sessionDate = 20605L,
                durationMs = 1800000L,
                pagesRead = 15
            )
        )

        val file = exporter.exportStatsToFile(
            sessions = sessions,
            totalBooksRead = 1,
            currentStreak = 1
        )

        assertNotNull(file)
        val content = file!!.readText()
        val lines = content.lines().filter {
            it.isNotBlank() && !it.startsWith("#")
        }

        // lines[0] = header, lines[1] = data
        val dataLine = lines[1]
        val parts = dataLine.split(",")

        assertEquals("Should have 4 CSV columns", 4, parts.size)
        assertEquals("Session date should match", "20605", parts[0])
        assertEquals("Book ID should match", "42", parts[1])
        assertEquals("Duration should match", "1800000", parts[2])
        assertEquals("Pages read should match", "15", parts[3])
    }
}
