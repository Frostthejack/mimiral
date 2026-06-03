package com.mimiral.app.data.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FootnotePageNumberCleanerTest {

    private lateinit var cleaner: FootnotePageNumberCleaner

    @Before
    fun setUp() {
        cleaner = FootnotePageNumberCleaner()
    }

    // ---- Footnote marker tests ----

    @Test
    fun `removes unicode superscript one`() {
        val result = cleaner.clean("Some text¹ with footnote")
        assertEquals("Some text with footnote", result.cleanedText)
        assertEquals(1, result.footnoteMarkersRemoved)
    }

    @Test
    fun `removes unicode superscript two`() {
        val result = cleaner.clean("Word² another")
        assertEquals("Word another", result.cleanedText)
        assertEquals(1, result.footnoteMarkersRemoved)
    }

    @Test
    fun `removes unicode superscript three`() {
        val result = cleaner.clean("Mark³ end")
        assertEquals("Mark end", result.cleanedText)
        assertEquals(1, result.footnoteMarkersRemoved)
    }

    @Test
    fun `removes multiple unicode superscripts`() {
        val result = cleaner.clean("First¹ second² third³")
        assertEquals("First second third", result.cleanedText)
        assertEquals(3, result.footnoteMarkersRemoved)
    }

    @Test
    fun `removes bracketed number markers`() {
        val result = cleaner.clean("Text [1] and [23] markers")
        assertEquals("Text and markers", result.cleanedText)
        assertEquals(2, result.footnoteMarkersRemoved)
    }

    @Test
    fun `removes bracketed asterisk markers`() {
        val result = cleaner.clean("Text [*] and [**] and [***]")
        assertEquals("Text and and", result.cleanedText)
        assertEquals(3, result.footnoteMarkersRemoved)
    }

    @Test
    fun `removes parenthesized number markers`() {
        val result = cleaner.clean("Endnote (1) and (5)")
        assertEquals("Endnote and", result.cleanedText)
        assertEquals(2, result.footnoteMarkersRemoved)
    }

    @Test
    fun `removes dagger markers`() {
        val result = cleaner.clean("Reference† and double‡")
        assertEquals("Reference and double", result.cleanedText)
        assertEquals(2, result.footnoteMarkersRemoved)
    }

    @Test
    fun `removes trailing asterisk markers`() {
        val result = cleaner.clean("word* next** last***")
        assertEquals("word next last", result.cleanedText)
        assertTrue(result.footnoteMarkersRemoved >= 3)
    }

    @Test
    fun `removes html superscript markers`() {
        val result = cleaner.clean("Text<sup>1</sup> and <sup class=\"fn\">2</sup>")
        assertEquals("Text and", result.cleanedText)
        assertEquals(2, result.footnoteMarkersRemoved)
    }

    @Test
    fun `removes bracketed dagger markers`() {
        val result = cleaner.clean("See [†] and [‡]")
        assertEquals("See and", result.cleanedText)
        assertEquals(2, result.footnoteMarkersRemoved)
    }

    // ---- Page number tests ----

    @Test
    fun `removes standalone page number on own line`() {
        val result = cleaner.clean("Text before\n42\nText after")
        assertEquals("Text before\nText after", result.cleanedText)
        assertEquals(1, result.pageNumbersRemoved)
    }

    @Test
    fun `removes braced page marker`() {
        val result = cleaner.clean("Text {42} more")
        assertEquals("Text more", result.cleanedText)
        assertEquals(1, result.pageNumbersRemoved)
    }

    @Test
    fun `removes bracketed page dot marker`() {
        val result = cleaner.clean("Text [p.42] more")
        assertEquals("Text more", result.cleanedText)
        assertEquals(1, result.pageNumbersRemoved)
    }

    @Test
    fun `removes bracketed pg marker`() {
        val result = cleaner.clean("Text [pg 42] more")
        assertEquals("Text more", result.cleanedText)
        assertEquals(1, result.pageNumbersRemoved)
    }

    @Test
    fun `removes centered page dash format`() {
        val result = cleaner.clean("Text - 42 - more")
        assertEquals("Text more", result.cleanedText)
        assertEquals(1, result.pageNumbersRemoved)
    }

    @Test
    fun `removes p-dot page line`() {
        val result = cleaner.clean("Text above\np.42\nText below")
        assertEquals("Text above\nText below", result.cleanedText)
        assertEquals(1, result.pageNumbersRemoved)
    }

    @Test
    fun `removes page word line`() {
        val result = cleaner.clean("Above\npage 42\nBelow")
        assertEquals("Above\nBelow", result.cleanedText)
        assertEquals(1, result.pageNumbersRemoved)
    }

    @Test
    fun `does not remove inline numbers`() {
        val result = cleaner.clean("There are 42 cats and 7 dogs.")
        assertEquals("There are 42 cats and 7 dogs.", result.cleanedText)
        assertEquals(0, result.pageNumbersRemoved)
    }

    @Test
    fun `does not remove year-like numbers inline`() {
        val result = cleaner.clean("The year 2024 was notable.")
        assertEquals("The year 2024 was notable.", result.cleanedText)
    }

    // ---- Main text preservation tests ----

    @Test
    fun `preserves normal text with no markers`() {
        val text = "The quick brown fox jumps over the lazy dog."
        val result = cleaner.clean(text)
        assertEquals(text, result.cleanedText)
        assertEquals(0, result.footnoteMarkersRemoved)
        assertEquals(0, result.pageNumbersRemoved)
    }

    @Test
    fun `preserves parenthetical sentences`() {
        val result = cleaner.clean("He said (see chapter three) and left.")
        assertEquals("He said (see chapter three) and left.", result.cleanedText)
        assertEquals(0, result.footnoteMarkersRemoved)
    }

    @Test
    fun `preserves numbers in sentences`() {
        val result = cleaner.clean("The population is 3.14 million (approximately).")
        // (approximately) shouldn't be removed since it's not just a number
        assertTrue(result.cleanedText.contains("approximately"))
    }

    @Test
    fun `preserves text with asterisk in middle`() {
        val result = cleaner.clean("5 * 3 = 15")
        // The asterisk in "5 * 3" should not be removed by the trailing asterisk pattern
        // since it's not at the end of a word
        assertTrue(result.cleanedText.contains("5"))
        assertTrue(result.cleanedText.contains("3"))
        assertTrue(result.cleanedText.contains("15"))
    }

    @Test
    fun `handles empty string`() {
        val result = cleaner.clean("")
        assertEquals("", result.cleanedText)
        assertEquals(0, result.footnoteMarkersRemoved)
        assertEquals(0, result.pageNumbersRemoved)
    }

    @Test
    fun `handles blank string`() {
        val result = cleaner.clean("   ")
        assertEquals("   ", result.cleanedText)
    }

    // ---- Combined tests ----

    @Test
    fun `removes mixed footnote and page markers`() {
        val input = """
            Chapter One
            
            It was the best of times¹, it was the worst of times[2].
            
            45
            
            To be or not to be†, that is the question.
        """.trimIndent()

        val result = cleaner.clean(input)

        // Footnote markers removed
        assertTrue(result.footnoteMarkersRemoved >= 3)

        // Page number removed
        assertTrue(result.pageNumbersRemoved >= 1)

        // Main text preserved
        assertTrue(result.cleanedText.contains("best of times"))
        assertTrue(result.cleanedText.contains("worst of times"))
        assertTrue(result.cleanedText.contains("To be or not to be"))
        assertTrue(result.cleanedText.contains("question"))

        // Markers not in output
        assertTrue(!result.cleanedText.contains("¹"))
        assertTrue(!result.cleanedText.contains("[2]"))
        assertTrue(!result.cleanedText.contains("†"))
    }

    @Test
    fun `removes multiple page numbers on separate lines`() {
        val input = "Start\n10\nMiddle\n20\nEnd"
        val result = cleaner.clean(input)
        assertTrue(result.pageNumbersRemoved >= 2)
        assertTrue(result.cleanedText.contains("Start"))
        assertTrue(result.cleanedText.contains("Middle"))
        assertTrue(result.cleanedText.contains("End"))
    }

    @Test
    fun `removes footnote body lines`() {
        val input = "Main text here.\n1. This is a footnote.\n2. Another footnote.\nMore main text."
        val result = cleaner.clean(input)
        assertTrue(result.footnoteMarkersRemoved >= 2)
        assertTrue(result.cleanedText.contains("Main text here"))
        assertTrue(result.cleanedText.contains("More main text"))
    }

    // ---- Edge cases ----

    @Test
    fun `consecutive footnote markers removed`() {
        val result = cleaner.clean("Word[1][2] end")
        assertEquals("Word end", result.cleanedText)
        assertEquals(2, result.footnoteMarkersRemoved)
    }

    @Test
    fun `braced page markers case insensitive`() {
        val result = cleaner.clean("Text [P.42] and [PG 99]")
        assertEquals("Text and", result.cleanedText)
        assertEquals(2, result.pageNumbersRemoved)
    }

    @Test
    fun `does not remove zero as standalone page number`() {
        val result = cleaner.clean("Some text\n0\nMore text")
        // 0 should not be treated as a page number
        assertTrue(result.cleanedText.contains("0") || result.pageNumbersRemoved == 0)
    }

    @Test
    fun `companion cleanText convenience method`() {
        val result = FootnotePageNumberCleaner.cleanText("Text¹ with [2] markers")
        assertTrue(!result.contains("¹"))
        assertTrue(!result.contains("[2]"))
        assertTrue(result.contains("Text"))
        assertTrue(result.contains("with"))
        assertTrue(result.contains("markers"))
    }
}
