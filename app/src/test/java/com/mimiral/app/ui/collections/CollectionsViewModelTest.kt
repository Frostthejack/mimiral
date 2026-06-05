package com.mimiral.app.ui.collections

import com.mimiral.app.data.local.entity.CollectionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsViewModelTest {

    @Test
    fun `CollectionsUiState - default values`() {
        val state = CollectionsUiState()

        assertTrue(state.collections.isEmpty())
        assertTrue(state.isLoading)
        assertFalse(state.isCreating)
        assertNull(state.editingCollection)
        assertNull(state.expandedCollectionId)
        assertTrue(state.booksInExpanded.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun `CollectionsUiState - copy with values`() {
        val collection = CollectionEntity(id = 1, name = "Favorites")
        val state = CollectionsUiState(
            collections = listOf(CollectionWithCount(collection, 3)),
            isLoading = false,
            editingCollection = collection
        )

        assertEquals(1, state.collections.size)
        assertFalse(state.isLoading)
        assertEquals(collection, state.editingCollection)
    }

    @Test
    fun `CollectionWithCount - holds correct data`() {
        val collection = CollectionEntity(id = 1, name = "Favorites")
        val withCount = CollectionWithCount(collection = collection, bookCount = 5)

        assertEquals("Favorites", withCount.collection.name)
        assertEquals(5, withCount.bookCount)
    }
}
