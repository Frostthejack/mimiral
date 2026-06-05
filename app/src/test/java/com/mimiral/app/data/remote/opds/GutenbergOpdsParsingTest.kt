package com.mimiral.app.data.remote.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for Project Gutenberg OPDS feed parsing.
 *
 * Gutenberg OPDS structure:
 * - Top-level feed: navigation entries (Popular, Latest, Random)
 * - Search results: book entries with subsection links + pagination
 * - Book entries: content has author, subsection link to book OPDS page
 * - Pagination: link rel="next" with start_index parameter
 * - Metadata: dc:subject (bookshelf), dc:language in book detail feeds
 */
class GutenbergOpdsParsingTest {

    private lateinit var parser: OpdsParser

    @Before
    fun setup() {
        parser = OpdsParser()
    }

    /**
     * Simulates the top-level Gutenberg OPDS feed with navigation entries
     * (Popular, Latest, Random categories).
     */
    private val gutenbergTopLevelFeed = """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom"
              xmlns:opds="http://opds-spec.org/2010/catalog"
              xmlns:dcterms="http://purl.org/dc/terms/">
            <id>http://www.gutenberg.org/ebooks.opds/</id>
            <updated>2026-06-04T23:02:56Z</updated>
            <title>Project Gutenberg</title>
            <subtitle>Free eBooks since 1971.</subtitle>
            <author>
                <name>Project Gutenberg</name>
                <uri>https://www.gutenberg.org</uri>
            </author>
            <icon>https://www.gutenberg.org/gutenberg/favicon.ico</icon>
            <link rel="self" type="application/atom+xml;profile=opds-catalog"
                  href="/ebooks.opds/"/>
            <link rel="start" type="application/atom+xml;profile=opds-catalog"
                  href="/ebooks.opds/"/>
            <entry>
                <updated>2026-06-04T23:02:56Z</updated>
                <id>https://www.gutenberg.org/ebooks/search.opds/?sort_order=downloads</id>
                <title>Popular</title>
                <content type="text">Our most popular books.</content>
                <link type="application/atom+xml;profile=opds-catalog"
                      rel="subsection"
                      href="/ebooks/search.opds/?sort_order=downloads"/>
            </entry>
            <entry>
                <updated>2026-06-04T23:02:56Z</updated>
                <id>https://www.gutenberg.org/ebooks/search.opds/?sort_order=release_date</id>
                <title>Latest</title>
                <content type="text">Our latest releases.</content>
                <link type="application/atom+xml;profile=opds-catalog"
                      rel="subsection"
                      href="/ebooks/search.opds/?sort_order=release_date"/>
            </entry>
            <entry>
                <updated>2026-06-04T23:02:56Z</updated>
                <id>https://www.gutenberg.org/ebooks/search.opds/?sort_order=random</id>
                <title>Random</title>
                <content type="text">Random books.</content>
                <link type="application/atom+xml;profile=opds-catalog"
                      rel="subsection"
                      href="/ebooks/search.opds/?sort_order=random"/>
            </entry>
        </feed>
    """.trimIndent()

    /**
     * Simulates a Gutenberg search result page with book entries and pagination.
     */
    private val gutenbergSearchFeed = """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom"
              xmlns:opds="http://opds-spec.org/2010/catalog"
              xmlns:dcterms="http://purl.org/dc/terms/">
            <id>http://www.gutenberg.org/ebooks/search.opds/?sort_order=downloads</id>
            <updated>2026-06-04T23:03:14Z</updated>
            <title>All Books</title>
            <subtitle>Free eBooks since 1971.</subtitle>
            <link rel="self" type="application/atom+xml;profile=opds-catalog"
                  href="/ebooks/search.opds/?sort_order=downloads"/>
            <link rel="start" type="application/atom+xml;profile=opds-catalog"
                  href="/ebooks.opds/"/>
            <link rel="next" title="Next Page"
                  type="application/atom+xml;profile=opds-catalog"
                  href="/ebooks/search.opds/?sort_order=downloads&amp;start_index=26"/>
            <entry>
                <updated>2026-06-04T23:03:14Z</updated>
                <id>https://www.gutenberg.org/ebooks/2701.opds</id>
                <title>Moby Dick; Or, The Whale</title>
                <content type="text">Herman Melville</content>
                <link type="application/atom+xml;profile=opds-catalog"
                      rel="subsection" href="/ebooks/2701.opds"/>
                <link type="image/png"
                      rel="http://opds-spec.org/image/thumbnail"
                      href="data:image/png;base64,abc123"/>
            </entry>
            <entry>
                <updated>2026-06-04T23:03:14Z</updated>
                <id>https://www.gutenberg.org/ebooks/1342.opds</id>
                <title>Pride and Prejudice</title>
                <content type="text">Jane Austen</content>
                <link type="application/atom+xml;profile=opds-catalog"
                      rel="subsection" href="/ebooks/1342.opds"/>
            </entry>
        </feed>
    """.trimIndent()

