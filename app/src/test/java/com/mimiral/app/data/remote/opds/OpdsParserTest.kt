package com.mimiral.app.data.remote.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpdsParserTest {

    private lateinit var parser: OpdsParser

    @Before
    fun setup() {
        parser = OpdsParser()
    }

    private val sampleFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom"
              xmlns:dc="http://purl.org/dc/elements/1.1/"
              xmlns:dcterms="http://purl.org/dc/terms/">
            <id>urn:uuid:test-catalog</id>
            <title>Test OPDS Catalog</title>
            <subtitle>A test catalog for unit tests</subtitle>
            <updated>2026-01-01T00:00:00Z</updated>
            <icon>/favicon.ico</icon>
            <link rel="self" type="application/atom+xml" href="https://example.com/catalog.atom"/>
            <link rel="start" type="application/atom+xml" href="https://example.com/catalog.atom"/>
            <link rel="up" type="application/atom+xml" href="https://example.com/"/>
            <link rel="next" type="application/atom+xml" href="https://example.com/c.atom?page=2"/>
            <entry>
                <id>urn:uuid:book-1</id>
                <title>Test Book One</title>
                <summary>A test book summary.</summary>
                <updated>2026-01-01T00:00:00Z</updated>
                <published>2025-12-01T00:00:00Z</published>
                <author>
                    <name>Author One</name>
                    <uri>https://example.com/authors/1</uri>
                </author>
            <category term="fiction" scheme="http://schema.org/BookFormatCategory" label="Fiction"/>
                <category term="fantasy" label="Fantasy"/>
                <dc:subject>Fiction</dc:subject>
                <dc:subject>Fantasy</dc:subject>
                <dc:publisher>Test Publisher</dc:publisher>
                <dc:issued>2025</dc:issued>
                <dc:language>en</dc:language>
                <dcterms:extent>320 pages</dcterms:extent>
                <link rel="http://opds-spec.org/image/thumbnail"
                      type="image/jpeg"
                      href="https://example.com/covers/1-thumb.jpg"/>
                <link rel="http://opds-spec.org/image"
                      type="image/jpeg"
                      href="https://example.com/covers/1.jpg"/>
                <link rel="http://opds-spec.org/acquisition/open-access"
                      type="application/epub+zip"
                      href="https://example.com/books/1.epub"/>
                <link rel="http://opds-spec.org/acquisition/open-access"
                      type="application/pdf"
                      href="https://example.com/books/1.pdf"/>
            </entry>
            <entry>
                <id>urn:uuid:sub-catalog</id>
                <title>Sub-Catalog</title>
                <summary>A navigation entry to a sub-catalog.</summary>
                <link rel="subsection"
                      type="application/atom+xml"
                      href="https://example.com/sub/catalog.atom"/>
            </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `parse feed basic metadata`() {
        val feed = parser.parseFeedString(sampleFeed)

        assertEquals("urn:uuid:test-catalog", feed.id)
        assertEquals("Test OPDS Catalog", feed.title)
        assertEquals("A test catalog for unit tests", feed.subtitle)
        assertEquals("2026-01-01T00:00:00Z", feed.updated)
        assertEquals("/favicon.ico", feed.icon)
    }

    @Test
    fun `parse feed links`() {
        val feed = parser.parseFeedString(sampleFeed)

        assertTrue("Should have feed-level links", feed.links.isNotEmpty())
        val selfLink = feed.links.firstOrNull { it.rel == OpdsLink.REL_SELF }
        assertNotNull("Should have self link", selfLink)
        assertEquals("https://example.com/catalog.atom", selfLink?.href)
    }

    @Test
    fun `parse feed navigation links`() {
        val feed = parser.parseFeedString(sampleFeed)

        assertTrue(
            "Should have navigation links",
            feed.navigationLinks.isNotEmpty() || feed.links.any { it.isNavigation }
        )
    }

    @Test
    fun `parse feed entries`() {
        val feed = parser.parseFeedString(sampleFeed)

        assertEquals("Should have 2 entries", 2, feed.entries.size)
    }

    @Test
    fun `parse entry basic metadata`() {
        val feed = parser.parseFeedString(sampleFeed)
        val entry = feed.entries[0]

        assertEquals("urn:uuid:book-1", entry.id)
        assertEquals("Test Book One", entry.title)
        assertEquals("A test book summary.", entry.summary)
        assertEquals("2026-01-01T00:00:00Z", entry.updated)
        assertEquals("2025-12-01T00:00:00Z", entry.published)
    }

    @Test
    fun `parse entry authors`() {
        val feed = parser.parseFeedString(sampleFeed)
        val entry = feed.entries[0]

        assertEquals("Should have 1 author", 1, entry.authors.size)
        assertEquals("Author One", entry.authors[0].name)
        assertEquals("https://example.com/authors/1", entry.authors[0].uri)
    }

    @Test
    fun `parse entry categories`() {
        val feed = parser.parseFeedString(sampleFeed)
        val entry = feed.entries[0]

        assertEquals("Should have 2 categories", 2, entry.categories.size)
        assertEquals("fiction", entry.categories[0].term)
        assertEquals("http://schema.org/BookFormatCategory", entry.categories[0].scheme)
        assertEquals("Fiction", entry.categories[0].label)
        assertEquals("fantasy", entry.categories[1].term)
        assertEquals("Fantasy", entry.categories[1].label)
    }

    @Test
    fun `parse entry dc metadata`() {
        val feed = parser.parseFeedString(sampleFeed)
        val entry = feed.entries[0]

        assertEquals(2, entry.dcSubjects.size)
        assertTrue(entry.dcSubjects.contains("Fiction"))
        assertTrue(entry.dcSubjects.contains("Fantasy"))
        assertEquals("Test Publisher", entry.dcPublisher)
        assertEquals("2025", entry.dcIssued)
        assertEquals("en", entry.dcLanguage)
        assertEquals("320 pages", entry.dctermsExtent)
    }

    @Test
    fun `parse entry links`() {
        val feed = parser.parseFeedString(sampleFeed)
        val entry = feed.entries[0]

        assertTrue("Should have links", entry.links.isNotEmpty())

        val thumbnail = entry.links.firstOrNull { it.isThumbnail }
        assertNotNull("Should have thumbnail link", thumbnail)
        assertEquals("https://example.com/covers/1-thumb.jpg", thumbnail?.href)

        val cover = entry.links.firstOrNull { it.isCover }
        assertNotNull("Should have cover link", cover)
        assertEquals("https://example.com/covers/1.jpg", cover?.href)
    }

    @Test
    fun `parse entry acquisition links`() {
        val feed = parser.parseFeedString(sampleFeed)
        val entry = feed.entries[0]

        assertTrue("Should be acquisition entry", entry.isAcquisition)
        assertEquals("Should have 2 acquisition links", 2, entry.acquisitionLinks.size)

        val epubLink = entry.acquisitionLinks.firstOrNull {
            it.type?.contains("epub") == true
        }
        assertNotNull("Should have EPUB link", epubLink)
        assertEquals("https://example.com/books/1.epub", epubLink?.href)
    }

    @Test
    fun `parse entry preferred acquisition link prefers epub`() {
        val feed = parser.parseFeedString(sampleFeed)
        val entry = feed.entries[0]

        val preferred = entry.preferredAcquisitionLink
        assertNotNull("Should have preferred link", preferred)
        assertTrue(
            "Should prefer EPUB",
            preferred?.type?.contains("epub") == true
        )
    }

    @Test
    fun `parse navigation entry`() {
        val feed = parser.parseFeedString(sampleFeed)
        val entry = feed.entries[1]

        assertEquals("urn:uuid:sub-catalog", entry.id)
        assertEquals("Sub-Catalog", entry.title)
        assertFalse("Navigation entry should not be acquisition", entry.isAcquisition)
        assertNull(
            "Navigation entry should have no preferred link",
            entry.preferredAcquisitionLink
        )

        val subsectionLink = entry.links.firstOrNull { it.rel == OpdsLink.REL_SUBSECTION }
        assertNotNull("Should have subsection link", subsectionLink)
        assertEquals("https://example.com/sub/catalog.atom", subsectionLink?.href)
    }

    @Test
    fun `parse entry thumbnail and cover urls`() {
        val feed = parser.parseFeedString(sampleFeed)
        val entry = feed.entries[0]

        assertEquals("https://example.com/covers/1-thumb.jpg", entry.thumbnailUrl)
        assertEquals("https://example.com/covers/1.jpg", entry.coverUrl)
    }

    @Test
    fun `parse entry with no authors`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <id>urn:uuid:feed</id>
                <title>Feed</title>
                <entry>
                    <id>urn:uuid:entry</id>
                    <title>Entry Without Author</title>
                </entry>
            </feed>
        """.trimIndent()

        val feed = parser.parseFeedString(xml)
        assertEquals(1, feed.entries.size)
        assertEquals(0, feed.entries[0].authors.size)
    }

    @Test
    fun `parse entry with content instead of summary`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <id>urn:uuid:feed</id>
                <title>Feed</title>
                <entry>
                    <id>urn:uuid:entry</id>
                    <title>Entry With Content</title>
                    <content type="text">Full content here.</content>
                </entry>
            </feed>
        """.trimIndent()

        val feed = parser.parseFeedString(xml)
        val entry = feed.entries[0]
        assertEquals("Full content here.", entry.content)
        assertNull(entry.summary)
    }

    @Test
    fun `parse empty feed`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <id>urn:uuid:empty</id>
                <title>Empty Catalog</title>
            </feed>
        """.trimIndent()

        val feed = parser.parseFeedString(xml)
        assertEquals("urn:uuid:empty", feed.id)
        assertEquals("Empty Catalog", feed.title)
        assertEquals(0, feed.entries.size)
    }

    @Test
    fun `parse feed with base href resolves relative links`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <id>urn:uuid:feed</id>
                <title>Feed</title>
                <link rel="self" type="application/atom+xml" href="/catalog.atom"/>
                <entry>
                    <id>urn:uuid:entry</id>
                    <title>Entry</title>
                    <link rel="http://opds-spec.org/acquisition/open-access"
                          type="application/epub+zip"
                          href="/books/1.epub"/>
                </entry>
            </feed>
        """.trimIndent()

        val feed = parser.parseFeedString(xml, baseHref = "https://example.com/opds/")
        val entry = feed.entries[0]

        val acqLink = entry.acquisitionLinks.first()
        assertTrue(
            "Link should be resolved",
            acqLink.href.startsWith("https://")
        )
    }

    @Test
    fun `opds link constants are correct`() {
        assertEquals("self", OpdsLink.REL_SELF)
        assertEquals("start", OpdsLink.REL_START)
        assertEquals("up", OpdsLink.REL_UP)
        assertEquals("next", OpdsLink.REL_NEXT)
        assertEquals("subsection", OpdsLink.REL_SUBSECTION)
        assertEquals(
            "http://opds-spec.org/image/thumbnail",
            OpdsLink.REL_THUMBNAIL
        )
        assertEquals("http://opds-spec.org/image", OpdsLink.REL_COVER)
        assertEquals(
            "http://opds-spec.org/acquisition",
            OpdsLink.REL_ACQUISITION_PREFIX
        )
        assertEquals(
            "http://opds-spec.org/acquisition/open-access",
            OpdsLink.REL_OPEN_ACCESS
        )
    }

    @Test
    fun `opds link isAcquisition detects acquisition relations`() {
        val acqLink = OpdsLink(
            href = "https://example.com/book.epub",
            rel = "http://opds-spec.org/acquisition/open-access",
            type = "application/epub+zip"
        )
        assertTrue("Should be acquisition", acqLink.isAcquisition)
        assertTrue("Should be open access", acqLink.isOpenAccess)

        val navLink = OpdsLink(
            href = "https://example.com/catalog.atom",
            rel = "subsection",
            type = "application/atom+xml"
        )
        assertFalse("Should not be acquisition", navLink.isAcquisition)
        assertFalse("Should not be open access", navLink.isOpenAccess)
    }

    @Test
    fun `opds link isNavigation detects atom feed links`() {
        val atomLink = OpdsLink(
            href = "https://example.com/feed.atom",
            type = "application/atom+xml"
        )
        assertTrue("Atom link should be navigation", atomLink.isNavigation)

        val epubLink = OpdsLink(
            href = "https://example.com/book.epub",
            type = "application/epub+zip"
        )
        assertFalse("EPUB link should not be navigation", epubLink.isNavigation)
    }

    @Test
    fun `parse entry with multiple authors`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <id>urn:uuid:feed</id>
                <title>Feed</title>
                <entry>
                    <id>urn:uuid:entry</id>
                    <title>Multi-Author Book</title>
                    <author><name>Author A</name></author>
                    <author><name>Author B</name></author>
                    <author><name>Author C</name></author>
                </entry>
            </feed>
        """.trimIndent()

        val feed = parser.parseFeedString(xml)
        val entry = feed.entries[0]
        assertEquals(3, entry.authors.size)
        assertEquals("Author A", entry.authors[0].name)
        assertEquals("Author B", entry.authors[1].name)
        assertEquals("Author C", entry.authors[2].name)
    }

    @Test
    fun `parse entry with borrow link`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <id>urn:uuid:feed</id>
                <title>Feed</title>
                <entry>
                    <id>urn:uuid:entry</id>
                    <title>Borrowable Book</title>
                    <link rel="http://opds-spec.org/acquisition/borrow"
                          type="application/epub+zip"
                          href="https://example.com/borrow/1"/>
                </entry>
            </feed>
        """.trimIndent()

        val feed = parser.parseFeedString(xml)
        val entry = feed.entries[0]
        assertTrue("Should be acquisition", entry.isAcquisition)
        assertFalse("Should not be open access", entry.links[0].isOpenAccess)
    }

    @Test
    fun `parse entry with sample link`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <id>urn:uuid:feed</id>
                <title>Feed</title>
                <entry>
                    <id>urn:uuid:entry</id>
                    <title>Sample Book</title>
                    <link rel="http://opds-spec.org/acquisition/sample"
                          type="application/epub+zip"
                          href="https://example.com/sample/1.epub"/>
                </entry>
            </feed>
        """.trimIndent()

        val feed = parser.parseFeedString(xml)
        val entry = feed.entries[0]
        assertTrue("Should be acquisition", entry.isAcquisition)
    }

    @Test
    fun `parse entry with buy link`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <id>urn:uuid:feed</id>
                <title>Feed</title>
                <entry>
                    <id>urn:uuid:entry</id>
                    <title>Paid Book</title>
                    <link rel="http://opds-spec.org/acquisition/buy"
                          type="application/epub+zip"
                          href="https://example.com/buy/1"/>
                </entry>
            </feed>
        """.trimIndent()

        val feed = parser.parseFeedString(xml)
        val entry = feed.entries[0]
        assertTrue("Should be acquisition", entry.isAcquisition)
    }

    @Test
    fun `parse feed with pagination links`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <id>urn:uuid:feed</id>
                <title>Paginated Catalog</title>
                <link rel="first" type="application/atom+xml"
                      href="https://example.com/catalog.atom?page=1"/>
                <link rel="last" type="application/atom+xml"
                      href="https://example.com/catalog.atom?page=10"/>
                <link rel="next" type="application/atom+xml"
                      href="https://example.com/catalog.atom?page=3"/>
                <link rel="previous" type="application/atom+xml"
                      href="https://example.com/catalog.atom?page=1"/>
                <entry>
                    <id>urn:uuid:entry</id>
                    <title>Book</title>
                </entry>
            </feed>
        """.trimIndent()

        val feed = parser.parseFeedString(xml)

        val firstLink = feed.links.firstOrNull { it.rel == OpdsLink.REL_FIRST }
        assertNotNull("Should have first link", firstLink)
        assertEquals("https://example.com/catalog.atom?page=1", firstLink?.href)

        val lastLink = feed.links.firstOrNull { it.rel == OpdsLink.REL_LAST }
        assertNotNull("Should have last link", lastLink)

        val nextLink = feed.links.firstOrNull { it.rel == OpdsLink.REL_NEXT }
        assertNotNull("Should have next link", nextLink)

        val prevLink = feed.links.firstOrNull { it.rel == OpdsLink.REL_PREVIOUS }
        assertNotNull("Should have previous link", prevLink)
    }

    @Test
    fun `parse entry with related link`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <id>urn:uuid:feed</id>
                <title>Feed</title>
                <entry>
                    <id>urn:uuid:entry</id>
                    <title>Book</title>
                    <link rel="related"
                      type="application/atom+xml"
                      href="https://example.com/related/1"
                      title="Related books"/>
                </entry>
            </feed>
        """.trimIndent()

        val feed = parser.parseFeedString(xml)
        val entry = feed.entries[0]
        val relatedLink = entry.links.firstOrNull { it.rel == OpdsLink.REL_RELATED }
        assertNotNull("Should have related link", relatedLink)
        assertEquals("Related books", relatedLink?.title)
    }
}
