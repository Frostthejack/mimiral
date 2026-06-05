package com.mimiral.app.ui.discover

import com.mimiral.app.data.remote.kavita.KavitaLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverViewModelTest {

    @Test
    fun `DiscoverUiState - default values`() {
        val state = DiscoverUiState()

        assertTrue(state.libraries.isEmpty())
        assertNull(state.selectedLibrary)
        assertTrue(state.series.isEmpty())
        assertNull(state.selectedSeries)
        assertFalse(state.isLoadingLibraries)
        assertFalse(state.isLoadingSeries)
        assertFalse(state.isLoadingMetadata)
        assertNull(state.error)
        assertTrue(state.navigationStack.isEmpty())
    }

    @Test
    fun `KavitaLibrary - typeLabel for manga`() {
        val lib = KavitaLibrary(id = 1, name = "Manga", type = 2)
        assertEquals("Manga", lib.typeLabel)
    }

    @Test
    fun `KavitaLibrary - typeLabel for books`() {
        val lib = KavitaLibrary(id = 2, name = "Books", type = 0)
        assertEquals("Books", lib.typeLabel)
    }

    @Test
    fun `KavitaLibrary - typeLabel for comics`() {
        val lib = KavitaLibrary(id = 3, name = "Comics", type = 1)
        assertEquals("Comics", lib.typeLabel)
    }

    @Test
    fun `KavitaLibrary - typeLabel for unknown type`() {
        val lib = KavitaLibrary(id = 4, name = "Unknown", type = 99)
        assertEquals("Library", lib.typeLabel)
    }

    @Test
    fun `SeriesItem - holds correct data`() {
        val item = SeriesItem(
            id = 1,
            name = "Test Series",
            description = "A test series",
            pages = 100,
            format = "MANGA",
            formatLabel = "Manga",
            coverUrl = "https://example.com/cover.jpg"
        )

        assertEquals(1, item.id)
        assertEquals("Test Series", item.name)
        assertEquals("A test series", item.description)
        assertEquals(100, item.pages)
        assertEquals("MANGA", item.format)
        assertEquals("Manga", item.formatLabel)
        assertEquals("https://example.com/cover.jpg", item.coverUrl)
    }

    @Test
    fun `VolumeItem - holds correct data`() {
        val item = VolumeItem(
            id = 1,
            name = "Volume 1",
            number = 1,
            pages = 200,
            chapterCount = 10,
            coverUrl = "https://example.com/vol1.jpg"
        )

        assertEquals(1, item.id)
        assertEquals("Volume 1", item.name)
        assertEquals(1, item.number)
        assertEquals(200, item.pages)
        assertEquals(10, item.chapterCount)
        assertEquals("https://example.com/vol1.jpg", item.coverUrl)
    }

    @Test
    fun `DiscoverNavItem - sealed class variants`() {
        val libraries = DiscoverNavItem.Libraries
        val lib = KavitaLibrary(id = 1, name = "Manga", type = 2)
        val seriesInLib = DiscoverNavItem.SeriesInLibrary(lib)
        val seriesDetail = DiscoverNavItem.SeriesDetail(
            KavitaLibrary(id = 1, name = "Manga", type = 2),
            "One Piece"
        )

        assertTrue(libraries is DiscoverNavItem.Libraries)
        assertTrue(seriesInLib is DiscoverNavItem.SeriesInLibrary)
        assertTrue(seriesDetail is DiscoverNavItem.SeriesDetail)
    }
}
