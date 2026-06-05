package com.mimiral.app.performance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.ChapterDao
import com.mimiral.app.data.local.dao.CollectionDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.database.MimiralDatabase
import com.mimiral.app.data.local.entity.BookCollectionCrossRef
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.ChapterEntity
import com.mimiral.app.data.local.entity.CollectionEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Performance test: large library with 10,000+ books.
 *
 * Seeds an in-memory Room database with 10,000 books and measures
 * query/insert/update/delete latency for the most common library operations.
 *
 * Runs as an Android instrumented test (androidTest) to get real SQLite + FTS5
 * behavior on an actual Android runtime.
 *
 * NOTE: allowMainThreadQueries is used for simplicity. On a real device the
 * DAO calls would be on Dispatchers.IO, but the relative timings are still
 * meaningful for comparing query performance.
 */
@RunWith(AndroidJUnit4::class)
class LibraryPerformanceTest {

    private lateinit var db: MimiralDatabase
    private lateinit var bookDao: BookDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var collectionDao: CollectionDao
    private lateinit var progressDao: ReadingProgressDao

    private val bookCount = 10_000
    private val report = StringBuilder()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MimiralDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookDao = db.bookDao()
        chapterDao = db.chapterDao()
        collectionDao = db.collectionDao()
        progressDao = db.readingProgressDao()

        report.appendLine("=".repeat(72))
        report.appendLine("Mimiral Library Performance Test")
        report.appendLine("Library size: $bookCount books")
        report.appendLine("Device: Android emulator (Medium_Phone_API_36.1)")
        report.appendLine("=".repeat(72))
        report.appendLine()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---- Lightweight book generation (avoids holding 10K full objects in memory) ----

    private fun makeBook(i: Int): BookEntity {
        val formats = listOf("EPUB", "PDF", "CBZ", "MOBI", "TXT", "FB2")
        val authors = listOf(
            "Jane Austen", "Charles Dickens", "Mark Twain", "Leo Tolstoy",
            "Fyodor Dostoevsky", "Victor Hugo", "Ernest Hemingway",
            "Virginia Woolf", "George Orwell", "Oscar Wilde",
            "Edgar Allan Poe", "H.G. Wells", "Arthur Conan Doyle",
            "Agatha Christie", "J.R.R. Tolkien", "C.S. Lewis",
            "Ray Bradbury", "Isaac Asimov", "Philip K. Dick", "Ursula Le Guin"
        )
        val prefixes = listOf(
            "The", "A", "My", "Dark", "Light", "Silent", "Lost", "Hidden",
            "Secret", "Last", "First", "Great", "Our", "Their", "An"
        )
        val nouns = listOf(
            "Garden", "Mountain", "River", "Forest", "Ocean", "City",
            "Tower", "Castle", "Road", "Journey", "Dream", "Shadow",
            "Storm", "Fire", "Wind", "Star", "Moon", "Sun", "Night", "Dawn"
        )
        val suffixes = listOf(
            "of Hope", "of Doom", "of Light", "of Darkness", "of Time",
            "of Memory", "of Silence", "of Dreams", "of Fire", "of Ice",
            "of the North", "of the Sea", "of the World", "of Eternity", ""
        )

        val prefix = prefixes[i % prefixes.size]
        val noun = nouns[(i / prefixes.size) % nouns.size]
        val suffix = suffixes[(i / (prefixes.size * nouns.size)) % suffixes.size]

        return BookEntity(
            id = i,
            filePath = "/storage/books/book_$i.${formats[i % formats.size].lowercase()}",
            title = "$prefix $noun $suffix ($i)",
            author = authors[i % authors.size],
            format = formats[i % formats.size],
            fileSize = 100_000L + (i * 137L) % 10_000_000L,
            dateAdded = System.currentTimeMillis() - (i * 3600_000L),
            dateModified = System.currentTimeMillis() - (i * 1800_000L),
            isDownloaded = i % 5 != 0,
            source = if (i % 5 == 0) "KAVITA" else "LOCAL",
            sourceId = if (i % 5 == 0) "kavita_$i" else null,
            kavitaSeriesId = if (i % 5 == 0) i else null,
            kavitaLibraryId = if (i % 5 == 0) (i % 3) + 1 else null
        )
    }

    // ---- Timing helper ----

    private inline fun <T> measureMs(label: String, thresholdMs: Long, block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val elapsed = System.currentTimeMillis() - start
        val status = if (elapsed <= thresholdMs) "PASS" else "FAIL"
        report.appendLine(String.format("  [%s] %s: %d ms (threshold: %d ms)", status, label, elapsed, thresholdMs))
        assertTrue(
            "$label took $elapsed ms, exceeding threshold of $thresholdMs ms",
            elapsed <= thresholdMs
        )
        return result
    }