    /**
     * Simulates a Gutenberg individual book OPDS page with full metadata.
     */
    private val gutenbergBookDetailFeed = """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom"
              xmlns:dc="http://purl.org/dc/elements/1.1/"
              xmlns:dcterms="http://purl.org/dc/terms/">
            <id>https://www.gutenberg.org/ebooks/2701.opds</id>
            <title>Moby Dick; Or, The Whale</title>
            <entry>
                <id>https://www.gutenberg.org/ebooks/2701</id>
                <title>Moby Dick; Or, The Whale</title>
                <author>
                    <name>Herman Melville</name>
                </author>
                <dc:language>en</dc:language>
                <dc:subject>Whaling -- Fiction</dc:subject>
                <dc:subject>Sea stories</dc:subject>
                <dc:subject>Psychological fiction</dc:subject>
                <dc:publisher>Project Gutenberg</dc:publisher>
                <dc:issued>1851</dc:issued>
                <dcterms:extent>654 pages</dcterms:extent>
                <link rel="http://opds-spec.org/acquisition/open-access"
                      type="application/epub+zip"
                      href="https://www.gutenberg.org/ebooks/2701.epub.images"/>
                <link rel="http://opds-spec.org/acquisition/open-access"
                      type="application/pdf"
                      href="https://www.gutenberg.org/ebooks/2701.pdf.images"/>
                <link rel="http://opds-spec.org/image/thumbnail"
                      type="image/jpeg"
                      href="https://www.gutenberg.org/cache/epub/2701/pg2701.cover.small.jpg"/>
                <link rel="http://opds-spec.org/image"
                      type="image/jpeg"
                      href="https://www.gutenberg.org/cache/epub/2701/pg2701.cover.medium.jpg"/>
            </entry>
        </feed>
    """.trimIndent()

    // --- Top-level feed tests ---

    @Test
    fun `gutenberg top-level feed parses correctly`() {
        val feed = parser.parseFeedString(gutenbergTopLevelFeed)

        assertEquals("Project Gutenberg", feed.title)
        assertEquals("Free eBooks since 1971.", feed.subtitle)
        assertEquals(3, feed.entries.size)
    }

    @Test
    fun `gutenberg top-level entries are navigation not acquisition`() {
        val feed = parser.parseFeedString(gutenbergTopLevelFeed)

        for (entry in feed.entries) {
            assertFalse(
                "Top-level entries should not be acquisition",
                entry.isAcquisition
            )
        }
    }

    @Test
    fun `gutenberg top-level entries have navigation links`() {
        val feed = parser.parseFeedString(gutenbergTopLevelFeed)

        val popularEntry = feed.entries.first { it.title == "Popular" }
        assertTrue(
            "Popular entry should have navigation link",
            popularEntry.links.any { it.isNavigation }
        )

        val subsectionLink = popularEntry.links.firstOrNull {
            it.rel == OpdsLink.REL_SUBSECTION
        }
        assertNotNull("Should have subsection link", subsectionLink)
        assertTrue(
            "Should point to search",
            subsectionLink?.href?.contains("search.opds") == true
        )
    }

    @Test
    fun `gutenberg top-level feed has expected categories`() {
        val feed = parser.parseFeedString(gutenbergTopLevelFeed)

        val titles = feed.entries.map { it.title }
        assertTrue(titles.contains("Popular"))
        assertTrue(titles.contains("Latest"))
        assertTrue(titles.contains("Random"))
    }

    // --- Search feed tests ---

    @Test
    fun `gutenberg search feed parses book entries`() {
        val feed = parser.parseFeedString(gutenbergSearchFeed)

        assertEquals("All Books", feed.title)
        assertEquals(2, feed.entries.size)
    }

    @Test
    fun `gutenberg search feed has pagination next link`() {
        val feed = parser.parseFeedString(gutenbergSearchFeed)

        val nextLink = feed.links.firstOrNull { it.rel == OpdsLink.REL_NEXT }
        assertNotNull("Should have next page link", nextLink)
        assertTrue(
            "Next link should contain start_index",
            nextLink?.href?.contains("start_index=26") == true
        )
    }

    @Test
    fun `gutenberg search entries have thumbnails`() {
        val feed = parser.parseFeedString(gutenbergSearchFeed)

        val mobyDick = feed.entries.first { it.title.contains("Moby Dick") }
        assertNotNull("Should have thumbnail", mobyDick.thumbnailUrl)
        assertTrue(
            "Thumbnail should be data URI or URL",
            mobyDick.thumbnailUrl?.startsWith("data:image/") == true ||
                mobyDick.thumbnailUrl?.startsWith("http") == true
        )
    }

