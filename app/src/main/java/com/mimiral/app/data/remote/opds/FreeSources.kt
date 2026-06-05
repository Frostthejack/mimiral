package com.mimiral.app.data.remote.opds

/**
 * Built-in free book sources for the Free Sources browser.
 *
 * Each source provides a curated OPDS catalog endpoint with known
 * OpenSearch support for searching.
 */
enum class FreeSource(
    val displayName: String,
    val description: String,
    val iconUrl: String? = null
) {
    PROJECT_GUTENBERG(
        displayName = "Project Gutenberg",
        description = "Over 70,000 free eBooks from the world's largest single collection of free books."
    ),
    STANDARD_EBOOKS(
        displayName = "Standard Ebooks",
        description = "Beautifully formatted free eBooks with professional typography and design."
    ),
    OPEN_LIBRARY(
        displayName = "Open Library",
        description = "An open, editable library catalog, building towards a web page for every book ever published."
    );

    /** The root OPDS catalog URL for this source. */
    val catalogUrl: String
        get() = when (this) {
            PROJECT_GUTENBERG -> "https://m.gutenberg.org/ebooks.opds/"
            STANDARD_EBOOKS -> "https://standardebooks.org/opds"
            OPEN_LIBRARY -> "https://openlibrary.org/search/inside.opds/"
        }

    /** Known OpenSearch URL template for direct search (skips discovery). */
    val searchTemplate: String?
        get() = when (this) {
            // Project Gutenberg uses a simple query parameter approach
            PROJECT_GUTENBERG -> null // Uses browse-based navigation
            STANDARD_EBOOKS -> null // Will use OpenSearch discovery
            OPEN_LIBRARY -> null // Will use OpenSearch discovery
        }

    companion object {
        /** Returns all built-in free sources. */
        fun all(): List<FreeSource> = entries.toList()
    }
}