    // =========================================================================
    // TEST 1: Full library benchmark (bulk insert 10K + all query types)
    // =========================================================================

    @Test
    fun performanceTest_fullLibraryBenchmark() = runBlocking {
        // -- Phase 1: Bulk insert 10K books in batches of 500 --
        // Using individual inserts (the production path) but batched to avoid OOM.
        report.appendLine("--- Phase 1: Bulk Insert (10,000 books, batch 500) ---")

        val BATCH_SIZE = 500
        var inserted = 0
        val insertStart = System.currentTimeMillis()

        for (batchStart in 1..bookCount step BATCH_SIZE) {
            val batchEnd = minOf(batchStart + BATCH_SIZE - 1, bookCount)
            for (i in batchStart..batchEnd) {
                bookDao.insertBook(makeBook(i))
            }
            inserted += (batchEnd - batchStart + 1)
        }

        val insertTime = System.currentTimeMillis() - insertStart
        report.appendLine(String.format("  Inserted %d books in %d ms (%.1f books/sec)",
            inserted, insertTime, inserted * 1000.0 / insertTime))
        // Threshold: 30s for 10K individual inserts on emulator
        assertTrue("Bulk insert took $insertTime ms, exceeding 60s threshold", insertTime < 60_000L)

        report.appendLine()
        report.appendLine("--- Phase 2: Count Operations ---")

        measureMs("Count all books", 100L) {
            val count = bookDao.getBookCount()
            assertTrue("Expected $bookCount books, got $count", count == bookCount)
        }

        report.appendLine()
        report.appendLine("--- Phase 3: Read Operations ---")

        measureMs("Get all books (first Flow emit)", 2000L) {
            val allBooks = bookDao.getAllBooks().first()
            assertTrue("Expected $bookCount books, got ${allBooks.size}", allBooks.size == bookCount)
        }

        measureMs("Search books LIKE '%Garden%'", 1000L) {
            val results = bookDao.searchBooks("Garden").first()
            assertTrue("Expected search results, got ${results.size}", results.size > 0)
        }

        measureMs("Search books LIKE '%zzzznonexistent%' (no results)", 1000L) {
            val results = bookDao.searchBooks("zzzznonexistent").first()
            assertTrue("Expected no results, got ${results.size}", results.isEmpty())
        }

        measureMs("Sort all books by title ASC", 2000L) {
            val sorted = bookDao.getAllBooksSortedByTitle().first()
            assertTrue("Expected $bookCount sorted books", sorted.size == bookCount)
        }

        measureMs("Sort all books by author ASC", 2000L) {
            val sorted = bookDao.getAllBooksSortedByAuthor().first()
            assertTrue("Expected $bookCount sorted books", sorted.size == bookCount)
        }

        measureMs("Sort all by date_added DESC", 2000L) {
            val sorted = bookDao.getAllBooksSortedByDateAdded().first()
            assertTrue("Expected $bookCount sorted books", sorted.size == bookCount)
        }

        // Seed reading progress first so the JOIN in sort-by-recent has data
        // (We'll do this properly in Phase 9, but we need some data for the JOIN)
        measureMs("Sort all by recent (JOIN with reading_progress) - empty progress", 2000L) {
            val sorted = bookDao.getAllBooksSortedByRecent().first()
            assertTrue("Expected $bookCount sorted books", sorted.size == bookCount)
        }

        report.appendLine()
        report.appendLine("--- Phase 4: Single-Book CRUD ---")

        measureMs("Get book by ID (#5000)", 50L) {
            val book = bookDao.getBookById(5000)
            assertTrue("Book #5000 should exist", book != null)
        }

        val newBook = BookEntity(
            id = 0, // auto-generate
            filePath = "/storage/books/perf_test_book.epub",
            title = "Performance Test Book",
            author = "Benchmark",
            format = "EPUB",
            fileSize = 1_000_000L
        )
        measureMs("Insert single book", 50L) {
            val id = bookDao.insertBook(newBook)
            assertTrue("Insert should return positive ID", id > 0)
        }

        measureMs("Update single book", 50L) {
            bookDao.updateBook(newBook.copy(title = "Updated Performance Test Book"))
        }

        measureMs("Delete single book", 50L) {
            val toDelete = bookDao.getBookByFilePath("/storage/books/perf_test_book.epub")
            if (toDelete != null) {
                bookDao.deleteBook(toDelete)
                assertTrue("Deleted book should not exist", bookDao.getBookById(toDelete.id) == null)
            }
        }

        report.appendLine()
        report.appendLine("--- Phase 5: Search + Sort Combinations ---")

        measureMs("Search 'The' sorted by title ASC", 2000L) {
            val results = bookDao.searchBooksSortedByTitle("The").first()
            assertTrue("Expected search+sort results", results.size > 0)
        }

        measureMs("Search 'The' sorted by recent (JOIN)", 2000L) {
            val results = bookDao.searchBooksSortedByRecent("The").first()
            assertTrue("Expected search+sort results", results.size > 0)
        }

        report.appendLine()
        report.appendLine("--- Phase 6: File Path Lookup ---")

        measureMs("Get all file paths", 2000L) {
            val paths = bookDao.getAllFilePaths()
            assertTrue("Expected $bookCount paths, got ${paths.size}", paths.size == bookCount)
        }

        report.appendLine()
        report.appendLine("--- Phase 7: Chapter Operations (FTS5) ---")
        // Seed chapters for 1000 books (5 chapters each = 5000 chapters)

        val chapterBatchSize = 500
        val totalChapters = 5000
        val chaptersPerBook = 5
        val booksWithChapters = totalChapters / chaptersPerBook // 1000

        measureMs("Bulk insert $totalChapters chapters (5 per book, 1000 books)", 30_000L) {
            for (batchStart in 1..booksWithChapters step chapterBatchSize / chaptersPerBook) {
                val batchEnd = minOf(batchStart + chapterBatchSize / chaptersPerBook - 1, booksWithChapters)
                val batch = mutableListOf<ChapterEntity>()
                var chId = (batchStart - 1) * chaptersPerBook + 1
                for (bookId in batchStart..batchEnd) {
                    for (chIdx in 0 until chaptersPerBook) {
                        batch.add(
                            ChapterEntity(
                                id = chId++,
                                bookId = bookId,
                                chapterIndex = chIdx,
                                title = "Chapter ${chIdx + 1}",
                                content = "Chapter ${chIdx + 1} content for book $bookId. " +
                                    "The quick brown fox jumps over the lazy dog. " +
                                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
                            )
                        )
                    }
                }
                chapterDao.insertChapters(batch)
            }
        }

        measureMs("FTS5 search 'quick brown fox' (across 5000 chapters)", 500L) {
            val results = chapterDao.searchChapters("quick brown fox").first()
            assertTrue("Expected FTS results, got ${results.size}", results.size > 0)
        }

        measureMs("FTS5 search in book #1 ('Lorem ipsum')", 200L) {
            val results = chapterDao.searchChaptersInBook(1, "Lorem ipsum").first()
            assertTrue("Expected FTS results in book", results.size > 0)
        }

        measureMs("Get chapters for book #1", 50L) {
            val results = chapterDao.getChaptersForBook(1).first()
            assertTrue("Expected 5 chapters", results.size == 5)
        }

        report.appendLine()
        report.appendLine("--- Phase 8: Collection Operations ---")

        val collections = (1..10).map { i ->
            CollectionEntity(id = i, name = "Collection $i", sortOrder = i)
        }

        measureMs("Insert 10 collections", 500L) {
            collections.forEach { collectionDao.insertCollection(it) }
        }

        measureMs("Add 1000 books to collections (100 per collection)", 5000L) {
            for (i in 1..1000) {
                collectionDao.addBookToCollection(
                    BookCollectionCrossRef(bookId = i, collectionId = (i % 10) + 1)
                )
            }
        }

        measureMs("Get books in collection #1 (JOIN)", 200L) {
            val books = collectionDao.getBooksInCollection(1).first()
            assertTrue("Expected books in collection", books.size > 0)
        }

        measureMs("Count books in collection #1", 100L) {
            val count = collectionDao.getBookCountInCollection(1)
            assertTrue("Expected > 0 books", count > 0)
        }

        measureMs("Get all collections", 100L) {
            val all = collectionDao.getAllCollections().first()
            assertTrue("Expected 10 collections", all.size == 10)
        }

        report.appendLine()
        report.appendLine("--- Phase 9: Reading Progress Operations ---")

        measureMs("Bulk insert 5000 reading progress entries", 30_000L) {
            for (batchStart in 1..5000 step 500) {
                val batchEnd = minOf(batchStart + 499, 5000)
                for (i in batchStart..batchEnd) {
                    progressDao.saveProgress(
                        ReadingProgressEntity(
                            id = i,
                            bookId = i,
                            chapterIndex = 0,
                            characterOffset = i * 1000L,
                            totalCharacters = 50000L,
                            pageNumber = i % 300,
                            totalPages = 300,
                            progressPercent = (i % 100).toFloat(),
                            lastReadTime = System.currentTimeMillis() - (i * 60_000L),
                            totalTimeRead = i * 3600L,
                            kavitaSynced = i % 2 == 0
                        )
                    )
                }
            }
        }

        measureMs("Get recent progress (LIMIT 10)", 100L) {
            val recent = progressDao.getRecentProgress().first()
            assertTrue("Expected 10 recent entries", recent.size == 10)
        }

        measureMs("Get progress for book #2500", 50L) {
            val progress = progressDao.getProgressForBook(2500)
            assertTrue("Progress should exist", progress != null)
        }

        // Now re-test sort-by-refer with progress data
        measureMs("Sort all by recent (JOIN with 5000 progress entries)", 2000L) {
            val sorted = bookDao.getAllBooksSortedByRecent().first()
            assertTrue("Expected $bookCount sorted books", sorted.size == bookCount)
        }

        report.appendLine()
        report.appendLine("=".repeat(72))
        report.appendLine("Performance test complete.")
        report.appendLine("=".repeat(72))
        println(report.toString())
    }

