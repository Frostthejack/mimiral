package com.mimiral.app.data.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * State of a DOC/DOCX document being parsed.
 */
sealed class DocState {
    /** No document is loaded. */
    object Idle : DocState()

    /** A DOC/DOCX is loaded and ready for reading. */
    data class Loaded(
        val title: String,
        val author: String?,
        val description: String?,
        val chapterCount: Int,
        val filePath: String,
        val isDocx: Boolean
    ) : DocState()

    /** An error occurred while parsing the document. */
    data class Error(val message: String, val cause: Throwable? = null) : DocState()
}

/**
 * Represents a single chapter/section in a DOC/DOCX document.
 */
data class DocChapter(
    val index: Int,
    val title: String,
    val text: String,
    val isHeading: Boolean = false,
    val headingLevel: Int = 0
)

/**
 * An embedded image extracted from a DOCX document.
 */
data class DocImage(
    val id: String,
    val mimeType: String,
    val data: ByteArray
) {
    fun toBitmap(): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (_: Exception) {
            null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DocImage) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Result of a cover/thumbnail extraction operation.
 */
sealed class DocCoverResult {
    data class Success(val bitmap: Bitmap, val mimeType: String) : DocCoverResult()
    data class NotFound(val message: String = "No cover image found in document") : DocCoverResult()
    data class Error(val message: String, val cause: Throwable? = null) : DocCoverResult()
}

/**
 * DOC and DOCX format parser.
 *
 * DOCX (OOXML):
 *   - Reads word/document.xml from the ZIP archive
 *   - Extracts paragraphs (<w:p>) with text runs (<w:r>) and text (<w:t>)
 *   - Preserves basic formatting: bold (<w:b/>), italic (<w:i/>)
 *   - Detects headings by style name (Heading1, Heading2, etc.)
 *   - Extracts embedded images from word/media/
 *
 * DOC (OLE2/BIFF):
 *   - Best-effort plain text extraction using heuristics
 *   - Reads raw bytes and extracts readable UTF-8/ASCII text segments
 *   - No formatting preservation (limitation of the simple approach)
 *
 * Thread-safe via internal mutex.
 */
class DocParser {

    private var currentFile: File? = null
    private var chapters: List<DocChapter> = emptyList()
    private var images: Map<String, DocImage> = emptyMap()
    private var bookTitle: String = ""
    private var bookAuthor: String? = null
    private var isDocx: Boolean = false
    private val mutex = Mutex()

    var state: DocState = DocState.Idle
        private set

    /**
     * Open and parse a DOC or DOCX file.
     */
    suspend fun openFile(file: File): DocState = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists()) {
                    val error = DocState.Error("File not found: ${file.absolutePath}")
                    state = error
                    return@withContext error
                }
                if (!file.canRead()) {
                    val error = DocState.Error("Cannot read file: ${file.absolutePath}")
                    state = error
                    return@withContext error
                }

                closeInternal()
                currentFile = file

                val ext = file.extension.lowercase()
                isDocx = ext == "docx"

                if (isDocx) {
                    parseDocx(file)
                } else {
                    parseDoc(file)
                }

                val loaded = DocState.Loaded(
                    title = bookTitle.ifBlank { file.nameWithoutExtension },
                    author = bookAuthor,
                    description = null,
                    chapterCount = chapters.size,
                    filePath = file.absolutePath,
                    isDocx = isDocx
                )
                state = loaded
                loaded
            } catch (e: Exception) {
                closeInternal()
                val error = DocState.Error("Failed to parse document: ${e.message}", e)
                state = error
                error
            }
        }
    }

    suspend fun getChapters(): List<DocChapter> = mutex.withLock { chapters }

    suspend fun getImages(): Map<String, DocImage> = mutex.withLock { images }

    suspend fun getTitle(): String = mutex.withLock { bookTitle }

    suspend fun getAuthor(): String? = mutex.withLock { bookAuthor }

    fun getCurrentFile(): File? = currentFile

    suspend fun getFullText(): String = mutex.withLock {
        chapters.joinToString("\n\n") { it.text }
    }

    suspend fun extractCover(): DocCoverResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (images.isNotEmpty()) {
                    val firstImage = images.values.first()
                    val bitmap = firstImage.toBitmap()
                    if (bitmap != null) {
                        return@withContext DocCoverResult.Success(
                            bitmap = bitmap,
                            mimeType = firstImage.mimeType
                        )
                    }
                }
                DocCoverResult.NotFound()
            } catch (e: Exception) {
                DocCoverResult.Error("Failed to extract cover: ${e.message}", e)
            }
        }
    }

    suspend fun close() = mutex.withLock {
        closeInternal()
        state = DocState.Idle
    }

    // ==================== DOCX Parsing (OOXML) ====================

    private fun parseDocx(file: File) {
        val chaptersList = mutableListOf<DocChapter>()
        val imagesMap = mutableMapOf<String, DocImage>()

        // First pass: extract images from the ZIP
        extractDocxImages(file, imagesMap)

        // Second pass: parse document.xml for text content
        val fis = FileInputStream(file)
        val bis = BufferedInputStream(fis)
        val zipStream = ZipInputStream(bis)

        var entry = zipStream.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                parseDocxDocument(zipStream, chaptersList)
                break
            }
            entry = zipStream.nextEntry
        }
        zipStream.close()

        // If no chapters were found (e.g., no headings), create one chapter from all text
        if (chaptersList.isEmpty()) {
            // Try to extract any text we can
            val allText = extractDocxPlainText(file)
            if (allText.isNotBlank()) {
                chaptersList.add(
                    DocChapter(
                        index = 0,
                        title = "Document",
                        text = allText.trim(),
                        isHeading = false
                    )
                )
            }
        }

        images = imagesMap
        chapters = chaptersList
    }

    private fun extractDocxImages(file: File, imagesMap: MutableMap<String, DocImage>) {
        try {
            val fis = FileInputStream(file)
            val bis = BufferedInputStream(fis)
            val zipStream = ZipInputStream(bis)

            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("word/media/") && !entry.isDirectory) {
                    val data = zipStream.readBytes()
                    if (data.isNotEmpty()) {
                        val name = File(entry.name).name
                        val mimeType = when {
                            name.endsWith(".png", true) -> "image/png"
                            name.endsWith(".jpg", true) ||
                                name.endsWith(".jpeg", true) -> "image/jpeg"
                            name.endsWith(".gif", true) -> "image/gif"
                            name.endsWith(".bmp", true) -> "image/bmp"
                            name.endsWith(".svg", true) -> "image/svg+xml"
                            else -> "application/octet-stream"
                        }
                        imagesMap[name] = DocImage(
                            id = name,
                            mimeType = mimeType,
                            data = data
                        )
                    }
                }
                entry = zipStream.nextEntry
            }
            zipStream.close()
        } catch (_: Exception) {
            // Image extraction is best-effort
        }
    }

    private fun parseDocxDocument(inputStream: InputStream, chaptersList: MutableList<DocChapter>) {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        val OOXML_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

        var currentChapterTitle = "Document"
        var currentChapterText = StringBuilder()
        var currentRunText = StringBuilder()
        var currentParagraphText = StringBuilder()
        var paragraphCount = 0
        var isBold = false
        var isItalic = false
        var isHeading = false
        var headingLevel = 0
        var inTextRun = false
        var inParagraph = false
        var inRunProperties = false
        var inParagraphProperties = false
        var inStyle = false
        var currentStyleId = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    val ns = parser.namespace ?: ""

                    when {
                        name == "p" && ns == OOXML_W -> {
                            inParagraph = true
                            currentParagraphText = StringBuilder()
                            isHeading = false
                            headingLevel = 0
                            currentStyleId = ""
                        }
                        name == "pPr" && ns == OOXML_W -> {
                            inParagraphProperties = true
                        }
                        name == "pStyle" && ns == OOXML_W -> {
                            inStyle = true
                            currentStyleId = parser.getAttributeValue(OOXML_W, "val") ?: ""
                            // Check if this is a heading style
                            val styleLower = currentStyleId.lowercase()
                            if (styleLower.startsWith("heading")) {
                                isHeading = true
                                headingLevel = styleLastNumber(styleLower)
                            }
                        }
                        name == "r" && ns == OOXML_W -> {
                            inTextRun = true
                            currentRunText = StringBuilder()
                            isBold = false
                            isItalic = false
                        }
                        name == "rPr" && ns == OOXML_W -> {
                            inRunProperties = true
                        }
                        name == "b" && ns == OOXML_W -> {
                            if (inRunProperties || inParagraphProperties) isBold = true
                        }
                        name == "i" && ns == OOXML_W -> {
                            if (inRunProperties || inParagraphProperties) isItalic = true
                        }
                        name == "t" && ns == OOXML_W -> {
                            // Text element - content comes in TEXT event
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    if (inTextRun) {
                        currentRunText.append(text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    val ns = parser.namespace ?: ""

                    when {
                        name == "t" && ns == OOXML_W -> {
                            // Text content already captured
                        }
                        name == "r" && ns == OOXML_W -> {
                            inTextRun = false
                            val runStr = currentRunText.toString()
                            if (runStr.isNotEmpty()) {
                                // Apply formatting markers
                                val formatted = when {
                                    isBold && isItalic -> "**$runStr**"
                                    isBold -> "**$runStr**"
                                    isItalic -> "_${runStr}_"
                                    else -> runStr
                                }
                                currentParagraphText.append(formatted)
                            }
                        }
                        name == "rPr" && ns == OOXML_W -> {
                            inRunProperties = false
                        }
                        name == "pStyle" && ns == OOXML_W -> {
                            inStyle = false
                        }
                        name == "pPr" && ns == OOXML_W -> {
                            inParagraphProperties = false
                        }
                        name == "p" && ns == OOXML_W -> {
                            inParagraph = false
                            val paraText = currentParagraphText.toString().trim()
                            if (paraText.isNotEmpty()) {
                                if (isHeading) {
                                    // Save previous chapter
                                    val prevText = currentChapterText.toString().trim()
                                    if (prevText.isNotEmpty()) {
                                        chaptersList.add(
                                            DocChapter(
                                                index = chaptersList.size,
                                                title = currentChapterTitle,
                                                text = prevText,
                                                isHeading = false
                                            )
                                        )
                                    }
                                    currentChapterTitle = paraText
                                    currentChapterText = StringBuilder()
                                } else {
                                    if (currentChapterText.isNotEmpty()) {
                                        currentChapterText.append("\n\n")
                                    }
                                    currentChapterText.append(paraText)
                                }
                            }
                            paragraphCount++
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        // Save the last chapter
        val remainingText = currentChapterText.toString().trim()
        if (remainingText.isNotEmpty()) {
            chaptersList.add(
                DocChapter(
                    index = chaptersList.size,
                    title = currentChapterTitle.ifBlank { "Section ${chaptersList.size + 1}" },
                    text = remainingText,
                    isHeading = false
                )
            )
        }

        // Extract title from core.xml if available
        extractDocxMetadata(parser = null, file = null, chaptersList = chaptersList)
    }

    private fun extractDocxPlainText(file: File): String {
        return try {
            val fis = FileInputStream(file)
            val bis = BufferedInputStream(fis)
            val zipStream = ZipInputStream(bis)
            val sb = StringBuilder()

            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val factory = XmlPullParserFactory.newInstance()
                    factory.isNamespaceAware = true
                    val parser = factory.newPullParser()
                    parser.setInput(zipStream, "UTF-8")

                    val OOXML_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                    var inText = false
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                if (parser.name == "t" && parser.namespace == OOXML_W) {
                                    inText = true
                                }
                            }
                            XmlPullParser.TEXT -> {
                                if (inText) {
                                    sb.append(parser.text ?: "")
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                if (parser.name == "t" && parser.namespace == OOXML_W) {
                                    inText = false
                                }
                                if (parser.name == "p" && parser.namespace == OOXML_W) {
                                    sb.append("\n")
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                    break
                }
                entry = zipStream.nextEntry
            }
            zipStream.close()
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractDocxMetadata(
        parser: XmlPullParser?,
        file: File?,
        chaptersList: MutableList<DocChapter>
    ) {
        if (file == null) return
        try {
            val fis = FileInputStream(file)
            val bis = BufferedInputStream(fis)
            val zipStream = ZipInputStream(bis)

            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "docProps/core.xml") {
                    val factory = XmlPullParserFactory.newInstance()
                    factory.isNamespaceAware = true
                    val xmlParser = factory.newPullParser()
                    xmlParser.setInput(zipStream, "UTF-8")

                    val DC = "http://purl.org/dc/elements/1.1/"
                    val DCTERMS = "http://purl.org/dc/terms/"

                    var inTitle = false
                    var inCreator = false
                    var eventType = xmlParser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                val ns = xmlParser.namespace ?: ""
                                when {
                                    xmlParser.name == "title" && ns == DC -> inTitle = true
                                    xmlParser.name == "creator" && ns == DC -> inCreator = true
                                }
                            }
                            XmlPullParser.TEXT -> {
                                val text = xmlParser.text ?: ""
                                when {
                                    inTitle -> bookTitle = text.trim()
                                    inCreator -> bookAuthor = text.trim()
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                val ns = xmlParser.namespace ?: ""
                                when {
                                    xmlParser.name == "title" && ns == DC -> inTitle = false
                                    xmlParser.name == "creator" && ns == DC -> inCreator = false
                                }
                            }
                        }
                        eventType = xmlParser.next()
                    }
                    break
                }
                entry = zipStream.nextEntry
            }
            zipStream.close()
        } catch (_: Exception) {
            // Metadata extraction is best-effort
        }
    }

    private fun styleLastNumber(styleLower: String): Int {
        val number = styleLastNumberRegex.find(styleLower)?.value?.toIntOrNull() ?: 1
        return number
    }

    // ==================== DOC Parsing (OLE2/BIFF heuristic) ====================

    private fun parseDoc(file: File) {
        val chaptersList = mutableListOf<DocChapter>()

        try {
            val data = file.readBytes()
            val text = extractTextFromDocBytes(data)

            if (text.isNotBlank()) {
                // Split into paragraphs
                val paragraphs = text.split(Regex("\\n{2,}"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (paragraphs.isNotEmpty()) {
                    // Try to detect headings (short lines that look like titles)
                    val currentChapterText = StringBuilder()
                    var chapterTitle = "Document"
                    var chapterIndex = 0

                    for (para in paragraphs) {
                        val isHeading = para.length < 100 &&
                            para.length > 2 &&
                            !para.endsWith(".") &&
                            para == para.replaceFirstChar { it.uppercase() } &&
                            !para.contains(Regex("\\d+\\."))

                        if (isHeading && currentChapterText.isNotEmpty()) {
                            chaptersList.add(
                                DocChapter(
                                    index = chapterIndex++,
                                    title = chapterTitle,
                                    text = currentChapterText.toString().trim()
                                )
                            )
                            currentChapterText.clear()
                            chapterTitle = para
                        } else {
                            if (currentChapterText.isNotEmpty()) {
                                currentChapterText.append("\n\n")
                            }
                            currentChapterText.append(para)
                        }
                    }

                    // Save last chapter
                    val remaining = currentChapterText.toString().trim()
                    if (remaining.isNotEmpty()) {
                        chaptersList.add(
                            DocChapter(
                                index = chapterIndex,
                                title = chapterTitle,
                                text = remaining
                            )
                        )
                    }
                }
            }

            if (chaptersList.isEmpty()) {
                // Fallback: just put everything in one chapter
                val rawText = extractTextFromDocBytes(data)
                if (rawText.isNotBlank()) {
                    chaptersList.add(
                        DocChapter(
                            index = 0,
                            title = file.nameWithoutExtension,
                            text = rawText.trim()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Best-effort parsing
        }

        chapters = chaptersList
    }

    /**
     * Heuristic text extraction from DOC (OLE2/BIFF) binary data.
     * Reads the raw bytes and extracts readable text segments.
     */
    private fun extractTextFromDocBytes(data: ByteArray): String {
        if (data.isEmpty()) return ""

        val textSegments = mutableListOf<String>()
        val currentSegment = StringBuilder()

        // DOC files typically store text in either ANSI or Unicode
        // Try to detect the encoding by looking for common patterns
        val isUnicode = detectUnicodeDoc(data)

        if (isUnicode) {
            // Read as UTF-16LE (2 bytes per character)
            var i = 0
            while (i < data.size - 1) {
                val char = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i].toInt() and 0xFF)
                if (char in 0x20..0x7E || char == 0x0A || char == 0x0D || char == 0x09) {
                    currentSegment.append(char.toChar())
                } else if (char > 0x7E && char < 0x1000) {
                    // Extended Latin / common Unicode
                    currentSegment.append(char.toChar())
                } else {
                    if (currentSegment.length > 3) {
                        textSegments.add(currentSegment.toString())
                    }
                    currentSegment.clear()
                }
                i += 2
            }
        } else {
            // Read as ANSI/ASCII
            for (byte in data) {
                val unsigned = byte.toInt() and 0xFF
                if (unsigned in 0x20..0x7E || unsigned == 0x0A ||
                    unsigned == 0x0D || unsigned == 0x09
                ) {
                    currentSegment.append(unsigned.toChar())
                } else {
                    if (currentSegment.length > 3) {
                        textSegments.add(currentSegment.toString())
                    }
                    currentSegment.clear()
                }
            }
        }

        if (currentSegment.length > 3) {
            textSegments.add(currentSegment.toString())
        }

        // Filter out binary garbage and join
        return textSegments
            .filter { segment ->
                val printableRatio = segment.count {
                    it.isLetterOrDigit() || it.isWhitespace()
                }.toFloat() / segment.length
                printableRatio > 0.6f && segment.length > 3
            }
            .joinToString("\n")
    }

    private fun detectUnicodeDoc(data: ByteArray): Boolean {
        if (data.size < 40) return false
        // Check for UTF-16 BOM
        if (data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte()) return true
        // Check FIB (File Information Block) for Unicode flag
        // In DOC files, offset 0x0A in the FIB contains flags
        // Bit 0x04 of the flags indicates Unicode
        try {
            val fibOffset = 0
            // wIdent at offset 0x00 should be 0xA5EC for DOC
            val wIdent = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
            if (wIdent == 0xA5EC) {
                // This is a valid DOC file, check for Unicode text
                // The fComplex flag at offset 0x0A bit 0x04
                val flags = data[0x0A].toInt() and 0xFF
                if (flags and 0x04 != 0) return true
            }
        } catch (_: Exception) { }

        // Heuristic: count null bytes in first 1024 bytes
        val sampleSize = minOf(1024, data.size)
        val nullCount = (0 until sampleSize).count { data[it].toInt() and 0xFF == 0 }
        // If more than 30% null bytes, likely Unicode
        return nullCount > sampleSize * 0.3
    }

    // ==================== Private helpers ====================

    private fun closeInternal() {
        currentFile = null
        chapters = emptyList()
        images = emptyMap()
        bookTitle = ""
        bookAuthor = null
        isDocx = false
    }

    companion object {
        private val styleLastNumberRegex = Regex("(\\d+)\\D*$")
    }
}
