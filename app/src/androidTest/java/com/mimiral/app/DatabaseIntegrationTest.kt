package com.mimiral.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.BookmarkDao
import com.mimiral.app.data.local.dao.CollectionDao
import com.mimiral.app.data.local.dao.HighlightDao
import com.mimiral.app.data.local.dao.OpdsCatalogDao
import com.mimiral.app.data.local.dao.PdfSettingsDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.dao.ReadingSessionDao
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.database.MimiralDatabase
import com.mimiral.app.data.local.entity.BookCollectionCrossRef
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.BookTagCrossRef
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.local.entity.ChapterEntity
import com.mimiral.app.data.local.entity.CollectionEntity
import com.mimiral.app.data.local.entity.HighlightEntity
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import com.mimiral.app.data.local.entity.PdfSettingsEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.local.entity.ReadingSessionEntity
import com.mimiral.app.data.local.entity.ServerEntity
import com.mimiral.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for Room database operations.
 *
 * Uses an in-memory database to verify DAO CRUD operations,
 * foreign key constraints, cascading deletes, and query correctness.
 *
 * These tests run on-device via AndroidJUnit4 and exercise the real
 * Room database layer (not mocked).
 */
@RunWith(AndroidJUnit4::class)
class DatabaseIntegrationTest {

    private lateinit var db: MimiralDatabase
    private lateinit var bookDao: BookDao
    private lateinit var readingProgressDao: ReadingProgressDao
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var highlightDao: HighlightDao
    private lateinit var collectionDao: CollectionDao
    private lateinit var serverDao: ServerDao
    private lateinit var opdsCatalogDao: OpdsCatalogDao
    private lateinit var pdfSettingsDao: PdfSettingsDao
    private lateinit var readingSessionDao: ReadingSessionDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MimiralDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookDao = db.bookDao()
        readingProgressDao = db.readingProgressDao()
        bookmarkDao = db.bookmarkDao()
        highlightDao = db.highlightDao()
        collectionDao = db.collectionDao()
        serverDao = db.serverDao()
        opdsCatalogDao = db.opdsCatalogDao()
        pdfSettingsDao = db.pdfSettingsDao()
        readingSessionDao = db.readingSessionDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ==================== BookDao Tests ====================

    @Test
    fun bookDao_insertAndRetrieve() = runBlocking {
        val book = BookEntity(
            filePath = "/storage/test.epub",
            title = "Test Book",
            author = "Test Author",
            format = "EPUB",
            fileSize = 1024,
            source = "LOCAL"
        )
        val id = bookDao.insertBook(book)
        assertTrue("Insert should return positive id", id > 0)

        val retrieved = bookDao.getBookById(id.toInt())
        assertNotNull("Book should be retrievable", retrieved)
        assertEquals("Test Book", retrieved!!.title)
        assertEquals("Test Author", retrieved.author)
        assertEquals("EPUB", retrieved.format)
    }

    @Test
    fun bookDao_insertReplace() = runBlocking {
        val book = BookEntity(
            filePath = "/storage/test.epub",
            title = "Original Title",
            format = "EPUB"
        )
        val id = bookDao.insertBook(book)

        val updated = book.copy(id = id.toInt(), title = "Updated Title")
        bookDao.updateBook(updated)

        val retrieved = bookDao.getBookById(id.toInt())
        assertEquals("Updated Title", retrieved!!.title)
    }

    @Test
    fun bookDao_delete() = runBlocking {
        val book = BookEntity(
            filePath = "/storage/delete_me.epub",
            title = "Delete Me",
            format = "PDF"
        )
        val id = bookDao.insertBook(book)
        val retrieved = bookDao.getBookById(id.toInt())
        assertNotNull(retrieved)

        bookDao.deleteBook(retrieved!!)
        val afterDelete = bookDao.getBookById(id.toInt())
        assertNull("Book should be null after delete", afterDelete)
    }

    @Test
    fun bookDao_getBookByFilePath() = runBlocking {
        val path = "/storage/unique_book.epub"
        val book = BookEntity(
            filePath = path,
            title = "Unique Book",
            format = "EPUB"
        )
        bookDao.insertBook(book)

        val found = bookDao.getBookByFilePath(path)
        assertNotNull("Should find book by file path", found)
        assertEquals("Unique Book", found!!.title)

        val notFound = bookDao.getBookByFilePath("/storage/nonexistent.epub")
        assertNull("Should return null for nonexistent path", notFound)
    }

