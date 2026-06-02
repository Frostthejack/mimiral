package com.mimiral.app.data.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceBoundaryDetectorTest {

    private val detector = SentenceBoundaryDetector()

    @Test
    fun `findSentences - empty text returns empty list`() {
        assertTrue(detector.findSentences("").isEmpty())
    }

    @Test
    fun `findSentences - blank text returns empty list`() {
        assertTrue(detector.findSentences("   ").isEmpty())
    }

    @Test
    fun `findSentences - single sentence`() {
        val result = detector.findSentences("Hello world.")
        assertEquals(1, result.size)
        assertEquals(0, result[0].start)
        assertEquals("Hello world.", result[0].text)
    }

    @Test
    fun `findSentences - two sentences`() {
        val result = detector.findSentences("Hello world. How are you?")
        assertEquals(2, result.size)
        assertEquals("Hello world.", result[0].text)
        assertEquals("How are you?", result[1].text)
    }

    @Test
    fun `findSentences - three sentences with exclamation`() {
        val result = detector.findSentences("Hello! How are you? I'm fine.")
        assertEquals(3, result.size)
        assertEquals("Hello!", result[0].text)
        assertEquals("How are you?", result[1].text)
        assertEquals("I'm fine.", result[2].text)
    }

    @Test
    fun `findSentences - preserves offsets in original text`() {
        val text = "First sentence. Second sentence."
        val result = detector.findSentences(text)
        assertEquals(2, result.size)
        assertEquals(text.substring(result[0].start, result[0].end).trim(), result[0].text)
        assertEquals(text.substring(result[1].start, result[1].end).trim(), result[1].text)
    }

    @Test
    fun `findSentences - handles question marks`() {
        val result = detector.findSentences("What time is it? Is it noon?")
        assertEquals(2, result.size)
        assertTrue(result[0].text.contains("What time is it?"))
        assertTrue(result[1].text.contains("Is it noon?"))
    }

    @Test
    fun `findSentenceAt - finds correct sentence`() {
        val text = "First sentence here. Second sentence here."
        val result = detector.findSentenceAt(text, 5)
        assertNotNull(result)
        assertTrue(result!!.text.contains("First"))
    }

    @Test
    fun `findSentenceAt - returns null for offset out of bounds`() {
        val text = "Hello world."
        assertNull(detector.findSentenceAt(text, 100))
        assertNull(detector.findSentenceAt(text, -1))
    }

    @Test
    fun `findSentenceAt - returns null for blank text`() {
        assertNull(detector.findSentenceAt("", 0))
    }

    @Test
    fun `countSentences - empty text returns zero`() {
        assertEquals(0, detector.countSentences(""))
    }

    @Test
    fun `countSentences - single sentence`() {
        assertEquals(1, detector.countSentences("Hello world."))
    }

    @Test
    fun `countSentences - multiple sentences`() {
        assertEquals(3, detector.countSentences("Hello. How are you? I'm fine."))
    }

    @Test
    fun `splitSentences - returns text only`() {
        val result = detector.splitSentences("Hello world. How are you?")
        assertEquals(listOf("Hello world.", "How are you?"), result)
    }

    @Test
    fun `splitSentences - empty text`() {
        assertTrue(detector.splitSentences("").isEmpty())
    }

    @Test
    fun `findSentences - handles newlines between sentences`() {
        val text = "First sentence.\nSecond sentence."
        val result = detector.findSentences(text)
        assertTrue(result.size >= 2)
    }

    @Test
    fun `findSentences - handles multiple spaces`() {
        val result = detector.findSentences("Hello.    How are you?")
        assertEquals(2, result.size)
    }
}
