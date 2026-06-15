package com.mimiral.app.data.reader

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.abs

/**
 * Extracts structured content blocks from a PDF using font metadata.
 *
 * PDFTextStripper can report font size and font name per text chunk via
 * [TextPosition]. This extractor subclasses it to capture per-character
 * font information, then applies heuristics to classify content:
 *
 * - **Heading**: font size significantly larger than body font &rarr; heading level
 *   proportional to size difference; or ALL CAPS short line (&lt;80 chars).
 * - **Bold**: font name contains Bold/Black/Heavy/SemiBold.
 * - **Quote**: indented text in italic font.
 * - **Paragraph**: default body text.
 *
 * Uses pdfbox-android (com.tom_roush), NOT desktop PDFBox (which requires java.awt).
 *
 * Usage:
 * ```
 * val extractor = PdfStructuredExtractor()
 * val blocks = extractor.extractPage(file, pageIndex)
 * // blocks: List<ContentBlock> — Heading, Paragraph, Quote, etc.
 * ```
 */
class PdfStructuredExtractor {

    companion object {
        private const val TAG = "PdfStructuredExtrator"

        /** Minimum ratio of chunk font size to body font size to qualify as a heading. */
        private const val HEADING_SIZE_RATIO_THRESHOLD = 1.2f

        /** Font name substrings that indicate bold weight. */
        private val BOLD_INDICATORS = listOf(
            "Bold",
            "Black",
            "Heavy",
            "SemiBold",
            "Demibold",
            "ExtraBold",
            "UltraBold"
        )

        /** Font name substrings that indicate italic style. */
        private val ITALIC_INDICATORS = listOf("Italic", "Oblique", "Slanted")

        /** Maximum line length (chars) for an ALL-CAPS line to be treated as a heading. */
        private const val ALL_CAPS_HEADING_MAX_LENGTH = 80

        /**
         * Mapping from font-size ratio (chunk / body) to heading level.
         * Ratios are descending; first match wins.
         */
        private val HEADING_LEVEL_RATIOS = listOf(
            2.0f to 1, // 2x+ body size → h1
            1.7f to 2, // 1.7x → h2
            1.5f to 3, // 1.5x → h3
            1.35f to 4, // 1.35x → h4
            1.25f to 5 // 1.25x → h5
        )
        // Anything between HEADING_SIZE_RATIO_THRESHOLD and 1.25x → h6

        /** Minimum number of text chunks needed to establish a reliable body font size. */
        private const val MIN_CHUNKS_FOR_BODY_FONT = 10

        /** Y-distance tolerance (in PDF points) to consider chunks on the same line. */
        private const val SAME_LINE_Y_TOLERANCE = 2.0f
    }

    /**
     * Represents a single text chunk with its font metadata.
     *
     * @param text       The text string for this chunk.
     * @param fontSize   Font size in PDF points.
     * @param fontName   Full font name from the PDF (e.g. "TimesNewRomanPS-BoldMT").
     * @param x          X position on the page (left edge).
     * @param y          Y position on the page (baseline).
     * @param isBold     Whether the font name indicates bold weight.
     * @param isItalic   Whether the font name indicates italic style.
     * @param width      Width of the text chunk in PDF points.
     */
    private data class FontChunk(
        val text: String,
        val fontSize: Float,
        val fontName: String,
        val x: Float,
        val y: Float,
        val isBold: Boolean,
        val isItalic: Boolean,
        val width: Float
    )

    /**
     * A line of text assembled from one or more [FontChunk]s at the same Y position.
     *
     * @param chunks   The chunks on this line, sorted by X position.
     * @param text     The concatenated text of all chunks.
     * @param avgFontSize  Average font size across chunks (weighted by text length).
     * @param isBold   Whether ALL chunks are bold.
     * @param isItalic Whether ANY chunk is italic.
     * @param minX     Leftmost X position (indented if significantly > 0).
     * @param isAllCaps Whether the trimmed text is all uppercase (letters only).
     */
    private data class TextLine(
        val chunks: List<FontChunk>,
        val text: String,
        val avgFontSize: Float,
        val isBold: Boolean,
        val isItalic: Boolean,
        val minX: Float,
        val isAllCaps: Boolean
    )

