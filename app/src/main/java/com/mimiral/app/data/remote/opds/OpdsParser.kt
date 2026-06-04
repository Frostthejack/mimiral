package com.mimiral.app.data.remote.opds

import android.util.Log
import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * OPDS 1.2 Atom feed parser.
 *
 * Parses OPDS catalog feeds (Atom XML) into structured [OpdsFeed] and [OpdsEntry] objects.
 * Implements the OPDS 1.2 specification for acquisition and navigation feeds.
 *
 * Uses DOM parsing via javax.xml (available in both Android runtime and JVM unit tests).
 */
class OpdsParser {

    companion object {
        private const val TAG = "OpdsParser"

        private val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    }

    /**
     * Parse an OPDS Atom feed from an InputStream.
     */
    fun parseFeed(
        inputStream: InputStream,
        baseHref: String? = null
    ): OpdsFeed {
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(inputStream)
        return parseFeedDocument(doc, baseHref)
    }

    /**
     * Parse an OPDS Atom feed from a String.
     */
    fun parseFeedString(
        xml: String,
        baseHref: String? = null
    ): OpdsFeed {
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(InputSource(StringReader(xml)))
        return parseFeedDocument(doc, baseHref)
    }

    private fun parseFeedDocument(
        doc: Document,
        baseHref: String?
    ): OpdsFeed {
        val feedElement = doc.getElementsByTagName("feed").item(0) as? Element
            ?: return OpdsFeed(id = "", title = "")

        val entries = mutableListOf<OpdsEntry>()
        val links = mutableListOf<OpdsLink>()
        val navigationLinks = mutableListOf<OpdsLink>()

        val childNodes = feedElement.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                when (element.tagName) {
                    "id" -> { /* handled below */ }
                    "title" -> { /* handled below */ }
                }
            }
        }

        val feedId = feedElement.getTextContentByTag("id") ?: ""
        val feedTitle = feedElement.getTextContentByTag("title") ?: ""
        val feedSubtitle = feedElement.getTextContentByTag("subtitle")
        val feedUpdated = feedElement.getTextContentByTag("updated")
        val feedIcon = feedElement.getTextContentByTag("icon")

        // Parse feed-level links
        val linkElements = feedElement.getElementsByTagName("link")
        for (i in 0 until linkElements.length) {
            val linkElement = linkElements.item(i) as? Element ?: continue
            val link = parseLinkElement(linkElement, baseHref)
            if (link != null) {
                links.add(link)
                if (link.isNavigation) {
                    navigationLinks.add(link)
                }
            }
        }

        // Parse entries
        val entryElements = feedElement.getElementsByTagName("entry")
        for (i in 0 until entryElements.length) {
            val entryElement = entryElements.item(i) as? Element ?: continue
            entries.add(parseEntryElement(entryElement, baseHref))
        }

        return OpdsFeed(
            id = feedId,
            title = feedTitle,
            subtitle = feedSubtitle,
            updated = feedUpdated,
            icon = feedIcon,
            entries = entries,
            links = links,
            navigationLinks = navigationLinks
        )
    }

    private fun parseEntryElement(
        entryElement: Element,
        baseHref: String?
    ): OpdsEntry {
        val entryId = entryElement.getTextContentByTag("id") ?: ""
        val entryTitle = entryElement.getTextContentByTag("title") ?: ""
        val entrySummary = entryElement.getTextContentByTag("summary")
        val entryContent = entryElement.getTextContentByTag("content")
        val entryUpdated = entryElement.getTextContentByTag("updated")
        val entryPublished = entryElement.getTextContentByTag("published")

        // Parse authors
        val authors = mutableListOf<OpdsAuthor>()
        val authorElements = entryElement.getElementsByTagName("author")
        for (i in 0 until authorElements.length) {
            val authorElement = authorElements.item(i) as? Element ?: continue
            val name = authorElement.getTextContentByTag("name") ?: continue
            val uri = authorElement.getTextContentByTag("uri")
            authors.add(OpdsAuthor(name = name, uri = uri))
        }

        // Parse categories
        val categories = mutableListOf<OpdsCategory>()
        val categoryElements = entryElement.getElementsByTagName("category")
        for (i in 0 until categoryElements.length) {
            val catElement = categoryElements.item(i) as? Element ?: continue
            val term = catElement.getAttribute("term") ?: continue
            val scheme = catElement.getAttribute("scheme")
            val label = catElement.getAttribute("label")
            categories.add(OpdsCategory(term = term, scheme = scheme, label = label))
        }

        // Parse links
        val links = mutableListOf<OpdsLink>()
        val linkElements = entryElement.getElementsByTagName("link")
        for (i in 0 until linkElements.length) {
            val linkElement = linkElements.item(i) as? Element ?: continue
            val link = parseLinkElement(linkElement, baseHref)
            if (link != null) links.add(link)
        }

        // Parse Dublin Core metadata
        val dcSubjects = mutableListOf<String>()
        val dcSubjectElements = entryElement.getElementsByTagName("dc:subject")
        for (i in 0 until dcSubjectElements.length) {
            val text = dcSubjectElements.item(i).textContent
            if (!text.isNullOrBlank()) dcSubjects.add(text)
        }
        // Also try without namespace prefix
        if (dcSubjects.isEmpty()) {
            val subjectElements = entryElement.getElementsByTagName("subject")
            for (i in 0 until subjectElements.length) {
                val text = subjectElements.item(i).textContent
                if (!text.isNullOrBlank()) dcSubjects.add(text)
            }
        }

        val dcPublisher = entryElement.getTextContentByTag("dc:publisher")
            ?: entryElement.getTextContentByTag("publisher")
        val dcIssued = entryElement.getTextContentByTag("dc:issued")
            ?: entryElement.getTextContentByTag("issued")
        val dcLanguage = entryElement.getTextContentByTag("dc:language")
            ?: entryElement.getTextContentByTag("language")
        val dctermsExtent = entryElement.getTextContentByTag("dcterms:extent")
            ?: entryElement.getTextContentByTag("extent")

        return OpdsEntry(
            id = entryId,
            title = entryTitle,
            summary = entrySummary,
            content = entryContent,
            updated = entryUpdated,
            published = entryPublished,
            authors = authors,
            categories = categories,
            links = links,
            dcSubjects = dcSubjects,
            dcPublisher = dcPublisher,
            dcIssued = dcIssued,
            dcLanguage = dcLanguage,
            dctermsExtent = dctermsExtent
        )
    }

    private fun parseLinkElement(
        linkElement: Element,
        baseHref: String?
    ): OpdsLink? {
        val href = linkElement.getAttribute("href")
        if (href.isNullOrBlank()) {
            Log.w(TAG, "Skipping link with empty href")
            return null
        }

        val rel = linkElement.getAttribute("rel")
        val type = linkElement.getAttribute("type")
        val title = linkElement.getAttribute("title")

        val properties = mutableMapOf<String, String>()

        // Parse OPDS acquisition properties
        val indirectElements = linkElement.getElementsByTagName("opds:indirectAcquisition")
        for (i in 0 until indirectElements.length) {
            val indirectElement = indirectElements.item(i) as? Element ?: continue
            val indirectType = indirectElement.getAttribute("type")
            if (indirectType != null) {
                properties["indirectAcquisition"] = indirectType
            }
        }

        // Parse OPDS price
        val priceElements = linkElement.getElementsByTagName("opds:price")
        for (i in 0 until priceElements.length) {
            val priceElement = priceElements.item(i) as? Element ?: continue
            val currency = priceElement.getAttribute("currencycode")
            val priceText = priceElement.textContent
            if (currency != null && priceText != null) {
                properties["price:$currency"] = priceText
            }
        }

        val resolvedHref = if (baseHref != null) {
            resolveUrl(href, baseHref)
        } else {
            href
        }

        return OpdsLink(
            href = resolvedHref,
            rel = rel,
            type = type,
            title = title,
            properties = properties
        )
    }

    private fun Element.getTextContentByTag(tagName: String): String? {
        val elements = this.getElementsByTagName(tagName)
        if (elements.length > 0) {
            val text = elements.item(0).textContent
            return if (text.isNullOrBlank()) null else text
        }
        return null
    }

    private fun resolveUrl(href: String, baseHref: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href
        }
        val base = if (baseHref.endsWith("/")) baseHref else "$baseHref/"
        return if (href.startsWith("/")) {
            val protocolEnd = base.indexOf("://")
            if (protocolEnd > 0) {
                val nextSlash = base.indexOf("/", protocolEnd + 3)
                if (nextSlash > 0) {
                    base.substring(0, nextSlash) + href
                } else {
                    base + href.substring(1)
                }
            } else {
                base + href.substring(1)
            }
        } else {
            base + href
        }
    }
}
