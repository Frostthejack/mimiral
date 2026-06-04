package com.mimiral.app.data.opds

/**
 * Represents a parsed OPDS catalog feed.
 */
data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    val navigationLinks: List<OpdsLink> = emptyList(),
    val searchLink: OpdsLink? = null
)

/**
 * Represents a single entry (book) in an OPDS feed.
 */
data class OpdsEntry(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val summary: String? = null,
    val language: String? = null,
    val published: String? = null,
    val updated: String? = null,
    val categories: List<String> = emptyList(),
    val coverLink: OpdsLink? = null,
    val thumbnailLink: OpdsLink? = null,
    val acquisitionLinks: List<OpdsLink> = emptyList(),
    val navigationLinks: List<OpdsLink> = emptyList()
)

/**
 * Represents a link in an OPDS feed entry.
 */
data class OpdsLink(
    val rel: String,
    val href: String,
    val type: String,
    val title: String? = null
)

/**
 * Well-known OPDS link relationship URIs.
 */
object OpdsRel {
    const val CATALOG_FEED =
        "http://opds-spec.org/catalog"
    const val ACQUISITION =
        "http://opds-spec.org/acquisition"
    const val ACQUISITION_OPEN =
        "http://opds-spec.org/acquisition/open-access"
    const val ACQUISITION_BORROW =
        "http://opds-spec.org/acquisition/borrow"
    const val ACQUISITION_BUY =
        "http://opds-spec.org/acquisition/buy"
    const val ACQUISITION_SAMPLE =
        "http://opds-spec.org/acquisition/sample"
    const val COVER =
        "http://opds-spec.org/image"
    const val THUMBNAIL =
        "http://opds-spec.org/image/thumbnail"
    const val SELF = "self"
    const val START = "start"
    const val UP = "up"
    const val NEXT = "next"
    const val PREVIOUS = "previous"
    const val SEARCH = "http://opds-spec.org/search"
    const val FACET = "http://opds-spec.org/facet"

    fun isAcquisition(rel: String): Boolean =
        rel == ACQUISITION || rel == ACQUISITION_OPEN || rel == ACQUISITION_BORROW ||
            rel == ACQUISITION_BUY || rel == ACQUISITION_SAMPLE ||
            rel.startsWith("http://opds-spec.org/acquisition")

    fun isImage(rel: String): Boolean =
        rel == COVER || rel == THUMBNAIL ||
            rel.startsWith("http://opds-spec.org/image")
}

/**
 * Default OPDS catalogs pre-configured in the app.
 */
object DefaultOpdsCatalogs {
    val STANDARD_EBOOKS = OpdsCatalogInfo(
        name = "Standard Ebooks",
        url = "https://standardebooks.org/opds/all",
        description = "Free, carefully produced ebook editions of public domain works."
    )

    val OPEN_LIBRARY = OpdsCatalogInfo(
        name = "Open Library",
        url = "https://openlibrary.org/opds",
        description = "Open Library — one web page for every book ever published."
    )

    val ALL = listOf(STANDARD_EBOOKS, OPEN_LIBRARY)
}

data class OpdsCatalogInfo(
    val name: String,
    val url: String,
    val description: String
)
