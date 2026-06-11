package com.mimiral.app.data.repository

import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.CollectionDao
import com.mimiral.app.data.local.entity.BookCollectionCrossRef
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.CollectionEntity
import com.mimiral.app.data.local.entity.CollectionWithBookCount
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Repository for collection (bookshelf) operations.
 * Provides CRUD for collections and book-collection membership.
 */
@Singleton
class CollectionRepository @Inject constructor(
    private val collectionDao: CollectionDao,
    private val bookDao: BookDao
) {
    fun getAllCollections(): Flow<List<CollectionEntity>> =
        collectionDao.getAllCollections()

    /**
     * Single-query batch fetch of all collections with their book counts.
     * Uses LEFT JOIN + GROUP BY to avoid the N+1 query problem.
     */
    fun getAllCollectionsWithBookCount(): Flow<List<CollectionWithBookCount>> =
        collectionDao.getAllCollectionsWithBookCount()

    suspend fun getCollectionById(collectionId: Int): CollectionEntity? =
        collectionDao.getCollectionById(collectionId)

    suspend fun createCollection(name: String, description: String? = null): Long {
        val maxOrder = collectionDao.getCollectionCount()
        val entity = CollectionEntity(
            name = name,
            description = description,
            sortOrder = maxOrder
        )
        return collectionDao.insertCollection(entity)
    }

    suspend fun insertCollection(collection: CollectionEntity): Long =
        collectionDao.insertCollection(collection)

    suspend fun updateCollection(collection: CollectionEntity) =
        collectionDao.updateCollection(collection)

    suspend fun deleteCollection(collection: CollectionEntity) {
        collectionDao.removeAllBooksFromCollection(collection.id)
        collectionDao.deleteCollection(collection)
    }

    suspend fun getCollectionCount(): Int =
        collectionDao.getCollectionCount()

    fun getBooksInCollection(collectionId: Int): Flow<List<BookEntity>> =
        collectionDao.getBooksInCollection(collectionId)

    suspend fun getBookCountInCollection(collectionId: Int): Int =
        collectionDao.getBookCountInCollection(collectionId)

    fun getCollectionsForBook(bookId: Int): Flow<List<CollectionEntity>> =
        collectionDao.getCollectionsForBook(bookId)

    suspend fun getCollectionIdsForBook(bookId: Int): List<Int> =
        collectionDao.getCollectionIdsForBook(bookId)

    suspend fun addBookToCollection(bookId: Int, collectionId: Int) {
        collectionDao.addBookToCollection(
            BookCollectionCrossRef(bookId = bookId, collectionId = collectionId)
        )
    }

    suspend fun removeBookFromCollection(bookId: Int, collectionId: Int) {
        collectionDao.removeBookFromCollection(bookId, collectionId)
    }

    /**
     * Add a single book to multiple collections in one operation.
     */
    suspend fun addBookToCollections(bookId: Int, collectionIds: List<Int>) {
        val crossRefs = collectionIds.map { collectionId ->
            BookCollectionCrossRef(bookId = bookId, collectionId = collectionId)
        }
        collectionDao.addBooksToCollections(crossRefs)
    }

    /**
     * Set the exact collections for a book, removing it from any collections
     * not in the provided list.
     */
    suspend fun setCollectionsForBook(bookId: Int, collectionIds: List<Int>) {
        val currentIds = getCollectionIdsForBook(bookId).toSet()
        val newIds = collectionIds.toSet()

        // Remove from collections no longer selected
        (currentIds - newIds).forEach { collectionId ->
            removeBookFromCollection(bookId, collectionId)
        }

        // Add to newly selected collections
        (newIds - currentIds).forEach { collectionId ->
            addBookToCollection(bookId, collectionId)
        }
    }

    /**
     * Add multiple books to a single collection.
     */
    suspend fun addBooksToCollection(bookIds: List<Int>, collectionId: Int) {
        val crossRefs = bookIds.map { bookId ->
            BookCollectionCrossRef(bookId = bookId, collectionId = collectionId)
        }
        collectionDao.addBooksToCollections(crossRefs)
    }

    /**
     * Add multiple books to multiple collections (cartesian product).
     */
    suspend fun addBooksToCollections(bookIds: List<Int>, collectionIds: List<Int>) {
        val crossRefs = bookIds.flatMap { bookId ->
            collectionIds.map { collectionId ->
                BookCollectionCrossRef(bookId = bookId, collectionId = collectionId)
            }
        }
        collectionDao.addBooksToCollections(crossRefs)
    }
}
