package com.mimiral.app.data.reader

import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RtfDebugTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var parser: RtfParser

    @Before
    fun setup() {
        parser = RtfParser()
    }

    @Test
    fun `debug simple RTF`() = runBlocking {
        val rtf = "{\\rtf1\\ansi\\ansicpg1252\\deff0\\deflang1033" +
            "{\\colortbl;\\red0\\green0\\blue0;}" +
            "\\viewkind4\\uc1\\pard\\cf1\\fs24 Hello, World!\\par" +
            "This is a test.\\par}"
        val file = tempFolder.newFile("debug.rtf")
        file.writeText(rtf, StandardCharsets.US_ASCII)
        val result = parser.parse(file)
        when (result) {
            is RtfParseResult.Success -> {
                println("SUCCESS text: [[${result.text}]]")
                assertTrue("Should contain 'Hello, World!'", result.text.contains("Hello, World!"))
            }
            is RtfParseResult.Error -> {
                println("ERROR: ${result.message}")
                result.cause?.printStackTrace()
                fail("Expected Success but got Error: ${result.message}")
            }
        }
    }
}
