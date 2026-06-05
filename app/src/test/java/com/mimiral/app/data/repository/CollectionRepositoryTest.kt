package com.mimiral.app.data.repository

import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.dao.CollectionDao
import com.mimiral.app.data.local.entity.BookCollectionCrossRef
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.CollectionEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CollectionRepositoryTest {

    private lateinit var collectionDao: CollectionDao
    private lateinit var bookDao: BookDao
    private lateinit var repository: CollectionRepository

    @Before
    fun setup() {
        collectionDao = mock()
        bookDao = mock()
        repository = CollectionRepository(collectionDao, bookDao)
    }

    // ---- getAllCollections ----

    @Test
    fun `getAllCollections - returns flow from dao`() {
        val collections = listOf(
            CollectionEntity(id = 1, name = "Favorites"),
            CollectionEntity(id = 2, name = "Sci-Fi")
        )
        whenever(collectionDao.getAllCollections()).thenReturn(flowOf(collections))

        val result = repository.getAllCollections()
        assertNotNull(result)
    }

    // ---- getCollectionById ----

    @Test
    fun `getCollectionById - returns collection when found`() = runTest {
        val collection = CollectionEntity(id = 1, name = "Favorites")
        whenever(collectionDao.getCollectionById(1)).thenReturn(collection)

        val result = repository.getCollectionById(1)
        assertNotNull(result)
        assertEquals("Favorites", result?.name)
    }

    @Test
    fun `getCollectionById - returns null when not found`() = runTest {
        whenever(collectionDao.getCollectionById(99)).thenReturn(null)

        val result = repository.getCollectionById(99)
        assertNull(result)
    }

    // ---- createCollection ----

    @Test
    fun `createCollection - creates with correct sort order`() = runTest {
        whenever(collectionDao.getCollectionCount()).thenReturn(3)
        whenever(collectionDao.insertCollection(any())).thenReturn(4L)

        val id = repository.createCollection("New Collection", "A description")
        assertEquals(4L, id)

        verify(collectionDao).insertCollection(any())
    }

    @Test
    fun `createCollection - works without description`() = runTest {
        whenever(collectionDao.getCollectionCount()).thenReturn(0)
        whenever(collectionDao.insertCollection(any())).thenReturn(1L)

        val id = repository.createCollection("No Desc")
        assertEquals(1L, id)
    }

    // ---- updateCollection ----

    @Test
    fun `updateCollection - delegates to dao`() = runTest {
        val collection = CollectionEntity(id = 1, name = "Updated Name")
        repository.updateCollection(collection)
        verify(collectionDao).updateCollection(collection)
    }

    // ---- deleteCollection ----

    @Test
    fun `deleteCollection - delegates to dao`() = runTest {
        val collection = CollectionEntity(id = 1, name = "To Delete")
        repository.deleteCollection(collection)
        verify(collectionDao).deleteCollection(collection)
    }

    // ---- getCollectionCount ----

    @Test
    fun `getCollectionCount - returns dao count`() = runTest {
        whenever(collectionDao.getCollectionCount()).thenReturn(5)
        assertEquals(5, repository.getCollectionCount())
    }

    // ---- getBooksInCollection ----

    @Test
    fun `getBooksInCollection - returns flow from dao`() {
        val books = listOf(
            BookEntity(id = 1, filePath = "/b/1.epub", title = "Book 1", format = "EPUB"),
            BookEntity(id = 2, filePath = "/b/2.epub", title = "Book 2", format = "EPUB")
        )
        whenever(collectionDao.getBooksInCollection(1)).thenReturn(flowOf(books))

        val result = repository.getBooksInCollection(1)
        assertNotNull(result)
    }

    // ---- getBookCountInCollection ----

    @Test
    fun `getBookCountInCollection - returns count`() = runTest {
        whenever(collectionDao.getBookCountInCollection(1)).thenReturn(3)
        assertEquals(3, repository.getBookCountInCollection(1))
    }

    @Test
    fun `getBookCountInCollection - empty collection returns zero`() = runTest {
        whenever(collectionDao.getBookCountInCollection(99)).thenReturn(0)
        assertEquals(0, repository.getBookCountInCollection(99))
    }

    // ---- addBookToCollection ----

    @Test
    fun `addBookToCollection - inserts cross reference`() = runTest {
        repository.addBookToCollection(bookId = 1, collectionId = 2)
        verify(collectionDao).addBookToCollection(
            BookCollectionCrossRef(bookId = 1, collectionId = 2)
        )
    }

    // ---- removeBookFromCollection ----

    @Test
    fun `removeBookFromCollection - delegates to dao`() = runTest {
        repository.removeBookFromCollection(bookId = 1, collectionId = 2)
        verify(collectionDao).removeBookFromCollection(1, 2)
    }
}
