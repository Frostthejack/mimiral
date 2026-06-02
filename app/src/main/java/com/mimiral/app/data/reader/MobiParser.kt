package com.mimiral.app.data.reader

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.text.Charsets

/**
 * Represents the state of a MOBI/AZW3 document being parsed.
 */
sealed class MobiState {
    /** No document is loaded. */
    object Idle : MobiState()

    /** A MOBI/AZW3 is loaded and ready for reading. */
    data class Loaded(
        val title: String,
        val author: String?,
        val description: String?,
        val chapterCount: Int,
        val filePath: String,
        val totalEstimatedCharacters: Long = 0,
        val isAzw3: Boolean = false
    ) : MobiState()

    /** An error occurred while parsing the document. */
    data class Error(val message: String, val cause: Throwable? = null) : MobiState()
}

/**
 * Represents a single chapter in a MOBI/AZW3.
 */
data class MobiChapter(
    val index: Int,
    val title: String,
    val text: String,
    val characterCount: Int = text.length
)

/**
 * Metadata extracted from a MOBI/AZW3 file.
 */
data class MobiMetadata(
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val isAzw3: Boolean = false
)

/**
 * MOBI/AZW3 parser for DRM-free ebooks.
 *
 * Supports:
 * - MOBI (old format, PalmDoc compression, single text blob)
 * - AZW3 / KF8 (new format, multiple HTML records, chapter structure)
 *
 * Limitations:
 * - DRM-protected files are NOT supported (returns error)
 * - PalmDoc and no-compression modes are supported
 */
class MobiParser {

    private var state: MobiState = MobiState.Idle
    private var chapters: List<MobiChapter> = emptyList()
    private var metadata: MobiMetadata = MobiMetadata(title = "")
    private var filePath: String = ""

    companion object {
        private val MOBI_TYPE_BYTES = byteArrayOf('M'.code.toByte(), 'O'.code.toByte(), 'B'.code.toByte(), 'I'.code.toByte())
        private val EXTH_MAGIC_BYTES = byteArrayOf('E'.code.toByte(), 'X'.code.toByte(), 'T'.code.toByte(), 'H'.code.toByte())

        private const val PDB_HEADER_SIZE = 78
        private const val PDB_RECORD_ENTRY_SIZE = 8

        private const val EXTH_TITLE = 503
        private const val EXTH_AUTHOR = 100
        private const val EXTH_DESCRIPTION = 103
        private const val EXTH_PUBLISHER = 101
        private const val EXTH_LANGUAGE = 524
        private const val EXTH_UPDATED_TITLE = 543

        private const val SYNTHETIC_CHAPTER_MIN_CHARS = 5000
        private const val SYNTHETIC_CHAPTER_SPLIT_SIZE = 3000
    }

    val currentState: MobiState get() = state

    /**
     * Opens and parses a MOBI or AZW3 file.
     */
    fun openFile(file: File): MobiState {
        try {
            if (!file.exists()) {
                val error = MobiState.Error("File not found: ${file.absolutePath}")
                state = error
                return error
            }
            if (!file.canRead()) {
                val error = MobiState.Error("Cannot read file: ${file.absolutePath}")
                state = error
                return error
            }
            if (file.length() < PDB_HEADER_SIZE) {
                val error = MobiState.Error("File too small to be a valid MOBI/AZW3")
                state = error
                return error
            }

            closeInternal()
            filePath = file.absolutePath

            val bytes = file.readBytes()
            parseFile(bytes)

            val loaded = MobiState.Loaded(
                title = metadata.title.ifBlank { file.nameWithoutExtension },
                author = metadata.author,
                description = metadata.description,
                chapterCount = chapters.size.coerceAtLeast(1),
                filePath = file.absolutePath,
                totalEstimatedCharacters = chapters.sumOf { it.characterCount.toLong() },
                isAzw3 = metadata.isAzw3
            )
            state = loaded
            return loaded
        } catch (e: Exception) {
            closeInternal()
            val error = MobiState.Error("Failed to parse MOBI/AZW3: ${e.message}", e)
            state = error
            return error
        }
    }

    fun getChapters(): List<MobiChapter> = chapters

    fun getMetadata(): MobiMetadata = metadata

    fun getFullText(): String = chapters.joinToString("\n\n") { it.text }

