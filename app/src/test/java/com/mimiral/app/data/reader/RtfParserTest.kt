package com.mimiral.app.data.reader

import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RtfParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var parser: RtfParser

    @Before
    fun setup() {
        parser = RtfParser()
    }

    @Test
    fun `parse simple RTF file`() = runBlocking {
        val rtf = "{\rtf1\ansi\ansicpg1252\deff0\deflang1033" +
            "{\colortbl;\red0\green0\blue0;}" +
            "\viewkind4\uc1\pard\cf1\fs24 Hello, World!\par" +
            "This is a test.\par}"
        val file = tempFolder.newFile("test.rtf")
        file.writeText(rtf, StandardCharsets.US_ASCII)

        val result = parser.parse(file)

        assertTrue(result is RtfParseResult.Success)
        val success = result as RtfParseResult.Success
        assertTrue("Should contain 'Hello, World!'", success.text.contains("Hello, World!"))
        assertTrue("Should contain 'This is a test'", success.text.contains("This is a test"))
    }

    @Test
    fun `parse RTF with Unicode characters`() = runBlocking {
        val rtf = "{\rtf1\ansi\ansicpg1252" +
            "\u8364? Euro sign\par" + // Euro sign
            "\u63? C\par}" // C
        val file = tempFolder.newFile("unicode.rtf")
        file.writeText(rtf, StandardCharsets.US_ASCII)

        val result = parser.parse(file)

        assertTrue(result is RtfParseResult.Success)
        val success = result as RtfParseResult.Success
        assertTrue(success.text.length > 0)
    }

    @Test
    fun `parse RTF with multiple paragraphs`() = runBlocking {
        val rtf = "{\rtf1\ansi\ansicpg1252" +
            "\par First paragraph.\par" +
            "\par Second paragraph.\par" +
            "\par Third paragraph.\par}"
        val file = tempFolder.newFile("multi_para.rtf")
        file.writeText(rtf, StandardCharsets.US_ASCII)

        val result = parser.parse(file)

        assertTrue(result is RtfParseResult.Success)
        val success = result as RtfParseResult.Success
        assertTrue("Should contain 'First paragraph'", success.text.contains("First paragraph"))
        assertTrue("Should contain 'Second paragraph'", success.text.contains("Second paragraph"))
    }

    @Test
    fun `parse RTF with tab characters`() = runBlocking {
        val rtf = "{\rtf1\ansi\ansicpg1252" +
            "Before\tab After\par}"
        val file = tempFolder.newFile("tabs.rtf")
        file.writeText(rtf, StandardCharsets.US_ASCII)

        val result = parser.parse(file)

        assertTrue(result is RtfParseResult.Success)
        val success = result as RtfParseResult.Success
        assertTrue("Should contain tab separator", success.text.contains("\t"))
    }

    @Test
    fun `parse RTF with special dashes`() = runBlocking {
        val rtf = "{\rtf1\ansi\ansicpg1252" +
            "Word\endash another\par" +
            "Word\emdash another\par}"
        val file = tempFolder.newFile("dashes.rtf")
        file.writeText(rtf, StandardCharsets.US_ASCII)

        val result = parser.parse(file)

        assertTrue(result is RtfParseResult.Success)
        val success = result as RtfParseResult.Success
        assertTrue("Should contain dashes", success.text.contains("another"))
    }

    @Test
    fun `parse empty RTF file`() = runBlocking {
        val file = tempFolder.newFile("empty.rtf")
        file.writeText("")

        val result = parser.parse(file)

        assertTrue(result is RtfParseResult.Success)
        val success = result as RtfParseResult.Success
        assertEquals(0, success.characterCount)
    }

    @Test
    fun `parse non-existent RTF file returns error`() = runBlocking {
        val file = File(tempFolder.root, "nonexistent.rtf")

        val result = parser.parse(file)

        assertTrue(result is RtfParseResult.Error)
        val error = result as RtfParseResult.Error
        assertTrue(error.message.contains("not found"))
    }

    @Test
    fun `title extracted from RTF content`() = runBlocking {
        val rtf = "{\rtf1\ansi\ansicpg1252" +
            "My Great Novel\par" +
            "Chapter one begins here.\par}"
        val file = tempFolder.newFile("titled_novel.rtf")
        file.writeText(rtf, StandardCharsets.US_ASCII)

        val result = parser.parse(file)

        assertTrue(result is RtfParseResult.Success)
        val success = result as RtfParseResult.Success
        assertTrue("Title should be 'My Great Novel'", success.title.contains("My Great Novel"))
    }

    @Test
    fun `parse from input stream`() = runBlocking {
        val rtf = "{\rtf1\ansi\ansicpg1252" +
            "Stream content here.\par}"
        val bytes = rtf.toByteArray(StandardCharsets.US_ASCII)
        val inputStream = bytes.inputStream()

        val result = parser.parse(inputStream, "stream_test.rtf")

        assertTrue(result is RtfParseResult.Success)
        val success = result as RtfParseResult.Success
        assertTrue(success.text.contains("Stream content"))
    }

    @Test
    fun `chapter breaks detected in RTF content`() = runBlocking {
        val rtf = "{\rtf1\ansi\ansicpg1252" +
            "Intro text.\par" +
            "\par" +
            "\par" +
            "\par" +
            "CHAPTER TWO\par" +
            "Content.\par}"
        val file = tempFolder.newFile("rtf_chapters.rtf")
        file.writeText(rtf, StandardCharsets.US_ASCII)

        val result = parser.parse(file)

        assertTrue(result is RtfParseResult.Success)
        val success = result as RtfParseResult.Success
        assertTrue("Should have chapter breaks", success.chapterBreaks.size >= 1)
    }

    @Test
    fun `font table is skipped in RTF`() = runBlocking {
        val rtf = "{\rtf1\ansi\ansicpg1252" +
            "{\fonttbl{\f0\froman\fcharset0 Times New Roman;}}" +
            "\f0\fs24 Visible text here.\par}"
        val file = tempFolder.newFile("fonttable.rtf")
        file.writeText(rtf, StandardCharsets.US_ASCII)

        val result = parser.parse(file)

        assertTrue(result is RtfParseResult.Success)
        val success = result as RtfParseResult.Success
        assertTrue("Should contain visible text", success.text.contains("Visible text"))
        assertFalse("Should not contain font table data", success.text.contains("Times New Roman"))
    }

    @Test
    fun `color table is skipped in RTF`() = runBlocking {
        val rtf = "{\rtf1\ansi\ansicpg1252" +
            "{\colortbl;\red255\green0\blue0;\red0\green255\blue0;}" +
            "Colored text here.\par}"
        val file = tempFolder.newFile("colortbl.rtf")
        file.writeText(rtf, StandardCharsets.US_ASCII)

        val result = parser.parse(file)

        assertTrue(result is RtfParseResult.Success)
        val success = result as RtfParseResult.Success
        assertTrue("Should contain text", success.text.contains("Colored text"))
        assertFalse("Should not contain color data", success.text.contains("\red255"))
    }
}
