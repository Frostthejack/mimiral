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
    val entries: List<OpdsEntry> = emptyList(),
    val navigationLinks: List<OpdsLink> = emptyList(),
    val navigationGroups: List<OpdsGroup> = emptyList()
)

/**
 * Represents a single entry in an OPDS feed (book or navigation link).
 */
data class OpdsEntry(
    val id: String,
    val title: String,
    val summary: String? = null,
    val content: String? = null,
    val updated: String? = null,
    val authors: List<OpdsAuthor> = emptyList(),
    val categories: List<String> = emptyList(),
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
    val ratingCount: Int? = null
)

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
    val length: String? = null
) {
    val isNavigation: Boolean
        get() = rel == null ||
            rel == "http://opds-spec.org/facet" ||
            rel.contains("navigation", ignoreCase = true) ||
            type?.contains("application/atom+xml") == true

    val isAcquisition: Boolean
        get() = rel?.startsWith("http://opds-spec.org/acquisition") == true ||
            rel?.contains("open-access") == true

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
}

/**
 * An OPDS navigation/acquisition group (OPDS 1.2 grouped navigation).
 */
data class OpdsGroup(
    val title: String,
    val subtitle: String? = null,
    val navigationLinks: List<OpdsLink> = emptyList(),
    val entries: List<OpdsEntry> = emptyList()
)
