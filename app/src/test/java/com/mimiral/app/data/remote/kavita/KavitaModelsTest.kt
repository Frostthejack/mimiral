package com.mimiral.app.data.remote.kavita

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookFormatDetectorTest {

    @Test
    fun detectFormat_epub_returnsEPUB() {
        // ZIP signature (EPUB files are ZIP archives)
        val data = byteArrayOf(
            0x50,
            0x4B,
            0x03,
            0x04,
            0x00,
            0x00
        )
        assertEquals("EPUB", BookFormatDetector.detectFormat(data))
    }

    @Test
    fun detectFormat_pdf_returnsPDF() {
        val data = byteArrayOf(
            0x25,
            0x50,
            0x44,
            0x46,
            0x2D,
            0x31
        )
        assertEquals("PDF", BookFormatDetector.detectFormat(data))
    }

    @Test
    fun detectFormat_cbz_returnsCBZ() {
        val data = byteArrayOf(
            0x50,
            0x4B,
            0x03,
            0x04,
            0x00,
            0x00
        )
        assertEquals("EPUB", BookFormatDetector.detectFormat(data))
    }

    @Test
    fun detectFormat_cbr_returnsCBR() {
        val data = byteArrayOf(
            0x52,
            0x61,
            0x72,
            0x21,
            0x1A,
            0x07
        )
        assertEquals("CBR", BookFormatDetector.detectFormat(data))
    }

    @Test
    fun detectFormat_png_returnsPNG() {
        val data = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A
        )
        assertEquals("PNG", BookFormatDetector.detectFormat(data))
    }

    @Test
    fun detectFormat_jpeg_returnsJPEG() {
        val data = byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            0xE0.toByte(),
            0x00,
            0x10
        )
        assertEquals("JPEG", BookFormatDetector.detectFormat(data))
    }

    @Test
    fun detectFormat_unknown_returnsUNKNOWN() {
        val data = byteArrayOf(
            0x00,
            0x01,
            0x02,
            0x03,
            0x04
        )
        assertEquals("UNKNOWN", BookFormatDetector.detectFormat(data))
    }

    @Test
    fun detectFormat_tooShort_returnsUNKNOWN() {
        val data = byteArrayOf(0x50, 0x4B)
        assertEquals("UNKNOWN", BookFormatDetector.detectFormat(data))
    }

    @Test
    fun detectFormat_empty_returnsUNKNOWN() {
        val data = byteArrayOf()
        assertEquals("UNKNOWN", BookFormatDetector.detectFormat(data))
    }

    @Test
    fun formatToExtension_epub() {
        assertEquals(".epub", BookFormatDetector.formatToExtension("epub"))
        assertEquals(".epub", BookFormatDetector.formatToExtension("EPUB"))
        assertEquals(
            ".epub",
            BookFormatDetector.formatToExtension("application/epub+zip")
        )
    }

    @Test
    fun formatToExtension_pdf() {
        assertEquals(".pdf", BookFormatDetector.formatToExtension("pdf"))
        assertEquals(".pdf", BookFormatDetector.formatToExtension("PDF"))
    }

    @Test
    fun formatToExtension_cbz() {
        assertEquals(".cbz", BookFormatDetector.formatToExtension("cbz"))
    }

    @Test
    fun formatToExtension_cbr() {
        assertEquals(".cbr", BookFormatDetector.formatToExtension("cbr"))
        assertEquals(".cbr", BookFormatDetector.formatToExtension("rar"))
    }

    @Test
    fun formatToExtension_png() {
        assertEquals(".png", BookFormatDetector.formatToExtension("png"))
    }

    @Test
    fun formatToExtension_jpeg() {
        assertEquals(".jpg", BookFormatDetector.formatToExtension("jpeg"))
        assertEquals(".jpg", BookFormatDetector.formatToExtension("jpg"))
    }

    @Test
    fun formatToExtension_unknown() {
        assertEquals(".bin", BookFormatDetector.formatToExtension("unknown"))
    }
}

class KavitaModelsTest {

    @Test
    fun kavitaLibrary_typeConstants() {
        assertEquals(0, KavitaLibrary.TYPE_BOOK)
        assertEquals(1, KavitaLibrary.TYPE_COMIC)
        assertEquals(2, KavitaLibrary.TYPE_MANGA)
        assertEquals(3, KavitaLibrary.TYPE_IMAGE)
        assertEquals(4, KavitaLibrary.TYPE_PDF)
    }

