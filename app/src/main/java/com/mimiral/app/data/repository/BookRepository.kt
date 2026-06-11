package com.mimiral.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.BookmarkDao
import com.mimiral.app.data.local.dao.ChapterDao
import com.mimiral.app.data.local.dao.HighlightDao
import com.mimiral.app.data.local.dao.PdfSettingsDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.dao.ReadingSessionDao
import com.mimiral.app.data.local.dao.ReadingTimeDao
import com.mimiral.app.data.local.dao.TagDao
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.BookTagCrossRef
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.local.entity.ChapterEntity
import com.mimiral.app.data.local.entity.HighlightEntity
import com.mimiral.app.data.local.entity.PdfSettingsEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.local.entity.ReadingSessionEntity
import com.mimiral.app.data.local.entity.TagEntity
import com.mimiral.app.data.local.scanner.FileScanner
import com.mimiral.app.data.local.settings.FilterOption
import com.mimiral.app.data.local.settings.SortOption
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val readingTimeDao: ReadingTimeDao,
    private val bookmarkDao: BookmarkDao,
    private val pdfSettingsDao: PdfSettingsDao,
    private val chapterDao: ChapterDao,
    private val highlightDao: HighlightDao,
    private val fileScanner: FileScanner,
    private val tagDao: TagDao,
    private val readingSessionDao: ReadingSessionDao
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
        SortOption.SERIES -> bookDao.getAllBooksSortedBySeries()
        // Progress and Rating need in-memory sorting (no DB columns for rating)
        SortOption.PROGRESS -> bookDao.getAllBooks()
        SortOption.RATING -> bookDao.getAllBooksSortedByTitle()
    }

    fun searchBooksSorted(query: String, sort: SortOption): Flow<List<BookEntity>> = when (sort) {
        SortOption.TITLE -> bookDao.searchBooksSortedByTitle(query)
        SortOption.AUTHOR -> bookDao.searchBooksSortedByAuthor(query)
        SortOption.RECENT -> bookDao.searchBooksSortedByRecent(query)
        SortOption.DATE_ADDED -> bookDao.searchBooksSortedByDateAdded(query)
        SortOption.SERIES -> bookDao.searchBooksSortedBySeries(query)
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
     * Mark a book as completed.
     * Sets completed_at timestamp and increments times_completed.
     * If no progress row exists, creates one with 100% progress.
     */
    suspend fun markCompleted(bookId: Int) {
        val existing = readingProgressDao.getProgressForBook(bookId)
        if (existing == null) {
            // No progress row yet — create one marked as completed
            readingProgressDao.saveProgress(
                ReadingProgressEntity(
                    bookId = bookId,
                    progressPercent = 100f,
                    completedAt = System.currentTimeMillis(),
                    timesCompleted = 1
                )
            )
        } else {
            readingProgressDao.markCompleted(bookId, System.currentTimeMillis())
        }
    }

    /**
     * Get all books that have been completed, ordered by completion date descending.
     */
    fun getCompletedBooks(): Flow<List<BookEntity>> =
        readingProgressDao.getCompletedBooks()

    /**
     * Get the completion timestamp for a book, or null if not completed.
     */
    suspend fun getCompletedAt(bookId: Int): Long? =
        readingProgressDao.getCompletedAt(bookId)

    /**
     * Get the number of times a book has been completed (re-reads).
     */
    suspend fun getTimesCompleted(bookId: Int): Int =
        readingProgressDao.getTimesCompleted(bookId) ?: 0

    /**
     * Count total completed books.
     */
    suspend fun getCompletedCount(): Int =
        readingProgressDao.getCompletedCount()

    /**
     * Count completed books within a time range (e.g. this week, this month).
     */
    suspend fun getCompletedCountInRange(startTime: Long, endTime: Long): Int =
        readingProgressDao.getCompletedCountInRange(startTime, endTime)

    // ---- Reading progress (updated) ----
    /**
     * Save reading progress for a book.
     * Calculates progressPercent from characterOffset / totalCharacters (EPUB)
     * or from pageNumber / totalPages (PDF) when character data is unavailable.
     *
     * @param sessionTimeDeltaMs Time spent in this reading session (ms), used to
     *     increment totalTimeRead. Pass 0 if not tracking time for this save.
     */
    suspend fun saveProgress(
        bookId: Int,
        chapterIndex: Int,
        characterOffset: Long,
        totalCharacters: Long,
        pageNumber: Int = 0,
        totalPages: Int = 0,
        lastReadPosition: String? = null,
        sessionTimeDeltaMs: Long = 0L
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
        val wasCompleted = existing?.completedAt != null
        val isNowCompleted = progressPercent >= 100f
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
            totalTimeRead = (existing?.totalTimeRead ?: 0) + sessionTimeDeltaMs,
            kavitaSynced = existing?.kavitaSynced ?: false,
            completedAt = when {
                isNowCompleted && !wasCompleted -> System.currentTimeMillis()
                else -> existing?.completedAt
            },
            timesCompleted = when {
                isNowCompleted && !wasCompleted -> (existing?.timesCompleted ?: 0) + 1
                else -> existing?.timesCompleted ?: 0
            }
        )
        readingProgressDao.saveProgress(entity)
    }

    /**
     * Save reading progress for a PDF book using page numbers.
     */
    suspend fun saveProgress(
        bookId: Int,
        pageNumber: Int,
        totalPages: Int,
        sessionTimeDeltaMs: Long = 0L
    ) {
        saveProgress(
            bookId = bookId,
            chapterIndex = 0,
            characterOffset = 0,
            totalCharacters = 0,
            pageNumber = pageNumber,
            totalPages = totalPages,
            lastReadPosition = null,
            sessionTimeDeltaMs = sessionTimeDeltaMs
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

    private fun progressMap(
        progressList: List<ReadingProgressEntity>
    ): Map<Int, ReadingProgressEntity> =
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
    suspend fun searchBooksAndChapters(query: String): List<BookEntity> {
        // Search books by title/author
        val bookResults = bookDao.searchBooks(query).first()
        // Search chapters by content, get distinct book IDs
        val chapterBookIds = chapterDao.searchChapterBookIds(query)
        val chapterResults = if (chapterBookIds.isNotEmpty()) {
            bookDao.getBooksByIds(chapterBookIds)
        } else {
            emptyList()
        }
        // Deduplicate by book ID, preserving order (book matches first)
        val seen = mutableSetOf<Int>()
        val combined = mutableListOf<BookEntity>()
        for (book in bookResults + chapterResults) {
            if (seen.add(book.id)) {
                combined.add(book)
            }
        }
        return combined
    }

    // ---- Series grouping ----

    suspend fun getDistinctSeriesNames(): List<String> =
        bookDao.getDistinctSeriesNames()

    fun getBooksInSeries(seriesName: String): Flow<List<BookEntity>> =
        bookDao.getBooksInSeries(seriesName)

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
        note: String?,
        kavitaChapterId: Int? = null
    ): Long {
        val now = System.currentTimeMillis()
        val entity = BookmarkEntity(
            bookId = bookId,
            chapterIndex = chapterIndex,
            pageNumber = pageNumber,
            position = position,
            title = title,
            note = note,
            createdTime = now,
            modifiedTime = now,
            kavitaChapterId = kavitaChapterId,
            kavitaSynced = kavitaChapterId != null
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

    suspend fun updateHighlightNote(highlightId: Int, note: String?) {
        highlightDao.updateNote(highlightId, note)
    }

    // ---- Tag management ----

    fun getTagsForBook(bookId: Int): Flow<List<TagEntity>> =
        tagDao.getTagsForBook(bookId)

    suspend fun getTagsForBookList(bookId: Int): List<TagEntity> =
        tagDao.getTagsForBookList(bookId)

    fun getAllTags(): Flow<List<TagEntity>> =
        tagDao.getAllTags()

    /**
     * Add a tag to a book. Creates the tag if it doesn't exist.
     * Returns the tag ID.
     */
    suspend fun addTagToBook(bookId: Int, tagName: String): Long {
        // Find or create the tag
        val existingTag = tagDao.getTagByName(tagName)
        val tagId = if (existingTag != null) {
            existingTag.id.toLong()
        } else {
            tagDao.insertTag(TagEntity(name = tagName))
        }
        tagDao.addTagToBook(BookTagCrossRef(bookId = bookId, tagId = tagId.toInt()))
        return tagId
    }

    suspend fun removeTagFromBook(bookId: Int, tagId: Int) =
        tagDao.removeTagFromBook(bookId, tagId)

    suspend fun removeAllTagsFromBook(bookId: Int) =
        tagDao.removeAllTagsFromBook(bookId)

    // ---- Rating ----

    suspend fun updateRating(bookId: Int, rating: Float?) {
        val book = bookDao.getBookById(bookId) ?: return
        bookDao.updateBook(book.copy(rating = rating))
    }

    // ---- Cover ----

    suspend fun updateCoverPath(bookId: Int, coverPath: String?) {
        val book = bookDao.getBookById(bookId) ?: return
        bookDao.updateBook(book.copy(coverPath = coverPath))
    }

    // ---- SAF / content URI import ----

    /**
     * Import a single book from a SAF content URI.
     * Copies the file to a temporary location, scans it, and inserts into the database.
     * Returns the book ID, or -1 if import failed.
     */
    suspend fun importBookFromUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            // Query the display name from the content URI
            var fileName = "unknown"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: "unknown"
                }
            }

            // Determine format from file extension
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension !in fileScanner.supportedExtensions()) return@withContext -1L

            // Copy content to a temporary file
            val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}_$fileName")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext -1L

            // Scan the temp file and insert into DB
            val book = fileScanner.scanFile(tempFile.absolutePath)
            tempFile.delete() // Clean up temp file

            book?.id?.toLong() ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    // ---- Reading Session tracking ----

    suspend fun recordReadingSession(
        bookId: Int,
        startTime: Long,
        endTime: Long,
        pagesRead: Int
    ) {
        val durationMs = endTime - startTime
        val sessionDate = java.time.LocalDate.now().toEpochDay()
        val session = ReadingSessionEntity(
            bookId = bookId,
            sessionDate = sessionDate,
            durationMs = durationMs,
            pagesRead = pagesRead
        )
        readingSessionDao.insertSession(session)
    }

    suspend fun getPagesReadToday(): Int {
        val todayEpochDay = java.time.LocalDate.now().toEpochDay()
        return readingSessionDao.getTotalPagesForDay(todayEpochDay)
    }

    suspend fun getPagesReadThisWeek(): Int {
        val today = java.time.LocalDate.now()
        val weekStart = today.minusDays(6).toEpochDay()
        val todayEpochDay = today.toEpochDay()
        return readingSessionDao.getSessionsInRange(weekStart, todayEpochDay)
            .first().sumOf { it.pagesRead }
    }

    suspend fun getPagesReadThisMonth(): Int {
        val today = java.time.LocalDate.now()
        val monthStart = today.withDayOfMonth(1).toEpochDay()
        val todayEpochDay = today.toEpochDay()
        return readingSessionDao.getSessionsInRange(monthStart, todayEpochDay)
            .first().sumOf { it.pagesRead }
    }

    suspend fun getTotalReadingTimeToday(): Long {
        val todayEpochDay = java.time.LocalDate.now().toEpochDay()
        return readingSessionDao.getTotalDurationForDay(todayEpochDay)
    }

    suspend fun getSessionCountToday(): Int {
        val todayEpochDay = java.time.LocalDate.now().toEpochDay()
        return readingSessionDao.getSessionsInRange(todayEpochDay, todayEpochDay)
            .first().size
    }

    data class DailyPageStatRaw(
        val epochDay: Long,
        val totalPages: Int
    )

    suspend fun getDailyPagesForLastDays(days: Int): List<DailyPageStatRaw> {
        val today = java.time.LocalDate.now()
        val startEpochDay = today.minusDays(days.toLong()).toEpochDay()
        val todayEpochDay = today.toEpochDay()
        val sessions = readingSessionDao.getSessionsInRange(startEpochDay, todayEpochDay)
            .first()
        return sessions.groupBy { it.sessionDate }.map { (day, sessionList) ->
            DailyPageStatRaw(
                epochDay = day,
                totalPages = sessionList.sumOf { it.pagesRead }
            )
        }.sortedBy { it.epochDay }
    }
}
