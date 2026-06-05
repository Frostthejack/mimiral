package com.mimiral.app.ui.reader

import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.HighlightEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightExporterContentTest {

    @Test
    fun `HighlightEntity - basic creation`() {
        val highlight = HighlightEntity(
            bookId = 1,
            chapterIndex = 0,
            selectedText = "Hello world",
            color = "yellow",
            createdTime = 1717500000000L
        )

        assertEquals(1, highlight.bookId)
        assertEquals(0, highlight.chapterIndex)
        assertEquals("Hello world", highlight.selectedText)
        assertEquals("yellow", highlight.color)
        assertEquals(1717500000000L, highlight.createdTime)
        assertNull(highlight.note)
    }

    @Test
    fun `HighlightEntity - with note`() {
        val highlight = HighlightEntity(
            bookId = 1,
            chapterIndex = 0,
            selectedText = "Important passage",
            note = "This is my note",
            color = "blue",
            createdTime = 1717500000000L
        )

        assertEquals("This is my note", highlight.note)
    }

    @Test
    fun `HighlightEntity - with position`() {
        val highlight = HighlightEntity(
            bookId = 1,
            chapterIndex = 0,
            selectedText = "Text",
            startPosition = "100",
            color = "green",
            createdTime = 1717500000000L
        )

        assertEquals("100", highlight.startPosition)
    }

    @Test
    fun `BookEntity - for export`() {
        val book = BookEntity(
            id = 1,
            filePath = "/books/test.epub",
            title = "Test Book",
            author = "John Doe",
            format = "EPUB"
        )

        assertEquals("Test Book", book.title)
        assertEquals("John Doe", book.author)
    }

    @Test
    fun `BookEntity - author can be null`() {
        val book = BookEntity(
            id = 1,
            filePath = "/books/anonymous.epub",
            title = "Anonymous Book",
            author = null,
            format = "EPUB"
        )

        assertNull(book.author)
    }
}
