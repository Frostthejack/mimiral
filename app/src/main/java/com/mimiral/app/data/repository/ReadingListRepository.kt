package com.mimiral.app.data.repository

import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.ReadingListDao
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.BookReadingListCrossRef
import com.mimiral.app.data.local.entity.ReadingListEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Repository for reading list operations.
 * Provides CRUD for reading lists and book-list membership.
 * Built-in lists (To Read, Reading, Finished) are seeded in the DB migration.
 */
@Singleton
class ReadingListRepository @Inject constructor(
    private val readingListDao: ReadingListDao,
    private val bookDao: BookDao
) {
    fun getAllReadingLists(): Flow<List<ReadingListEntity>> =
        readingListDao.getAllReadingLists()

    suspend fun getReadingListById(listId: Int): ReadingListEntity? =
        readingListDao.getReadingListById(listId)

    suspend fun getReadingListByType(listType: String): ReadingListEntity? =
        readingListDao.getReadingListByType(listType)

    suspend fun createReadingList(name: String): Long {
        val maxOrder = readingListDao.getReadingListCount()
        val entity = ReadingListEntity(
            name = name,
            listType = "CUSTOM",
            createdTime = System.currentTimeMillis(),
            sortOrder = maxOrder
        )
        return readingListDao.insertReadingList(entity)
    }

    suspend fun updateReadingList(list: ReadingListEntity) =
        readingListDao.updateReadingList(list)

    suspend fun deleteReadingList(list: ReadingListEntity) =
        readingListDao.deleteReadingList(list)

    suspend fun getReadingListCount(): Int =
        readingListDao.getReadingListCount()

    fun getBooksInReadingList(listId: Int): Flow<List<BookEntity>> =
        readingListDao.getBooksInReadingList(listId)

    suspend fun getBookCountInReadingList(listId: Int): Int =
        readingListDao.getBookCountInReadingList(listId)

    suspend fun addBookToReadingList(bookId: Int, listId: Int) {
        readingListDao.addBookToReadingList(
            BookReadingListCrossRef(
                bookId = bookId,
                readingListId = listId,
                addedTime = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeBookFromReadingList(bookId: Int, listId: Int) {
        readingListDao.removeBookFromReadingList(bookId, listId)
    }

    suspend fun isBookInReadingList(bookId: Int, listId: Int): Boolean =
        readingListDao.isBookInReadingList(bookId, listId)

    suspend fun getBuiltInListForType(listType: String): ReadingListEntity? {
        return readingListDao.getReadingListByType(listType)
    }
}
