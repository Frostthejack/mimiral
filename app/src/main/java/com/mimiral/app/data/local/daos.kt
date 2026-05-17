package com.mimiral.app.data.local.dao

import androidx.room.*
import com.mimiral.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY date_added DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: Int): BookEntity?

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    fun searchBooks(query: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int
}

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId")
    suspend fun getProgressForBook(bookId: Int): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress ORDER BY last_read_time DESC LIMIT 10")
    fun getRecentProgress(): Flow<List<ReadingProgressEntity>>
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
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE book_id = :bookId ORDER BY chapter_index")
    fun getHighlightsForBook(bookId: Int): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity): Long

    @Delete
    suspend fun deleteHighlight(highlight: HighlightEntity)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sort_order")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Delete
    suspend fun deleteCollection(collection: CollectionEntity)

    @Query("SELECT * FROM books INNER JOIN book_collections ON books.id = book_collections.book_id WHERE book_collections.collection_id = :collectionId")
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
