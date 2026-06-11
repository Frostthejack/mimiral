package com.mimiral.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mimiral.app.data.local.entity.BookCollectionCrossRef
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.CollectionEntity
import kotlinx.coroutines.flow.Flow

data class CollectionWithBookCount(
    val id: Int,
    val name: String,
    val description: String?,
    val createdTime: Long,
    val sortOrder: Int,
    val bookCount: Int
)

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY sort_order")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :collectionId")
    suspend fun getCollectionById(collectionId: Int): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookToCollection(crossRef: BookCollectionCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBooksToCollections(crossRefs: List<BookCollectionCrossRef>)

    @androidx.room.Delete
    suspend fun deleteCollection(collection: CollectionEntity)

    @androidx.room.Update
    suspend fun updateCollection(collection: CollectionEntity)

    @Query("DELETE FROM book_collections WHERE book_id = :bookId AND collection_id = :collectionId")
    suspend fun removeBookFromCollection(bookId: Int, collectionId: Int)

    @Query("DELETE FROM book_collections WHERE collection_id = :collectionId")
    suspend fun removeAllBooksFromCollection(collectionId: Int)

    @Query("SELECT COUNT(*) FROM collections")
    suspend fun getCollectionCount(): Int

    @Query("SELECT books.* FROM books INNER JOIN book_collections ON books.id = book_collections.book_id WHERE book_collections.collection_id = :collectionId")
    fun getBooksInCollection(collectionId: Int): Flow<List<BookEntity>>

    @Query("SELECT COUNT(*) FROM book_collections WHERE collection_id = :collectionId")
    suspend fun getBookCountInCollection(collectionId: Int): Int

    @Query("SELECT collections.* FROM collections INNER JOIN book_collections ON collections.id = book_collections.collection_id WHERE book_collections.book_id = :bookId ORDER BY collections.sort_order")
    fun getCollectionsForBook(bookId: Int): Flow<List<CollectionEntity>>

    @Query("SELECT collection_id FROM book_collections WHERE book_id = :bookId")
    suspend fun getCollectionIdsForBook(bookId: Int): List<Int>

    @Query("SELECT * FROM book_collections")
    suspend fun getAllBookCollections(): List<BookCollectionCrossRef>

    /**
     * Single-query batch fetch: returns all collections with their book counts
     * using a LEFT JOIN + GROUP BY, avoiding the N+1 query problem.
     */
    @Query("SELECT collections.id AS id, collections.name AS name, collections.description AS description, collections.created_time AS createdTime, collections.sort_order AS sortOrder, COUNT(book_collections.book_id) AS bookCount FROM collections LEFT JOIN book_collections ON collections.id = book_collections.collection_id GROUP BY collections.id ORDER BY collections.sort_order")
    fun getAllCollectionsWithBookCount(): Flow<List<CollectionWithBookCount>>
}
