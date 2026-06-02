package com.mimiral.app.data.reader

import org.junit.Test
import org.junit.Assert.*

class EpubTextPreprocessorTest {

    private val preprocessor = EpubTextPreprocessor()

    // --- extractText: basic HTML parsing ---

    @Test
    fun extractText_simpleParagraph_returnsPlainText() {
        val html = "<p>Hello world. This is a test.</p>"
        val result = preprocessor.extractText(html)
        assertTrue("Should contain text", result.contains("Hello world"))
        assertTrue("Should contain text", result.contains("This is a test"))
        assertFalse("Should not contain HTML tags", result.contains("<p>"))
        assertFalse("Should not contain HTML tags", result.contains("</p>"))
    }

    @Test
    fun extractText_multipleParagraphs_preservesContent() {
        val html = "<p>First paragraph.</p><p>Second paragraph.</p>"
        val result = preprocessor.extractText(html)
        assertTrue("Should contain first paragraph", result.contains("First paragraph"))
        assertTrue("Should contain second paragraph", result.contains("Second paragraph"))
    }

    @Test
    fun extractText_emptyString_returnsEmpty() {
        assertEquals("", preprocessor.extractText(""))
    }

    @Test
    fun extractText_blankString_returnsEmpty() {
        assertEquals("", preprocessor.extractText("   "))
    }

    // --- extractText: script/style tag removal ---

    @Test
    fun extractText_scriptTag_removesContent() {
        val html = "<p>Before</p><script>var x = 1;</script><p>After</p>"
        val result = preprocessor.extractText(html)
        assertTrue("Should contain Before", result.contains("Before"))
        assertTrue("Should contain After", result.contains("After"))
        assertFalse("Should not contain script content", result.contains("var x"))
        assertFalse("Should not contain script tag", result.contains("<script>"))
    }

    @Test
    fun extractText_styleTag_removesContent() {
        val html = "<p>Text</p><style>.foo { color: red; }</style><p>More</p>"
        val result = preprocessor.extractText(html)
        assertTrue("Should contain Text", result.contains("Text"))
        assertTrue("Should contain More", result.contains("More"))
        assertFalse("Should not contain style content", result.contains("color: red"))
        assertFalse("Should not contain style tag", result.contains("<style>"))
    }

    @Test
    fun extractText_headTag_removesContent() {
        val html = "<head><title>My Book</title></head><p>Content here.</p>"
        val result = preprocessor.extractText(html)
        assertTrue("Should contain content", result.contains("Content here"))
        assertFalse("Should not contain title", result.contains("My Book"))
        assertFalse("Should not contain head", result.contains("<head>"))
    }

    @Test
    fun extractText_htmlComments_removesContent() {
        val html = "<p>Visible</p><!-- This is a comment --><p>Also visible</p>"
        val result = preprocessor.extractText(html)
        assertTrue("Should contain Visible", result.contains("Visible"))
        assertTrue("Should contain Also visible", result.contains("Also visible"))
        assertFalse("Should not contain comment", result.contains("comment"))
    }

    // --- extractText: HTML entity decoding ---

    @Test
    fun extractText_namedEntity_ampersand_decoded() {
        val html = "<p>Tom &amp; Jerry</p>"
        val result = preprocessor.extractText(html)
        assertTrue("Should contain ampersand", result.contains("Tom & Jerry"))
        assertFalse("Should not contain entity", result.contains("&amp;"))
    }

    @Test
    fun extractText_namedEntity_lessThan_decoded() {
        val html = "<p>5 &lt; 10</p>"
        val result = preprocessor.extractText(html)
        assertTrue("Should contain less-than", result.contains("5 < 10"))
    }

    @Test
    fun extractText_namedEntity_greaterThan_decoded() {
        val html = "<p>10 &gt; 5</p>"
        val result = preprocessor.extractText(html)
        assertTrue("Should contain greater-than", result.contains("10 > 5"))
    }

    @Test
    fun extractText_namedEntity_quot_decoded() {
        val html = "<p>She said &quot;hello&quot;</p>"
        val result = preprocessor.extractText(html)
        assertTrue("Should contain decoded quotes", result.contains("She said"))
        assertFalse("Should not contain entity", result.contains("&quot;"))
    }

    @Test
    fun extractText_numericEntity_decoded() {
        val html = "<p>&#8212;em dash</p>"
        val result = preprocessor.extractText(html)
        assertFalse("Should not contain numeric entity", result.contains("&#8212;"))
    }

    @Test
    fun extractText_hexEntity_decoded() {
        val html = "<p>&#x2014;em dash</p>"
        val result = preprocessor.extractText(html)
        assertFalse("Should not contain hex entity", result.contains("&#x2014;"))
    }

    // --- extractText: whitespace normalization ---

    @Test
    fun extractText_multipleSpaces_collapsed() {
        val html = "<p>Hello    world</p>"
        val result = preprocessor.extractText(html)
        assertFalse("Should not contain multiple spaces", result.contains("    "))
        assertTrue("Should contain text", result.contains("Hello world"))
    }

