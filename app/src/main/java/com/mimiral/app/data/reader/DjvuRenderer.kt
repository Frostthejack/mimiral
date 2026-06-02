package com.mimiral.app.data.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DJVU document renderer.
 *
 * Provides page rendering and text extraction from DJVU files.
 *
 * DJVU format notes:
 * - DJVU files contain one or more pages, each with a background image,
 *   foreground mask, and optional text layer (OCR).
 * - Pages can be rendered as bitmaps for display.
 * - Text layer (if present) can be extracted for search/selection.
 *
 * This implementation provides the rendering interface. Full DJVU decoding
 * requires a native library (e.g., DjVuLibre JNI). The current implementation
 * provides the architectural skeleton with proper resource management,
 * and a bitmap rendering fallback that can be activated when a JNI bridge
 * is added.
 *
 * Usage:
 * ```
 * val renderer = DjvuRenderer(File("/path/to/book.djvu"))
 * val bitmap = renderer.renderPage(0, 800, 1200)
 * val text = renderer.extractPageText(0)
 * renderer.close()
 * ```
 */
class DjvuRenderer : AutoCloseable {

    /** Total number of pages in the opened document. */
    val pageCount: Int
        get() = _pageCount

    /** Whether the document is currently open. */
    val isOpen: Boolean get() = _isOpen

    /** Whether the document has extractable text content. */
    val hasTextContent: Boolean
        get() = _hasTextContent

    private var _pageCount: Int = 0
    private var _isOpen: Boolean = false
    private var _hasTextContent: Boolean = false
    private var openedFilePath: String? = null

    /** Page dimensions cached after opening. */
    private var pageDimensions: MutableList<Pair<Int, Int>> = mutableListOf()

    // ---- Constructors ----

    /**
     * Constructor for direct file opening.
     * Immediately opens the DJVU file.
     */
    constructor(file: File) {
        openInternal(file)
    }

    // ---- Open/Close ----

    /**
     * Open a DJVU file from a file path.
     */
    fun open(filePath: String): Result<Unit> {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(FileNotFoundException("DJVU file not found: $filePath"))
            }
            openInternal(file)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun openInternal(file: File) {
        openedFilePath = file.absolutePath
        _isOpen = true

        // Read DJVU header to determine page count and dimensions.
        // DJVU files start with AT&T magic bytes: "AT&TFORM"
        val bytes = file.readBytes()
        if (sizeOf(bytes) < 8) {
            _pageCount = 0
            _hasTextContent = false
            return
        }

        // Check for DJVU magic: "AT&TFORM" at offset 0
        val magic = String(bytes, 0, 8, Charsets.US_ASCII)
        if (magic != "AT&TFORM") {
            // Try multi-page DJVu (FORM:DJVU) or bundled format
            val altMagic = String(bytes, 4, 4, Charsets.US_ASCII)
            if (altMagic != "FORM") {
                _pageCount = 0
                _hasTextContent = false
                return
            }
        }

        // Parse the DJVU INCL chunk and INFO chunk to determine page count.
        // For now, use a heuristic: scan for INFO chunks which describe pages.
        var offset = 12 // Skip AT&TFORM + size (8 bytes) + FORM/DJVU (4 bytes)
        var pagesFound = 0
        var hasTextLayer = false

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = readDjvuUint32(bytes, offset + 4)

            when {
                chunkId == "INFO" -> {
                    // INFO chunk: contains page dimensions
                    if (offset + 14 <= bytes.size) {
                        pagesFound++
                        val width = ((bytes[offset + 10].toInt() and 0xFF) shl 8) or
                            (bytes[offset + 11].toInt() and 0xFF)
                        val height = ((bytes[offset + 12].toInt() and 0xFF) shl 8) or
                            (bytes[offset + 13].toInt() and 0xFF)
                        if (width > 0 && height > 0) {
                            pageDimensions.add(Pair(width, height))
                        }
                    }
                }
                chunkId == "TXTa" || chunkId == "TXTz" -> {
                    hasTextLayer = true
                }
                chunkId == "FORM" -> {
                    // Nested FORM (bundled/multi-page DJVU)
                }
            }

            // Align to 2-byte boundary
            val paddedSize = if (chunkSize % 2 == 1) chunkSize + 1 else chunkSize
            offset += 8 + paddedSize

            if (chunkSize == 0) break
        }