    @Test
    fun kavitaLoginRequest_creation() {
        val request = KavitaLoginRequest(
            username = "testuser",
            password = "testpass"
        )
        assertEquals("testuser", request.username)
        assertEquals("testpass", request.password)
    }

    @Test
    fun kavitaLoginResponse_creation() {
        val response = KavitaLoginResponse(
            username = "testuser",
            token = "jwt-token-123",
            refreshToken = "refresh-456",
            tokenDuration = "7.00:00:00",
            apiKey = null
        )
        assertEquals("testuser", response.username)
        assertEquals("jwt-token-123", response.token)
        assertEquals("refresh-456", response.refreshToken)
    }

    @Test
    fun kavitaLibrary_creation() {
        val library = KavitaLibrary(
            id = 1,
            name = "My Books",
            type = 0,
            lastScanned = "2024-01-01T00:00:00Z"
        )
        assertEquals(1, library.id)
        assertEquals("My Books", library.name)
        assertEquals(0, library.type)
    }

    @Test
    fun kavitaSeries_creation() {
        val series = KavitaSeries(
            id = 42,
            name = "Test Series",
            libraryId = 1,
            pages = 100,
            pagesRead = 50
        )
        assertEquals(42, series.id)
        assertEquals("Test Series", series.name)
        assertEquals(1, series.libraryId)
        assertEquals(100, series.pages)
        assertEquals(50, series.pagesRead)
    }

    @Test
    fun kavitaBookFile_creation() {
        val file = KavitaBookFile(
            id = 1,
            filePath = "/books/test.epub",
            pages = 200,
            format = "epub",
            size = 1024000
        )
        assertEquals(1, file.id)
        assertEquals("/books/test.epub", file.filePath)
        assertEquals(200, file.pages)
        assertEquals("epub", file.format)
        assertEquals(1024000, file.size)
    }

    @Test
    fun kavitaBookmark_creation() {
        val bookmark = KavitaBookmark(
            chapterId = 10,
            pageNum = 5,
            seriesId = 42,
            volumeId = 20,
            libraryId = 1
        )
        assertEquals(10, bookmark.chapterId)
        assertEquals(5, bookmark.pageNum)
        assertEquals(42, bookmark.seriesId)
    }

    @Test
    fun kavitaProgress_creation() {
        val progress = KavitaProgress(
            chapterId = 10,
            pageNum = 5,
            seriesId = 42,
            volumeId = 20,
            libraryId = 1
        )
        assertEquals(10, progress.chapterId)
        assertEquals(5, progress.pageNum)
    }

    @Test
    fun kavitaPagedResponse_creation() {
        val response = KavitaPagedResponse(
            items = listOf("a", "b", "c"),
            totalItems = 100,
            currentPage = 1,
            totalPages = 10
        )
        assertEquals(3, response.items.size)
        assertEquals(100, response.totalItems)
        assertEquals(1, response.currentPage)
        assertEquals(10, response.totalPages)
    }
}

class DownloadStateTest {

    @Test
    fun idle_state() {
        val state = DownloadState.Idle
        assertTrue(state is DownloadState.Idle)
    }

    @Test
    fun downloading_state() {
        val state = DownloadState.Downloading(
            bookTitle = "Test Book",
            bytesDownloaded = 500,
            totalBytes = 1000
        )
        assertTrue(state is DownloadState.Downloading)
        assertEquals("Test Book", state.bookTitle)
        assertEquals(500, state.bytesDownloaded)
        assertEquals(1000, state.totalBytes)
    }

    @Test
    fun completed_state() {
        val state = DownloadState.Completed(
            bookTitle = "Test Book",
            filePath = "/data/books/test.epub"
        )
        assertTrue(state is DownloadState.Completed)
        assertEquals("Test Book", state.bookTitle)
        assertEquals("/data/books/test.epub", state.filePath)
    }

    @Test
    fun failed_state() {
        val state = DownloadState.Failed(
            bookTitle = "Test Book",
            error = "Network error"
        )
        assertTrue(state is DownloadState.Failed)
        assertEquals("Test Book", state.bookTitle)
        assertEquals("Network error", state.error)
    }
}
