package com.mimiral.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.local.entity.CollectionEntity
import com.mimiral.app.data.local.entity.HighlightEntity
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import com.mimiral.app.data.local.entity.PdfSettingsEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.local.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY date_added DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: Int): BookEntity?

    @Query(
        "SELECT * FROM books WHERE title LIKE '%' || :query || '%' " +
            "OR author LIKE '%' || :query || '%'"
    )
    fun searchBooks(query: String): Flow<List<BookEntity>>

    // -- Sorted queries --

    @Query("SELECT * FROM books ORDER BY title ASC")
    fun getAllBooksSortedByTitle(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY author ASC")
    fun getAllBooksSortedByAuthor(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY date_added DESC")
    fun getAllBooksSortedByDateAdded(): Flow<List<BookEntity>>

    @Query(
        "SELECT books.* FROM books " +
            "LEFT JOIN reading_progress ON books.id = reading_progress.book_id " +
            "ORDER BY reading_progress.last_read_time DESC"
    )
    fun getAllBooksSortedByRecent(): Flow<List<BookEntity>>

    // -- Sorted + searched queries --

    @Query(
        "SELECT * FROM books WHERE title LIKE '%' || :query || '%' " +
            "OR author LIKE '%' || :query || '%' ORDER BY title ASC"
    )
    fun searchBooksSortedByTitle(query: String): Flow<List<BookEntity>>

    @Query(
        "SELECT * FROM books WHERE title LIKE '%' || :query || '%' " +
            "OR author LIKE '%' || :query || '%' ORDER BY author ASC"
    )
    fun searchBooksSortedByAuthor(query: String): Flow<List<BookEntity>>

    @Query(
        "SELECT * FROM books WHERE title LIKE '%' || :query || '%' " +
            "OR author LIKE '%' || :query || '%' ORDER BY date_added DESC"
    )
    fun searchBooksSortedByDateAdded(query: String): Flow<List<BookEntity>>

    @Query(
        "SELECT books.* FROM books " +
            "LEFT JOIN reading_progress ON books.id = reading_progress.book_id " +
            "WHERE books.title LIKE '%' || :query || '%' " +
            "OR books.author LIKE '%' || :query || '%' " +
            "ORDER BY reading_progress.last_read_time DESC"
    )
    fun searchBooksSortedByRecent(query: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int

    @Query("SELECT * FROM books WHERE file_path = :filePath LIMIT 1")
    suspend fun getBookByFilePath(filePath: String): BookEntity?

    @Query("SELECT file_path FROM books")
    suspend fun getAllFilePaths(): List<String>
}

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId")
    suspend fun getProgressForBook(bookId: Int): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId")
    fun getProgressForBookFlow(bookId: Int): Flow<ReadingProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress ORDER BY last_read_time DESC LIMIT 10")
    fun getRecentProgress(): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress")
    fun getAllProgress(): Flow<List<ReadingProgressEntity>>
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId ORDER BY chapter_index, page_number")
    fun getBookmarksForBook(bookId: Int): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("SELECT * FROM bookmarks WHERE kavita_synced = 0")
    suspend fun getUnsyncedBookmarks(): List<BookmarkEntity>

    @Query(
        "SELECT COUNT(*) FROM bookmarks " +
            "WHERE book_id = :bookId " +
            "AND chapter_index = :chapterIndex " +
            "AND page_number = :pageNumber " +
            "AND (position = :position OR (position IS NULL AND :position IS NULL))"
    )
    suspend fun countBookmarksAtPosition(
        bookId: Int,
        chapterIndex: Int,
        pageNumber: Int,
        position: String?
    ): Int

    @Query(
        "SELECT * FROM bookmarks " +
            "WHERE book_id = :bookId " +
            "AND chapter_index = :chapterIndex " +
            "AND page_number = :pageNumber " +
            "AND (position = :position OR (position IS NULL AND :position IS NULL)) " +
            "LIMIT 1"
    )
    suspend fun getBookmarkAtPosition(
        bookId: Int,
        chapterIndex: Int,
        pageNumber: Int,
        position: String?
    ): BookmarkEntity?
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE book_id = :bookId ORDER BY chapter_index")
    fun getHighlightsForBook(bookId: Int): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity): Long

    @Delete
    suspend fun deleteHighlight(highlight: HighlightEntity)

    @Query("UPDATE highlights SET note = :note WHERE id = :highlightId")
    suspend fun updateNote(highlightId: Int, note: String?)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sort_order")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Delete
    suspend fun deleteCollection(collection: CollectionEntity)

    @Query(
        "SELECT books.* FROM books " +
            "INNER JOIN book_collections ON books.id = book_collections.book_id " +
            "WHERE book_collections.collection_id = :collectionId"
    )
    fun getBooksInCollection(collectionId: Int): Flow<List<BookEntity>>
}

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers WHERE is_active = 1")
    fun getActiveServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE type = :type AND is_active = 1 LIMIT 1")
    suspend fun getActiveServerByType(type: String): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity): Long

    @Update
    suspend fun updateServer(server: ServerEntity)
}

@Dao
interface OpdsCatalogDao {
    @Query("SELECT * FROM opds_catalogs WHERE is_active = 1")
    fun getActiveCatalogs(): Flow<List<OpdsCatalogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalog(catalog: OpdsCatalogEntity): Long

    @Delete
    suspend fun deleteCatalog(catalog: OpdsCatalogEntity)
}

@Dao
interface PdfSettingsDao {
    @Query("SELECT * FROM pdf_settings WHERE book_id = :bookId")
    suspend fun getSettingsForBook(bookId: Int): PdfSettingsEntity?

    @Query("SELECT * FROM pdf_settings WHERE book_id = :bookId")
    fun getSettingsForBookFlow(bookId: Int): Flow<PdfSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: PdfSettingsEntity)

    @Query("DELETE FROM pdf_settings WHERE book_id = :bookId")
    suspend fun deleteSettingsForBook(bookId: Int)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY chapter_index")
    fun getChaptersForBook(bookId: Int): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE book_id = :bookId AND chapter_index = :chapterIndex")
    suspend fun getChapter(bookId: Int, chapterIndex: Int): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE book_id = :bookId")
    suspend fun deleteChaptersForBook(bookId: Int)

    /**
     * FTS5 full-text search across chapter content.
     * Returns chapters matching the query text, ranked by relevance.
     */
    @Query(
        "SELECT chapters.* FROM chapters " +
            "INNER JOIN chapters_fts ON chapters.id = chapters_fts.rowid " +
            "WHERE chapters_fts MATCH :query"
    )
    fun searchChapters(query: String): Flow<List<ChapterEntity>>

    /**
     * FTS5 search scoped to a specific book.
     */
    @Query(
        "SELECT chapters.* FROM chapters " +
            "INNER JOIN chapters_fts ON chapters.id = chapters_fts.rowid " +
            "WHERE chapters_fts MATCH :query AND chapters.book_id = :bookId"
    )
    fun searchChaptersInBook(bookId: Int, query: String): Flow<List<ChapterEntity>>
}
