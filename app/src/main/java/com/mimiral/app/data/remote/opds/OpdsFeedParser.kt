package com.mimiral.app.data.remote.opds

import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Parses OPDS 1.2 (Atom XML) catalog feeds into [OpdsFeed] objects.
 *
 * OPDS 1.2 is built on Atom 1.0 syndication format with custom link relations
 * for acquisition, navigation, images, etc.
 */
object OpdsFeedParser {

    private const val ATOM_NS = "http://www.w3.org/2005/Atom"
    private const val OPDS_NS = "http://opds-spec.org/2010/catalog"
    private const val DC_NS = "http://purl.org/dc/terms/"

    fun parse(xml: String): OpdsFeed {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var title = ""
        var id: String? = null
        var subtitle: String? = null
        var icon: String? = null
        var updated: String? = null
        val entries = mutableListOf<OpdsEntry>()
        val navLinks = mutableListOf<OpdsLink>()
        val navGroups = mutableListOf<OpdsGroup>()

        var inEntry = false
        var inGroup = false
        var currentEntry: OpdsEntryBuilder? = null
        var currentGroup: OpdsGroupBuilder? = null
        var currentAuthor: OpdsAuthorBuilder? = null
        var textBuffer = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    textBuffer = StringBuilder()

                    when {
                        tagName == "entry" && !inGroup -> {
                            inEntry = true
                            currentEntry = OpdsEntryBuilder()
                        }
                        tagName == "group" && inGroup -> {
                            // Nested group — skip
                        }
                        tagName == "feed" && inGroup -> {
                            // Nested feed in group — skip
                        }
                        inGroup && tagName == "entry" -> {
                            inEntry = true
                            currentEntry = OpdsEntryBuilder()
                        }
                        tagName == "group" -> {
                            inGroup = true
                            currentGroup = OpdsGroupBuilder()
                        }
                        inEntry && tagName == "author" -> {
                            currentAuthor = OpdsAuthorBuilder()
                        }
                        tagName == "link" -> {
                            val link = parseLink(parser)
                            when {
                                inEntry -> currentEntry?.addLink(link)
                                inGroup -> currentGroup?.addLink(link)
                                else -> navLinks.add(link)
                            }
                        }
                        tagName == "category" -> {
                            val term = parser.getAttributeValue(null, "term")
                            if (term != null) {
                                if (inEntry) currentEntry?.addCategory(term)
                            }
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    textBuffer.append(parser.text ?: "")
                }

                XmlPullParser.END_TAG -> {
                    val text = textBuffer.toString().trim()

                    when {
                        inEntry && tagName == "entry" -> {
                            currentEntry?.let { entries.add(it.build()) }
                            inEntry = false
                            currentEntry = null
                        }
                        inGroup && tagName == "entry" -> {
                            currentEntry?.let { currentGroup?.addEntry(it.build()) }
                            inEntry = false
                            currentEntry = null
                        }
                        tagName == "group" -> {
                            currentGroup?.let { navGroups.add(it.build()) }
                            inGroup = false
                            currentGroup = null
                        }
                        inEntry && tagName == "author" -> {
                            currentAuthor?.let { currentEntry?.addAuthor(it.build()) }
                            currentAuthor = null
                        }
                        inEntry && tagName == "id" -> currentEntry?.id = text
                        inEntry && tagName == "title" -> currentEntry?.title = text
                        inEntry && tagName == "summary" -> currentEntry?.summary = text
                        inEntry && tagName == "content" -> currentEntry?.content = text
                        inEntry && tagName == "updated" -> currentEntry?.updated = text
                        inEntry && tagName == "issued" -> currentEntry?.issued = text
                        inEntry && tagName == "publisher" -> currentEntry?.publisher = text
                        inEntry && tagName == "language" -> currentEntry?.language = text
                        inEntry && tagName == "name" && currentAuthor != null ->
                            currentAuthor?.name = text
                        inEntry && tagName == "uri" && currentAuthor != null ->
                            currentAuthor?.uri = text
                        !inEntry && !inGroup && tagName == "title" -> title = text
                        !inEntry && !inGroup && tagName == "id" -> id = text
                        !inEntry && !inGroup && tagName == "subtitle" -> subtitle = text
                        !inEntry && !inGroup && tagName == "icon" -> icon = text
                        !inEntry && !inGroup && tagName == "updated" -> updated = text
                        inGroup && !inEntry && tagName == "title" ->
                            currentGroup?.title = text
                        inGroup && !inEntry && tagName == "subtitle" ->
                            currentGroup?.subtitle = text
                    }
                }
            }
            eventType = parser.next()
        }

        return OpdsFeed(
            title = title,
            id = id,
            subtitle = subtitle,
            icon = icon,
            updated = updated,
            entries = entries,
            navigationLinks = navLinks,
            navigationGroups = navGroups
        )
    }

    private fun parseLink(parser: XmlPullParser): OpdsLink {
        val href = parser.getAttributeValue(null, "href") ?: ""
        val rel = parser.getAttributeValue(null, "rel")
        val type = parser.getAttributeValue(null, "type")
        val title = parser.getAttributeValue(null, "title")
        val hreflang = parser.getAttributeValue(null, "hreflang")
        val length = parser.getAttributeValue(null, "length")
        return OpdsLink(
            href = href,
            rel = rel,
            type = type,
            title = title,
            hreflang = hreflang,
            length = length
        )
    }

    private class OpdsEntryBuilder {
        var id: String = ""
        var title: String = ""
        var summary: String? = null
        var content: String? = null
        var updated: String? = null
        var issued: String? = null
        var publisher: String? = null
        var language: String? = null
        val authors = mutableListOf<OpdsAuthor>()
        val categories = mutableListOf<String>()
        val allLinks = mutableListOf<OpdsLink>()

        fun addAuthor(author: OpdsAuthor) { authors.add(author) }
        fun addCategory(cat: String) { categories.add(cat) }
        fun addLink(link: OpdsLink) { allLinks.add(link) }

        fun build(): OpdsEntry {
            val thumbnails = allLinks.filter { it.isThumbnail }
            val covers = allLinks.filter { it.isCover }
            val downloads = allLinks.filter { it.isAcquisition }
            val navLinks = allLinks.filter { it.isNavigation }
            val navLink = navLinks.firstOrNull()
            val isNav = navLink != null && downloads.isEmpty()

            return OpdsEntry(
                id = id,
                title = title,
                summary = summary,
                content = content,
                updated = updated,
                authors = authors,
                categories = categories,
                coverImageUrl = covers.firstOrNull()?.href,
                thumbnailUrl = (thumbnails.firstOrNull() ?: covers.firstOrNull())?.href,
                downloadLinks = downloads,
                acquisitionLinks = downloads,
                navigationLink = navLink,
                isNavigationEntry = isNav,
                issued = issued,
                publisher = publisher,
                language = language
            )
        }
    }

    private class OpdsAuthorBuilder {
        var name: String = ""
        var uri: String? = null
        fun build() = OpdsAuthor(name = name, uri = uri)
    }

    private class OpdsGroupBuilder {
        var title: String = ""
        var subtitle: String? = null
        val links = mutableListOf<OpdsLink>()
        val entries = mutableListOf<OpdsEntry>()

        fun addLink(link: OpdsLink) { links.add(link) }
        fun addEntry(entry: OpdsEntry) { entries.add(entry) }

        fun build() = OpdsGroup(
            title = title,
            subtitle = subtitle,
            navigationLinks = links,
            entries = entries
        )
    }
}
