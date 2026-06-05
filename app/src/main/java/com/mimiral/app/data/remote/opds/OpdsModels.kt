package com.mimiral.app.data.remote.opds

/**
 * OPDS 1.2 Atom feed representation.
 * Top-level catalog feed containing entries and navigation links.
 */
data class OpdsFeed(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val updated: String? = null,
    val icon: String? = null,
    val entries: List<OpdsEntry> = emptyList(),
    val links: List<OpdsLink> = emptyList(),
    val navigationLinks: List<OpdsLink> = emptyList()
)

/**
 * A single entry in an OPDS catalog feed.
 * Represents either a book (acquisition entry) or a sub-catalog (navigation entry).
 */
data class OpdsEntry(
    val id: String,
    val title: String,
    val summary: String? = null,
    val content: String? = null,
    val updated: String? = null,
    val published: String? = null,
    val authors: List<OpdsAuthor> = emptyList(),
    val categories: List<OpdsCategory> = emptyList(),
    val links: List<OpdsLink> = emptyList(),
    val dcSubjects: List<String> = emptyList(),
    val dcPublisher: String? = null,
    val dcIssued: String? = null,
    val dcLanguage: String? = null,
    val dctermsExtent: String? = null
) {
    /**
     * Returns true if this entry has acquisition links (i.e., it's a downloadable book).
     */
    val isAcquisition: Boolean
        get() = links.any { it.isAcquisition }

    /**
     * Returns the thumbnail image URL if available.
     */
    val thumbnailUrl: String?
        get() = links.firstOrNull { it.isThumbnail }?.href

    /**
     * Returns the cover image URL if available.
     */
    val coverUrl: String?
        get() = links.firstOrNull { it.isCover }?.href

    /**
     * Returns all acquisition (download) links grouped by MIME type.
     */
    val acquisitionLinks: List<OpdsLink>
        get() = links.filter { it.isAcquisition }

    /**
     * Returns the first acquisition link with the best available format.
     * Preference order: EPUB, PDF, other supported formats.
     */
    val preferredAcquisitionLink: OpdsLink?
        get() {
            val acquisitions = acquisitionLinks
            if (acquisitions.isEmpty()) return null
            // Prefer EPUB, then PDF, then first available
            return acquisitions.firstOrNull {
                it.type?.contains("epub", ignoreCase = true) == true
            } ?: acquisitions.firstOrNull {
                it.type?.contains("pdf", ignoreCase = true) == true
            } ?: acquisitions.first()
        }
}

/**
 * Author information for an OPDS entry.
 */
data class OpdsAuthor(
    val name: String,
    val uri: String? = null
)

/**
 * Link element in OPDS/Atom feed.
 * Follows OPDS 1.2 link relation conventions.
 */
data class OpdsLink(
    val href: String,
    val rel: String? = null,
    val type: String? = null,
    val title: String? = null,
    val properties: Map<String, String> = emptyMap()
) {
    /** Link relation constants from OPDS spec and Atom. */
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

    val isThumbnail: Boolean
        get() = rel == REL_THUMBNAIL

    val isCover: Boolean
        get() = rel == REL_COVER

    val isAcquisition: Boolean
        get() = rel != null && rel.startsWith(REL_ACQUISITION_PREFIX)

    val isOpenAccess: Boolean
        get() = rel == REL_OPEN_ACCESS

    val isNavigation: Boolean
        get() = type?.contains("application/atom+xml") == true ||
            type?.contains("application/opds+json") == true

    val formatLabel: String
        get() = when {
            type == null -> "Download"
            type.contains("epub", ignoreCase = true) -> "EPUB"
            type.contains("pdf", ignoreCase = true) -> "PDF"
            type.contains("mobi", ignoreCase = true) -> "MOBI"
            type.contains("application/atom", ignoreCase = true) -> "Feed"
            else -> type.substringAfterLast("/").uppercase()
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
 * OPDS feed level constants.
 */
object OpdsConstants {
    const val ATOM_NAMESPACE = "http://www.w3.org/2005/Atom"
    const val DC_NAMESPACE = "http://purl.org/dc/elements/1.1/"
    const val DCTERMS_NAMESPACE = "http://purl.org/dc/terms/"
    const val OP_DS_NAMESPACE = "http://opds-spec.org/2010/catalog"
    const val THUMB_NS = "http://opds-spec.org/image/thumbnail"
}
