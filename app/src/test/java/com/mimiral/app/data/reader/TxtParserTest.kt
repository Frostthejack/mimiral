package com.mimiral.app.data.reader

import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TxtParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var parser: TxtParser

    @Before
    fun setup() {
        parser = TxtParser()
    }

    @Test
    fun `parse simple UTF-8 file`() = runBlocking {
        val file = tempFolder.newFile("test.txt")
        file.writeText("Hello, World!\nThis is a test.", StandardCharsets.UTF_8)

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertEquals("Hello, World!\nThis is a test.", success.text)
        assertEquals("UTF-8", success.encoding)
        assertEquals("Hello, World!", success.title)
        assertTrue(success.characterCount > 0)
    }

    @Test
    fun `parse UTF-8 with BOM`() = runBlocking {
        val file = tempFolder.newFile("test_bom.txt")
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val content = "Hello with BOM".toByteArray(StandardCharsets.UTF_8)
        file.writeBytes(bom + content)

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertEquals("UTF-8-BOM", success.encoding)
        assertEquals("Hello with BOM", success.text)
    }

    @Test
    fun `parse UTF-16 LE file`() = runBlocking {
        val file = tempFolder.newFile("test_utf16le.txt")
        val content = "Hello UTF-16".toByteArray(StandardCharsets.UTF_16LE)
        // Add UTF-16LE BOM
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        file.writeBytes(bom + content)

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertEquals("UTF-16LE", success.encoding)
        assertEquals("Hello UTF-16", success.text)
    }

    @Test
    fun `parse UTF-16 BE file`() = runBlocking {
        val file = tempFolder.newFile("test_utf16be.txt")
        val content = "Hello UTF-16BE".toByteArray(StandardCharsets.UTF_16BE)
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        file.writeBytes(bom + content)

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertEquals("UTF-16BE", success.encoding)
        assertEquals("Hello UTF-16BE", success.text)
    }

    @Test
    fun `parse Latin-1 file with special chars`() = runBlocking {
        val file = tempFolder.newFile("test_latin1.txt")
        // cafe with accented e (0xE9 in Latin-1)
        val content = "caf\u00E9".toByteArray(StandardCharsets.ISO_8859_1)
        file.writeBytes(content)

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertTrue(success.encoding == "ISO-8859-1" || success.encoding == "UTF-8")
        assertTrue(success.text.contains("caf"))
    }

    @Test
    fun `parse empty file`() = runBlocking {
        val file = tempFolder.newFile("empty.txt")
        file.writeText("")

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertEquals("", success.text)
        assertEquals(0, success.characterCount)
    }

    @Test
    fun `parse non-existent file returns error`() = runBlocking {
        val file = File(tempFolder.root, "nonexistent.txt")

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Error)
        val error = result as TxtParseResult.Error
        assertTrue(error.message.contains("not found"))
    }

    @Test
    fun `chapter breaks detected with form feed`() = runBlocking {
        val file = tempFolder.newFile("chapters_ff.txt")
        val text = "Chapter 1 content here.\n\f\nChapter 2 content here."
        file.writeText(text)

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertTrue("Should have chapter break at form feed", success.chapterBreaks.size >= 2)
    }

    @Test
    fun `chapter breaks detected with markdown headers`() = runBlocking {
        val file = tempFolder.newFile("chapters_md.txt")
        val text = "# Chapter 1\nContent here.\n\n# Chapter 2\nMore content."
        file.writeText(text)

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertTrue("Should have chapter breaks at headers", success.chapterBreaks.size >= 2)
        assertEquals(0, success.chapterBreaks[0]) // First break at position 0
    }

    @Test
    fun `chapter breaks detected with multiple blank lines`() = runBlocking {
        val file = tempFolder.newFile("chapters_blank.txt")
        val text = "Part 1 content here.\n\n\n\nPart 2 content here."
        file.writeText(text)

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertTrue("Should have chapter break at 3+ blank lines", success.chapterBreaks.size >= 2)
    }

    @Test
    fun `chapter breaks with all-caps heading`() = runBlocking {
        val file = tempFolder.newFile("chapters_caps.txt")
        val text = "Intro text here.\n\n\nCHAPTER TWO\nContent of chapter 2."
        file.writeText(text)

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertTrue("Should detect CHAPTER TWO as heading", success.chapterBreaks.size >= 2)
    }

    @Test
    fun `title extracted from first line`() = runBlocking {
        val file = tempFolder.newFile("titled.txt")
        file.writeText("My Book Title\n\nChapter 1 content...")

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertEquals("My Book Title", success.title)
    }

    @Test
    fun `title falls back to filename when first line is empty`() = runBlocking {
        val file = tempFolder.newFile("mybook.txt")
        file.writeText("\n\nSome content after empty lines.")

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertEquals("mybook", success.title)
    }

    @Test
    fun `parse from input stream`() = runBlocking {
        val bytes = "Stream content here.".toByteArray(StandardCharsets.UTF_8)
        val inputStream = bytes.inputStream()

        val result = parser.parse(inputStream, "testfile.txt")

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertEquals("Stream content here.", success.text)
        assertEquals("testfile", success.title)
    }

    @Test
    fun `always has chapter break at position 0`() = runBlocking {
        val file = tempFolder.newFile("simple.txt")
        file.writeText("Just some text without any chapter breaks.")

        val result = parser.parse(file)

        assertTrue(result is TxtParseResult.Success)
        val success = result as TxtParseResult.Success
        assertTrue("Should always have break at 0", success.chapterBreaks.contains(0))
    }
}
