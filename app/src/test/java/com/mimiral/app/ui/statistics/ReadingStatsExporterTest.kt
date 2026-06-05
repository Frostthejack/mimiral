package com.mimiral.app.ui.statistics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mimiral.app.data.local.entity.ReadingSessionEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingStatsExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var exporter: ReadingStatsExporter

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
                startTime = 1717200000000L,
                endTime = 1717203600000L,
                durationSeconds = 3600,
                pagesRead = 30,
                date = "2026-06-01"
            ),
            ReadingSessionEntity(
                id = 2,
                bookId = 10,
                startTime = 1717286400000L,
                endTime = 1717290000000L,
                durationSeconds = 3600,
                pagesRead = 25,
                date = "2026-06-02"
            ),
            ReadingSessionEntity(
                id = 3,
                bookId = 20,
                startTime = 1717372800000L,
                endTime = 1717374600000L,
                durationSeconds = 1800,
                pagesRead = 15,
                date = "2026-06-03"
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
        assertTrue("Should contain header comment", content.contains("# Mimiral Reading Statistics Export"))
        assertTrue("Should contain total sessions", content.contains("# Total sessions: 3"))
        assertTrue("Should contain total pages", content.contains("# Total pages read: 70"))
        assertTrue("Should contain total reading time", content.contains("# Total reading time (seconds): 9000"))
        assertTrue("Should contain books completed", content.contains("# Books completed: 2"))
        assertTrue("Should contain current streak", content.contains("# Current streak (days): 3"))

        // Check CSV header
        assertTrue("Should contain CSV header", content.contains("date,book_id,start_time,end_time,duration_seconds,pages_read"))

        // Check CSV data rows (sorted by startTime ascending)
        val lines = content.lines().filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("date") }
        assertEquals("Should have 3 data rows", 3, lines.size)

        // First row: 2026-06-01, book 10
        assertTrue("First row should contain 2026-06-01 data", lines[0].startsWith("2026-06-01,10,"))
        // Second row: 2026-06-02, book 10
        assertTrue("Second row should contain 2026-06-02 data", lines[1].startsWith("2026-06-02,10,"))
        // Third row: 2026-06-03, book 20
        assertTrue("Third row should contain 2026-06-03 data", lines[2].startsWith("2026-06-03,20,"))
    }

    @Test
    fun `exported file has csv extension`() = runBlocking {
        val sessions = listOf(
            ReadingSessionEntity(
                id = 1,
                bookId = 1,
                startTime = 1717200000000L,
                endTime = 1717203600000L,
                durationSeconds = 3600,
                pagesRead = 10,
                date = "2026-06-01"
            )
        )

        val file = exporter.exportStatsToFile(
            sessions = sessions,
            totalBooksRead = 1,
            currentStreak = 1
        )

        assertNotNull(file)
        assertTrue("File should have .csv extension", file!!.name.endsWith(".csv"))
        assertTrue("File name should start with mimiral_statistics_", file.name.startsWith("mimiral_statistics_"))
    }

    @Test
    fun `exported file is in cache exports directory`() = runBlocking {
        val sessions = listOf(
            ReadingSessionEntity(
                id = 1,
                bookId = 1,
                startTime = 1717200000000L,
                endTime = 1717203600000L,
                durationSeconds = 3600,
                pagesRead = 10,
                date = "2026-06-01"
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
                startTime = 1717372800000L, // June 3
                endTime = 1717374600000L,
                durationSeconds = 1800,
                pagesRead = 5,
                date = "2026-06-03"
            ),
            ReadingSessionEntity(
                id = 1,
                bookId = 1,
                startTime = 1717200000000L, // June 1
                endTime = 1717203600000L,
                durationSeconds = 3600,
                pagesRead = 10,
                date = "2026-06-01"
            ),
            ReadingSessionEntity(
                id = 2,
                bookId = 1,
                startTime = 1717286400000L, // June 2
                endTime = 1717290000000L,
                durationSeconds = 3600,
                pagesRead = 8,
                date = "2026-06-02"
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
            it.isNotBlank() && !it.startsWith("#") && !it.startsWith("date")
        }

        // Should be sorted by start_time ascending: June 1, June 2, June 3
        assertTrue("First row should be June 1", lines[0].startsWith("2026-06-01"))
        assertTrue("Second row should be June 2", lines[1].startsWith("2026-06-02"))
        assertTrue("Third row should be June 3", lines[2].startsWith("2026-06-03"))
    }

    @Test
    fun `CSV values are correctly formatted`() = runBlocking {
        val sessions = listOf(
            ReadingSessionEntity(
                id = 1,
                bookId = 42,
                startTime = 1717200000000L,
                endTime = 1717201800000L,
                durationSeconds = 1800,
                pagesRead = 15,
                date = "2026-06-01"
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

        assertEquals("Should have 6 CSV columns", 6, parts.size)
        assertEquals("Date should match", "2026-06-01", parts[0])
        assertEquals("Book ID should match", "42", parts[1])
        assertEquals("Start time should match", "1717200000000", parts[2])
        assertEquals("End time should match", "1717201800000", parts[3])
        assertEquals("Duration should match", "1800", parts[4])
        assertEquals("Pages read should match", "15", parts[5])
    }
}