    @Test
    fun bookDao_getAllFilePaths() = runBlocking {
        bookDao.insertBook(BookEntity(filePath = "/a.epub", title = "A", format = "EPUB"))
        bookDao.insertBook(BookEntity(filePath = "/b.pdf", title = "B", format = "PDF"))
        bookDao.insertBook(BookEntity(filePath = "/c.epub", title = "C", format = "EPUB"))

        val paths = bookDao.getAllFilePaths()
        assertEquals(3, paths.size)
        assertTrue(paths.contains("/a.epub"))
        assertTrue(paths.contains("/b.pdf"))
        assertTrue(paths.contains("/c.epub"))
    }

    @Test
    fun bookDao_getBookCount() = runBlocking {
        assertEquals(0, bookDao.getBookCount())

        bookDao.insertBook(BookEntity(filePath = "/x.epub", title = "X", format = "EPUB"))
        assertEquals(1, bookDao.getBookCount())

        bookDao.insertBook(BookEntity(filePath = "/y.epub", title = "Y", format = "EPUB"))
        assertEquals(2, bookDao.getBookCount())
    }

    @Test
    fun bookDao_searchBooks() = runBlocking {
        bookDao.insertBook(BookEntity(filePath = "/a.epub", title = "Kotlin in Action", format = "EPUB", author = "JetBrains"))
        bookDao.insertBook(BookEntity(filePath = "/b.epub", title = "Android Development", format = "EPUB", author = "Google"))
        bookDao.insertBook(BookEntity(filePath = "/c.pdf", title = "Kotlin Cookbook", format = "PDF", author = "O'Reilly"))

        val kotlinBooks = bookDao.searchBooks("Kotlin").first()
        assertEquals(2, kotlinBooks.size)

        val googleBooks = bookDao.searchBooks("Google").first()
        assertEquals(1, googleBooks.size)
        assertEquals("Android Development", googleBooks[0].title)
    }

    @Test
    fun bookDao_sortedByTitle() = runBlocking {
        bookDao.insertBook(BookEntity(filePath = "/c.epub", title = "C Book", format = "EPUB"))
        bookDao.insertBook(BookEntity(filePath = "/a.epub", title = "A Book", format = "EPUB"))
        bookDao.insertBook(BookEntity(filePath = "/b.epub", title = "B Book", format = "EPUB"))

        val sorted = bookDao.getAllBooksSortedByTitle().first()
        assertEquals(3, sorted.size)
        assertEquals("A Book", sorted[0].title)
        assertEquals("B Book", sorted[1].title)
        assertEquals("C Book", sorted[2].title)
    }

    // ==================== ReadingProgressDao Tests ====================

    @Test
    fun readingProgressDao_insertAndRetrieve() = runBlocking {
        // Insert a book first
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/progress.epub", title = "Progress Test", format = "EPUB")
        ).toInt()

        val progress = ReadingProgressEntity(
            bookId = bookId,
            chapterIndex = 3,
            characterOffset = 1500,
            totalCharacters = 50000,
            pageNumber = 25,
            totalPages = 200,
            progressPercent = 12.5f,
            lastReadPosition = "epubcfi(/4/2/6)",
            totalTimeRead = 3600000L
        )
        readingProgressDao.saveProgress(progress)

