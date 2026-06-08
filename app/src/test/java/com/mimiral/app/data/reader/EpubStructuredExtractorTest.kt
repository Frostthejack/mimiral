package com.mimiral.app.data.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubStructuredExtractorTest {

    private val extractor = EpubStructuredExtractor()

    // ---- Headings ----

    @Test
    fun `extracts h1 heading`() {
        val html = "<h1>Chapter One</h1>"
        val blocks = extractor.extract(html)
        assertEquals(1, blocks.size)
        val heading = blocks[0] as ContentBlock.Heading
        assertEquals(1, heading.level)
        assertEquals("Chapter One", heading.text)
    }

    @Test
    fun `extracts h3 heading`() {
        val html = "<h3>Section Title</h3>"
        val blocks = extractor.extract(html)
        assertEquals(1, blocks.size)
        val heading = blocks[0] as ContentBlock.Heading
        assertEquals(3, heading.level)
        assertEquals("Section Title", heading.text)
    }

    // ---- Paragraphs ----

    @Test
    fun `extracts simple paragraph`() {
        val html = "<p>Hello world</p>"
        val blocks = extractor.extract(html)
        assertEquals(1, blocks.size)
        val para = blocks[0] as ContentBlock.Paragraph
        assertEquals("Hello world", para.text)
        // Plain text paragraphs take the fast path with empty spans
        assertEquals(true, para.spans.isEmpty())
    }

    @Test
    fun `extracts paragraph with bold span`() {
        val html = "<p>This is <strong>bold</strong> text</p>"
        val blocks = extractor.extract(html)
        assertEquals(1, blocks.size)
        val para = blocks[0] as ContentBlock.Paragraph
        assertEquals("This is bold text", para.text)
        // Bold span should be present
        val boldSpans = para.spans.filter { it.isBold }
        assertTrue("Should have at least one bold span", boldSpans.isNotEmpty())
        // Bold span offsets should be within text range
        for (span in boldSpans) {
            assertTrue("span.start should be >= 0", span.start >= 0)
            assertTrue("span.end should be <= text.length", span.end <= para.text.length)
        }
    }

    @Test
    fun `extracts paragraph with italic span`() {
        val html = "<p>This is <em>italic</em> text</p>"
        val blocks = extractor.extract(html)
        assertEquals(1, blocks.size)
        val para = blocks[0] as ContentBlock.Paragraph
        val italicSpans = para.spans.filter { it.isItalic }
        assertTrue("Should have at least one italic span", italicSpans.isNotEmpty())
    }

    @Test
    fun `extracts paragraph with bold and italic`() {
        val html = "<p>This is <strong><em>bold italic</em></strong> text</p>"
        val blocks = extractor.extract(html)
        assertEquals(1, blocks.size)
        val para = blocks[0] as ContentBlock.Paragraph
        val boldItalicSpans = para.spans.filter { it.isBold && it.isItalic }
        assertTrue("Should have at least one bold+italic span", boldItalicSpans.isNotEmpty())
    }

    @Test
    fun `extracts paragraph with b and i tags`() {
        val html = "<p>This is <b>bold</b> and <i>italic</i></p>"
        val blocks = extractor.extract(html)
        val para = blocks[0] as ContentBlock.Paragraph
        val boldSpans = para.spans.filter { it.isBold }
        val italicSpans = para.spans.filter { it.isItalic }
        assertTrue("Should have bold span", boldSpans.isNotEmpty())
        assertTrue("Should have italic span", italicSpans.isNotEmpty())
    }

    // ---- Quotes ----

    @Test
    fun `extracts blockquote`() {
        val html = "<blockquote>To be or not to be</blockquote>"
        val blocks = extractor.extract(html)
        assertEquals(1, blocks.size)
        val quote = blocks[0] as ContentBlock.Quote
        assertEquals("To be or not to be", quote.text)
    }

    // ---- List items ----

    @Test
    fun `extracts unordered list items`() {
        val html = "<ul><li>First</li><li>Second</li><li>Third</li></ul>"
        val blocks = extractor.extract(html)
        assertEquals(3, blocks.size)
        val items = blocks.map { it as ContentBlock.ListItem }
        assertEquals("First", items[0].text)
        assertEquals(0, items[0].order)  // unordered = order 0
        assertEquals("Second", items[1].text)
        assertEquals("Third", items[2].text)
    }

    @Test
    fun `extracts ordered list items`() {
        val html = "<ol><li>Step one</li><li>Step two</li></ol>"
        val blocks = extractor.extract(html)
        assertEquals(2, blocks.size)
        val items = blocks.map { it as ContentBlock.ListItem }
        assertTrue("First item should be ordered", items[0].order > 0)
        assertTrue("Second item should be ordered", items[1].order > 0)
        assertEquals("Step one", items[0].text)
        assertEquals("Step two", items[1].text)
    }

    // ---- Mixed content ----

    @Test
    fun `extracts mixed content`() {
        val html = """
            <h2>Introduction</h2>
            <p>This is the <strong>first</strong> paragraph.</p>
            <blockquote>A famous quote</blockquote>
            <ul><li>Item A</li><li>Item B</li></ul>
            <p>Final paragraph.</p>
        """.trimIndent()
        val blocks = extractor.extract(html)
        assertTrue(blocks.size >= 4)

        val heading = blocks.filterIsInstance<ContentBlock.Heading>().firstOrNull()
        assertEquals(2, heading?.level)
        assertEquals("Introduction", heading?.text)

        val quote = blocks.filterIsInstance<ContentBlock.Quote>().firstOrNull()
        assertEquals("A famous quote", quote?.text)

        val listItems = blocks.filterIsInstance<ContentBlock.ListItem>()
        assertTrue(listItems.size >= 2)
    }

    // ---- Edge cases ----

    @Test
    fun `empty html returns empty list`() {
        val blocks = extractor.extract("")
        assertEquals(0, blocks.size)
    }

    @Test
    fun `blank html returns empty list`() {
        val blocks = extractor.extract("   \n  ")
        assertEquals(0, blocks.size)
    }

    @Test
    fun `strips script tags`() {
        val html = "<script>alert('hi')</script><p>Content</p>"
        val blocks = extractor.extract(html)
        assertEquals(1, blocks.size)
        val para = blocks[0] as ContentBlock.Paragraph
        assertEquals("Content", para.text)
    }

    @Test
    fun `strips style tags`() {
        val html = "<style>p { color: red; }</style><p>Content</p>"
        val blocks = extractor.extract(html)
        assertEquals(1, blocks.size)
    }

    @Test
    fun `decodes html entities`() {
        val html = "<p>Tom &amp; Jerry &lt;3&gt;</p>"
        val blocks = extractor.extract(html)
        val para = blocks[0] as ContentBlock.Paragraph
        assertTrue(para.text.contains("Tom & Jerry"))
        assertTrue(para.text.contains("<3>"))
    }

    @Test
    fun `br tag inside paragraph creates line break`() {
        val html = "<p>Line one<br/>Line two</p>"
        val blocks = extractor.extract(html)
        val para = blocks[0] as ContentBlock.Paragraph
        assertTrue(para.text.contains("Line one"))
        assertTrue(para.text.contains("Line two"))
    }

    // ---- TextSpan offset-based API ----

    @Test
    fun `paragraph text property returns full text`() {
        val html = "<p>Hello world</p>"
        val blocks = extractor.extract(html)
        val para = blocks[0] as ContentBlock.Paragraph
        assertEquals("Hello world", para.text)
    }

    @Test
    fun `styled paragraph has non-empty spans`() {
        val html = "<p>Hello <strong>world</strong></p>"
        val blocks = extractor.extract(html)
        val para = blocks[0] as ContentBlock.Paragraph
        assertTrue("Styled paragraph should have spans", para.spans.isNotEmpty())
        // Spans should have valid offsets
        for (span in para.spans) {
            assertTrue("span.start >= 0", span.start >= 0)
            assertTrue("span.end > span.start", span.end > span.start)
            assertTrue("span.end <= text.length", span.end <= para.text.length)
        }
    }

    @Test
    fun `bold span has isBold true`() {
        val html = "<p><strong>bold text</strong></p>"
        val blocks = extractor.extract(html)
        val para = blocks[0] as ContentBlock.Paragraph
        // All-bold paragraph uses isBold flag on paragraph, not spans
        assertEquals(true, para.isBold)
    }

    // ---- Tokenizer (internal tests) ----

    @Test
    fun `tokenizer produces correct tokens`() {
        val html = "<p>Hello <strong>world</strong></p>"
        val tokens = extractor.tokenize(html)
        assertEquals(6, tokens.size)
        assertTrue(tokens[0] is EpubStructuredExtractor.Token.OpenTag)
        assertEquals("p", (tokens[0] as EpubStructuredExtractor.Token.OpenTag).name)
        assertTrue(tokens[1] is EpubStructuredExtractor.Token.Text)
        assertEquals("Hello ", (tokens[1] as EpubStructuredExtractor.Token.Text).content)
        assertTrue(tokens[2] is EpubStructuredExtractor.Token.OpenTag)
        assertEquals("strong", (tokens[2] as EpubStructuredExtractor.Token.OpenTag).name)
    }

    @Test
    fun `tokenizer handles self-closing tags`() {
        val html = "<br/>"
        val tokens = extractor.tokenize(html)
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is EpubStructuredExtractor.Token.SelfClosingTag)
        assertEquals("br", (tokens[0] as EpubStructuredExtractor.Token.SelfClosingTag).name)
    }

    // ---- extractPlainText ----

    @Test
    fun `extractPlainText returns joined text`() {
        val html = "<h1>Title</h1><p>Paragraph</p>"
        val text = extractor.extractPlainText(html)
        assertTrue(text.contains("Title"))
        assertTrue(text.contains("Paragraph"))
    }
}