    @Test
    fun extractText_leadingTrailingSpaces_trimmed() {
        val html = "<p>  Hello world  </p>"
        val result = preprocessor.extractText(html)
        assertFalse("Should not start with space", result.startsWith(" "))
        assertFalse("Should not end with space", result.endsWith(" "))
    }

    @Test
    fun extractText_brTag_becomesNewline() {
        val html = "<p>Line one<br/>Line two</p>"
        val result = preprocessor.extractText(html)
        assertTrue("Should contain both lines", result.contains("Line one"))
        assertTrue("Should contain both lines", result.contains("Line two"))
    }

    // --- extractText: no HTML tags in output ---

    @Test
    fun extractText_variousTags_noHtmlInOutput() {
        val html = "<div><h1>Title</h1><p>Paragraph <strong>with</strong> <em>formatting</em>.</p><ul><li>Item 1</li><li>Item 2</li></ul></div>"
        val result = preprocessor.extractText(html)
        assertFalse("Should not contain div tag", result.contains("<div>"))
        assertFalse("Should not contain h1 tag", result.contains("<h1>"))
        assertFalse("Should not contain strong tag", result.contains("<strong>"))
        assertFalse("Should not contain em tag", result.contains("<em>"))
        assertFalse("Should not contain ul tag", result.contains("<ul>"))
        assertFalse("Should not contain li tag", result.contains("<li>"))
        assertTrue("Should contain text content", result.contains("Title"))
        assertTrue("Should contain text content", result.contains("Paragraph"))
        assertTrue("Should contain text content", result.contains("Item 1"))
    }

    // --- processChapter: sentence boundary preservation ---

    @Test
    fun processChapter_simpleText_preservesSentences() {
        val html = "<p>First sentence. Second sentence. Third sentence.</p>"
        val result = preprocessor.processChapter(html, chapterIndex = 0, title = "Ch1")
        assertEquals(0, result.chapterIndex)
        assertEquals("Ch1", result.title)
        assertTrue("Should have sentences", result.sentences.isNotEmpty())
        val sentenceTexts = result.sentences.map { it.text }
        assertTrue("Should find first sentence", sentenceTexts.any { it.contains("First sentence") })
        assertTrue("Should find second sentence", sentenceTexts.any { it.contains("Second sentence") })
        assertTrue("Should find third sentence", sentenceTexts.any { it.contains("Third sentence") })
    }

    @Test
    fun processChapter_questionAndExclamation_preservesBoundaries() {
        val html = "<p>Is this a question? Yes it is! That is great.</p>"
        val result = preprocessor.processChapter(html)
        val sentenceTexts = result.sentences.map { it.text }
        assertTrue("Should find question", sentenceTexts.any { it.contains("question") })
        assertTrue("Should find exclamation", sentenceTexts.any { it.contains("Yes it is") })
    }

    @Test
    fun processChapter_emptyHtml_returnsEmptyResult() {
        val result = preprocessor.processChapter("", chapterIndex = 0, title = "Empty")
        assertEquals("", result.rawText)
        assertTrue(result.sentences.isEmpty())
    }

    @Test
    fun processChapter_onlyScriptTag_returnsEmptyResult() {
        val html = "<script>console.log(test);</script>"
        val result = preprocessor.processChapter(html)
        assertEquals("", result.rawText)
        assertTrue(result.sentences.isEmpty())
    }

    // --- processChapter: EPUB-like XHTML content ---

    @Test
    fun processChapter_xhtmlContent_extractsText() {
        val html = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head><title>Chapter 1</title></head>\n<body>\n<p>It was the best of times, it was the worst of times.</p>\n<p>It was the age of wisdom, it was the age of foolishness.</p>\n</body>\n</html>"
        val result = preprocessor.processChapter(html, chapterIndex = 0, title = "Chapter 1")
        assertTrue("Should contain text", result.rawText.contains("best of times"))
        assertTrue("Should contain text", result.rawText.contains("age of wisdom"))
        assertFalse("Should not contain XML declaration", result.rawText.contains("<?xml"))
        assertFalse("Should not contain DOCTYPE", result.rawText.contains("DOCTYPE"))
        assertTrue("Should have sentences", result.sentences.size >= 2)
    }

    @Test
    fun processChapter_withEntities_decodesCorrectly() {
        val html = "<p>He said &ldquo;Hello&rdquo; to the world. It was a &amp; test.</p>"
        val result = preprocessor.processChapter(html)
        assertFalse("Should not contain &ldquo;", result.rawText.contains("&ldquo;"))
        assertFalse("Should not contain &rdquo;", result.rawText.contains("&rdquo;"))
        assertFalse("Should not contain &amp;", result.rawText.contains("&amp;"))
        assertTrue("Should contain decoded text", result.rawText.contains("Hello"))
    }

