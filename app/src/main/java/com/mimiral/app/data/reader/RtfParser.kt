package com.mimiral.app.data.reader

import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of an RTF file parsing operation.
 */
sealed class RtfParseResult {
    data class Success(
        val text: String,
        val title: String,
        val filePath: String,
        val characterCount: Int,
        val chapterBreaks: List<Int>
    ) : RtfParseResult()

    data class Error(val message: String, val cause: Throwable? = null) : RtfParseResult()
}

/**
 * Lightweight RTF (Rich Text Format) parser that extracts plain text.
 *
 * Handles:
 * - RTF control words (\word)
 * - RTF groups ({ })
 * - Character encoding (ANSI, Unicode \ucN\uN)
 * - Special characters (\tab, \par, \line, \emdash, endash, etc.)
 * - Font tables, color tables (skipped)
 * - Pictures, objects (skipped)
 *
 * This is a pure Kotlin implementation that does not require external libraries.
 * It is designed for extracting readable text content from RTF documents for
 * rendering in the reader view.
 *
 * Usage:
 * ```
 * val parser = RtfParser()
 * val result = parser.parse(File("/path/to/document.rtf"))
 * when (result) {
 *     is RtfParseResult.Success -> { /* use result.text */ }
 *     is RtfParseResult.Error -> { /* handle error */ }
 * }
 * ```
 */
class RtfParser {

    companion object {
        /** Maximum nesting depth for RTF groups to prevent stack overflow. */
        const val MAX_GROUP_DEPTH = 64

        /** Unicode replacement character for unknown Unicode code points. */
        const val UNICODE_REPLACEMENT = 0xFFFD
    }

    /**
     * Parse an RTF file and extract plain text.
     *
     * @param file The RTF file to parse.
     * @return [RtfParseResult.Success] on success, [RtfParseResult.Error] on failure.
     */
    suspend fun parse(file: File): RtfParseResult = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext RtfParseResult.Error("File not found: " + file.absolutePath)
            }
            if (!file.canRead()) {
                return@withContext RtfParseResult.Error("Cannot read file: " + file.absolutePath)
            }

            val bytes = file.readBytes()
            if (bytes.isEmpty()) {
                return@withContext RtfParseResult.Success(
                    "",
                    file.nameWithoutExtension,
                    file.absolutePath,
                    0,
                    listOf(0)
                )
            }

            // RTF is typically ANSI-encoded; try to detect encoding from RTF header
            val encoding = detectRtfEncoding(bytes)
            val rawText = String(bytes, encoding)

            val parsed = extractText(rawText)
            val plainText = parsed.text
            val chapterBreaks = detectChapterBreaks(plainText)
            val title = extractTitle(plainText, file.nameWithoutExtension)

            RtfParseResult.Success(
                plainText,
                title,
                file.absolutePath,
                plainText.length,
                chapterBreaks
            )
        } catch (e: Exception) {
            RtfParseResult.Error("Failed to parse RTF file: " + e.message, e)
        }
    }

    /**
     * Parse RTF from an InputStream.
     */
    suspend fun parse(
        inputStream: InputStream,
        fileName: String
    ): RtfParseResult = withContext(Dispatchers.IO) {
        try {
            val bytes = inputStream.readBytes()
            if (bytes.isEmpty()) {
                val baseName = if (fileName.contains(".")) {
                    fileName.substringBeforeLast(".")
                } else {
                    fileName
                }
                return@withContext RtfParseResult.Success("", baseName, "", 0, listOf(0))
            }

            val encoding = detectRtfEncoding(bytes)
            val rawText = String(bytes, encoding)
            val parsed = extractText(rawText)
            val plainText = parsed.text
            val chapterBreaks = detectChapterBreaks(plainText)
            val title = extractTitle(plainText, baseName)

            RtfParseResult.Success(plainText, title, "", plainText.length, chapterBreaks)
        } catch (e: Exception) {
            RtfParseResult.Error("Failed to parse RTF from stream: " + e.message, e)
        }
    }

    // ---- RTF Encoding Detection ----

    /**
     * Detect the character encoding from the RTF header.
     *
     * RTF files can specify encoding via:
     * - \ansicpgN (ANSI code page, e.g., \ansicpg1252 for Windows-1252)
     * - \cpgN (code page)
     * - Default: Windows-1252 (ANSI)
     */
    private fun detectRtfEncoding(bytes: ByteArray): Charset {
        // Look for \ansicpgN in the first 256 bytes
        val headerStr = String(
            bytes.copyOfRange(0, minOf(bytes.size, 256)),
            StandardCharsets.US_ASCII
        )

        // Check for \ansicpg
        val ansiCpgMatch = Regex("""\ansicpg(\d+)""").find(headerStr)
        if (ansiCpgMatch != null) {
            val codePage = ansiCpgMatch.groupValues[1].toIntOrNull() ?: 1252
            return charsetForCodePage(codePage)
        }

        // Check for \cpg
        val cpgMatch = Regex("""\cpg(\d+)""").find(headerStr)
        if (cpgMatch != null) {
            val codePage = cpgMatch.groupValues[1].toIntOrNull() ?: 1252
            return charsetForCodePage(codePage)
        }

        // Default: Windows-1252
        return Charset.forName("windows-1252")
    }

    /**
     * Map RTF code page numbers to Java Charset objects.
     */
    private fun charsetForCodePage(codePage: Int): Charset {
        return when (codePage) {
            437 -> Charset.forName("IBM437")
            708 -> Charset.forName("ASMO-708")
            737 -> Charset.forName("x-IBM737")
            775 -> Charset.forName("x-IBM775")
            850 -> Charset.forName("IBM850")
            852 -> Charset.forName("IBM852")
            855 -> Charset.forName("IBM855")
            857 -> Charset.forName("IBM857")
            860 -> Charset.forName("IBM860")
            861 -> Charset.forName("x-IBM861")
            862 -> Charset.forName("IBM862")
            863 -> Charset.forName("IBM863")
            864 -> Charset.forName("x-IBM864")
            865 -> Charset.forName("IBM865")
            866 -> Charset.forName("IBM866")
            869 -> Charset.forName("x-IBM869")
            874 -> Charset.forName("x-IBM874")
            932 -> Charset.forName("Shift_JIS")
            936 -> Charset.forName("GBK")
            949 -> Charset.forName("EUC-KR")
            950 -> Charset.forName("Big5")
            1200 -> StandardCharsets.UTF_16LE // Unicode (little-endian)
            1250 -> Charset.forName("windows-1250")
            1251 -> Charset.forName("windows-1251")
            1252 -> Charset.forName("windows-1252")
            1253 -> Charset.forName("windows-1253")
            1254 -> Charset.forName("windows-1254")
            1255 -> Charset.forName("windows-1255")
            1256 -> Charset.forName("windows-1256")
            1257 -> Charset.forName("windows-1257")
            1258 -> Charset.forName("windows-1258")
            1361 -> Charset.forName("x-Johab")
            10000 -> Charset.forName("x-MacRoman")
            10001 -> Charset.forName("x-MacJapanese")
            10002 -> Charset.forName("x-MacChineseTrad")
            10003 -> Charset.forName("x-MacKorean")
            10004 -> Charset.forName("x-MacArabic")
            10005 -> Charset.forName("x-MacHebrew")
            10006 -> Charset.forName("x-MacGreek")
            10007 -> Charset.forName("x-MacCyrillic")
            10008 -> Charset.forName("x-MacChineseSimp")
            10010 -> Charset.forName("x-MacRomania")
            10017 -> Charset.forName("x-MacUkraine")
            10021 -> Charset.forName("x-MacThai")
            10029 -> Charset.forName("x-MacCentralEurope")
            10079 -> Charset.forName("x-MacIceland")
            10081 -> Charset.forName("x-MacTurkish")
            10082 -> Charset.forName("x-MacCroatian")
            65000 -> StandardCharsets.UTF_8 // \utf8
            65001 -> StandardCharsets.UTF_8
            else -> Charset.forName("windows-1252") // Default fallback
        }
    }

    // ---- RTF Text Extraction ----

    /**
     * Extract plain text from raw RTF source.
     */
    private fun extractText(rtfSource: String): RtfText {
        val tokens = tokenize(rtfSource)
        return parseTokens(tokens, rtfSource)
    }

    /**
     * Tokenize the RTF source into a list of tokens.
     */
    private fun tokenize(source: String): List<RtfToken> {
        val tokens = mutableListOf<RtfToken>()
        var i = 0

        while (i < source.length) {
            val c = source[i]
            when {
                c == '{' -> {
                    tokens.add(RtfToken.StartGroup)
                    i++
                }
                c == '}' -> {
                    tokens.add(RtfToken.EndGroup)
                    i++
                }
                c == '\\' -> {
                    // Control word or control symbol
                    i++
                    if (i >= source.length) break
                    val next = source[i]
                    if (next.isLetter()) {
                        // Control word: \word[optDigit]
                        val start = i
                        while (i < source.length && source[i].isLetter()) i++
                        val word = source.substring(start, i)
                        // Check for numeric parameter
                        val paramStart = i
                        if (i < source.length && (source[i] == '-' || source[i].isDigit())) {
                            i++ // consume minus or first digit
                            while (i < source.length && source[i].isDigit()) i++
                        }
                        val param = if (i > paramStart) {
                            source.substring(paramStart, i).toIntOrNull()
                        } else {
                            null
                        }
                        // Check for optional space delimiter
                        if (i < source.length && source[i] == ' ') i++
                        tokens.add(RtfToken.ControlWord(word, param))
                    } else {
                        // Control symbol: \c
                        tokens.add(RtfToken.ControlSymbol(next.toString()))
                        i++
                    }
                }
                c != '\r' && c != '\n' -> {
                    // Plain text
                    val start = i
                    while (i < source.length && source[i] != '{' && source[i] != '}' &&
                        source[i] != '\\' && source[i] != '\r' && source[i] != '\n'
                    ) {
                        i++
                    }
                    if (i > start) {
                        tokens.add(RtfToken.Text(source.substring(start, i)))
                    }
                }
                else -> i++ // Skip CR/LF
            }
        }
        return tokens
    }

    /**
     * Parse RTF tokens and extract text.
     */
    private fun parseTokens(tokens: List<RtfToken>, source: String): Text {
        val sb = StringBuilder()
        var i = 0
        var groupDepth = 0
        var skipDepth = -1
        var ucSkip = 1 // \ucN - bytes to skip after Unicode code point
        var inFontTable = false
        var inColorTable = false
        var inPict = false
        var inObject = false

        // Build font table for \u (Unicode) resolution
        // For simplicity, we just emit the character code
        val defaultFontCharset = StandardCharsets.UTF_8

        while (i < tokens.size) {
            val token = tokens[i]
            when (token) {
                is RtfToken.StartGroup -> {
                    groupDepth++
                    i++
                }
                is RtfToken.EndGroup -> {
                    groupDepth--
                    if (skipDepth >= 0 && groupDepth < skipDepth) {
                        skipDepth = -1
                    }
                    i++
                }
                is RtfToken.ControlWord -> {
                    if (skipDepth >= 0 && groupDepth >= skipDepth) {
                        i++
                        continue
                    }
                    when (token.word) {
                        // Special characters
                        "par" -> sb.append("\n")
                        "line" -> sb.append("\n")
                        "tab" -> sb.append("\t")
                        "emdash" -> sb.append("\u2014")
                        "endash" -> sb.append("\u2013")
                        "emspace" -> sb.append(" ")
                        "enspace" -> sb.append(" ")
                        "qmspace" -> sb.append(" ")
                        "bullet" -> sb.append("\u2022")
                        "lquote" -> sb.append("\u2018")
                        "rquote" -> sb.append("\u2019")
                        "ldblquote" -> sb.append("\u201C")
                        "rdblquote" -> sb.append("\u201D")

                        // Unicode character: \uN
                        "u" -> {
                            val code = token.param ?: 0
                            val charCode = if (code < 0) code + 65536 else code
                            try {
                                sb.append(charCode.toChar())
                            } catch (_: Exception) {
                                sb.append("?")
                            }
                            // Skip the replacement character(s) indicated by \ucN
                            ucSkip = 1 // Default: skip 1 ANSI char after Unicode
                        }

                        // Unicode skip count
                        "uc" -> {
                            ucSkip = token.param ?: 1
                        }

                        // Groups to skip
                        "fonttbl" -> { skipDepth = groupDepth; inFontTable = true }
                        "colortbl" -> { skipDepth = groupDepth; inColorTable = true }
                        "pict" -> { skipDepth = groupDepth; inPict = true }
                        "object" -> { skipDepth = groupDepth; inObject = true }
                        "stylesheet" -> skipDepth = groupDepth
                        "info" -> skipDepth = groupDepth
                        "*\跳过*" -> skipDepth = groupDepth // Unknown \* groups with skipping
                        "*" -> {
                            // Check if next token is a control word we should skip
                            if (i + 1 < tokens.size && tokens[i + 1] is RtfToken.ControlWord) {
                                skipDepth = groupDepth
                            }
                        }

                        // ANSI code page
                        "ansicpg" -> { /* already handled in encoding detection */ }

                        // Ignored formatting
                        "b", "i", "u", "ul", "ulnone", "ulw", "uldb", "fs", "f", "cf",
                        "cb", "highlight", "ulwave", "ulth", "strike", "deleted",
                        "nosupersub", "super", "sub", "caps", "outl", "shad", "scaps",
                        "anish", "mac", "pc", "pca", "deff", "lang", "nouicompat",
                        "translat" -> { /* formatting, ignore */ }

                        else -> {
                            // If next token is a text token starting with space,
                            // skip the space (it is a control word delimiter)
                        }
                    }
                    i++
                    // Skip ANSI replacement characters after Unicode code point
                    if (token.word == "u") {
                        var skipCount = ucSkip
                        while (skipCount > 0 && i < tokens.size) {
                            val next = tokens[i]
                            when (next) {
                                is RtfToken.Text -> i++
                                is RtfToken.ControlSymbol -> i++
                                else -> skipCount = 0
                            }
                            skipCount--
                        }
                    }
                }
                is RtfToken.ControlSymbol -> {
                    when (token.symbol) {
                        "~" -> sb.append("\u00A0") // Non-breaking space
                        "-" -> sb.append("\u00AD") // Optional hyphen
                        "_" -> sb.append("\u2013") // Non-breaking hyphen
                        "|" -> { /* soft line break, ignore */ }
                        "'" -> {
                            // Hex escape: 'XX
                            // Already decoded by charset
                        }
                        else -> { /* Other control symbols are formatting */ }
                    }
                    i++
                }
                is RtfToken.Text -> {
                    if (skipDepth < 0 || groupDepth < skipDepth) {
                        sb.append(token.text)
                    }
                    i++
                }
            }
            // Safety: prevent infinite loops
            if (groupDepth > MAX_GROUP_DEPTH) break
        }

        val result = normalizeWhitespace(sb.toString())
        return RtfText(result)
    }

    /**
     * Normalize whitespace in extracted text.
     */
    private fun normalizeWhitespace(text: String): String {
        var result = text
        // Collapse multiple blank lines to max 2
        result = result.replace(Regex("\n{3,}"), "\n\n")
        // Remove trailing spaces
        result = result.replace(Regex(" +\n"), "\n")
        return result.trim()
    }

    // ---- Chapter break detection (reuse same logic as TxtParser) ----

    private fun detectChapterBreaks(text: String): List<Int> {
        if (text.isBlank()) return emptyList()
        val breaks = mutableListOf<Int>()
        val lines = text.split("\n")
        var charOffset = 0
        var consecutiveBlankLines = 0

        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trimEnd('\r')

            // Handle \par embedded in RTF-extracted text
            if (trimmed.contains("\u000C")) {
                breaks.add(charOffset + trimmed.indexOf("\u000C"))
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

/**
 * Wrapper for parsed text content.
 */
data class RtfText(val text: String)

/**
 * RTF token types for the tokenizer.
 */
sealed class RtfToken {
    object StartGroup : RtfToken()
    object EndGroup : RtfToken()
    data class ControlWord(val word: String, val param: Int? = null) : RtfToken()
    data class ControlSymbol(val symbol: String) : RtfToken()
    data class RtfText(val text: String) : RtfToken()
}
