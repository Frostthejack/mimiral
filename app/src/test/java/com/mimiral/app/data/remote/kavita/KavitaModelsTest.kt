package com.mimiral.app.data.remote.kavita

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Kavita data models.
 */
class KavitaModelsTest {

    @Test
    fun `login request serializes correctly`() {
        val request = KavitaLoginRequest(
            username = "testuser",
            password = "testpass"
        )
        assertEquals("testuser", request.username)
        assertEquals("testpass", request.password)
    }

    @Test
    fun `login response stores token correctly`() {
        val response = KavitaLoginResponse(
            token = "jwt-token-123",
            refreshToken = "refresh-456",
            username = "testuser",
            email = "test@example.com"
        )
        assertEquals("jwt-token-123", response.token)
        assertEquals("refresh-456", response.refreshToken)
        assertEquals("testuser", response.username)
        assertEquals("test@example.com", response.email)
    }

    @Test
    fun `login response handles null optional fields`() {
        val response = KavitaLoginResponse(token = "token-only")
        assertEquals("token-only", response.token)
        assertNull(response.refreshToken)
        assertNull(response.username)
        assertNull(response.email)
    }

    @Test
    fun `server info stores all fields`() {
        val info = KavitaServerInfo(
            installId = "abc-123",
            isDocker = true,
            architecture = "x64",
            os = "Linux",
            version = "0.8.1",
            totalLibraries = 5
        )
        assertEquals("abc-123", info.installId)
        assertTrue(info.isDocker)
        assertEquals("x64", info.architecture)
        assertEquals("Linux", info.os)
        assertEquals("0.8.1", info.version)
        assertEquals(5, info.totalLibraries)
    }

    @Test
    fun `server info uses defaults`() {
        val info = KavitaServerInfo()
        assertNull(info.installId)
        assertFalse(info.isDocker)
        assertEquals(0, info.totalLibraries)
    }

    @Test
    fun `library stores fields correctly`() {
        val library = KavitaLibrary(
            id = 1,
            name = "Manga",
            type = 1,
            lastScanned = "2024-01-01"
        )
        assertEquals(1, library.id)
        assertEquals("Manga", library.name)
        assertEquals(1, library.type)
        assertEquals("2024-01-01", library.lastScanned)
        assertTrue(library.items.isEmpty())
    }

    @Test
    fun `series stores fields correctly`() {
        val series = KavitaSeries(
            id = 42,
            name = "One Piece",
            format = "Manga",
            pages = 1000,
            pagesRead = 500,
            libraryId = 1,
            libraryName = "Manga"
        )
        assertEquals(42, series.id)
        assertEquals("One Piece", series.name)
        assertEquals(1000, series.pages)
        assertEquals(500, series.pagesRead)
    }

    @Test
    fun `paginated response stores items correctly`() {
        val items = listOf(
            KavitaSeries(id = 1, name = "Series A", libraryId = 1),
            KavitaSeries(id = 2, name = "Series B", libraryId = 1)
        )
        val response = KavitaPaginatedResponse(
            items = items,
            totalItems = 10,
            currentPage = 1,
            totalPages = 5,
            pageSize = 2
        )
        assertEquals(2, response.items.size)
        assertEquals(10, response.totalItems)
        assertEquals(1, response.currentPage)
        assertEquals(5, response.totalPages)
    }

    @Test
    fun `kavita result success wraps data`() {
        val result = KavitaResult.Success("test data")
        assertEquals("test data", result.data)
    }

    @Test
    fun `kavita result error wraps message and code`() {
        val result = KavitaResult.Error(
            message = "Not found",
            code = 404
        )
        assertEquals("Not found", result.message)
        assertEquals(404, result.code)
        assertNull(result.cause)
    }

    @Test
    fun `kavita result error wraps cause`() {
        val exception = RuntimeException("network failure")
        val result = KavitaResult.Error(
            message = "Network error",
            cause = exception
        )
        assertEquals("Network error", result.message)
        assertSame(exception, result.cause)
    }

    @Test
    fun `reading progress stores fields correctly`() {
        val progress = KavitaReadingProgress(
            id = 1,
            chapterId = 10,
            pagesRead = 5,
            volumeId = 2,
            seriesId = 3,
            libraryId = 1
        )
        assertEquals(1, progress.id)
        assertEquals(10, progress.chapterId)
        assertEquals(5, progress.pagesRead)
        assertEquals(2, progress.volumeId)
        assertEquals(3, progress.seriesId)
        assertEquals(1, progress.libraryId)
    }

    @Test
    fun `user info stores fields correctly`() {
        val user = KavitaUserInfo(
            id = "user-1",
            username = "admin",
            email = "admin@kavita.com",
            apiKey = "key-123"
        )
        assertEquals("user-1", user.id)
        assertEquals("admin", user.username)
        assertEquals("admin@kavita.com", user.email)
        assertEquals("key-123", user.apiKey)
    }
}
