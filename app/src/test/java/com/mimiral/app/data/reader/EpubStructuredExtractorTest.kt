package com.mimiral.app.data.reader

import org.junit.Assert.assertEquals
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
        assertEquals(1, para.spans.size)
        assertEquals("Hello world", para.spans[0].text)
        assertEquals(false, para.spans[0].bold)
        assertEquals(false, para.spans[0].italic)
    }

    @Test
    fun `extracts paragraph with bold span`() {
        val html = "<p>This is <strong>bold</strong> text</p>"
        val blocks = extractor.extract(html)
        assertEquals(1, blocks.size)
        val para = blocks[0] as ContentBlock.Paragraph
        assertEquals(3, para.spans.size)
        assertEquals("This is ", para.spans[0].text)
        assertEquals(false, para.spans[0].bold)
        assertEquals("bold", para.spans[1].text)
        assertEquals(true, para.spans[1].bold)
        assertEquals(" text", para.spans[2].text)
        assertEquals(false, para.spans[2].bold)
    }

    @Test
    fun `extracts paragraph with italic span`() {
        val html = "<p>This is <em>italic</em> text</p>"
        val blocks = extractor.extract(html)
        assertEquals(1, blocks.size)
        val para = blocks[0] as ContentBlock.Paragraph
        assertEquals(3, para.spans.size)
        assertEquals("italic", para.spans[1].text)
        assertEquals(true, para.spans[1].italic)
    }

    @Test
    fun `extracts paragraph with bold and italic`() {
        val html = "<p>This is <strong><em>bold italic</em></strong> text</p>"
        val blocks = extractor.extract(html)
        assertEquals(1, blocks.size)
        val para = blocks[0] as ContentBlock.Paragraph
        val boldItalicSpan = para.spans.first { it.text == "bold italic" }
        assertEquals(true, boldItalicSpan.bold)
        assertEquals(true, boldItalicSpan.italic)
    }

    @Test
    fun `extracts paragraph with b and i tags`() {
        val html = "<p>This is <b>bold</b> and <i>italic</i></p>"
        val blocks = extractor.extract(html)
        val para = blocks[0] as ContentBlock.Paragraph
        val boldSpan = para.spans.first { it.bold }
        val italicSpan = para.spans.first { it.italic }
        assertEquals("bold", boldSpan.text)
        assertEquals("italic", italicSpan.text)
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
        assertEquals(false, items[0].ordered)
        assertEquals(1, items[0].index)
        assertEquals("Second", items[1].text)
        assertEquals(2, items[1].index)
        assertEquals("Third", items[2].text)
        assertEquals(3, items[2].index)
    }

    @Test
    fun `extracts ordered list items`() {
        val html = "<ol><li>Step one</li><li>Step two</li></ol>"
        val blocks = extractor.extract(html)
        assertEquals(2, blocks.size)
        val items = blocks.map { it as ContentBlock.ListItem }
        assertEquals(true, items[0].ordered)
        assertEquals(1, items[0].index)
        assertEquals(true, items[1].ordered)
        assertEquals(2, items[1].index)
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

        // Expect: heading, paragraph, quote, 2 list items, paragraph = 6 blocks
        assertTrue(blocks.size >= 4) // at minimum heading, para, quote, para

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
        // Should have 2 spans split at the <br>
        assertTrue(para.spans.size >= 2)
        assertTrue(para.text.contains("Line one"))
        assertTrue(para.text.contains("Line two"))
    }

    // ---- TextSpan convenience properties ----

    @Test
    fun `paragraph text property concatenates spans`() {
        val para = ContentBlock.Paragraph(listOf(TextSpan("Hello ", true), TextSpan("world")))
        assertEquals("Hello world", para.text)
    }

    @Test
    fun `paragraph hasStyle detects styled spans`() {
        val plain = ContentBlock.Paragraph(listOf(TextSpan("hello")))
        assertEquals(false, plain.hasStyle)

        val bold = ContentBlock.Paragraph(listOf(TextSpan("hello", bold = true)))
        assertEquals(true, bold.hasStyle)

        val italic = ContentBlock.Paragraph(listOf(TextSpan("hello", italic = true)))
        assertEquals(true, italic.hasStyle)
    }

    // ---- Tokenizer (internal tests) ----

    @Test
    fun `tokenizer produces correct tokens`() {
        val html = "<p>Hello <strong>world</strong></p>"
        val tokens = extractor.tokenize(html)
        // Tokens: OpenTag(p), Text("Hello "), OpenTag(strong), Text("world"), CloseTag(strong), CloseTag(p)
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

    // ---- ContentBlock.Heading validation ----

    @Test(expected = IllegalArgumentException::class)
    fun `heading level must be 1-6`() {
        ContentBlock.Heading(level = 0, text = "Invalid")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `heading level must not exceed 6`() {
        ContentBlock.Heading(level = 7, text = "Invalid")
    }
}
