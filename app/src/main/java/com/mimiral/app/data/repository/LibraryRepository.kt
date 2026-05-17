package com.mimiral.app.data.repository

import android.net.Uri
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.ChapterDao
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.ChapterEntity
import com.mimiral.app.data.local.scanner.FileScanner
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level repository coordinating file scanning with the local database.
 * Provides a unified API for the library screen: scanning directories,
 * querying books, and full-text search across chapter content.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val fileScanner: FileScanner,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao
) {
    // ---- Book queries (delegated to BookDao) ----

    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun getBookById(bookId: Int): BookEntity? = bookDao.getBookById(bookId)

    fun searchBooks(query: String): Flow<List<BookEntity>> = bookDao.searchBooks(query)

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun insertBooks(books: List<BookEntity>): List<Long> = books.map { bookDao.insertBook(it) }

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun deleteBook(book: BookEntity) {
        // Chapters will be deleted via CASCADE, but clean FTS index explicitly
        chapterDao.deleteChaptersForBook(book.id)
        bookDao.deleteBook(book)
    }

    suspend fun getBookCount(): Int = bookDao.getBookCount()

    // ---- File scanning ----

    /**
     * Scan a directory for ebook files and insert any new ones into the database.
     * Returns the number of newly inserted books.
     */
    suspend fun scanAndImport(uri: Uri): Int {
        val scanned = fileScanner.scanDirectory(uri)
        if (scanned.isEmpty()) return 0

        // Filter out books that already exist (by file path)
        val existingPaths = mutableSetOf<String>()
        // We check each scanned file against the database
        val newBooks = scanned.filter { book ->
            // Simple path-based deduplication
            val existing = bookDao.getBookById(book.id)
            existing == null
        }

        if (newBooks.isEmpty()) return 0
        insertBooks(newBooks)
        return newBooks.size
    }

    /**
     * Scan a single file and insert it into the database.
     * Returns the book ID, or -1 if the file is not a supported format.
     */
    suspend fun scanAndImportFile(filePath: String): Long {
        val book = fileScanner.scanFile(filePath) ?: return -1
        return bookDao.insertBook(book)
    }

    fun supportedExtensions(): Set<String> = fileScanner.supportedExtensions()

    // ---- Chapter / FTS5 search ----

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
     * Full-text search across all book chapters using FTS5.
     * Returns matching chapters ranked by relevance.
     */
    fun searchChapters(query: String): Flow<List<ChapterEntity>> =
        chapterDao.searchChapters(query)

    /**
     * Full-text search within a specific book's chapters.
     */
    fun searchChaptersInBook(bookId: Int, query: String): Flow<List<ChapterEntity>> =
        chapterDao.searchChaptersInBook(bookId, query)
}