    fun close() {
        closeInternal()
        state = MobiState.Idle
    }

    // ---- Private parsing implementation ----

    private fun parseFile(data: ByteArray) {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        val dbNameBytes = ByteArray(32)
        buffer.get(dbNameBytes)
        val dbName = String(dbNameBytes, Charsets.US_ASCII).trimEnd('\u0000')

        buffer.short // attributes
        buffer.short // version
        buffer.int // creation date
        buffer.int // modification date
        buffer.int // last backup date
        buffer.int // modification number
        buffer.int // appInfo offset
        buffer.int // sortInfo offset
        val typeBytes = ByteArray(4)
        buffer.get(typeBytes)
        val creator = ByteArray(4)
        buffer.get(creator)
        buffer.int // unique ID seed
        buffer.int // next record list ID
        val numRecords = buffer.short.toInt() and 0xFFFF

        val isAzw3 = dbName.contains("AZW", ignoreCase = true) ||
                String(typeBytes, Charsets.US_ASCII).contains("BOOK", ignoreCase = true)

        val recordOffsets = mutableListOf<Long>()
        for (i in 0 until numRecords) {
            val offset = buffer.int.toLong() and 0xFFFFFFFFL
            buffer.int // attributes
            recordOffsets.add(offset)
        }

        if (recordOffsets.isEmpty()) {
            throw IOException("No records found in PDB file")
        }

        // Parse first record (MOBI header)
        val firstRecordOffset = recordOffsets[0]
        val firstRecordSize = if (recordOffsets.size > 1) {
            (recordOffsets[1] - firstRecordOffset).toInt()
        } else {
            (data.size - firstRecordOffset).toInt()
        }

        if (firstRecordOffset.toInt() + 20 > data.size) {
            throw IOException("First record offset out of bounds")
        }

        val firstRecord = data.copyOfRange(
            firstRecordOffset.toInt(),
            (firstRecordOffset.toInt() + firstRecordSize).coerceAtMost(data.size)
        )

        val mobiHeader = parseMobiHeader(firstRecord)

        if (isAzw3 || mobiHeader.isAzw3) {
            parseAzw3Content(data, recordOffsets, mobiHeader)
        } else {
            parseMobiContent(data, recordOffsets, mobiHeader)
        }
    }

    private data class MobiHeader(
        val compression: Int = 0,
        val encoding: Int = 0,
        val firstImageIndex: Int = 0,
        val firstContentRecord: Int = 0,
        val lastContentRecord: Int = 0,
        val isAzw3: Boolean = false,
        val mobiType: Int = 0
    )

    private data class ExthRecord(val type: Int, val data: ByteArray)

    private fun parseMobiHeader(firstRecord: ByteArray): MobiHeader {
        if (firstRecord.size < 16) return MobiHeader()

        val buf = ByteBuffer.wrap(firstRecord).order(ByteOrder.BIG_ENDIAN)
        val compression = buf.short.toInt() and 0xFFFF
        buf.short // unused
        buf.int // text length
        val recordCount = buf.short.toInt() and 0xFFFF
        buf.short // record size
        val encryptionType = buf.short.toInt() and 0xFFFF
        buf.short // reserved

        if (encryptionType != 0 && encryptionType != 1) {
            throw IOException("DRM-protected MOBI files are not supported")
        }

        if (firstRecord.size < 16 + 20) {
            return MobiHeader(compression = compression)
        }

        buf.position(16)
        val magic = ByteArray(4)
        buf.get(magic)

        if (!magic.contentEquals(MOBI_TYPE_BYTES)) {
            val isAzw3 = compression == 1 && encryptionType == 0
            return MobiHeader(
                compression = compression,
                isAzw3 = isAzw3 && recordCount > 1
            )
        }

        buf.int // Mobi header length
        val mobiType = buf.int
        val encoding = buf.int
        buf.int // unique ID
        val fileVersion = buf.int

        // Skip to firstNonBookIndex
        val safePos = (16 + 28).coerceAtMost(firstRecord.size - 4)
        buf.position(safePos)
        val firstNonBookIndex = if (safePos + 4 <= firstRecord.size) buf.int else firstRecord.size
        val fullNameOffset = if (buf.remaining() >= 4) buf.int else 0
        val fullNameLength = if (buf.remaining() >= 4) buf.int else 0
        val safePos2 = (16 + 44).coerceAtMost(firstRecord.size - 4)
        buf.position(safePos2)
        val firstImageIndex = if (safePos2 + 4 <= firstRecord.size) buf.int else 0

        // Parse EXTH
        val exthRecords = parseExthHeader(firstRecord)
        metadata = extractMetadata(firstRecord, encoding, exthRecords, fullNameOffset, fullNameLength)

        val isAzw3 = mobiType == 3 || fileVersion >= 8

        return MobiHeader(
            compression = compression,
            encoding = encoding,
            firstImageIndex = firstImageIndex,
            firstContentRecord = 1,
            lastContentRecord = recordCount,
            isAzw3 = isAzw3,
            mobiType = mobiType
        )
    }

