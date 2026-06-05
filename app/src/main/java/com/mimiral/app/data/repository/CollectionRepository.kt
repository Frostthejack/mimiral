package com.mimiral.app.data.repository

import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.CollectionDao
import com.mimiral.app.data.local.entity.BookCollectionCrossRef
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.CollectionEntity
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

    suspend fun updateCollection(collection: CollectionEntity) =
        collectionDao.updateCollection(collection)

    suspend fun deleteCollection(collection: CollectionEntity) =
        collectionDao.deleteCollection(collection)

    suspend fun getCollectionCount(): Int =
        collectionDao.getCollectionCount()

    fun getBooksInCollection(collectionId: Int): Flow<List<BookEntity>> =
        collectionDao.getBooksInCollection(collectionId)

    suspend fun getBookCountInCollection(collectionId: Int): Int =
        collectionDao.getBookCountInCollection(collectionId)

    suspend fun addBookToCollection(bookId: Int, collectionId: Int) {
        collectionDao.addBookToCollection(
            BookCollectionCrossRef(bookId = bookId, collectionId = collectionId)
        )
    }

    suspend fun removeBookFromCollection(bookId: Int, collectionId: Int) {
        collectionDao.removeBookFromCollection(bookId, collectionId)
    }
}
