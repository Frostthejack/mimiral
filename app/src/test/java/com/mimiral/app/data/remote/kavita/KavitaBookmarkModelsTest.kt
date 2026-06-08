package com.mimiral.app.data.remote.kavita

import org.junit.Assert.assertEquals
import org.junit.Test

class KavitaBookmarkModelsTest {

    @Test
    fun `KavitaBookmarkRequest creates correct data class`() {
        val request = KavitaBookmarkRequest(
            page = 10,
            chapterId = 99,
            seriesId = 20,
            libraryId = 3
        )

        assertEquals(10, request.page)
        assertEquals(99, request.chapterId)
        assertEquals(20, request.seriesId)
        assertEquals(3, request.libraryId)
    }

    @Test
    fun `KavitaChapterBookmark creates correct data class`() {
        val bookmark = KavitaChapterBookmark(
            chapterId = 42,
            page = 5,
            seriesId = 10
        )

        assertEquals(42, bookmark.chapterId)
        assertEquals(5, bookmark.page)
        assertEquals(10, bookmark.seriesId)
    }

    @Test
    fun `KavitaChapterMapping creates correct data class`() {
        val mapping = KavitaChapterMapping(
            libraryId = 1,
            seriesId = 10,
            chapterId = 42
        )

        assertEquals(1, mapping.libraryId)
        assertEquals(10, mapping.seriesId)
        assertEquals(42, mapping.chapterId)
    }

    @Test
    fun `KavitaResult Success holds data`() {
        val result = KavitaResult.Success("test data")
        assertEquals("test data", result.data)
    }

    @Test
    fun `KavitaResult Error holds message and code`() {
        val result = KavitaResult.Error(
            message = "Not found",
            code = 404
        )

        assertEquals("Not found", result.message)
        assertEquals(404, result.code)
    }

    @Test
    fun `KavitaBookmarkRequest copy creates with modified page`() {
        val original = KavitaBookmarkRequest(
            page = 5,
            chapterId = 42,
            seriesId = 10,
            libraryId = 1
        )

        val deleted = original.copy(page = -1)
        assertEquals(-1, deleted.page)
        assertEquals(42, deleted.chapterId)
    }
}