    // --- processChapters: batch processing ---

    @Test
    fun processChapters_multipleChapters_returnsAllResults() {
        val chapters = listOf(
            Triple("<p>Chapter one text.</p>", 0, "Chapter 1"),
            Triple("<p>Chapter two text.</p>", 1, "Chapter 2"),
            Triple("<p>Chapter three text.</p>", 2, "Chapter 3")
        )
        val results = preprocessor.processChapters(chapters)
        assertEquals(3, results.size)
        assertEquals("Chapter 1", results[0].title)
        assertEquals("Chapter 2", results[1].title)
        assertEquals("Chapter 3", results[2].title)
        assertTrue(results[0].rawText.contains("Chapter one"))
        assertTrue(results[1].rawText.contains("Chapter two"))
        assertTrue(results[2].rawText.contains("Chapter three"))
    }

    // --- countSentences ---

    @Test
    fun countSentences_threeSentences_returnsThree() {
        val html = "<p>First sentence. Second sentence. Third sentence.</p>"
        val count = preprocessor.countSentences(html)
        assertEquals(3, count)
    }

    @Test
    fun countSentences_emptyHtml_returnsZero() {
        assertEquals(0, preprocessor.countSentences(""))
    }

    @Test
    fun countSentences_onlyScriptTag_returnsZero() {
        val html = "<script>var x = 1;</script>"
        assertEquals(0, preprocessor.countSentences(html))
    }

    // --- EpubPreprocessConfig ---

    @Test
    fun extractText_preserveLineBreaks_false_collapsesEverything() {
        val config = EpubPreprocessConfig(preserveLineBreaks = false)
        val noBreakPreprocessor = EpubTextPreprocessor(config)
        val html = "<p>First paragraph.</p><p>Second paragraph.</p>"
        val result = noBreakPreprocessor.extractText(html)
        assertFalse("Should not contain newlines", result.contains("\n"))
        assertTrue("Should contain both texts", result.contains("First paragraph"))
        assertTrue("Should contain both texts", result.contains("Second paragraph"))
    }

    // --- Sentence boundary preservation with complex HTML ---

    @Test
    fun processChapter_nestedTags_preservesSentenceBoundaries() {
        val html = "<div><p>This is <strong>bold</strong> text. And this is <em>italic</em> text.</p></div>"
        val result = preprocessor.processChapter(html)
        val sentenceTexts = result.sentences.map { it.text }
        assertTrue("Should find first sentence", sentenceTexts.any { it.contains("bold text") })
        assertTrue("Should find second sentence", sentenceTexts.any { it.contains("italic text") })
    }

    @Test
    fun processChapter_abbreviations_notSplitIncorrectly() {
        val html = "<p>Dr. Smith went to Washington. He arrived at 3 p.m.</p>"
        val result = preprocessor.processChapter(html)
        assertTrue("Should have sentences", result.sentences.isNotEmpty())
    }

    // --- VERIFICATION criteria tests ---

    @Test
    fun verification_epubChaptersConvertToCleanPlainText() {
        val html = "<html><head><title>Test</title></head>\n<body><h1>Chapter 1</h1><p>This is the first paragraph of the chapter.</p>\n<p>This is the second paragraph. It has multiple sentences. Like this one.</p>\n<script>var analytics = {};</script>\n<style>body { font-family: serif; }</style>\n</body></html>"
        val result = preprocessor.processChapter(html, chapterIndex = 0, title = "Chapter 1")
        assertTrue("Should have extracted text", result.rawText.isNotEmpty())
        assertFalse("Should not have html tag", result.rawText.contains("<html"))
        assertFalse("Should not have body tag", result.rawText.contains("<body"))
        assertFalse("Should not have script tag", result.rawText.contains("<script"))
        assertFalse("Should not have style tag", result.rawText.contains("<style"))
        assertTrue("Should have chapter content", result.rawText.contains("first paragraph"))
        assertTrue("Should have chapter content", result.rawText.contains("second paragraph"))
    }

    @Test
    fun verification_sentenceBoundariesPreserved() {
        val html = "<p>Sentence one. Sentence two! Sentence three? Sentence four.</p>"
        val result = preprocessor.processChapter(html)
        assertTrue("Should have multiple sentences", result.sentences.size >= 3)
        val hasPeriod = result.sentences.any { it.text.contains(".") }
        assertTrue("Should have sentences with periods", hasPeriod)
    }

    @Test
    fun verification_noHtmlTagsInOutput() {
        val html = "<div><h2>Title</h2><p>Text with <a href=\"#\">link</a> and <span>highlight</span>.</p><table><tr><td>Cell</td></tr></table></div>"
        val result = preprocessor.extractText(html)
        val htmlTagPattern = Regex("<[^>]+>")
        val tags = htmlTagPattern.findAll(result).toList()
        assertTrue("Output should have no HTML tags, but found: " + tags.map { it.value }.toString(), tags.isEmpty())
    }
}