    // ---- Data class for search results ----
    private data class SearchResult(val term: String, val description: String, val count: Int, val timeMs: Long)

    // =========================================================================
    // TEST 2: Search performance at scale (different search selectivity)
    // =========================================================================

    @Test
    fun performanceTest_searchPerformanceAtScale() = runBlocking {
        // Seed 10K books in batches
        for (batchStart in 1..bookCount step 500) {
            val batchEnd = minOf(batchStart + 499, bookCount)
            for (i in batchStart..batchEnd) {
                bookDao.insertBook(makeBook(i))
            }
        }

        report.appendLine("--- Search Performance at Scale ($bookCount books) ---")

        val results = listOf(
            measureSearch("The", "common word"),
            measureSearch("Garden", "medium selectivity"),
            measureSearch("zzzznonexistent", "no results"),
            measureSearch("of", "very common"),
            measureSearch("Dark Tower", "multi-word")
        )

        for (r in results) {
            report.appendLine(String.format("  Search '%s' (%s): %d results in %d ms",
                r.term, r.description, r.count, r.timeMs))
        }

        val sortedStart = System.currentTimeMillis()
        val sortedResults = bookDao.searchBooksSortedByTitle("The").first()
        val sortedElapsed = System.currentTimeMillis() - sortedStart
        report.appendLine(String.format(
            "  Search 'The' + sort by title: %d results in %d ms",
            sortedResults.size, sortedElapsed
        ))

        println(report.toString())
    }

