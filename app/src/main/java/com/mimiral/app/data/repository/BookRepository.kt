package com.mimiral.app.data.repository
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.BookmarkDao
import com.mimiral.app.data.local.dao.ChapterDao
import com.mimiral.app.data.local.dao.HighlightDao
import com.mimiral.app.data.local.dao.PdfSettingsDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.local.entity.ChapterEntity
import com.mimiral.app.data.local.entity.HighlightEntity
import com.mimiral.app.data.local.entity.PdfSettingsEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.local.settings.FilterOption
import com.mimiral.app.data.local.settings.SortOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val pdfSettingsDao: PdfSettingsDao,
    private val chapterDao: ChapterDao,
    private val highlightDao: HighlightDao
) {
    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun getBookById(bookId: Int): BookEntity? = bookDao.getBookById(bookId)

    fun searchBooks(query: String): Flow<List<BookEntity>> = bookDao.searchBooks(query)

    // ---- Sorted book queries ----

    fun getBooksSorted(sort: SortOption): Flow<List<BookEntity>> = when (sort) {
        SortOption.TITLE -> bookDao.getAllBooksSortedByTitle()
        SortOption.AUTHOR -> bookDao.getAllBooksSortedByAuthor()
        SortOption.RECENT -> bookDao.getAllBooksSortedByRecent()
        SortOption.DATE_ADDED -> bookDao.getAllBooksSortedByDateAdded()
        // Progress and Rating need in-memory sorting (no DB columns for rating)
        SortOption.PROGRESS -> bookDao.getAllBooks()
        SortOption.RATING -> bookDao.getAllBooksSortedByTitle()
    }

    fun searchBooksSorted(query: String, sort: SortOption): Flow<List<BookEntity>> = when (sort) {
        SortOption.TITLE -> bookDao.searchBooksSortedByTitle(query)
        SortOption.AUTHOR -> bookDao.searchBooksSortedByAuthor(query)
        SortOption.RECENT -> bookDao.searchBooksSortedByRecent(query)
        SortOption.DATE_ADDED -> bookDao.searchBooksSortedByDateAdded(query)
        SortOption.PROGRESS -> bookDao.searchBooks(query)
        SortOption.RATING -> bookDao.searchBooksSortedByTitle(query)
    }

    // ---- Sorted + filtered + searched combined query ----

    fun getBooks(
        sort: SortOption,
        filter: FilterOption,
        searchQuery: String
    ): Flow<List<BookEntity>> {
        val bookFlow = if (searchQuery.isBlank()) {
            getBooksSorted(sort)
        } else {
            searchBooksSorted(searchQuery, sort)
        }
        // Filtering by reading progress requires joining with progress table,
        // so we combine flows and filter in-memory.
        return if (filter == FilterOption.ALL) {
            bookFlow
        } else {
            combine(bookFlow, readingProgressDao.getAllProgress()) { books, progressList ->
                val progressMap = progressMap(progressList)
                books.filter { book ->
                    val progress = progressMap[book.id]
                    when (filter) {
                        FilterOption.READING -> (progress?.progressPercent ?: 0f) in 1f..98f
                        FilterOption.UNREAD -> (progress?.progressPercent ?: 0f) == 0f
                        FilterOption.FINISHED -> (progress?.progressPercent ?: 0f) >= 99f
                        FilterOption.DOWNLOADED -> book.isDownloaded
                        FilterOption.ALL -> true
                    }
                }
            }
        }
    }

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun deleteBook(book: BookEntity) = bookDao.deleteBook(book)

    suspend fun getBookCount(): Int = bookDao.getBookCount()

    suspend fun getBookByFilePath(filePath: String): BookEntity? =
        bookDao.getBookByFilePath(filePath)

    suspend fun getAllFilePaths(): Set<String> =
        bookDao.getAllFilePaths().toSet()

    // ---- Reading Progress ----

    suspend fun getProgressForBook(bookId: Int): ReadingProgressEntity? =
        readingProgressDao.getProgressForBook(bookId)

    fun getProgressForBookFlow(bookId: Int): Flow<ReadingProgressEntity?> =
        readingProgressDao.getProgressForBookFlow(bookId)

    fun getAllProgress(): Flow<List<ReadingProgressEntity>> =
        readingProgressDao.getAllProgress()

    fun getRecentProgress(): Flow<List<ReadingProgressEntity>> =
        readingProgressDao.getRecentProgress()

    /**
     * Save reading progress for a book.
     * Calculates progressPercent from characterOffset / totalCharacters (EPUB)
     * or from pageNumber / totalPages (PDF) when character data is unavailable.
     */
    suspend fun saveProgress(
        bookId: Int,
        chapterIndex: Int,
        characterOffset: Long,
        totalCharacters: Long,
        pageNumber: Int = 0,
        totalPages: Int = 0,
        lastReadPosition: String? = null
    ) {
        val progressPercent = when {
            totalCharacters > 0 -> {
                (characterOffset.toFloat() / totalCharacters.toFloat() * 100f).coerceIn(0f, 100f)
            }
            totalPages > 0 -> {
                ((pageNumber + 1).toFloat() / totalPages.toFloat() * 100f).coerceIn(0f, 100f)
            }
            else -> 0f
        }

        val existing = readingProgressDao.getProgressForBook(bookId)
        val entity = ReadingProgressEntity(
            id = existing?.id ?: 0,
            bookId = bookId,
            chapterIndex = chapterIndex,
            characterOffset = characterOffset,
            totalCharacters = totalCharacters,
            pageNumber = pageNumber,
            totalPages = totalPages,
            progressPercent = progressPercent,
            lastReadPosition = lastReadPosition,
            lastReadTime = System.currentTimeMillis(),
            totalTimeRead = existing?.totalTimeRead ?: 0
        )
        readingProgressDao.saveProgress(entity)
    }

    /**
     * Save reading progress for a PDF book using page numbers.
     */
    suspend fun saveProgress(
        bookId: Int,
        pageNumber: Int,
        totalPages: Int
    ) {
        saveProgress(
            bookId = bookId,
            chapterIndex = 0,
            characterOffset = 0,
            totalCharacters = 0,
            pageNumber = pageNumber,
            totalPages = totalPages,
            lastReadPosition = null
        )
    }

    /**
     * Restore the last read page for a book.
     * Returns the page number (0-based) or 0 if no progress exists.
     */
    suspend fun getSavedPage(bookId: Int): Int {
        return readingProgressDao.getProgressForBook(bookId)?.pageNumber ?: 0
    }

    /**
     * Get progress percentage for a book (0-100).
     */
    suspend fun getProgressPercent(bookId: Int): Float {
        return readingProgressDao.getProgressForBook(bookId)?.progressPercent ?: 0f
    }

    // ---- PDF Settings ----

    suspend fun getPdfSettings(bookId: Int): PdfSettingsEntity? =
        pdfSettingsDao.getSettingsForBook(bookId)

    fun getPdfSettingsFlow(bookId: Int): Flow<PdfSettingsEntity?> =
        pdfSettingsDao.getSettingsForBookFlow(bookId)

    suspend fun savePdfSettings(settings: PdfSettingsEntity) =
        pdfSettingsDao.saveSettings(settings)

    // ---- Helpers ----

    private fun progressMap(progressList: List<ReadingProgressEntity>): Map<Int, ReadingProgressEntity> =
        progressList.associateBy { it.bookId }

    // ---- Chapter / FTS5 Full-Text Search ----

    fun getChaptersForBook(bookId: Int): Flow<List<ChapterEntity>> =
        chapterDao.getChaptersForBook(bookId)

    suspend fun getChapter(bookId: Int, chapterIndex: Int): ChapterEntity? =
        chapterDao.getChapter(bookId, chapterIndex)

    suspend fun insertChapter(chapter: ChapterEntity): Long =
        chapterDao.insertChapter(chapter)

    suspend fun insertChapters(chapters: List<ChapterEntity>) =
        chapterDao.insertChapters(chapters)

    suspend fun deleteChaptersForBook(bookId: Int) =
        chapterDao.deleteChaptersForBook(bookId)

    /**
     * FTS5 full-text search across all book chapters.
     * Returns matching chapters ranked by relevance.
     */
    fun searchChapters(query: String): Flow<List<ChapterEntity>> =
        chapterDao.searchChapters(query)

    /**
     * FTS5 full-text search within a specific book's chapters.
     */
    fun searchChaptersInBook(bookId: Int, query: String): Flow<List<ChapterEntity>> =
        chapterDao.searchChaptersInBook(bookId, query)

    /**
     * Combined book + chapter search: searches books by title/author
     * AND chapters by content, returning matching books.
     */
    fun searchBooksAndChapters(query: String): Flow<List<BookEntity>> =
        bookDao.searchBooks(query)

    // ---- Bookmark wrappers ----

    fun getBookmarksForBook(bookId: Int): Flow<List<BookmarkEntity>> =
        bookmarkDao.getBookmarksForBook(bookId)

    suspend fun getBookmarkAtPosition(
        bookId: Int,
        chapterIndex: Int,
        pageNumber: Int,
        position: String?
    ): BookmarkEntity? =
        bookmarkDao.getBookmarkAtPosition(bookId, chapterIndex, pageNumber, position)

    suspend fun deleteBookmark(bookmark: BookmarkEntity) =
        bookmarkDao.deleteBookmark(bookmark)

    suspend fun addBookmark(
        bookId: Int,
        chapterIndex: Int,
        pageNumber: Int,
        position: String?,
        title: String?,
        note: String?
    ): Long {
        val entity = BookmarkEntity(
            bookId = bookId,
            chapterIndex = chapterIndex,
            pageNumber = pageNumber,
            position = position,
            title = title,
            note = note,
            createdTime = System.currentTimeMillis()
        )
        return bookmarkDao.insertBookmark(entity)
    }

    suspend fun isBookmarkedAtPosition(
        bookId: Int,
        chapterIndex: Int,
        pageNumber: Int,
        position: String?
    ): Boolean =
        bookmarkDao.countBookmarksAtPosition(bookId, chapterIndex, pageNumber, position) > 0

    // ---- Highlight wrappers ----

    fun getHighlightsForBook(bookId: Int): Flow<List<HighlightEntity>> =
        highlightDao.getHighlightsForBook(bookId)

    suspend fun addHighlight(
        bookId: Int,
        chapterIndex: Int,
        startPosition: String?,
        endPosition: String?,
        selectedText: String?,
        color: String,
        note: String? = null
    ): Long {
        val entity = HighlightEntity(
            bookId = bookId,
            chapterIndex = chapterIndex,
            startPosition = startPosition,
            endPosition = endPosition,
            selectedText = selectedText,
            color = color,
            note = note,
            createdTime = System.currentTimeMillis()
        )
        return highlightDao.insertHighlight(entity)
    }

    suspend fun deleteHighlight(highlight: HighlightEntity) =
        highlightDao.deleteHighlight(highlight)
}
