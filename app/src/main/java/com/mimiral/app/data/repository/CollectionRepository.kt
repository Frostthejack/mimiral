package com.mimiral.app.data.repository

import com.mimiral.app.data.local.dao.CollectionDao
import com.mimiral.app.data.local.entity.BookCollectionCrossRef
import com.mimiral.app.data.local.entity.CollectionEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing collections and book-collection relationships.
 * Provides CRUD for collections and many-to-many assignment of books to collections.
 */
@Singleton
class CollectionRepository @Inject constructor(
    private val collectionDao: CollectionDao
) {
    fun getAllCollections(): Flow<List<CollectionEntity>> =
        collectionDao.getAllCollections()

    suspend fun getCollectionById(collectionId: Int): CollectionEntity? =
        collectionDao.getCollectionById(collectionId)

    suspend fun insertCollection(collection: CollectionEntity): Long =
        collectionDao.insertCollection(collection)

    suspend fun updateCollection(collection: CollectionEntity) =
        collectionDao.updateCollection(collection)

    suspend fun deleteCollection(collection: CollectionEntity) =
        collectionDao.deleteCollection(collection)

    suspend fun getCollectionCount(): Int =
        collectionDao.getCollectionCount()

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