    private fun measureSearch(term: String, description: String): SearchResult {
        val start = System.currentTimeMillis()
        val results = runBlocking { bookDao.searchBooks(term).first() }
        val elapsed = System.currentTimeMillis() - start
        return SearchResult(term, description, results.size, elapsed)
    }

    // =========================================================================
    // TEST 3: Batch vs individual insert comparison
    // =========================================================================

    @Test
    fun performanceTest_insertBatchVsIndividual() = runBlocking {
        report.appendLine("--- Insert Strategy Comparison ---")

        // Individual book inserts (1000 books)
        val freshContext = ApplicationProvider.getApplicationContext<Context>()
        db.close()
        db = Room.inMemoryDatabaseBuilder(freshContext, MimiralDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val freshBookDao = db.bookDao()
        val freshChapterDao = db.chapterDao()

        val indStart = System.currentTimeMillis()
        for (i in 1..1000) {
            freshBookDao.insertBook(makeBook(i))
        }
        val indTime = System.currentTimeMillis() - indStart
        report.appendLine("  Individual insertBook (1000 books): $indTime ms")

        // Batch chapter inserts vs individual
        val chapters = mutableListOf<ChapterEntity>()
        var chId = 1
        for (bookId in 1..200) {
            for (chIdx in 0 until 5) {
                chapters.add(
                    ChapterEntity(id = chId++, bookId = bookId, chapterIndex = chIdx,
                        title = "Ch ${chIdx + 1}", content = "Chapter content for FTS testing.")
                )
            }
        }

        val batchStart = System.currentTimeMillis()
        freshChapterDao.insertChapters(chapters)
        val batchTime = System.currentTimeMillis() - batchStart
        report.appendLine("  Batch insertChapters (1000 chapters): $batchTime ms")

        // Individual chapter inserts
        val chapters2 = mutableListOf<ChapterEntity>()
        chId = 1001
        for (bookId in 201..400) {
            for (chIdx in 0 until 5) {
                chapters2.add(
                    ChapterEntity(id = chId++, bookId = bookId, chapterIndex = chIdx,
                        title = "Ch ${chIdx + 1}", content = "Chapter content for FTS testing.")
                )
            }
        }

        val indChStart = System.currentTimeMillis()
        chapters2.forEach { freshChapterDao.insertChapter(it) }
        val indChTime = System.currentTimeMillis() - indChStart
        report.appendLine("  Individual insertChapter (1000 chapters): $indChTime ms")
        report.appendLine()

        if (batchTime < indChTime) {
            report.appendLine("  >> Batch insert is ${indChTime - batchTime} ms faster for chapters")
            report.appendLine("  >> Speedup: ${"%.1f".format(indChTime.toFloat() / batchTime)}x")
        } else {
            report.appendLine("  >> Individual insert was faster (unexpected)")
        }

        println(report.toString())
    }
}
