package com.mimiral.app.data.remote.opds

import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Parses OPDS (Atom-based) and OpenSearch 1.1 XML feeds.
 *
 * Uses Android's built-in XmlPullParser — no external dependencies needed.
 * Handles both OPDS acquisition catalogs and OpenSearch description documents.
 */
object OpdsXmlParser {

    private const val NS_ATOM = "http://www.w3.org/2005/Atom"
    private const val NS_OPDS = "http://opds-spec.org/2010/catalog"
    private const val NS_DC = "http://purl.org/dc/terms/"
    private const val NS_OPENSEARCH = "http://a9.com/-/spec/opensearch/1.1/"

    // ---- Public API ----

    /**
     * Parse an OPDS feed (Atom) from XML string.
     */
    fun parseFeed(xml: String): OpdsFeed {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        return parseFeed(parser)
    }

    /**
     * Parse an OpenSearch 1.1 description document from XML string.
     */
    fun parseOpenSearchDescription(xml: String): OpenSearchDescription {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        return parseOpenSearch(parser)
    }

    // ---- OPDS Feed Parser ----

    private fun parseFeed(parser: XmlPullParser): OpdsFeed {
        var id = ""
        var title = ""
        var subtitle = ""
        var updated = ""
        var icon: String? = null
        var author: OpdsAuthor? = null
        val entries = mutableListOf<OpdsEntry>()
        val links = mutableListOf<OpdsLink>()
        var searchUrl: String? = null
        var totalResults: Int? = null
        var itemsPerPage: Int? = null
        var startIndex: Int? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name
                val ns = parser.namespace ?: ""

                when {
                    tag == "id" -> id = parser.nextText()
                    tag == "title" && ns == NS_ATOM -> title = parser.nextText()
                    tag == "subtitle" -> subtitle = parser.nextText()
                    tag == "updated" -> updated = parser.nextText()
                    tag == "icon" -> icon = parser.nextText()
                    tag == "author" -> author = parseAuthor(parser)
                    tag == "entry" -> entries.add(parseEntry(parser))
                    tag == "link" -> {
                        val link = parseLink(parser)
                        links.add(link)
                        if (link.isSearch) {
                            searchUrl = link.href
                        }
                    }
                    tag == "totalResults" && ns == NS_OPENSEARCH ->
                        totalResults = parser.nextText().toIntOrNull()
                    tag == "itemsPerPage" && ns == NS_OPENSEARCH ->
                        itemsPerPage = parser.nextText().toIntOrNull()
                    tag == "startIndex" && ns == NS_OPENSEARCH ->
                        startIndex = parser.nextText().toIntOrNull()
                }
            }
            eventType = parser.next()
        }

        return OpdsFeed(
            id = id,
            title = title,
            subtitle = subtitle,
            updated = updated,
            icon = icon,
            author = author,
            entries = entries,
            links = links,
            searchUrl = searchUrl,
            totalResults = totalResults,
            itemsPerPage = itemsPerPage,
            startIndex = startIndex
        )
    }

    private fun parseEntry(parser: XmlPullParser): OpdsEntry {
        var id = ""
        var title = ""
        var summary = ""
        var content = ""
        var updated = ""
        var published = ""
        val authors = mutableListOf<OpdsAuthor>()
        val links = mutableListOf<OpdsLink>()
        val categories = mutableListOf<String>()
        var language: String? = null
        var issued: String? = null
        var publisher: String? = null

        var depth = parser.depth
        var eventType = parser.next()

        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name
                val ns = parser.namespace ?: ""

                when {
                    tag == "id" -> id = parser.nextText()
                    tag == "title" && ns == NS_ATOM -> title = parser.nextText()
                    tag == "summary" -> summary = parser.nextText()
                    tag == "content" -> content = parser.nextText()
                    tag == "updated" -> updated = parser.nextText()
                    tag == "published" -> published = parser.nextText()
                    tag == "author" -> authors.add(parseAuthor(parser))
                    tag == "link" -> links.add(parseLink(parser))
                    tag == "category" -> {
                        val term = parser.getAttributeValue(null, "term")
                        if (term != null) categories.add(term)
                    }
                    tag == "language" && ns == NS_DC -> language = parser.nextText()
                    tag == "issued" && ns == NS_DC -> issued = parser.nextText()
                    tag == "publisher" && ns == NS_DC -> publisher = parser.nextText()
                    tag == "format" && ns == NS_DC -> {
                        // dcterms:format — could be used for MIME type
                    }
                }
            }
            eventType = parser.next()
        }

        // Extract UI-relevant fields from links
        val coverImageUrl = links.firstOrNull { it.isCover }?.href
        val thumbnailUrl = links.firstOrNull { it.isThumbnail }?.href
        val downloadLinks = links.filter { it.isAcquisition }
        val acquisitionLinks = links.filter { it.isAcquisition }
        val navigationLink = links.firstOrNull { it.isNavigation }
        val isNavigationEntry = navigationLink != null && acquisitionLinks.isEmpty()

        return OpdsEntry(
            id = id,
            title = title,
            summary = summary,
            content = content,
            updated = updated,
            published = published,
            authors = authors,
            categories = categories,
            coverImageUrl = coverImageUrl,
            thumbnailUrl = thumbnailUrl,
            downloadLinks = downloadLinks,
            acquisitionLinks = acquisitionLinks,
            navigationLink = navigationLink,
            isNavigationEntry = isNavigationEntry,
            issued = issued,
            publisher = publisher,
            language = language,
            links = links
        )
    }

    private fun parseAuthor(parser: XmlPullParser): OpdsAuthor {
        var name = ""
        var uri: String? = null

        var depth = parser.depth
        var eventType = parser.next()

        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "name" -> name = parser.nextText()
                    "uri" -> uri = parser.nextText()
                }
            }
            eventType = parser.next()
        }

        return OpdsAuthor(name = name, uri = uri)
    }

    private fun parseLink(parser: XmlPullParser): OpdsLink {
        val href = parser.getAttributeValue(null, "href") ?: ""
        val rel = parser.getAttributeValue(null, "rel") ?: ""
        val type = parser.getAttributeValue(null, "type") ?: ""
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

    // ---- OpenSearch Description Parser ----

    private fun parseOpenSearch(parser: XmlPullParser): OpenSearchDescription {
        var shortName = ""
        var description = ""
        var tags = ""
        var contact: String? = null
        val urls = mutableListOf<OpenSearchUrl>()
        val queries = mutableListOf<OpenSearchQuery>()
        var developer: String? = null
        var attribution = ""
        var syndicationRight = "open"
        var adultContent = false
        var language = "*"
        var outputEncoding = "UTF-8"
        var inputEncoding = "UTF-8"

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name

                when (tag) {
                    "ShortName" -> shortName = parser.nextText()
                    "Description" -> description = parser.nextText()
                    "Tags" -> tags = parser.nextText()
                    "Contact" -> contact = parser.nextText()
                    "Url" -> urls.add(parseOpenSearchUrl(parser))
                    "Query" -> queries.add(parseOpenSearchQuery(parser))
                    "Developer" -> developer = parser.nextText()
                    "Attribution" -> attribution = parser.nextText()
                    "SyndicationRight" -> syndicationRight = parser.nextText()
                    "AdultContent" -> {
                        val text = parser.nextText()
                        adultContent = text == "true" || text == "1"
                    }
                    "Language" -> language = parser.nextText()
                    "OutputEncoding" -> outputEncoding = parser.nextText()
                    "InputEncoding" -> inputEncoding = parser.nextText()
                }
            }
            eventType = parser.next()
        }

        return OpenSearchDescription(
            shortName = shortName,
            description = description,
            tags = tags,
            contact = contact,
            urls = urls,
            queries = queries,
            developer = developer,
            attribution = attribution,
            syndicationRight = syndicationRight,
            adultContent = adultContent,
            language = language,
            outputEncoding = outputEncoding,
            inputEncoding = inputEncoding
        )
    }

    private fun parseOpenSearchUrl(parser: XmlPullParser): OpenSearchUrl {
        val type = parser.getAttributeValue(null, "type") ?: ""
        val template = parser.getAttributeValue(null, "template") ?: ""
        val rel = parser.getAttributeValue(null, "rel") ?: "results"
        val indexOffset = parser.getAttributeValue(null, "indexOffset")?.toIntOrNull() ?: 1
        val pageOffset = parser.getAttributeValue(null, "pageOffset")?.toIntOrNull() ?: 1

        return OpenSearchUrl(
            type = type,
            template = template,
            rel = rel,
            indexOffset = indexOffset,
            pageOffset = pageOffset
        )
    }

    private fun parseOpenSearchQuery(parser: XmlPullParser): OpenSearchQuery {
        val role = parser.getAttributeValue(null, "role") ?: ""
        val title = parser.getAttributeValue(null, "title")
        val totalResults = parser.getAttributeValue(null, "totalResults")?.toIntOrNull()
        val searchTerms = parser.getAttributeValue(null, "searchTerms")
        val count = parser.getAttributeValue(null, "count")?.toIntOrNull()
        val startIndex = parser.getAttributeValue(null, "startIndex")?.toIntOrNull()
        val startPage = parser.getAttributeValue(null, "startPage")?.toIntOrNull()
        val language = parser.getAttributeValue(null, "language")
        val inputEncoding = parser.getAttributeValue(null, "inputEncoding")
        val outputEncoding = parser.getAttributeValue(null, "outputEncoding")

        return OpenSearchQuery(
            role = role,
            title = title,
            totalResults = totalResults,
            searchTerms = searchTerms,
            count = count,
            startIndex = startIndex,
            startPage = startPage,
            language = language,
            inputEncoding = inputEncoding,
            outputEncoding = outputEncoding
        )
    }
}
