package com.mimiral.app.data.remote.opds

/**
 * Represents a parsed OPDS 1.2 catalog feed.
 */
data class OpdsFeed(
    val title: String,
    val id: String? = null,
    val subtitle: String? = null,
    val icon: String? = null,
    val updated: String? = null,
    val author: OpdsAuthor? = null,
    val entries: List<OpdsEntry> = emptyList(),
    val navigationLinks: List<OpdsLink> = emptyList(),
    val navigationGroups: List<OpdsGroup> = emptyList(),
    val searchUrl: String? = null,
    val totalResults: Int? = null,
    val itemsPerPage: Int? = null,
    val startIndex: Int? = null,
    val links: List<OpdsLink> = emptyList()
)

/**
 * Represents a single entry in an OPDS feed (book or navigation link).
 * Supports both direct-field access (for UI) and links-based access (for parsers).
 */
data class OpdsEntry(
    val id: String,
    val title: String,
    val summary: String? = null,
    val content: String? = null,
    val updated: String? = null,
    val published: String? = null,
    val authors: List<OpdsAuthor> = emptyList(),
    val categories: List<String> = emptyList(),
    // Direct fields (used by UI code)
    val coverImageUrl: String? = null,
    val thumbnailUrl: String? = null,
    val downloadLinks: List<OpdsLink> = emptyList(),
    val acquisitionLinks: List<OpdsLink> = emptyList(),
    val navigationLink: OpdsLink? = null,
    val isNavigationEntry: Boolean = false,
    val issued: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val rating: Float? = null,
    val ratingCount: Int? = null,
    // Parser fields (used by OpdsParser/OpdsXmlParser)
    val links: List<OpdsLink> = emptyList(),
    val dcSubjects: List<String> = emptyList(),
    val dcPublisher: String? = null,
    val dcIssued: String? = null,
    val dcLanguage: String? = null,
    val dctermsExtent: String? = null
) {
    /** Returns true if this entry has acquisition links (i.e., it's a downloadable book). */
    val isAcquisition: Boolean
        get() = acquisitionLinks.isNotEmpty() || links.any { it.isAcquisition }

    /** Returns the first acquisition link with the best available format. */
    val preferredAcquisitionLink: OpdsLink?
        get() {
            val acquisitions = acquisitionLinks.ifEmpty { links.filter { it.isAcquisition } }
            if (acquisitions.isEmpty()) return null
            return acquisitions.firstOrNull { it.type?.contains("epub", ignoreCase = true) == true }
                ?: acquisitions.firstOrNull { it.type?.contains("pdf", ignoreCase = true) == true }
                ?: acquisitions.first()
        }
}

/**
 * Author info in an OPDS entry.
 */
data class OpdsAuthor(
    val name: String,
    val uri: String? = null
)

/**
 * A link in an OPDS feed (navigation, acquisition, image, etc.).
 */
data class OpdsLink(
    val href: String,
    val rel: String? = null,
    val type: String? = null,
    val title: String? = null,
    val hreflang: String? = null,
    val length: String? = null,
    val properties: Map<String, String> = emptyMap()
) {
    val isNavigation: Boolean
        get() {
            val notEpub = type?.contains("application/epub+zip") != true
            val notImage = type?.startsWith("image/") != true
            return (rel == null && notEpub && notImage && !isAcquisition) ||
                rel == "http://opds-spec.org/facet" ||
                rel?.contains("navigation", ignoreCase = true) == true ||
                type?.contains("application/atom+xml") == true
        }

    val isAcquisition: Boolean
        get() = rel?.startsWith("http://opds-spec.org/acquisition") == true ||
            rel?.contains("open-access") == true

    val isOpenAccess: Boolean
        get() = rel?.contains("open-access") == true

    val isImage: Boolean
        get() = type?.startsWith("image/") == true ||
            rel?.contains("image") == true ||
            rel?.contains("thumbnail") == true

    val isThumbnail: Boolean
        get() = rel?.contains("thumbnail") == true ||
            rel?.let {
                it.contains("http://opds-spec.org/image/thumbnail") ||
                    it.contains("thumbnail")
            } == true

    val isCover: Boolean
        get() = rel?.contains("cover") == true ||
            rel?.let {
                it.contains("http://opds-spec.org/image") &&
                    !it.contains("thumbnail")
            } == true

    val isSearch: Boolean
        get() = type == "application/opensearchdescription+xml"

    val fileExtension: String?
        get() {
            val cleanType = type ?: return null
            return when {
                cleanType.contains("epub") -> ".epub"
                cleanType.contains("pdf") -> ".pdf"
                cleanType.contains("mobi") -> ".mobi"
                cleanType.contains("application/atom") -> ""
                else -> null
            }
        }

    val formatName: String?
        get() = when {
            type == null -> null
            type.contains("epub") -> "EPUB"
            type.contains("pdf") -> "PDF"
            type.contains("mobi") -> "MOBI"
            type.contains("application/atom") -> null
            else -> type.substringAfterLast("/").uppercase()
        }

    companion object {
        const val REL_SELF = "self"
        const val REL_START = "start"
        const val REL_UP = "up"
        const val REL_NEXT = "next"
        const val REL_PREVIOUS = "previous"
        const val REL_FIRST = "first"
        const val REL_LAST = "last"
        const val REL_SUBSECTION = "subsection"
        const val REL_RELATED = "related"
        const val REL_THUMBNAIL = "http://opds-spec.org/image/thumbnail"
        const val REL_COVER = "http://opds-spec.org/image"
        const val REL_ACQUISITION_PREFIX = "http://opds-spec.org/acquisition"
        const val REL_OPEN_ACCESS = "http://opds-spec.org/acquisition/open-access"
        const val REL_BORROW = "http://opds-spec.org/acquisition/borrow"
        const val REL_BUY = "http://opds-spec.org/acquisition/buy"
        const val REL_SAMPLE = "http://opds-spec.org/acquisition/sample"
    }
}

/**
 * Dublin Core / DCMI metadata category.
 */
data class OpdsCategory(
    val term: String,
    val scheme: String? = null,
    val label: String? = null
)

/**
 * An OPDS navigation/acquisition group (OPDS 1.2 grouped navigation).
 */
data class OpdsGroup(
    val title: String,
    val subtitle: String? = null,
    val navigationLinks: List<OpdsLink> = emptyList(),
    val entries: List<OpdsEntry> = emptyList()
)

/**
 * OPDS feed level constants.
 */
object OpdsConstants {
    const val ATOM_NAMESPACE = "http://www.w3.org/2005/Atom"
    const val DC_NAMESPACE = "http://purl.org/dc/elements/1.1/"
    const val DCTERMS_NAMESPACE = "http://purl.org/dc/terms/"
    const val OP_DS_NAMESPACE = "http://opds-spec.org/2010/catalog"
    const val THUMB_NS = "http://opds-spec.org/image/thumbnail"
}

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
