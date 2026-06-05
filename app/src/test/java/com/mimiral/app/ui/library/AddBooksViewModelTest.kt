package com.mimiral.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddBooksViewModelTest {

    @Test
    fun `AddBooksUiState - default values`() {
        val state = AddBooksUiState()

        assertEquals(0, state.lastImportCount)
        assertNull(state.errorMessage)
    }

    @Test
    fun `AddBooksUiState - copy preserves values`() {
        val state = AddBooksUiState(lastImportCount = 5, errorMessage = null)
        assertEquals(5, state.lastImportCount)
        assertNull(state.errorMessage)
    }

    @Test
    fun `AddBooksUiState - error state`() {
        val state = AddBooksUiState(errorMessage = "Scan failed")
        assertEquals("Scan failed", state.errorMessage)
        assertEquals(0, state.lastImportCount)
    }
}