    /**
     * Extracts structured content blocks from a single page of a PDF.
     *
     * @param file      The PDF file.
     * @param pageIndex Zero-based page index.
     * @return List of [ContentBlock] representing the page's structured content.
     *         Returns an empty list on error or if the page has no text.
     */
    fun extractPage(file: File, pageIndex: Int): List<ContentBlock> {
        try {
            PDDocument.load(file).use { document ->
                if (pageIndex < 0 || pageIndex >= document.numberOfPages) return emptyList()
                val (chunks, stdText) = collectChunks(document, pageIndex)
                if (chunks.isEmpty()) return emptyList()
                val bodyFontSize = estimateBodyFontSize(chunks)
                val lines = assembleLines(chunks)
                val reconciled = reconcileWithStdText(lines, stdText)
                return classifyLines(reconciled, bodyFontSize)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract structured content from page $pageIndex", e)
            return emptyList()
        }
    }

    /**
     * Extracts structured content blocks from a range of pages.
     *
     * @param file      The PDF file.
     * @param startPage Zero-based start page index (inclusive).
     * @param endPage   Zero-based end page index (inclusive).
     * @return List of [ContentBlock] for all pages in the range.
     */
    fun extractPages(file: File, startPage: Int, endPage: Int): List<ContentBlock> {
        try {
            PDDocument.load(file).use { document ->
                return extractPagesFromDocument(document, startPage, endPage)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract structured content from pages $startPage-$endPage", e)
            return emptyList()
        }
    }

    /**
     * Extracts structured content blocks from a range of pages in an already-open document.
     * Prefer this over [extractPages] when processing multiple sections of the same PDF,
     * to avoid reopening the file on each call.
     *
     * Processes each page individually so that Y-coordinate-based line assembly stays
     * correct — Y coordinates in PDFBox are page-relative, so processing multiple pages
     * in one pass causes chunks from different pages to interleave incorrectly.
     *
     * @param document  An already-open [PDDocument].
     * @param startPage Zero-based start page index (inclusive).
     * @param endPage   Zero-based end page index (inclusive).
     * @return List of [ContentBlock] for all pages in the range.
     */
    fun extractPagesFromDocument(
        document: PDDocument,
        startPage: Int,
        endPage: Int
    ): List<ContentBlock> {
        val pageCount = document.numberOfPages
        val actualEnd = endPage.coerceAtMost(pageCount - 1)
        if (startPage < 0 || startPage > actualEnd) return emptyList()
        val allBlocks = mutableListOf<ContentBlock>()
        for (pageIndex in startPage..actualEnd) {
            val (chunks, stdText) = collectChunks(document, pageIndex)
            if (chunks.isEmpty()) continue
            val bodyFontSize = estimateBodyFontSize(chunks)
            val lines = assembleLines(chunks)
            val reconciled = reconcileWithStdText(lines, stdText)
            allBlocks.addAll(classifyLines(reconciled, bodyFontSize))
        }
        return allBlocks
    }

    // ---- Chunk collection via PDFTextStripper ----

    /**
     * Collects font-metadata-rich text chunks from a page using a custom PDFTextStripper.
     *
     * This overrides [PDFTextStripper.processTextPosition] to capture each
     * [TextPosition] object (which carries font size, font name, and position)
     * instead of just the concatenated text string.
     */
    private fun collectChunks(document: PDDocument, pageIndex: Int): Pair<List<FontChunk>, String> {
        val chunks = mutableListOf<FontChunk>()

        val stripper = object : PDFTextStripper() {
            init {
                startPage = pageIndex + 1 // 1-indexed
                endPage = pageIndex + 1
                sortByPosition = true
            }

            override fun processTextPosition(text: TextPosition) {
                super.processTextPosition(text)
                val character = text.unicode ?: return
                if (character.isEmpty()) return
                val codePoint = character[0].code
                if (codePoint < 0x20 && character != "\n" && character != "\r") return
                if (codePoint == 0x200B) return

                val fontSize = text.fontSize
                val font = text.font
                val fontName = font?.name ?: ""
                val isBold = BOLD_INDICATORS.any { fontName.contains(it, ignoreCase = true) }
                val isItalic = ITALIC_INDICATORS.any { fontName.contains(it, ignoreCase = true) }

                chunks.add(
                    FontChunk(
                        text = character,
                        fontSize = fontSize,
                        fontName = fontName,
                        x = text.xDirAdj,
                        y = text.yDirAdj,
                        isBold = isBold,
                        isItalic = isItalic,
                        width = text.widthDirAdj
                    )
                )
            }
        }

        var stdText = ""
        try {
            stdText = stripper.getText(document)
        } catch (e: Exception) {
            Log.w(TAG, "PDFTextStripper failed on page $pageIndex", e)
        }

        return Pair(chunks, stdText)
    }

    // ---- Standard-text reconciliation ----

    /**
     * Replaces each TextLine's text with the corresponding line from PDFBox's
     * standard getText() output when line counts match. This corrects character
     * encoding errors that arise from fonts with missing or non-standard ToUnicode
     * CMaps, because the standard PDFBox pipeline applies additional Unicode
     * resolution steps (ActualText marked content, normalization) that our
     * per-glyph processTextPosition override would miss without calling super.
     *
     * Falls back to chunk-assembled text if line counts differ (e.g. PDFBox
     * splits or joins lines differently than our Y-position grouping).
     */
    private fun reconcileWithStdText(
        chunkLines: List<TextLine>,
        stdText: String
    ): List<TextLine> {
        if (stdText.isBlank()) return chunkLines
        val stdLines = stdText.lines().filter { it.isNotBlank() }
        if (stdLines.size != chunkLines.size) return chunkLines
        return chunkLines.mapIndexed { i, line ->
            val std = stdLines[i].trim()
            if (std.isBlank()) return@mapIndexed line
            val newIsAllCaps = std.length <= ALL_CAPS_HEADING_MAX_LENGTH &&
                std.filter { c -> c.isLetter() }.all { c -> c.isUpperCase() } &&
                std.any { c -> c.isLetter() }
            line.copy(text = std, isAllCaps = newIsAllCaps)
        }
    }

    // ---- Body font estimation ----

    /**
     * Estimates the body (default) font size from the collected chunks.
     *
     * Strategy: count the total text length for each font size; the size with
     * the most characters is the body font. This is robust against headings
     * (fewer characters) and footers/headers (also fewer characters).
     *
     * Falls back to the median font size if there are too few chunks.
     */
    private fun estimateBodyFontSize(chunks: List<FontChunk>): Float {
        if (chunks.size < MIN_CHUNKS_FOR_BODY_FONT) {
            // Not enough data — use median
            val sortedSizes = chunks.map { it.fontSize }.sorted()
            return sortedSizes[sortedSizes.size / 2]
        }

        // Bucket by rounded font size, weighted by character count
        val sizeBuckets = mutableMapOf<Int, Int>() // rounded size -> total chars
        for (chunk in chunks) {
            val rounded = chunk.fontSize.toInt()
            if (rounded > 0) {
                sizeBuckets[rounded] = (sizeBuckets[rounded] ?: 0) + chunk.text.length
            }
        }

        if (sizeBuckets.isEmpty()) {
            return chunks.first().fontSize
        }

        // The bucket with the most characters is the body font
        val bodySizeRounded = sizeBuckets.maxByOrNull { it.value }?.key
            ?: return chunks.first().fontSize

        return bodySizeRounded.toFloat()
    }

    // ---- Line assembly ----

    /**
     * Groups chunks into lines based on Y position, then concatenates text.
     *
     * Chunks on the same Y baseline (within [SAME_LINE_Y_TOLERANCE]) are
     * merged into a single [TextLine]. Lines are sorted top-to-bottom
     * (descending Y in PDF coordinates where Y=0 is the bottom).
     */
    private fun assembleLines(chunks: List<FontChunk>): List<TextLine> {
        if (chunks.isEmpty()) return emptyList()

        // Sort chunks by Y first (lines), then X (left to right within line)
        val sorted = chunks.sortedWith(compareBy({ -it.y }, { it.x }))

        val lines = mutableListOf<MutableList<FontChunk>>()
        var currentLine = mutableListOf(sorted.first())

        for (i in 1 until sorted.size) {
            val chunk = sorted[i]
            val prevY = currentLine.first().y

            if (abs(chunk.y - prevY) <= SAME_LINE_Y_TOLERANCE) {
                currentLine.add(chunk)
            } else {
                lines.add(currentLine)
                currentLine = mutableListOf(chunk)
            }
        }
        lines.add(currentLine)

        return lines.map { lineChunks ->
            // Sort within line by X position (left to right)
            val sortedChunks = lineChunks.sortedBy { it.x }
            // Many PDFs represent spaces as X-position gaps rather than explicit space chars.
            // Insert a space wherever the gap between adjacent chunks exceeds 25% of font size.
            val text = buildString {
                var prevEndX = Float.NEGATIVE_INFINITY
                var prevFontSize = 0f
                for (chunk in sortedChunks) {
                    if (prevEndX > Float.NEGATIVE_INFINITY) {
                        val gap = chunk.x - prevEndX
                        if (gap > prevFontSize * 0.25f && !endsWith(' ')) {
                            append(' ')
                        }
                    }
                    append(chunk.text)
                    prevEndX = chunk.x + chunk.width
                    prevFontSize = chunk.fontSize.coerceAtLeast(1f)
                }
            }.trim()

            // Weighted average font size by text length
            val totalLen = sortedChunks.sumOf { it.text.length }
            val avgFontSize = if (totalLen > 0) {
                sortedChunks.sumOf { (it.fontSize * it.text.length).toDouble() }
                    .toFloat() / totalLen
            } else {
                sortedChunks.first().fontSize
            }

            val isBold = sortedChunks.all { it.isBold } && sortedChunks.isNotEmpty()
            val isItalic = sortedChunks.any { it.isItalic }
            val minX = sortedChunks.minOf { it.x }
            val isAllCaps = text.isNotBlank() &&
                text.length <= ALL_CAPS_HEADING_MAX_LENGTH &&
                text.filter { it.isLetter() }.all { it.isUpperCase() } &&
                text.any { it.isLetter() } // Must have at least one letter

            TextLine(
                chunks = sortedChunks,
                text = text,
                avgFontSize = avgFontSize,
                isBold = isBold,
                isItalic = isItalic,
                minX = minX,
                isAllCaps = isAllCaps
            )
        }.filter { it.text.isNotBlank() } // Drop blank lines
    }

    // ---- Classification ----

    /**
     * Classifies each [TextLine] into a [ContentBlock] based on font heuristics.
     *
     * Heuristics (in priority order):
     * 1. Font size &ge; [HEADING_SIZE_RATIO_THRESHOLD] &times; body size &rarr; [ContentBlock.Heading]
     *    with level from [HEADING_LEVEL_RATIOS].
     * 2. ALL CAPS short line &rarr; [ContentBlock.Heading] level 3.
     * 3. Indented italic text &rarr; [ContentBlock.Quote].
     * 4. Bold text &rarr; [ContentBlock.Paragraph] with isBold=true.
     * 5. Everything else &rarr; [ContentBlock.Paragraph].
     */
    private fun classifyLines(
        lines: List<TextLine>,
        bodyFontSize: Float
    ): List<ContentBlock> {
        if (bodyFontSize <= 0f || lines.isEmpty()) return emptyList()

        // Estimate left margin (most common minX across lines) to detect indentation
        val leftMargin = estimateLeftMargin(lines)

        val blocks = mutableListOf<ContentBlock>()
        var index = 0

        for (line in lines) {
            val sizeRatio = line.avgFontSize / bodyFontSize

            // Heuristic 1: Font size significantly larger than body
            if (sizeRatio >= HEADING_SIZE_RATIO_THRESHOLD) {
                val level = determineHeadingLevel(sizeRatio)
                blocks.add(ContentBlock.Heading(index = index, text = line.text, level = level))
                index++
                continue
            }

            // Heuristic 2: ALL CAPS short line (likely a heading)
            if (line.isAllCaps) {
                // Use bold/caps convention: bold=all-caps → level 2, just all-caps → level 3
                val level = if (line.isBold) 2 else 3
                blocks.add(ContentBlock.Heading(index = index, text = line.text, level = level))
                index++
                continue
            }

            // Heuristic 3: Indented italic text → quote
            val isIndented = line.minX > leftMargin + bodyFontSize * 2 // 2em indent
            if (isIndented && line.isItalic) {
                blocks.add(ContentBlock.Quote(index = index, text = line.text))
                index++
                continue
            }

            // Heuristic 4 & 5: Paragraph (with optional bold flag)
            blocks.add(
                ContentBlock.Paragraph(index = index, text = line.text, isBold = line.isBold)
            )
            index++
        }

        return blocks
    }

    /**
     * Determines heading level from the font-size ratio (chunk size / body size).
     */
    private fun determineHeadingLevel(sizeRatio: Float): Int {
        for ((threshold, level) in HEADING_LEVEL_RATIOS) {
            if (sizeRatio >= threshold) return level
        }
        // Between HEADING_SIZE_RATIO_THRESHOLD and the smallest explicit ratio → h6
        return 6
    }

    /**
     * Estimates the left margin (most common minX) across lines.
     *
     * This is used to detect indented text (e.g., block quotes). We bucket
     * X positions to the nearest 5 PDF points to handle slight variations.
     */
    private fun estimateLeftMargin(lines: List<TextLine>): Float {
        if (lines.isEmpty()) return 0f

        // Bucket minX values to nearest 5 points
        val buckets = mutableMapOf<Int, Int>() // bucket -> count
        for (line in lines) {
            val bucket = (line.minX / 5f).toInt()
            buckets[bucket] = (buckets[bucket] ?: 0) + 1
        }

        val mostCommonBucket = buckets.maxByOrNull { it.value }?.key ?: 0
        return mostCommonBucket * 5f
    }
}
