package com.mimiral.app.data.remote.opds

/**
 * Represents a parsed OPDS feed (Atom-based catalog).
 */
data class OpdsFeed(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val updated: String = "",
    val icon: String? = null,
    val author: OpdsAuthor? = null,
    val entries: List<OpdsEntry> = emptyList(),
    val links: List<OpdsLink> = emptyList(),
    val searchUrl: String? = null,
    val totalResults: Int? = null,
    val itemsPerPage: Int? = null,
    val startIndex: Int? = null
)

/**
 * Represents a single entry in an OPDS feed (a book or navigation link).
 */
data class OpdsEntry(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val content: String = "",
    val updated: String = "",
    val published: String = "",
    val authors: List<OpdsAuthor> = emptyList(),
    val links: List<OpdsLink> = emptyList(),
    val categories: List<String> = emptyList(),
    val language: String? = null,
    val issued: String? = null,
    val publisher: String? = null
) {
    /**
     * Returns acquisition links (links to download the actual book file).
     */
    val acquisitionLinks: List<OpdsLink>
        get() = links.filter { it.isAcquisition }

    /**
     * Returns the thumbnail image link.
     */
    val thumbnailLink: OpdsLink?
        get() = links.find { it.isThumbnail }
            ?: links.find { it.rel.contains("thumbnail", ignoreCase = true) }

    /**
     * Returns the cover image link.
     */
    val coverLink: OpdsLink?
        get() = links.find { it.isCover }
            ?: links.find { it.rel.contains("cover", ignoreCase = true) }

    /**
     * Returns the file extension from the first acquisition link, or null.
     */
    val fileExtension: String?
        get() = acquisitionLinks.firstOrNull()?.let { link ->
            val href = link.href.substringBefore("?")
            val ext = href.substringAfterLast('.', "").lowercase()
            if (ext in SUPPORTED_EXTENSIONS) ext else null
        }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf(
            "epub", "pdf", "mobi", "azw3", "fb2", "djvu",
            "cbz", "cbr", "txt", "rtf", "doc", "docx", "md"
        )
    }
}

/**
 * Represents a link in an OPDS feed entry.
 */
data class OpdsLink(
    val href: String = "",
    val rel: String = "",
    val type: String = "",
    val title: String? = null,
    val hreflang: String? = null,
    val length: String? = null
) {
    val isAcquisition: Boolean
        get() = rel == "http://opds-spec.org/acquisition" ||
            rel == "http://opds-spec.org/acquisition/open-access" ||
            rel.startsWith("http://opds-spec.org/acquisition/")

    val isThumbnail: Boolean
        get() = rel == "http://opds-spec.org/image/thumbnail" ||
            rel == "thumbnail"

    val isCover: Boolean
        get() = rel == "http://opds-spec.org/image" ||
            rel == "cover"

    val isNavigation: Boolean
        get() = rel == "subsection" || type == "application/atom+xml"

    val isSearch: Boolean
        get() = type == "application/opensearchdescription+xml"

    val isOpdsFeed: Boolean
        get() = type == "application/atom+xml;profile=opds-catalog" ||
            type == "application/atom+xml"
}

/**
 * Represents an author in an OPDS feed.
 */
data class OpdsAuthor(
    val name: String = "",
    val uri: String? = null
)

/**
 * Represents an OpenSearch 1.1 description document.
 */
data class OpenSearchDescription(
    val shortName: String = "",
    val description: String = "",
    val tags: String = "",
    val contact: String? = null,
    val urls: List<OpenSearchUrl> = emptyList(),
    val queries: List<OpenSearchQuery> = emptyList(),
    val developer: String? = null,
    val attribution: String = "",
    val syndicationRight: String = "open",
    val adultContent: Boolean = false,
    val language: String = "*",
    val outputEncoding: String = "UTF-8",
    val inputEncoding: String = "UTF-8"
) {
    /**
     * Returns the search URL template for the given type, or null if not found.
     */
    fun getSearchUrl(type: String = "application/atom+xml"): String? {
        return urls.find { it.type == type }?.template
            ?: urls.firstOrNull()?.template
    }
}

/**
 * Represents a URL in an OpenSearch description.
 */
data class OpenSearchUrl(
    val type: String = "",
    val template: String = "",
    val rel: String = "results",
    val indexOffset: Int = 1,
    val pageOffset: Int = 1
)

/**
 * Represents a query in an OpenSearch description.
 */
data class OpenSearchQuery(
    val role: String = "",
    val title: String? = null,
    val totalResults: Int? = null,
    val searchTerms: String? = null,
    val count: Int? = null,
    val startIndex: Int? = null,
    val startPage: Int? = null,
    val language: String? = null,
    val inputEncoding: String? = null,
    val outputEncoding: String? = null
)