        val retrieved = readingProgressDao.getProgressForBook(bookId)
        assertNotNull("Progress should be retrievable", retrieved)
        assertEquals(bookId, retrieved!!.bookId)
        assertEquals(3, retrieved.chapterIndex)
        assertEquals(1500, retrieved.characterOffset)
        assertEquals(25, retrieved.pageNumber)
        assertEquals(12.5f, retrieved.progressPercent)
        assertEquals("epubcfi(/4/2/6)", retrieved.lastReadPosition)
    }

    @Test
    fun readingProgressDao_upsertReplaces() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/upsert.epub", title = "Upsert Test", format = "EPUB")
        ).toInt()

        val progress1 = ReadingProgressEntity(
            bookId = bookId,
            pageNumber = 10,
            progressPercent = 5f
        )
        readingProgressDao.saveProgress(progress1)

        val progress2 = ReadingProgressEntity(
            bookId = bookId,
            pageNumber = 50,
            progressPercent = 25f,
            kavitaSynced = true
        )
        readingProgressDao.saveProgress(progress2)

        val retrieved = readingProgressDao.getProgressForBook(bookId)
        assertEquals(50, retrieved!!.pageNumber)
        assertEquals(25f, retrieved.progressPercent)
        assertEquals(true, retrieved.kavitaSynced)
    }

    @Test
    fun readingProgressDao_getRecentProgress() = runBlocking {
        val book1 = bookDao.insertBook(BookEntity(filePath = "/r1.epub", title = "R1", format = "EPUB")).toInt()
        val book2 = bookDao.insertBook(BookEntity(filePath = "/r2.epub", title = "R2", format = "EPUB")).toInt()

        readingProgressDao.saveProgress(
            ReadingProgressEntity(bookId = book1, pageNumber = 1, lastReadTime = 1000L)
        )
        readingProgressDao.saveProgress(
            ReadingProgressEntity(bookId = book2, pageNumber = 2, lastReadTime = 2000L)
        )

        val recent = readingProgressDao.getRecentProgress().first()
        assertTrue("Should have recent progress entries", recent.size >= 2)
    }

    // ==================== BookmarkDao Tests ====================

    @Test
    fun bookmarkDao_insertAndRetrieve() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/bm.epub", title = "Bookmark Test", format = "EPUB")
        ).toInt()

        val bookmark = BookmarkEntity(
            bookId = bookId,
            chapterIndex = 2,
            pageNumber = 15,
            position = "epubcfi(/4/2/15)",
            title = "Important passage",
            note = "Remember this",
            kavitaSynced = false
        )
        val id = bookmarkDao.insertBookmark(bookmark)
        assertTrue(id > 0)

        val bookmarks = bookmarkDao.getBookmarksForBook(bookId).first()
        assertEquals(1, bookmarks.size)
        assertEquals("Important passage", bookmarks[0].title)
        assertEquals("Remember this", bookmarks[0].note)
    }

    @Test
    fun bookmarkDao_delete() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/bm_del.epub", title = "BM Delete", format = "EPUB")
        ).toInt()

        val bookmark = BookmarkEntity(
            bookId = bookId,
            pageNumber = 5,
            title = "To Delete"
        )
        val id = bookmarkDao.insertBookmark(bookmark)
        val inserted = bookmarkDao.getBookmarkAtPosition(bookId, 0, 5, null)
        assertNotNull(inserted)

        bookmarkDao.deleteBookmark(inserted!!)
        val afterDelete = bookmarkDao.getBookmarksForBook(bookId).first()
        assertEquals(0, afterDelete.size)
    }

    @Test
    fun bookmarkDao_getUnsyncedBookmarks() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/bm_sync.epub", title = "BM Sync", format = "EPUB")
        ).toInt()

        bookmarkDao.insertBookmark(
            BookmarkEntity(bookId = bookId, pageNumber = 1, kavitaSynced = false)
        )
        bookmarkDao.insertBookmark(
            BookmarkEntity(bookId = bookId, pageNumber = 2, kavitaSynced = true)
        )
        bookmarkDao.insertBookmark(
            BookmarkEntity(bookId = bookId, pageNumber = 3, kavitaSynced = false)
        )

        val unsynced = bookmarkDao.getUnsyncedBookmarks()
        assertEquals(2, unsynced.size)
        assertTrue(unsynced.all { !it.kavitaSynced })
    }

    @Test
    fun bookmarkDao_countBookmarksAtPosition() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/bm_count.epub", title = "BM Count", format = "EPUB")
        ).toInt()

        bookmarkDao.insertBookmark(
            BookmarkEntity(bookId = bookId, chapterIndex = 1, pageNumber = 10, position = "pos1")
        )
        bookmarkDao.insertBookmark(
            BookmarkEntity(bookId = bookId, chapterIndex = 1, pageNumber = 10, position = "pos1")
        )
        bookmarkDao.insertBookmark(
            BookmarkEntity(bookId = bookId, chapterIndex = 1, pageNumber = 20, position = "pos2")
        )

        assertEquals(2, bookmarkDao.countBookmarksAtPosition(bookId, 1, 10, "pos1"))
        assertEquals(1, bookmarkDao.countBookmarksAtPosition(bookId, 1, 20, "pos2"))
        assertEquals(0, bookmarkDao.countBookmarksAtPosition(bookId, 1, 99, null))
    }

    @Test
    fun bookmarkDao_updateKavitaSync() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/bm_kavita.epub", title = "BM Kavita", format = "EPUB")
        ).toInt()

        val id = bookmarkDao.insertBookmark(
            BookmarkEntity(bookId = bookId, pageNumber = 5, kavitaSynced = false)
        ).toInt()

        bookmarkDao.updateKavitaSync(id, chapterId = 42, modifiedTime = 1234567890L)

        val synced = bookmarkDao.getKavitaSyncedBookmarkIds(bookId)
        assertEquals(1, synced.size)
        assertEquals(42, synced[0].kavitaChapterId)
        assertEquals(1234567890L, synced[0].modifiedTime)
    }

    // ==================== HighlightDao Tests ====================

    @Test
    fun highlightDao_insertAndRetrieve() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/hl.epub", title = "Highlight Test", format = "EPUB")
        ).toInt()

        val highlight = HighlightEntity(
            bookId = bookId,
            chapterIndex = 1,
            startPosition = "epubcfi(/4/2)",
            endPosition = "epubcfi(/4/2/10)",
            selectedText = "The quick brown fox",
            color = "#FFEB3B",
            note = "Important quote"
        )
        val id = highlightDao.insertHighlight(highlight)
        assertTrue(id > 0)

        val highlights = highlightDao.getHighlightsForBook(bookId).first()
        assertEquals(1, highlights.size)
        assertEquals("The quick brown fox", highlights[0].selectedText)
        assertEquals("#FFEB3B", highlights[0].color)
        assertEquals("Important quote", highlights[0].note)
    }

    @Test
    fun highlightDao_updateNote() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/hl_note.epub", title = "HL Note", format = "EPUB")
        ).toInt()

        val id = highlightDao.insertHighlight(
            HighlightEntity(
                bookId = bookId,
                selectedText = "text",
                color = "#FFEB3B",
                note = "original note"
            )
        ).toInt()

        highlightDao.updateNote(id, "updated note")

        val highlights = highlightDao.getHighlightsForBook(bookId).first()
        assertEquals("updated note", highlights[0].note)
    }

    @Test
    fun highlightDao_delete() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/hl_del.epub", title = "HL Delete", format = "EPUB")
        ).toInt()

        val id = highlightDao.insertHighlight(
            HighlightEntity(bookId = bookId, selectedText = "to delete", color = "#FF0000")
        ).toInt()

        val before = highlightDao.getHighlightsForBook(bookId).first()
        assertEquals(1, before.size)

        highlightDao.deleteHighlight(before[0])
        val after = highlightDao.getHighlightsForBook(bookId).first()
        assertEquals(0, after.size)
    }

    // ==================== CollectionDao Tests ====================

    @Test
    fun collectionDao_crud() = runBlocking {
        val collection = CollectionEntity(
            name = "Favorites",
            description = "My favorite books",
            sortOrder = 1
        )
        val id = collectionDao.insertCollection(collection)
        assertTrue(id > 0)

        val retrieved = collectionDao.getCollectionById(id.toInt())
        assertNotNull(retrieved)
        assertEquals("Favorites", retrieved!!.name)
        assertEquals("My favorite books", retrieved.description)
        assertEquals(1, retrieved.sortOrder)
    }

    @Test
    fun collectionDao_update() = runBlocking {
        val id = collectionDao.insertCollection(
            CollectionEntity(name = "Original", sortOrder = 0)
        ).toInt()

        val updated = collectionDao.getCollectionById(id.toInt())!!
        collectionDao.updateCollection(updated.copy(name = "Updated", sortOrder = 5))

        val retrieved = collectionDao.getCollectionById(id.toInt())
        assertEquals("Updated", retrieved!!.name)
        assertEquals(5, retrieved.sortOrder)
    }

    @Test
    fun collectionDao_delete() = runBlocking {
        val id = collectionDao.insertCollection(
            CollectionEntity(name = "To Delete")
        ).toInt()

        val before = collectionDao.getCollectionById(id.toInt())
        assertNotNull(before)

        collectionDao.deleteCollection(before!!)
        val after = collectionDao.getCollectionById(id.toInt())
        assertNull(after)
    }

    @Test
    fun collectionDao_getAllCollections() = runBlocking {
        collectionDao.insertCollection(CollectionEntity(name = "Alpha", sortOrder = 1))
        collectionDao.insertCollection(CollectionEntity(name = "Beta", sortOrder = 0))
        collectionDao.insertCollection(CollectionEntity(name = "Gamma", sortOrder = 2))

        val all = collectionDao.getAllCollections().first()
        assertEquals(3, all.size)
        // Should be ordered by sort_order
        assertEquals("Beta", all[0].name)
        assertEquals("Alpha", all[1].name)
        assertEquals("Gamma", all[2].name)
    }

    @Test
    fun collectionDao_addAndRemoveBook() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/col.epub", title = "Collection Book", format = "EPUB")
        ).toInt()
        val colId = collectionDao.insertCollection(
            CollectionEntity(name = "Test Collection")
        ).toInt()

        collectionDao.addBookToCollection(BookCollectionCrossRef(bookId, colId))

        val booksInCol = collectionDao.getBooksInCollection(colId).first()
        assertEquals(1, booksInCol.size)
        assertEquals("Collection Book", booksInCol[0].title)

        val count = collectionDao.getBookCountInCollection(colId)
        assertEquals(1, count)

        collectionDao.removeBookFromCollection(bookId, colId)
        val afterRemove = collectionDao.getBooksInCollection(colId).first()
        assertEquals(0, afterRemove.size)
    }

    @Test
    fun collectionDao_getCollectionCount() = runBlocking {
        assertEquals(0, collectionDao.getCollectionCount())

        collectionDao.insertCollection(CollectionEntity(name = "C1"))
        collectionDao.insertCollection(CollectionEntity(name = "C2"))

        assertEquals(2, collectionDao.getCollectionCount())
    }

    // ==================== ServerDao Tests ====================

    @Test
    fun serverDao_insertAndRetrieve() = runBlocking {
        val server = ServerEntity(
            name = "My Kavita",
            type = "KAVITA",
            url = "http://192.168.1.100:5000",
            username = "admin",
            password = "secret",
            apiKey = "kavita-api-key-123",
            isActive = true
        )
        val id = serverDao.insertServer(server)
        assertTrue(id > 0)

        val active = serverDao.getActiveServerByType("KAVITA")
        assertNotNull(active)
        assertEquals("My Kavita", active!!.name)
        assertEquals("http://192.168.1.100:5000", active.url)
        assertEquals("kavita-api-key-123", active.apiKey)
    }

    @Test
    fun serverDao_getActiveServerByType_inactiveNotReturned() = runBlocking {
        serverDao.insertServer(
            ServerEntity(
                name = "Inactive Kavita",
                type = "KAVITA",
                url = "http://old:5000",
                isActive = false
            )
        )
        serverDao.insertServer(
            ServerEntity(
                name = "Active Kavita",
                type = "KAVITA",
                url = "http://new:5000",
                isActive = true
            )
        )

        val active = serverDao.getActiveServerByType("KAVITA")
        assertNotNull(active)
        assertEquals("Active Kavita", active!!.name)
    }

    @Test
    fun serverDao_getActiveServers() = runBlocking {
        serverDao.insertServer(
            ServerEntity(name = "Kavita", type = "KAVITA", url = "http://k:5000", isActive = true)
        )
        serverDao.insertServer(
            ServerEntity(name = "Calibre", type = "CALIBRE", url = "http://c:8080", isActive = true)
        )
        serverDao.insertServer(
            ServerEntity(name = "Inactive", type = "KOMGA", url = "http://i:8080", isActive = false)
        )

        val active = serverDao.getActiveServers().first()
        assertEquals(2, active.size)
    }

    @Test
    fun serverDao_update() = runBlocking {
        val id = serverDao.insertServer(
            ServerEntity(name = "Original", type = "KAVITA", url = "http://old:5000", isActive = true)
        ).toInt()

        val server = serverDao.getActiveServerByType("KAVITA")!!
        serverDao.updateServer(server.copy(name = "Updated", url = "http://new:5000"))

        val updated = serverDao.getActiveServerByType("KAVITA")
        assertEquals("Updated", updated!!.name)
        assertEquals("http://new:5000", updated.url)
    }

    // ==================== OpdsCatalogDao Tests ====================

    @Test
    fun opdsCatalogDao_insertAndRetrieve() = runBlocking {
        val catalog = OpdsCatalogEntity(
            name = "Project Gutenberg",
            url = "https://www.gutenberg.org/catalog/",
            authType = "NONE",
            isActive = true
        )
        val id = opdsCatalogDao.insertCatalog(catalog)
        assertTrue(id > 0)

        val active = opdsCatalogDao.getActiveCatalogs().first()
        assertEquals(1, active.size)
        assertEquals("Project Gutenberg", active[0].name)
        assertEquals("https://www.gutenberg.org/catalog/", active[0].url)
    }

    @Test
    fun opdsCatalogDao_getActiveCatalogCount() = runBlocking {
        opdsCatalogDao.insertCatalog(
            OpdsCatalogEntity(name = "Active1", url = "http://a1.com", isActive = true)
        )
        opdsCatalogDao.insertCatalog(
            OpdsCatalogEntity(name = "Active2", url = "http://a2.com", isActive = true)
        )
        opdsCatalogDao.insertCatalog(
            OpdsCatalogEntity(name = "Inactive", url = "http://i.com", isActive = false)
        )

        assertEquals(2, opdsCatalogDao.getActiveCatalogCount())
    }

    @Test
    fun opdsCatalogDao_delete() = runBlocking {
        val id = opdsCatalogDao.insertCatalog(
            OpdsCatalogEntity(name = "To Delete", url = "http://del.com", isActive = true)
        ).toInt()

        val before = opdsCatalogDao.getActiveCatalogs().first()
        assertEquals(1, before.size)

        opdsCatalogDao.deleteCatalog(before[0])
        val after = opdsCatalogDao.getActiveCatalogs().first()
        assertEquals(0, after.size)
    }

    @Test
    fun opdsCatalogDao_withAuth() = runBlocking {
        val catalog = OpdsCatalogEntity(
            name = "Private Catalog",
            url = "http://private.com/feed",
            username = "user",
            password = "pass",
            token = "bearer-token",
            authType = "BASIC",
            isActive = true
        )
        opdsCatalogDao.insertCatalog(catalog)

        val active = opdsCatalogDao.getActiveCatalogs().first()
        assertEquals(1, active.size)
        assertEquals("user", active[0].username)
        assertEquals("pass", active[0].password)
        assertEquals("bearer-token", active[0].token)
        assertEquals("BASIC", active[0].authType)
    }

    // ==================== PdfSettingsDao Tests ====================

    @Test
    fun pdfSettingsDao_insertAndRetrieve() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/pdf_test.pdf", title = "PDF Test", format = "PDF")
        ).toInt()

        val settings = PdfSettingsEntity(
            bookId = bookId,
            cropLeft = 10,
            cropTop = 20,
            cropRight = 15,
            cropBottom = 25,
            autoDetected = true
        )
        pdfSettingsDao.saveSettings(settings)

        val retrieved = pdfSettingsDao.getSettingsForBook(bookId)
        assertNotNull(retrieved)
        assertEquals(10, retrieved!!.cropLeft)
        assertEquals(20, retrieved.cropTop)
        assertEquals(15, retrieved.cropRight)
        assertEquals(25, retrieved.cropBottom)
        assertEquals(true, retrieved.autoDetected)
    }

    @Test
    fun pdfSettingsDao_upsertReplaces() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/pdf_upsert.pdf", title = "PDF Upsert", format = "PDF")
        ).toInt()

        pdfSettingsDao.saveSettings(
            PdfSettingsEntity(bookId = bookId, cropLeft = 5, autoDetected = true)
        )
        pdfSettingsDao.saveSettings(
            PdfSettingsEntity(bookId = bookId, cropLeft = 50, autoDetected = false)
        )

        val retrieved = pdfSettingsDao.getSettingsForBook(bookId)
        assertEquals(50, retrieved!!.cropLeft)
        assertEquals(false, retrieved.autoDetected)
    }

    @Test
    fun pdfSettingsDao_delete() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/pdf_del.pdf", title = "PDF Delete", format = "PDF")
        ).toInt()

        pdfSettingsDao.saveSettings(PdfSettingsEntity(bookId = bookId))
        assertNotNull(pdfSettingsDao.getSettingsForBook(bookId))

        pdfSettingsDao.deleteSettingsForBook(bookId)
        assertNull(pdfSettingsDao.getSettingsForBook(bookId))
    }

    // ==================== ReadingSessionDao Tests ====================

    @Test
    fun readingSessionDao_insertAndRetrieve() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/session.epub", title = "Session Test", format = "EPUB")
        ).toInt()

        val session = ReadingSessionEntity(
            bookId = bookId,
            startTime = 1704067200000L,
            endTime = 1704070800000L,
            durationSeconds = 3600,
            pagesRead = 10,
            date = "2024-01-01"
        )
        val id = readingSessionDao.insertSession(session)
        assertTrue(id > 0)

        val all = readingSessionDao.getAllSessionsList()
        assertEquals(1, all.size)
        assertEquals(bookId, all[0].bookId)
        assertEquals(3600, all[0].durationSeconds)
        assertEquals(10, all[0].pagesRead)
        assertEquals("2024-01-01", all[0].date)
    }

    @Test
    fun readingSessionDao_getSessionsForBook() = runBlocking {
        val book1 = bookDao.insertBook(BookEntity(filePath = "/s1.epub", title = "S1", format = "EPUB")).toInt()
        val book2 = bookDao.insertBook(BookEntity(filePath = "/s2.epub", title = "S2", format = "EPUB")).toInt()

        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = book1, startTime = 1000, endTime = 2000, date = "2024-01-01")
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = book1, startTime = 3000, endTime = 4000, date = "2024-01-02")
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = book2, startTime = 5000, endTime = 6000, date = "2024-01-01")
        )

        val book1Sessions = readingSessionDao.getSessionsForBook(book1).first()
        assertEquals(2, book1Sessions.size)
    }

    @Test
    fun readingSessionDao_getSessionsForDate() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/sd.epub", title = "SD", format = "EPUB")
        ).toInt()

        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-03-15", startTime = 1000, endTime = 2000)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-03-15", startTime = 3000, endTime = 4000)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-03-16", startTime = 5000, endTime = 6000)
        )

        val march15 = readingSessionDao.getSessionsForDate("2024-03-15").first()
        assertEquals(2, march15.size)
        assertTrue(march15.all { it.date == "2024-03-15" })
    }

    @Test
    fun readingSessionDao_getSessionsBetweenDates() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/sbd.epub", title = "SBD", format = "EPUB")
        ).toInt()

        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-01-01", startTime = 1000, endTime = 2000)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-01-15", startTime = 3000, endTime = 4000)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-02-01", startTime = 5000, endTime = 6000)
        )

        val jan = readingSessionDao.getSessionsBetweenDates("2024-01-01", "2024-01-31").first()
        assertEquals(2, jan.size)
    }

    @Test
    fun readingSessionDao_getTotalReadingTimeBetween() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/trt.epub", title = "TRT", format = "EPUB")
        ).toInt()

        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-06-01", startTime = 1000, endTime = 2000, durationSeconds = 1800)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-06-02", startTime = 3000, endTime = 4000, durationSeconds = 3600)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-07-01", startTime = 5000, endTime = 6000, durationSeconds = 7200)
        )

        val juneTime = readingSessionDao.getTotalReadingTimeBetween("2024-06-01", "2024-06-30")
        assertEquals(5400L, juneTime)

        val julyTime = readingSessionDao.getTotalReadingTimeBetween("2024-07-01", "2024-07-31")
        assertEquals(7200L, julyTime)
    }

    @Test
    fun readingSessionDao_getTotalPagesReadBetween() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/tpr.epub", title = "TPR", format = "EPUB")
        ).toInt()

        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-04-01", startTime = 1000, endTime = 2000, pagesRead = 5)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-04-02", startTime = 3000, endTime = 4000, pagesRead = 12)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-05-01", startTime = 5000, endTime = 6000, pagesRead = 20)
        )

        val aprilPages = readingSessionDao.getTotalPagesReadBetween("2024-04-01", "2024-04-30")
        assertEquals(17, aprilPages)
    }

    @Test
    fun readingSessionDao_getSessionCountForDate() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/scfd.epub", title = "SCFD", format = "EPUB")
        ).toInt()

        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-08-01", startTime = 1000, endTime = 2000)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-08-01", startTime = 3000, endTime = 4000)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-08-02", startTime = 5000, endTime = 6000)
        )

        assertEquals(2, readingSessionDao.getSessionCountForDate("2024-08-01"))
        assertEquals(1, readingSessionDao.getSessionCountForDate("2024-08-02"))
        assertEquals(0, readingSessionDao.getSessionCountForDate("2024-08-03"))
    }

    @Test
    fun readingSessionDao_getTotalReadingDays() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/trd.epub", title = "TRD", format = "EPUB")
        ).toInt()

        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-01-01", startTime = 1000, endTime = 2000)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-01-01", startTime = 3000, endTime = 4000)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-01-03", startTime = 5000, endTime = 6000)
        )

        assertEquals(2, readingSessionDao.getTotalReadingDays())
    }

    @Test
    fun readingSessionDao_getDistinctDatesDesc() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/ddd.epub", title = "DDD", format = "EPUB")
        ).toInt()

        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-01-01", startTime = 1000, endTime = 2000)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-03-01", startTime = 3000, endTime = 4000)
        )
        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-02-01", startTime = 5000, endTime = 6000)
        )

        val dates = readingSessionDao.getDistinctDatesDesc()
        assertEquals(3, dates.size)
        assertEquals("2024-03-01", dates[0])
        assertEquals("2024-02-01", dates[1])
        assertEquals("2024-01-01", dates[2])
    }

    // ==================== Cross-Entity Tests ====================

    @Test
    fun cascadeDelete_bookRemovesProgress() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/cascade.epub", title = "Cascade Test", format = "EPUB")
        ).toInt()

        readingProgressDao.saveProgress(
            ReadingProgressEntity(bookId = bookId, pageNumber = 10)
        )
        assertNotNull(readingProgressDao.getProgressForBook(bookId))

        val book = bookDao.getBookById(bookId)!!
        bookDao.deleteBook(book)

        val progressAfter = readingProgressDao.getProgressForBook(bookId)
        assertNull("Progress should be deleted when book is deleted", progressAfter)
    }

    @Test
    fun cascadeDelete_bookRemovesBookmarks() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/cascade_bm.epub", title = "Cascade BM", format = "EPUB")
        ).toInt()

        bookmarkDao.insertBookmark(
            BookmarkEntity(bookId = bookId, pageNumber = 5, title = "Test")
        )
        assertEquals(1, bookmarkDao.getBookmarksForBook(bookId).first().size)

        val book = bookDao.getBookById(bookId)!!
        bookDao.deleteBook(book)

        val bookmarksAfter = bookmarkDao.getBookmarksForBook(bookId).first()
        assertEquals(0, bookmarksAfter.size)
    }

    @Test
    fun cascadeDelete_bookRemovesHighlights() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/cascade_hl.epub", title = "Cascade HL", format = "EPUB")
        ).toInt()

        highlightDao.insertHighlight(
            HighlightEntity(bookId = bookId, selectedText = "test", color = "#FF0000")
        )
        assertEquals(1, highlightDao.getHighlightsForBook(bookId).first().size)

        val book = bookDao.getBookById(bookId)!!
        bookDao.deleteBook(book)

        val highlightsAfter = highlightDao.getHighlightsForBook(bookId).first()
        assertEquals(0, highlightsAfter.size)
    }

    @Test
    fun cascadeDelete_bookRemovesReadingSessions() = runBlocking {
        val bookId = bookDao.insertBook(
            BookEntity(filePath = "/cascade_rs.epub", title = "Cascade RS", format = "EPUB")
        ).toInt()

        readingSessionDao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = "2024-01-01", startTime = 1000, endTime = 2000)
        )
        assertEquals(1, readingSessionDao.getSessionsForBook(bookId).first().size)

        val book = bookDao.getBookById(bookId)!!
        bookDao.deleteBook(book)

        val sessionsAfter = readingSessionDao.getSessionsForBook(bookId).first()
        assertEquals(0, sessionsAfter.size)
    }

    @Test
    fun fullBookLifecycle() = runBlocking {
        // 1. Insert book
        val bookId = bookDao.insertBook(
            BookEntity(
                filePath = "/lifecycle.epub",
                title = "Lifecycle Book",
                author = "Author",
                format = "EPUB",
                kavitaSeriesId = 100,
                kavitaLibraryId = 1
            )
        ).toInt()

        // 2. Add reading progress
        readingProgressDao.saveProgress(
            ReadingProgressEntity(
                bookId = bookId,
                chapterIndex = 5,
                pageNumber = 50,
                progressPercent = 25f
            )
        )

        // 3. Add bookmarks
        bookmarkDao.insertBookmark(
            BookmarkEntity(bookId = bookId, pageNumber = 10, title = "Chapter 1")
        )
        bookmarkDao.insertBookmark(
            BookmarkEntity(bookId = bookId, pageNumber = 50, title = "Current")
        )

        // 4. Add highlights
        highlightDao.insertHighlight(
            HighlightEntity(
                bookId = bookId,
                selectedText = "A memorable quote",
                color = "#FFEB3B",
                note = "Important"
            )
        )

        // 5. Add reading sessions
        readingSessionDao.insertSession(
            ReadingSessionEntity(
                bookId = bookId,
                date = "2024-06-01",
                startTime = 1000,
                endTime = 5000,
                durationSeconds = 3600,
                pagesRead = 10
            )
        )

        // 6. Add to collection
        val colId = collectionDao.insertCollection(
            CollectionEntity(name = "Reading")
        ).toInt()
        collectionDao.addBookToCollection(BookCollectionCrossRef(bookId, colId))

        // 7. Add PDF settings (even though it's EPUB, testing the DAO)
        pdfSettingsDao.saveSettings(
            PdfSettingsEntity(bookId = bookId, autoDetected = false)
        )

        // Verify everything
        assertNotNull(bookDao.getBookById(bookId))
        assertNotNull(readingProgressDao.getProgressForBook(bookId))
        assertEquals(2, bookmarkDao.getBookmarksForBook(bookId).first().size)
        assertEquals(1, highlightDao.getHighlightsForBook(bookId).first().size)
        assertEquals(1, readingSessionDao.getSessionsForBook(bookId).first().size)
        assertEquals(1, collectionDao.getBooksInCollection(colId).first().size)
        assertNotNull(pdfSettingsDao.getSettingsForBook(bookId))

        // 8. Delete book cascades
        val book = bookDao.getBookById(bookId)!!
        bookDao.deleteBook(book)

        // Verify cascaded deletes
        assertNull(bookDao.getBookById(bookId))
        assertNull(readingProgressDao.getProgressForBook(bookId))
        assertEquals(0, bookmarkDao.getBookmarksForBook(bookId).first().size)
        assertEquals(0, highlightDao.getHighlightsForBook(bookId).first().size)
        assertEquals(0, readingSessionDao.getSessionsForBook(bookId).first().size)
        assertNull(pdfSettingsDao.getSettingsForBook(bookId))
        // Collection should still exist, just without the book
        assertNotNull(collectionDao.getCollectionById(colId))
        assertEquals(0, collectionDao.getBooksInCollection(colId).first().size)
    }
}
