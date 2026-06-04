package com.mimiral.app.data.opds

import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Parse OPDS Atom/XML feeds using Android's built-in XmlPullParser.
 * Handles both OPDS 1.1 and 1.2 Atom feed formats.
 */
object OpdsParser {

    private const val NS_ATOM = "http://www.w3.org/2005/Atom"
    private const val NS_OPDS = "http://opds-spec.org/2010/catalog"
    private const val NS_DCTERMS = "http://purl.org/dc/terms/"
    private const val NS_OPENSEARCH = "http://a9.com/-/spec/opensearch/1.1/"

    fun parse(xml: String, baseUrl: String = ""): OpdsFeed {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var title = ""
        val entries = mutableListOf<OpdsEntry>()
        val navLinks = mutableListOf<OpdsLink>()
        var searchLink: OpdsLink? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name
                when {
                    tag == "title" && !isInEntry(parser) ->
                        title = readText(parser)

                    tag == "entry" && !isInEntry(parser) ->
                        entries.add(parseEntry(parser))

                    tag == "link" && !isInEntry(parser) -> {
                        val link = parseLink(parser)
                        when {
                            isSearchLink(link) -> searchLink = link
                            else -> navLinks.add(link)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return OpdsFeed(
            title = title,
            entries = entries,
            navigationLinks = navLinks,
            searchLink = searchLink
        )
    }

    private fun parseEntry(parser: XmlPullParser): OpdsEntry {
        var id = ""
        var title = ""
        val authors = mutableListOf<String>()
        var summary: String? = null
        var language: String? = null
        var published: String? = null
        var updated: String? = null
        val categories = mutableListOf<String>()
        var coverLink: OpdsLink? = null
        var thumbnailLink: OpdsLink? = null
        val acquisitionLinks = mutableListOf<OpdsLink>()
        val navLinks = mutableListOf<OpdsLink>()

        var depth = parser.depth
        var eventType = parser.next()

        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name
                when (tag) {
                    "id" -> id = readText(parser)
                    "title" -> title = readText(parser)
                    "summary", "content" -> {
                        if (summary == null) summary = readText(parser)
                    }
                    "name" -> authors.add(readText(parser))
                    "language", "dcterms:language" -> {
                        if (language == null) language = readText(parser)
                    }
                    "published", "dcterms:issued" -> {
                        if (published == null) published = readText(parser)
                    }
                    "updated" -> {
                        if (updated == null) updated = readText(parser)
                    }
                    "category" -> {
                        val label = parser.getAttributeValue(null, "label")
                            ?: parser.getAttributeValue(null, "term")
                        if (label != null) categories.add(label)
                    }
                    "link" -> {
                        val link = parseLink(parser)
                        when {
                            OpdsRel.isImage(link.rel) -> {
                                when {
                                    link.rel.contains("thumbnail") -> thumbnailLink = link
                                    coverLink == null -> coverLink = link
                                    else -> {}
                                }
                            }
                            OpdsRel.isAcquisition(link.rel) ->
                                acquisitionLinks.add(link)
                            else -> navLinks.add(link)
                        }
                    }
                    "author" -> {
                        var authorDepth = parser.depth
                        var authorEvent = parser.next()
                        while (!(
                            authorEvent == XmlPullParser.END_TAG &&
                                parser.depth == authorDepth
                            )
                        ) {
                            if (authorEvent == XmlPullParser.START_TAG &&
                                parser.name == "name"
                            ) {
                                authors.add(readText(parser))
                            }
                            authorEvent = parser.next()
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return OpdsEntry(
            id = id,
            title = title,
            authors = authors,
            summary = summary,
            language = language,
            published = published,
            updated = updated,
            categories = categories,
            coverLink = coverLink,
            thumbnailLink = thumbnailLink,
            acquisitionLinks = acquisitionLinks,
            navigationLinks = navLinks
        )
    }

    private fun parseLink(parser: XmlPullParser): OpdsLink {
        val rel = parser.getAttributeValue(null, "rel") ?: ""
        val href = parser.getAttributeValue(null, "href") ?: ""
        val type = parser.getAttributeValue(null, "type") ?: ""
        val title = parser.getAttributeValue(null, "title")
        return OpdsLink(rel = rel, href = href, type = type, title = title)
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        var eventType = parser.next()
        while (eventType != XmlPullParser.END_TAG) {
            if (eventType == XmlPullParser.TEXT) {
                result += parser.text ?: ""
            } else if (eventType == XmlPullParser.ENTITY_REF) {
                result += parser.text ?: ""
            }
            eventType = parser.next()
        }
        return result.trim()
    }

    private fun isInEntry(parser: XmlPullParser): Boolean {
        var depth = parser.depth
        var p = parser
        // Walk up the stack by checking if we're nested inside an <entry> tag
        // Since XmlPullParser is forward-only, we track depth:
        // If current depth > 2 and we're inside an entry, the parent is "entry"
        // This is a heuristic: entry tags are typically at depth 2
        return depth > 2
    }

    private fun isSearchLink(link: OpdsLink): Boolean =
        link.rel == OpdsRel.SEARCH ||
            link.type == "application/opensearchdescription+xml"
}
