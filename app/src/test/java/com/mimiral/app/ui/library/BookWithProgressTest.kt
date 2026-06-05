package com.mimiral.app.ui.library

import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookWithProgressTest {

    private val book = BookEntity(
        id = 1,
        filePath = "/books/test.epub",
        title = "Test Book",
        author = "Test Author",
        format = "EPUB"
    )

    @Test
    fun `progressPercent - returns zero when no progress`() {
        val bwp = BookWithProgress(book = book, progress = null)
        assertEquals(0f, bwp.progressPercent, 0.01f)
    }

    @Test
    fun `progressPercent - returns progress value`() {
        val progress = ReadingProgressEntity(bookId = 1, progressPercent = 45.5f)
        val bwp = BookWithProgress(book = book, progress = progress)
        assertEquals(45.5f, bwp.progressPercent, 0.01f)
    }

    @Test
    fun `currentPage - returns zero when no progress`() {
        val bwp = BookWithProgress(book = book, progress = null)
        assertEquals(0, bwp.currentPage)
    }

    @Test
    fun `currentPage - returns page number`() {
        val progress = ReadingProgressEntity(bookId = 1, pageNumber = 42)
        val bwp = BookWithProgress(book = book, progress = progress)
        assertEquals(42, bwp.currentPage)
    }

    @Test
    fun `totalPages - returns zero when no progress`() {
        val bwp = BookWithProgress(book = book, progress = null)
        assertEquals(0, bwp.totalPages)
    }

    @Test
    fun `totalPages - returns total pages`() {
        val progress = ReadingProgressEntity(bookId = 1, totalPages = 300)
        val bwp = BookWithProgress(book = book, progress = progress)
        assertEquals(300, bwp.totalPages)
    }

    @Test
    fun `lastReadTime - returns zero when no progress`() {
        val bwp = BookWithProgress(book = book, progress = null)
        assertEquals(0L, bwp.lastReadTime)
    }

    @Test
    fun `lastReadTime - returns last read time`() {
        val progress = ReadingProgressEntity(bookId = 1, lastReadTime = 1717500000000L)
        val bwp = BookWithProgress(book = book, progress = progress)
        assertEquals(1717500000000L, bwp.lastReadTime)
    }

    @Test
    fun `isReading - false when no progress`() {
        val bwp = BookWithProgress(book = book, progress = null)
        assertFalse(bwp.isReading)
    }

    @Test
    fun `isReading - false when progress is zero`() {
        val progress = ReadingProgressEntity(bookId = 1, progressPercent = 0f)
        val bwp = BookWithProgress(book = book, progress = progress)
        assertFalse(bwp.isReading)
    }

    @Test
    fun `isReading - true when progress is greater than zero`() {
        val progress = ReadingProgressEntity(bookId = 1, progressPercent = 1f)
        val bwp = BookWithProgress(book = book, progress = progress)
        assertTrue(bwp.isReading)
    }

    @Test
    fun `isFinished - false when progress is below 99`() {
        val progress = ReadingProgressEntity(bookId = 1, progressPercent = 98f)
        val bwp = BookWithProgress(book = book, progress = progress)
        assertFalse(bwp.isFinished)
    }

    @Test
    fun `isFinished - true when progress is exactly 99`() {
        val progress = ReadingProgressEntity(bookId = 1, progressPercent = 99f)
        val bwp = BookWithProgress(book = book, progress = progress)
        assertTrue(bwp.isFinished)
    }

    @Test
    fun `isFinished - true when progress is 100`() {
        val progress = ReadingProgressEntity(bookId = 1, progressPercent = 100f)
        val bwp = BookWithProgress(book = book, progress = progress)
        assertTrue(bwp.isFinished)
    }

    @Test
    fun `isFinished - false when no progress`() {
        val bwp = BookWithProgress(book = book, progress = null)
        assertFalse(bwp.isFinished)
    }

    @Test
    fun `book entity is preserved`() {
        val bwp = BookWithProgress(book = book, progress = null)
        assertEquals("Test Book", bwp.book.title)
        assertEquals("Test Author", bwp.book.author)
    }
}