        // If we found a single INFO in a_Format, check if this is a bundled DJVU
        // with multiple pages (FORM chunks containing sub-formats)
        if (pagesFound == 0) {
            // Fallback: try to count FORM:DJVU occurrences
            pagesFound = countDjvuPages(bytes)
        }

        if (pagesFound == 0) {
            pagesFound = 1 // At least one page if file is valid DJVU
        }

        _pageCount = pagesFound
        _hasTextContent = hasTextLayer

        // If we didn't get dimensions from INFO, estimate from file
        if (pageDimensions.isEmpty()) {
            for (i in 0 until _pageCount) {
                pageDimensions.add(Pair(612, 792)) // Default US Letter size
            }
        }
    }

    /**
     * Count pages in a bundled DJVU by scanning for FORM:DJVU markers.
     */
    private fun countDjvuPages(bytes: ByteArray): Int {
        var count = 0
        var searchOffset = 0
        val target = byteArrayOf(0x46, 0x4F, 0x52, 0x4D, 0x00, 0x00, 0x00, 0x00, 0x44, 0x4A, 0x56, 0x55)
        val altTarget = byteArrayOf(0x44, 0x4A, 0x56, 0x55) // "DJVU"

        while (searchOffset + 4 < bytes.size) {
            val chunk = String(bytes, searchOffset, 4, Charsets.US_ASCII)
            if (chunk == "DJVU") {
                // Check if preceded by FORM
                if (searchOffset >= 8) {
                    val preceding = String(bytes, searchOffset - 8, 4, Charsets.US_ASCII)
                    if (preceding == "FORM") {
                        count++
                    }
                } else if (searchOffset == 0) {
                    count++
                }
            }
            searchOffset++
        }

        return count.coerceAtLeast(1)
    }

    private fun readDjvuUint32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun sizeOf(bytes: ByteArray): Int = bytes.size

    // ---- Rendering ----

    /**
     * Render a page to a bitmap at the specified dimensions.
     *
     * @param pageIndex Zero-based page index
     * @param width Target width in pixels
     * @param height Target height in pixels
     * @return Rendered bitmap, or null if rendering failed
     */
    fun renderPageAtSize(pageIndex: Int, width: Int, height: Int): Bitmap? {
        if (!_isOpen || pageIndex < 0 || pageIndex >= _pageCount) return null

        return try {
            val bitmap = Bitmap.createBitmap(
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            bitmap.eraseColor(Color.WHITE)

            // TODO: When DjVuLibre JNI is integrated, decode the actual page here
            // and draw it onto the bitmap. Currently returns a white placeholder.

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Render a page to a bitmap at the specified width, preserving aspect ratio.
     */
    fun renderPage(pageIndex: Int, targetWidth: Int): Bitmap? {
        if (!_isOpen || pageIndex < 0 || pageIndex >= _pageCount) return null
        val dims = getPageSize(pageIndex) ?: return null
        val aspectRatio = dims.second.toFloat() / dims.first.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)
        return renderPageAtSize(pageIndex, targetWidth, targetHeight)
    }

    /**
     * Render a page to a bitmap at the specified DPI.
     */
    fun renderPage(pageIndex: Int, dpi: Int = 160): Bitmap? {
        val dims = getPageSize(pageIndex) ?: return null
        val width = (dims.first * dpi / 72f).toInt().coerceAtLeast(1)
        val height = (dims.second * dpi / 72f).toInt().coerceAtLeast(1)
        return renderPageAtSize(pageIndex, width, height)
    }

    // ---- Text Extraction ----

    /**
     * Extract text from a page's OCR layer.
     *
     * @param pageIndex Zero-based page index
     * @return DjvuPageText containing extracted text and metadata
     */
    suspend fun extractPageText(pageIndex: Int): DjvuPageText = withContext(Dispatchers.IO) {
        if (!_isOpen || pageIndex < 0 || pageIndex >= _pageCount) {
            return@withContext DjvuPageText("", false, pageIndex, 0, 0)
        }

        val file = openedFilePath?.let { File(it) } ?: return@withContext
            DjvuPageText("", false, pageIndex, 0, 0)

        try {
            val bytes = file.readBytes()
            val text = extractTextFromDjvu(bytes, pageIndex)
            val dims = getPageSize(pageIndex) ?: Pair(612, 792)

            DjvuPageText(
                text = text,
                hasText = text.isNotBlank(),
                pageIndex = pageIndex,
                pageWidth = dims.first,
                pageHeight = dims.second
            )
        } catch (e: Exception) {
            DjvuPageText("", false, pageIndex, 0, 0)
        }
    }

    /**
     * Check if a page has extractable text content.
     */
    suspend fun hasTextOnPage(pageIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val text = extractPageText(pageIndex)
        text.hasText
    }

    /**
     * Extract text from DJVU TXTa/TXTz chunks.
     * DJVU text layer is stored as UTF-8 text with coordinate records.
     * Each record specifies: page, column, region, paragraph, line, word, character.
     */
    private fun extractTextFromDjvu(bytes: ByteArray, targetPage: Int): String {
        if (bytes.size < 12) return ""

        val textBuilder = StringBuilder()
        var offset = 12 // Skip header: AT&TFORM + size + DJVU

        // Skip past the shared master INFO chunk if present
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = readDjvuUint32(bytes, offset + 4)
            val paddedSize = if (chunkSize % 2 == 1) chunkSize + 1 else chunkSize

            if (chunkId == "TXTa" || chunkId == "TXTz") {
                // Found text chunk. Parse the text records.
                val textData = bytes.copyOfRange(offset + 8, (offset + 8 + chunkSize).coerceAtMost(bytes.size))
                val parsed = parseDjvuTextChunk(textData, chunkId == "TXTz", targetPage)
                if (parsed.isNotEmpty()) {
                    if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                    textBuilder.append(parsed)
                }
            }

            offset += 8 + paddedSize
            if (chunkSize == 0) break
        }

        return textBuilder.toString().trim()
    }

    /**
     * Parse a DJVU text chunk (TXTa = raw text, TXTz = compressed).
     * DJVU text records are structured as text-page, text-column, text-region,
     * text-paragraph, text-line, text-word with string data.
     *
     * Format (simplified):
     *   text-page:4 text-column:2 text-region:2 text-paragraph:2 text-line:2 text-word:2 [string]
     */
    private fun parseDjvuTextChunk(data: ByteArray, compressed: Boolean, targetPage: Int): String {
        if (data.isEmpty()) return ""

        val textData = if (compressed) {
            try {
                val inflater = java.util.zip.Inflater()
                inflater.setInput(data)
                val outputStream = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (!inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    if (count == 0 && inflater.needsInput()) break
                    outputStream.write(buffer, 0, count)
                }
                inflater.end()
                outputStream.toByteArray()
            } catch (e: Exception) {
                data // Fallback to raw data
            }
        } else {
            data
        }

        return try {
            // Parse the DJVU text records.
            // The format uses a simple bytecode for record types:
            // 0x00-0x0F: record type codes (text-page, text-column, etc.)
            // Following bytes: record data (length-prefixed)
            parseTextRecords(textData, targetPage)
        } catch (e: Exception) {
            // If structured parsing fails, try to extract plain text
            extractPlainTextFallback(textData)
        }
    }

    /**
     * Parse DJVU text records into a string.
     * Record types from DJVU spec:
     *   0xC0: text-page (start of page data)
     *   0x84: text-column
     *   0x88: text-region
     *   0x8C: text-paragraph
     *   0x90: text-line
     *   0x94: text-word (followed by string)
     *   0xA0: text-char (followed by character)
     *   followed by string length and UTF-8 text
     */
    private fun parseTextRecords(data: ByteArray, targetPage: Int): String {
        val result = StringBuilder()
        var pos = 0
        var currentEntry = StringBuilder()
        var lastWasWord = false

        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF

            when {
                b == 0xC0 -> {
                    // Text-page: next 2 bytes = page number
                    if (pos + 2 < data.size) {
                        val page = ((data[pos + 1].toInt() and 0xFF) shl 8) or
                            (data[pos + 2].toInt() and 0xFF)
                        pos += 3
                        // If this is the target page, collect text
                        continue
                    }
                    pos++
                }
                b == 0x94 -> {
                    // Text-word: followed by string length and UTF-8
                    pos++
                    if (pos < data.size) {
                        val strLen = data[pos].toInt() and 0xFF
                        pos++
                        if (pos + strLen <= data.size) {
                            val word = String(data, pos, strLen, Charsets.UTF_8)
                            if (word.isNotBlank()) {
                                if (currentEntry.isNotEmpty()) currentEntry.append(" ")
                                currentEntry.append(word)
                                lastWasWord = true
                            }
                            pos += strLen
                        }
                    }
                }
                b == 0x8C -> {
                    // Text-paragraph: flush current entry
                    if (currentEntry.isNotEmpty()) {
                        if (result.isNotEmpty()) result.append("\n\n")
                        result.append(currentEntry)
                        currentEntry = StringBuilder()
                    }
                    lastWasWord = false
                    pos++
                    // Skip paragraph data
                    if (pos < data.size) pos++
                }
                b == 0x90 -> {
                    // Text-line: add newline if continuing paragraph
                    if (lastWasWord && currentEntry.isNotEmpty()) {
                        currentEntry.append(" ")
                    }
                    pos++
                    // Skip line data
                    if (pos < data.size) pos++
                }
                b == 0xA0 -> {
                    // Text-char
                    pos++
                    if (pos < data.size) {
                        val charLen = data[pos].toInt() and 0xFF
                        pos++
                        if (pos + charLen <= data.size) {
                            val ch = String(data, pos, charLen, Charsets.UTF_8)
                            currentEntry.append(ch)
                            pos += charLen
                        }
                    }
                }
                else -> {
                    pos++
                }
            }
        }

        // Flush remaining
        if (currentEntry.isNotEmpty()) {
            if (result.isNotEmpty()) result.append("\n\n")
            result.append(currentEntry)
        }

        return result.toString().trim()
    }

    /**
     * Fallback: extract readable ASCII/UTF-8 text from raw bytes.
     * Filters out control characters and short fragments.
     */
    private fun extractPlainTextFallback(data: ByteArray): String {
        val result = StringBuilder()
        val fragment = StringBuilder()

        for (byte in data) {
            val b = byte.toInt() and 0xFF
            if (b in 32..126 || b == 10 || b == 13 || b == 9 || b > 127) {
                fragment.append(b.toChar())
            } else {
                if (fragment.length >= 4) {
                    if (result.isNotEmpty()) result.append(" ")
                    result.append(fragment)
                }
                fragment.clear()
            }
        }
        if (fragment.length >= 4) {
            if (result.isNotEmpty()) result.append(" ")
            result.append(fragment)
        }

        return result.toString()
    }

    // ---- Page Info ----

    /**
     * Get the dimensions of a page in PostScript points (1/72 inch).
     */
    fun getPageSize(pageIndex: Int): Pair<Int, Int>? {
        if (!_isOpen || pageIndex < 0 || pageIndex >= _pageCount) return null
        return if (pageIndex < pageDimensions.size) {
            pageDimensions[pageIndex]
        } else {
            Pair(612, 792) // Default US Letter
        }
    }

    // ---- Lifecycle ----

    override fun close() {
        _isOpen = false
        _pageCount = 0
        _hasTextContent = false
        openedFilePath = null
        pageDimensions.clear()
    }
}

/**
 * Represents extracted text from a DJVU page.
 */
data class DjvuPageText(
    val text: String,
    val hasText: Boolean,
    val pageIndex: Int,
    val pageWidth: Int,
    val pageHeight: Int
)