    @Test
    fun `gutenberg search entries have navigation links to book pages`() {
        val feed = parser.parseFeedString(gutenbergSearchFeed)

        for (entry in feed.entries) {
            val subsectionLink = entry.links.firstOrNull {
                it.rel == OpdsLink.REL_SUBSECTION
            }
            assertNotNull(
                "${entry.title} should have subsection link",
                subsectionLink
            )
            assertTrue(
                "Should point to book OPDS page",
                subsectionLink?.href?.contains("/ebooks/") == true
            )
        }
    }

    // --- Book detail feed tests ---

    @Test
    fun `gutenberg book detail parses dc metadata`() {
        val feed = parser.parseFeedString(gutenbergBookDetailFeed)
        val entry = feed.entries.first()

        assertEquals("en", entry.dcLanguage)
        assertEquals("Project Gutenberg", entry.dcPublisher)
        assertEquals("1851", entry.dcIssued)
        assertEquals("654 pages", entry.dctermsExtent)
    }

    @Test
    fun `gutenberg book detail parses dc subjects`() {
        val feed = parser.parseFeedString(gutenbergBookDetailFeed)
        val entry = feed.entries.first()

        assertEquals(3, entry.dcSubjects.size)
        assertTrue(entry.dcSubjects.contains("Whaling -- Fiction"))
        assertTrue(entry.dcSubjects.contains("Sea stories"))
        assertTrue(entry.dcSubjects.contains("Psychological fiction"))
    }

    @Test
    fun `gutenberg book detail has acquisition links`() {
        val feed = parser.parseFeedString(gutenbergBookDetailFeed)
        val entry = feed.entries.first()

        assertTrue("Should be acquisition", entry.isAcquisition)
        assertEquals(2, entry.acquisitionLinks.size)

        val epubLink = entry.acquisitionLinks.firstOrNull {
            it.type?.contains("epub") == true
        }
        assertNotNull("Should have EPUB link", epubLink)
        assertTrue(
            "EPUB link should be open access",
            epubLink?.isOpenAccess == true
        )
    }

    @Test
    fun `gutenberg book detail preferred link is epub`() {
        val feed = parser.parseFeedString(gutenbergBookDetailFeed)
        val entry = feed.entries.first()

        val preferred = entry.preferredAcquisitionLink
        assertNotNull("Should have preferred link", preferred)
        assertTrue(
            "Should prefer EPUB",
            preferred?.type?.contains("epub") == true
        )
    }

    @Test
    fun `gutenberg book detail has cover and thumbnail`() {
        val feed = parser.parseFeedString(gutenbergBookDetailFeed)
        val entry = feed.entries.first()

        assertNotNull("Should have thumbnail", entry.thumbnailUrl)
        assertNotNull("Should have cover", entry.coverImageUrl)
        assertTrue(
            "Cover should be different from thumbnail",
            entry.coverImageUrl != entry.thumbnailUrl
        )
    }

    // --- Pagination tests ---

    @Test
    fun `gutenberg pagination next link is not navigation`() {
        val feed = parser.parseFeedString(gutenbergSearchFeed)

        val nextLink = feed.links.firstOrNull { it.rel == OpdsLink.REL_NEXT }
        assertNotNull(nextLink)
        // Next page links use Atom XML type, so they are navigation
        assertTrue(
            "Next page link should be navigation (Atom XML)",
            nextLink?.isNavigation == true
        )
    }

    @Test
    fun `gutenberg top-level feed has no next page`() {
        val feed = parser.parseFeedString(gutenbergTopLevelFeed)

        val nextLink = feed.links.firstOrNull { it.rel == OpdsLink.REL_NEXT }
        assertNull("Top-level feed should not have next page", nextLink)
    }

    // --- Link relation tests ---

    @Test
    fun `gutenberg subsection links are navigation`() {
        val feed = parser.parseFeedString(gutenbergTopLevelFeed)
        val entry = feed.entries.first()

        val subsectionLink = entry.links.firstOrNull {
            it.rel == OpdsLink.REL_SUBSECTION
        }
        assertNotNull(subsectionLink)
        assertTrue(
            "Subsection link should be navigation",
            subsectionLink?.isNavigation == true
        )
    }

    @Test
    fun `gutenberg acquisition links are not navigation`() {
        val feed = parser.parseFeedString(gutenbergBookDetailFeed)
        val entry = feed.entries.first()

        for (link in entry.acquisitionLinks) {
            assertFalse(
                "Acquisition link should not be navigation",
                link.isNavigation
            )
        }
    }
}
