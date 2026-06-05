package com.mimiral.app.data.local.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LibrarySettingsTest {

    @Test
    fun `LibrarySettings - default values`() {
        val settings = LibrarySettings()

        assertEquals(SortOption.RECENT, settings.sortOption)
        assertEquals(FilterOption.ALL, settings.filterOption)
        assertEquals(ViewMode.GRID, settings.viewMode)
    }

    @Test
    fun `LibrarySettings - custom values`() {
        val settings = LibrarySettings(
            sortOption = SortOption.TITLE,
            filterOption = FilterOption.READING,
            viewMode = ViewMode.LIST
        )

        assertEquals(SortOption.TITLE, settings.sortOption)
        assertEquals(FilterOption.READING, settings.filterOption)
        assertEquals(ViewMode.LIST, settings.viewMode)
    }

    // ---- SortOption ----

    @Test
    fun `SortOption - has seven values`() {
        val values = SortOption.values()
        assertEquals(7, values.size)
    }

    @Test
    fun `SortOption - RECENT has correct display name`() {
        assertEquals("Recent", SortOption.RECENT.displayName)
    }

    @Test
    fun `SortOption - TITLE has correct display name`() {
        assertEquals("Title", SortOption.TITLE.displayName)
    }

    @Test
    fun `SortOption - AUTHOR has correct display name`() {
        assertEquals("Author", SortOption.AUTHOR.displayName)
    }

    @Test
    fun `SortOption - SERIES has correct display name`() {
        assertEquals("Series", SortOption.SERIES.displayName)
    }

    @Test
    fun `SortOption - PROGRESS has correct display name`() {
        assertEquals("Progress", SortOption.PROGRESS.displayName)
    }

    @Test
    fun `SortOption - RATING has correct display name`() {
        assertEquals("Rating", SortOption.RATING.displayName)
    }

    @Test
    fun `SortOption - DATE_ADDED has correct display name`() {
        assertEquals("Date Added", SortOption.DATE_ADDED.displayName)
    }

    // ---- FilterOption ----

    @Test
    fun `FilterOption - has five values`() {
        val values = FilterOption.values()
        assertEquals(5, values.size)
    }

    @Test
    fun `FilterOption - ALL has correct display name`() {
        assertEquals("All", FilterOption.ALL.displayName)
    }

    @Test
    fun `FilterOption - READING has correct display name`() {
        assertEquals("Reading", FilterOption.READING.displayName)
    }

    @Test
    fun `FilterOption - UNREAD has correct display name`() {
        assertEquals("Unread", FilterOption.UNREAD.displayName)
    }

    @Test
    fun `FilterOption - FINISHED has correct display name`() {
        assertEquals("Finished", FilterOption.FINISHED.displayName)
    }

    @Test
    fun `FilterOption - DOWNLOADED has correct display name`() {
        assertEquals("Downloaded", FilterOption.DOWNLOADED.displayName)
    }

    // ---- ViewMode ----

    @Test
    fun `ViewMode - has two values`() {
        val values = ViewMode.values()
        assertEquals(2, values.size)
    }

    @Test
    fun `ViewMode - contains GRID and LIST`() {
        val values = ViewMode.values()
        assertNotNull(values.find { it.name == "GRID" })
        assertNotNull(values.find { it.name == "LIST" })
    }
}
