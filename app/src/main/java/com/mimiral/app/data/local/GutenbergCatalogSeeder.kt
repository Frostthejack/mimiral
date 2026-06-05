package com.mimiral.app.data.local

import com.mimiral.app.data.local.dao.OpdsCatalogDao
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-configures Project Gutenberg as a default free OPDS catalog.
 *
 * Project Gutenberg OPDS feed structure:
 * - Top-level: https://m.gutenberg.org/ebooks.opds/
 * - Search by popularity: /ebooks/search.opds/?sort_order=downloads
 * - Search by release date: /ebooks/search.opds/?sort_order=release_date
 * - Random: /ebooks/search.opds/?sort_order=random
 * - Individual book: /ebooks/{id}.opds/
 *
 * Pagination: feed-level link with rel="next" and start_index parameter.
 * Metadata: dc:subject (bookshelf/genre), dc:language, dc:author in
 * individual book feeds.
 */
@Singleton
class GutenbergCatalogSeeder @Inject constructor(
    private val catalogDao: OpdsCatalogDao
) {

    companion object {
        const val GUTENBERG_NAME = "Project Gutenberg"
        const val GUTENBERG_URL = "https://m.gutenberg.org/ebooks.opds/"
        const val GUTENBERG_DESCRIPTION = "Free eBooks since 1971."
    }

    /**
     * Seeds the default Gutenberg catalog if no catalogs exist.
     * Called on first launch or when catalog list is empty.
     */
    suspend fun seedIfEmpty() {
        val existing = catalogDao.getActiveCatalogCount()
        if (existing == 0) {
            val gutenberg = OpdsCatalogEntity(
                name = GUTENBERG_NAME,
                url = GUTENBERG_URL,
                isActive = true
            )
            catalogDao.insertCatalog(gutenberg)
        }
    }

    /**
     * Returns true if the given catalog is the Project Gutenberg catalog.
     */
    fun isGutenbergCatalog(catalog: OpdsCatalogEntity): Boolean {
        return catalog.name == GUTENBERG_NAME ||
            catalog.url.contains("gutenberg.org", ignoreCase = true)
    }
}