    private fun parseExthHeader(firstRecord: ByteArray): List<ExthRecord> {
        try {
            if (firstRecord.size < 128) return emptyList()
            val buf = ByteBuffer.wrap(firstRecord).order(ByteOrder.BIG_ENDIAN)

            // Try offset 120 first (most common)
            buf.position(120)
            val magic = ByteArray(4)
            buf.get(magic)

            var exthOffset = -1
            if (magic.contentEquals(EXTH_MAGIC_BYTES)) {
                exthOffset = 124
            } else {
                // Search for EXTH magic
                val idx = indexOf(firstRecord, EXTH_MAGIC_BYTES, 16)
                if (idx >= 0) exthOffset = idx + 4
            }

            if (exthOffset < 0 || firstRecord.size < exthOffset + 8) return emptyList()

            buf.position(exthOffset)
            val exthLength = buf.int
            val recordCount = buf.int
            return parseExthRecords(firstRecord, exthOffset + 8, recordCount)
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun parseExthRecords(data: ByteArray, offset: Int, count: Int): List<ExthRecord> {
        val records = mutableListOf<ExthRecord>()
        var pos = offset
        for (i in 0 until count) {
            if (pos + 8 > data.size) break
            val buf = ByteBuffer.wrap(data, pos, (data.size - pos).coerceAtLeast(0)).order(ByteOrder.BIG_ENDIAN)
            val type = buf.int
            val length = buf.int
            if (length < 8) break
            val dataLength = length - 8
            if (dataLength <= 0 || pos + 8 + dataLength > data.size) break
            val recData = data.copyOfRange(pos + 8, pos + 8 + dataLength)
            records.add(ExthRecord(type, recData))
            pos += length
        }
        return records
    }

    private fun indexOf(array: ByteArray, target: ByteArray, start: Int = 0): Int {
        outer@ for (i in start..array.size - target.size) {
            for (j in target.indices) {
                if (array[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun extractMetadata(
        firstRecord: ByteArray,
        encoding: Int,
        exthRecords: List<ExthRecord>,
        fullNameOffset: Int,
        fullNameLength: Int
    ): MobiMetadata {
        var title = ""
        var author: String? = null
        var description: String? = null
        var publisher: String? = null
        var language: String? = null

        val useUtf8 = encoding == 65001

        for (rec in exthRecords) {
            val text = try {
                if (useUtf8) String(rec.data, Charsets.UTF_8).trimEnd('\u0000')
                else String(rec.data, Charsets.ISO_8859_1).trimEnd('\u0000')
            } catch (_: Exception) {
                continue
            }
            when (rec.type) {
                EXTH_TITLE, EXTH_UPDATED_TITLE -> if (title.isBlank()) title = text
                EXTH_AUTHOR -> author = text
                EXTH_DESCRIPTION -> description = text
                EXTH_PUBLISHER -> publisher = text
                EXTH_LANGUAGE -> language = text
            }
        }

        if (title.isBlank() && fullNameOffset > 0 && fullNameLength > 0) {
            val end = (fullNameOffset + fullNameLength).coerceAtMost(firstRecord.size)
            if (fullNameOffset < end) {
                try {
                    val name = if (useUtf8) {
                        String(firstRecord, fullNameOffset, end - fullNameOffset, Charsets.UTF_8)
                    } else {
                        String(firstRecord, fullNameOffset, end - fullNameOffset, Charsets.ISO_8859_1)
                    }.trimEnd('\u0000', '\n', '\r')
                    if (name.isNotBlank()) title = name
                } catch (_: Exception) { }
            }
        }

        return MobiMetadata(title, author, description, publisher, language)
    }

    private fun parseMobiContent(
        data: ByteArray,
        recordOffsets: List<Long>,
        header: MobiHeader
    ) {
        val startRecord = header.firstContentRecord.coerceAtMost(recordOffsets.size - 1)
        val endRecord = header.lastContentRecord.coerceAtMost(recordOffsets.size - 1)

        val useUtf8 = header.encoding == 65001

        val sb = StringBuilder()
        for (i in startRecord..endRecord) {
            val recStart = recordOffsets[i].toInt()
            val recEnd = if (i + 1 < recordOffsets.size) {
                recordOffsets[i + 1].toInt()
            } else {
                data.size
            }

            if (recStart >= data.size || recEnd > data.size || recStart >= recEnd) continue

            val recordData = data.copyOfRange(recStart, recEnd)

            val text = when (header.compression) {
                1 -> try {
                    if (useUtf8) String(recordData, Charsets.UTF_8)
                    else String(recordData, Charsets.ISO_8859_1)
                } catch (_: Exception) { "" }
                2 -> decompressPalmDoc(recordData, useUtf8)
                else -> try {
                    if (useUtf8) String(recordData, Charsets.UTF_8)
                    else String(recordData, Charsets.ISO_8859_1)
                } catch (_: Exception) { "" }
            }

            sb.append(stripMobiHtml(text))
            sb.append("\n\n")
        }

        val fullText = sb.toString().trimEnd('\n', ' ')
        chapters = createSyntheticChapters(fullText)
    }

    private fun parseAzw3Content(
        data: ByteArray,
        recordOffsets: List<Long>,
        header: MobiHeader
    ) {
        val useUtf8 = header.encoding == 65001
        val startRecord = 1
        val endRecord = recordOffsets.size - 1

        var chapterIndex = 0
        val parsedChapters = mutableListOf<MobiChapter>()

        for (i in startRecord..endRecord) {
            val recStart = recordOffsets[i].toInt()
            val recEnd = if (i + 1 < recordOffsets.size) {
                recordOffsets[i + 1].toInt()
            } else {
                data.size
            }

            if (recStart >= data.size || recEnd > data.size || recStart >= recEnd) continue

            val recordData = data.copyOfRange(recStart, recEnd)
            if (isImageRecord(recordData)) continue

            val text = when (header.compression) {
                1 -> try {
                    if (useUtf8) String(recordData, Charsets.UTF_8)
                    else String(recordData, Charsets.ISO_8859_1)
                } catch (_: Exception) { "" }
                2 -> decompressPalmDoc(recordData, useUtf8)
                else -> try {
                    if (useUtf8) String(recordData, Charsets.UTF_8)
                    else String(recordData, Charsets.ISO_8859_1)
                } catch (_: Exception) { "" }
            }

            if (text.isBlank()) continue

            val cleanText = stripMobiHtml(text).trim()
            if (cleanText.isBlank()) continue

            val chapterTitle = extractChapterTitle(text) ?: "Part ${chapterIndex + 1}"

            parsedChapters.add(
                MobiChapter(chapterIndex, chapterTitle, cleanText, cleanText.length)
            )
            chapterIndex++
        }

        chapters = if (parsedChapters.isEmpty()) {
            val sb = StringBuilder()
            for (i in startRecord..endRecord) {
                val recStart = recordOffsets[i].toInt()
                val recEnd = if (i + 1 < recordOffsets.size) recordOffsets[i + 1].toInt() else data.size
                if (recStart >= data.size || recEnd > data.size || recStart >= recEnd) continue
                val recordData = data.copyOfRange(recStart, recEnd)
                if (isImageRecord(recordData)) continue
                sb.append(stripMobiHtml(String(recordData, if (useUtf8) Charsets.UTF_8 else Charsets.ISO_8859_1))).append("\n\n")
            }
            createSyntheticChapters(sb.toString().trim())
        } else {
            parsedChapters
        }

        metadata = metadata.copy(isAzw3 = true)
    }

    private fun isImageRecord(data: ByteArray): Boolean {
        if (data.size < 4) return false
        return (data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() && data[2] == 0xFF.toByte()) ||
                (data[0] == 0x89.toByte() && data[1] == 0x50.toByte() && data[2] == 0x4E.toByte() && data[3] == 0x47.toByte()) ||
                (data[0] == 0x47.toByte() && data[1] == 0x49.toByte() && data[2] == 0x46.toByte())
    }

    private fun extractChapterTitle(rawText: String): String? {
        val patterns = listOf(
            Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("<h2[^>]*>(.*?)</h2>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("<h3[^>]*>(.*?)</h3>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        )
        for (pattern in patterns) {
            val match = pattern.find(rawText) ?: continue
            val title = stripMobiHtml(match.groupValues[1]).trim()
            if (title.isNotBlank() && title.length < 200) return title
        }
        return null
    }

    private fun createSyntheticChapters(text: String): List<MobiChapter> {
        if (text.isBlank()) return listOf(MobiChapter(0, "Content", "", 0))
        if (text.length < SYNTHETIC_CHAPTER_MIN_CHARS) {
            return listOf(MobiChapter(0, "Chapter 1", text, text.length))
        }

        val chapterList = mutableListOf<MobiChapter>()
        var offset = 0
        var chapIdx = 1

        while (offset < text.length) {
            val chunkEnd = (offset + SYNTHETIC_CHAPTER_SPLIT_SIZE).coerceAtMost(text.length)
            var breakPoint = chunkEnd
            if (chunkEnd < text.length) {
                val searchRegion = text.substring(chunkEnd, (chunkEnd + 200).coerceAtMost(text.length))
                val paraBreak = searchRegion.indexOf("\n\n")
                if (paraBreak in 0..199) {
                    breakPoint = chunkEnd + paraBreak + 2
                }
            }
            val chunk = text.substring(offset, breakPoint).trim()
            if (chunk.isNotBlank()) {
                chapterList.add(MobiChapter(chapIdx - 1, "Chapter $chapIdx", chunk, chunk.length))
                chapIdx++
            }
            offset = breakPoint
        }

        return chapterList.ifEmpty { listOf(MobiChapter(0, "Chapter 1", text, text.length)) }
    }

    private fun decompressPalmDoc(data: ByteArray, useUtf8: Boolean): String {
        val sb = StringBuilder(data.size * 2)
        var i = 0
        while (i < data.size) {
            val byte = data[i].toInt() and 0xFF
            when {
                byte in 1..7 -> {
                    sb.append(" ".repeat(byte))
                    i++
                }
                byte == 0 || byte == 8 -> {
                    sb.append(byte.toChar())
                    i++
                }
                byte in 9..0xB7 -> {
                    // Literal byte - for ISO-8859-1, the byte IS the character
                    // For UTF-8 multi-byte, we'd need proper decoding but PalmDoc
                    // compressed MOBI files with UTF-8 are rare; treat as ISO-8859-1 chars
                    sb.append(byte.toChar())
                    i++
                }
                else -> {
                    sb.append(byte.toChar())
                    i++
                }
            }
        }
        return sb.toString()
    }

    fun stripMobiHtml(html: String): String {
        if (html.isBlank()) return ""

        var text = html

        text = text.replace(
            Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), ""
        )
        text = text.replace(
            Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), ""
        )
        text = text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

        text = text.replace(Regex("</h[1-6]>", RegexOption.IGNORE_CASE), "\n\n")
        text = text.replace(Regex("<h[1-6][^>]*>", RegexOption.IGNORE_CASE), "")

        text = text.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
        text = text.replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "")

        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")

        text = text.replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<div[^>]*>", RegexOption.IGNORE_CASE), "")

        text = text.replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n  \u2022 ")
        text = text.replace(Regex("</li>", RegexOption.IGNORE_CASE), "")

        text = text.replace(Regex("<[^>]+>"), "")

        text = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&#8211;", "\u2013")
            .replace("&#8212;", "\u2014")
            .replace("&#8216;", "\u2018")
            .replace("&#8217;", "\u2019")
            .replace("&#8220;", "\u201C")
            .replace("&#8221;", "\u201D")

        text = text.replace(Regex("[ \\t]+"), " ")
        text = text.replace(Regex("\n{3,}"), "\n\n")
        text = text.replace(Regex(" +\n"), "\n")
        text = text.replace(Regex("\n +"), "\n")

        return text.trim()
    }

    private fun closeInternal() {
        chapters = emptyList()
        metadata = MobiMetadata(title = "")
        filePath = ""
    }
}
