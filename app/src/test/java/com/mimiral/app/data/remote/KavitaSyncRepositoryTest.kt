package com.mimiral.app.data.remote

import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.local.entity.ServerEntity
import com.mimiral.app.data.remote.kavita.KavitaReadingProgressRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

class KavitaSyncRepositoryTest {

    @Mock
    private lateinit var kavitaApi: KavitaSyncApi

    @Mock
    private lateinit var bookDao: BookDao

    @Mock
    private lateinit var readingProgressDao: ReadingProgressDao

    @Mock
    private lateinit var serverDao: ServerDao

    @Mock
    private lateinit var progressSyncRepository: KavitaReadingProgressRepository

    private lateinit var repository: KavitaSyncRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = KavitaSyncRepository(
            kavitaApi = kavitaApi,
            bookDao = bookDao,
            readingProgressDao = readingProgressDao,
            serverDao = serverDao,
            progressSyncRepository = progressSyncRepository
        )
    }

    @Test
    fun `pushProgress returns NoKavitaBook when book has no kavitaSeriesId`() = runTest {
        val book = BookEntity(
            id = 1,
            filePath = "/test.epub",
            title = "Test",
            format = "EPUB",
            kavitaSeriesId = null,
            kavitaLibraryId = null
        )
        whenever(bookDao.getBookById(1)).thenReturn(book)

        val result = repository.pushProgress(1, 5)

        assertTrue(result is SyncResult.NoKavitaBook)
        verify(kavitaApi, never()).pushProgress(any())
    }

    @Test
    fun `pushProgress returns NoKavitaBook when book has no kavitaLibraryId`() = runTest {
        val book = BookEntity(
            id = 1,
            filePath = "/test.epub",
            title = "Test",
            format = "EPUB",
            kavitaSeriesId = 42,
            kavitaLibraryId = null
        )
        whenever(bookDao.getBookById(1)).thenReturn(book)

        val result = repository.pushProgress(1, 5)

        assertTrue(result is SyncResult.NoKavitaBook)
        verify(kavitaApi, never()).pushProgress(any())
    }

    @Test
    fun `pushProgress returns Success when API call succeeds`() = runTest {
        val book = BookEntity(
            id = 1,
            filePath = "/test.epub",
            title = "Test",
            format = "EPUB",
            kavitaSeriesId = 42,
            kavitaLibraryId = 1
        )
        val progress = ReadingProgressEntity(
            id = 1,
            bookId = 1,
            pageNumber = 5,
            kavitaSynced = false
        )
        whenever(bookDao.getBookById(1)).thenReturn(book)
        whenever(readingProgressDao.getProgressForBook(1)).thenReturn(progress)
        whenever(kavitaApi.pushProgress(any())).thenReturn(
            Response.success(KavitaProgressResponse(success = true))
        )

        val result = repository.pushProgress(1, 5)

        assertTrue(result is SyncResult.Success)
        verify(readingProgressDao).saveProgress(progress.copy(kavitaSynced = true))
    }

    @Test
    fun `pushProgress returns Error when API call fails`() = runTest {
        val book = BookEntity(
            id = 1,
            filePath = "/test.epub",
            title = "Test",
            format = "EPUB",
            kavitaSeriesId = 42,
            kavitaLibraryId = 1
        )
        whenever(bookDao.getBookById(1)).thenReturn(book)
        whenever(kavitaApi.pushProgress(any())).thenReturn(
            Response.error(500, okhttp3.ResponseBody.create(null, "Server Error"))
        )

        val result = repository.pushProgress(1, 5)

        assertTrue(result is SyncResult.Error)
    }

    @Test
    fun `pullProgress returns NoKavitaBook when book has no kavitaSeriesId`() = runTest {
        val book = BookEntity(
            id = 1,
            filePath = "/test.epub",
            title = "Test",
            format = "EPUB",
            kavitaSeriesId = null,
            kavitaLibraryId = null
        )
        whenever(bookDao.getBookById(1)).thenReturn(book)

        val result = repository.pullProgress(1)

        assertTrue(result is SyncResult.NoKavitaBook)
        verify(kavitaApi, never()).pullProgress(any(), any())
    }

    @Test
    fun `pullProgress returns Success when API returns progress data`() = runTest {
        val book = BookEntity(
            id = 1,
            filePath = "/test.epub",
            title = "Test",
            format = "EPUB",
            kavitaSeriesId = 42,
            kavitaLibraryId = 1
        )
        whenever(bookDao.getBookById(1)).thenReturn(book)
        whenever(kavitaApi.pullProgress(42, 1)).thenReturn(
            Response.success(
                KavitaProgressData(
                    seriesId = 42,
                    libraryId = 1,
                    pageNumber = 10,
                    lastModified = "2026-01-01T00:00:00.000Z"
                )
            )
        )

        val result = repository.pullProgress(1)

        assertTrue(result is SyncResult.Success)
    }

    @Test
    fun `pullProgress returns Error when API call fails`() = runTest {
        val book = BookEntity(
            id = 1,
            filePath = "/test.epub",
            title = "Test",
            format = "EPUB",
            kavitaSeriesId = 42,
            kavitaLibraryId = 1
        )
        whenever(bookDao.getBookById(1)).thenReturn(book)
        whenever(kavitaApi.pullProgress(42, 1)).thenReturn(
            Response.error(404, okhttp3.ResponseBody.create(null, "Not Found"))
        )

        val result = repository.pullProgress(1)

        assertTrue(result is SyncResult.Error)
    }

    @Test
    fun `syncProgress returns NoKavitaBook when book has no kavita IDs`() = runTest {
        val book = BookEntity(
            id = 1,
            filePath = "/test.epub",
            title = "Test",
            format = "EPUB",
            kavitaSeriesId = null,
            kavitaLibraryId = null
        )
        whenever(bookDao.getBookById(1)).thenReturn(book)

        val result = repository.syncProgress(1, 5)

        assertTrue(result is SyncResult.NoKavitaBook)
    }

    @Test
    fun `syncProgress pushes local progress when local is newer`() = runTest {
        val book = BookEntity(
            id = 1,
            filePath = "/test.epub",
            title = "Test",
            format = "EPUB",
            kavitaSeriesId = 42,
            kavitaLibraryId = 1
        )
        val progress = ReadingProgressEntity(
            id = 1,
            bookId = 1,
            pageNumber = 10,
            kavitaSynced = false
        )
        whenever(bookDao.getBookById(1)).thenReturn(book)
        whenever(readingProgressDao.getProgressForBook(1)).thenReturn(progress)
        // Remote has older timestamp
        whenever(kavitaApi.pullProgress(42, 1)).thenReturn(
            Response.success(
                KavitaProgressData(
                    seriesId = 42,
                    libraryId = 1,
                    pageNumber = 5,
                    lastModified = "2025-01-01T00:00:00.000Z"
                )
            )
        )
        whenever(kavitaApi.pushProgress(any())).thenReturn(
            Response.success(KavitaProgressResponse(success = true))
        )

        val result = repository.syncProgress(
            bookId = 1,
            localPageNumber = 10,
            localTimestamp = System.currentTimeMillis()
        )

        assertTrue(result is SyncResult.Success)
        // Verify push was called (local wins because it's newer)
        verify(kavitaApi).pushProgress(any())
    }

    @Test
    fun `getKavitaServer delegates to serverDao`() = runTest {
        val server = ServerEntity(
            id = 1,
            name = "My Kavita",
            type = "KAVITA",
            url = "http://localhost:5000",
            isActive = true
        )
        whenever(serverDao.getActiveServerByType("KAVITA")).thenReturn(server)

        val result = repository.getKavitaServer()

        assertEquals(server, result)
    }
}
