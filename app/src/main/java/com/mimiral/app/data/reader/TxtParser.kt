package com.mimiral.app.data.reader

import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class TxtParseResult {
    data class Success(
        val text: String,
        val encoding: String,
        val chapterBreaks: List<Int>,
        val title: String,
        val filePath: String,
        val characterCount: Int
    ) : TxtParseResult()

    data class Error(val message: String, val cause: Throwable? = null) : TxtParseResult()
}

class TxtParser {

    companion object {
        private const val DETECTION_BUFFER_SIZE = 8192
        private const val UTF8_CONFIDENCE_THRESHOLD = 0.8
    }

    suspend fun parse(file: File): TxtParseResult = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext TxtParseResult.Error("File not found: " + file.absolutePath)
            }
            if (!file.canRead()) {
                return@withContext TxtParseResult.Error("Cannot read file: " + file.absolutePath)
            }

            val bytes = file.readBytes()
            if (bytes.isEmpty()) {
                return@withContext TxtParseResult.Success(
                    "",
                    "UTF-8",
                    listOf(0),
                    file.nameWithoutExtension,
                    file.absolutePath,
                    0
                )
            }

            val encoding = detectEncoding(bytes)
            val text = decodeBytes(bytes, encoding)
            val chapterBreaks = detectChapterBreaks(text)
            val title = extractTitle(text, file.nameWithoutExtension)

            TxtParseResult.Success(
                text,
                encoding,
                chapterBreaks,
                title,
                file.absolutePath,
                text.length
            )
        } catch (e: OutOfMemoryError) {
            TxtParseResult.Error("File too large to load into memory", e)
        } catch (e: Exception) {
            TxtParseResult.Error("Failed to parse TXT file: " + e.message, e)
        }
    }

    suspend fun parse(
        inputStream: InputStream,
        fileName: String
    ): TxtParseResult = withContext(Dispatchers.IO) {
        try {
            val bytes = inputStream.readBytes()
            if (bytes.isEmpty()) {
                val baseName = if (fileName.contains(".")) {
                    fileName.substringBeforeLast(".")
                } else {
                    fileName
                }
                return@withContext TxtParseResult.Success("", "UTF-8", listOf(0), baseName, "", 0)
            }

            val encoding = detectEncoding(bytes)
            val text = decodeBytes(bytes, encoding)
            val chapterBreaks = detectChapterBreaks(text)
            val baseName = if (fileName.contains(".")) {
                fileName.substringBeforeLast(".")
            } else {
                fileName
            }
            val title = extractTitle(text, baseName)

            TxtParseResult.Success(text, encoding, chapterBreaks, title, "", text.length)
        } catch (e: Exception) {
            TxtParseResult.Error("Failed to parse TXT from stream: " + e.message, e)
        }
    }

    private fun detectEncoding(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "UTF-8"
        val bomEncoding = detectBom(bytes)
        if (bomEncoding != null) return bomEncoding
        if (isValidUtf8(bytes)) return "UTF-8"
        if (bytes.size >= 2) {
            val utf16 = detectUtf16(bytes)
            if (utf16 != null) return utf16
        }
        return "ISO-8859-1"
    }

    private fun detectBom(bytes: ByteArray): String? {
        if (bytes.size < 2) return null
        if (bytes.size >= 4) {
            if (bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte() &&
                bytes[2] == 0xFE.toByte() && bytes[3] == 0xFF.toByte()
            ) {
                return "UTF-32BE"
            }
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() &&
                bytes[2] == 0x00.toByte() && bytes[3] == 0x00.toByte()
            ) {
                return "UTF-32LE"
            }
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return "UTF-8-BOM"
        }
        if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return "UTF-16BE"
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return "UTF-16LE"
        return null
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        var validChars = 0
        var totalChars = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            totalChars++
            when {
                b < 0x80 -> { validChars++; i++ }
                b in 0xC2..0xDF -> {
                    if (i + 1 < bytes.size && isContinuationByte(bytes[i + 1])) {
                        validChars++; i += 2
                    } else {
                        i++
                    }
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 < bytes.size &&
                        isContinuationByte(bytes[i + 1]) && isContinuationByte(bytes[i + 2])
                    ) {
                        validChars++; i += 3
                    } else {
                        i++
                    }
                }
                b in 0xF0..0xF4 -> {
                    if (i + 3 < bytes.size &&
                        isContinuationByte(bytes[i + 1]) &&
                        isContinuationByte(bytes[i + 2]) &&
                        isContinuationByte(bytes[i + 3])
                    ) {
                        validChars++; i += 4
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
        return if (totalChars == 0) {
            true
        } else {
            validChars.toFloat() / totalChars.toFloat() >= UTF8_CONFIDENCE_THRESHOLD
        }
    }

    private fun isContinuationByte(b: Byte): Boolean = (b.toInt() and 0xC0) == 0x80

    private fun detectUtf16(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        var evenNulls = 0
        var oddNulls = 0
        val sampleSize = minOf(bytes.size, DETECTION_BUFFER_SIZE)
        for (i in 0 until sampleSize - 1 step 2) {
            if (bytes[i].toInt() and 0xFF == 0x00) evenNulls++
            if (bytes[i + 1].toInt() and 0xFF == 0x00) oddNulls++
        }
        val pairs = sampleSize / 2
        if (pairs == 0) return null
        val evenRatio = evenNulls.toFloat() / pairs
        val oddRatio = oddNulls.toFloat() / pairs
        return when {
            oddRatio > 0.3f && evenRatio < 0.1f -> "UTF-16BE"
            evenRatio > 0.3f && oddRatio < 0.1f -> "UTF-16LE"
            else -> null
        }
    }

    private fun decodeBytes(bytes: ByteArray, encoding: String): String {
        val offset: Int
        val charset: Charset
        when (encoding) {
            "UTF-8-BOM" -> { offset = 3; charset = StandardCharsets.UTF_8 }
            "UTF-16LE" -> {
                offset = if (bytes.size >= 2 &&
                    bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
                ) {
                    2
                } else {
                    0
                }
                charset = StandardCharsets.UTF_16LE
            }
            "UTF-16BE" -> {
                offset = if (bytes.size >= 2 &&
                    bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
                ) {
                    2
                } else {
                    0
                }
                charset = StandardCharsets.UTF_16BE
            }
            "UTF-32LE" -> { offset = 4; charset = Charset.forName("UTF-32LE") }
            "UTF-32BE" -> { offset = 4; charset = Charset.forName("UTF-32BE") }
            "ISO-8859-1" -> { offset = 0; charset = StandardCharsets.ISO_8859_1 }
            else -> { offset = 0; charset = StandardCharsets.UTF_8 }
        }
        val relevant = if (offset > 0 && offset < bytes.size) {
            bytes.copyOfRange(offset, bytes.size)
        } else {
            bytes
        }
        return String(relevant, charset)
    }

    private fun detectChapterBreaks(text: String): List<Int> {
        if (text.isBlank()) return emptyList()
        val breaks = mutableListOf<Int>()
        val lines = text.split("\n")
        var charOffset = 0
        var consecutiveBlankLines = 0
        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trimEnd('\r')
            val ffIdx = trimmed.indexOf('\u000C')
            if (ffIdx >= 0) {
                breaks.add(charOffset + ffIdx)
                consecutiveBlankLines = 0
                charOffset += line.length + 1
                continue
            }
            if (trimmed.startsWith("#")) {
                breaks.add(charOffset)
                consecutiveBlankLines = 0
                charOffset += line.length + 1
                continue
            }
            if (trimmed.isEmpty()) {
                consecutiveBlankLines++
                if (consecutiveBlankLines == 3) breaks.add(charOffset)
            } else {
                if (consecutiveBlankLines >= 2 && isChapterHeading(trimmed)) {
                    if (breaks.isNotEmpty() && breaks.last() == charOffset) {
                        breaks.removeAt(breaks.size - 1)
                    }
                    breaks.add(charOffset)
                }
                consecutiveBlankLines = 0
            }
            charOffset += line.length + 1
        }
        if (breaks.isEmpty() || breaks[0] != 0) breaks.add(0, 0)
        return breaks.distinct().sorted()
    }

    private fun isChapterHeading(line: String): Boolean {
        if (line.length > 60 || line.isBlank()) return false
        val letters = line.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        return letters.count { it.isUpperCase() }.toFloat() / letters.length >= 0.7f
    }

    private fun extractTitle(text: String, filenameTitle: String): String {
        if (text.isBlank()) return filenameTitle
        val firstLine = text.trimStart().substringBefore("\n").trim()
        return if (firstLine.isNotEmpty() && firstLine.length <= 100 &&
            !firstLine.endsWith(".")
        ) {
            firstLine
        } else {
            filenameTitle
        }
    }
}
