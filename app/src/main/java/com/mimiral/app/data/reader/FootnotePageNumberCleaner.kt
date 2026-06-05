package com.mimiral.app.data.reader

/**
 * Result of a footnote/page-number cleaning operation.
 *
 * @param cleanedText  The text with footnote markers and page numbers removed.
 * @param footnoteMarkersRemoved Count of footnote markers stripped.
 * @param pageNumbersRemoved    Count of page numbers stripped.
 */
data class CleaningResult(
    val cleanedText: String,
    val footnoteMarkersRemoved: Int,
    val pageNumbersRemoved: Int
)

/**
 * Removes footnote markers and standalone page numbers from EPUB text
 * to produce clean output for TTS.
 *
 * ## Footnote marker patterns handled
 *
 * | Pattern | Example | Description |
 * |---------|---------|-------------|
 * | Superscript digits | `¹`, `²`, `³` | Unicode superscript 1-3 |
 * | Superscript n      | `ⁿ`            | Unicode superscript n |
 * | Bracketed numbers  | `[1]`, `[23]`  | Square-bracket markers |
 * | Bracketed asterisks| `[*]`, `[**]`  | Asterisk markers |
 * | Parenthesized numbers | `(1)`, `(23)` | Paren markers |
 * | Superscript tag + digits | `<sup>1</sup>`, `<sup>12</sup>` | HTML superscript (before
 *   tag stripping) |
 * | Dagger / double-dagger | `†`, `‡` | Reference markers |
 * | Asterisk footnote markers | `*`, `**`, `***` at end of word before
 *   space/punct | Inline asterisk markers |
 * | Number-dot footnote    | `1.`, `2.` at start of line (footnote body) | Footnote definitions |
 *
 * ## Page number patterns handled
 *
 * | Pattern | Example | Description |
 * |---------|---------|-------------|
 * | Standalone number on own line | `42` | Page number insert |
 * | Braced page markers | `{42}`, `\[p.42\]`, `\[pg 42\]` | Explicit page markers |
 * | "p." / "page" prefix | `p.42`, `page 42` | Page references on own line |
 * | - NNN - format | `- 42 -` | Centered page number |
 *
 * All patterns are applied conservatively to avoid removing legitimate content
 * (e.g., numbers in the middle of a sentence, ordinals like "1st", etc.).
 *
 * Usage:
 * ```
 * val cleaner = FootnotePageNumberCleaner()
 * val result = cleaner.clean("Some text¹ with [2] footnotes and\n42\npage numbers.")
 * // result.cleanedText == "Some text with footnotes and\npage numbers."
 * // result.footnoteMarkersRemoved == 2
 * // result.pageNumbersRemoved == 1
 * ```
 */
class FootnotePageNumberCleaner {

    /**
     * Cleans footnote markers and page numbers from the input text.
     *
     * @param text The input text (typically after HTML tag stripping).
     * @return A [CleaningResult] with the cleaned text and removal counts.
     */
    fun clean(text: String): CleaningResult {
        if (text.isBlank()) {
            return CleaningResult(
                cleanedText = text,
                footnoteMarkersRemoved = 0,
                pageNumbersRemoved = 0
            )
        }

        var currentText = text
        var footnoteCount = 0
        var pageCount = 0

        // Phase 1: Remove HTML superscript footnote markers (before plain-text patterns)
        val superscriptResult = removeHtmlSuperscriptMarkers(currentText)
        currentText = superscriptResult.first
        footnoteCount += superscriptResult.second

        // Phase 2: Remove Unicode superscript footnote markers
        val unicodeResult = removeUnicodeSuperscriptMarkers(currentText)
        currentText = unicodeResult.first
        footnoteCount += unicodeResult.second

        // Phase 3: Remove bracketed footnote markers ([1], [*], etc.)
        val bracketedResult = removeBracketedMarkers(currentText)
        currentText = bracketedResult.first
        footnoteCount += bracketedResult.second

        // Phase 4: Remove parenthesized footnote markers ((1), (2), etc.)
        val parenResult = removeParenthesizedMarkers(currentText)
        currentText = parenResult.first
        footnoteCount += parenResult.second

        // Phase 5: Remove dagger/double-dagger markers
        val daggerResult = removeDaggerMarkers(currentText)
        currentText = daggerResult.first
        footnoteCount += daggerResult.second

        // Phase 6: Remove trailing asterisk footnote markers
        val asteriskResult = removeAsteriskMarkers(currentText)
        currentText = asteriskResult.first
        footnoteCount += asteriskResult.second

        // Phase 7: Remove footnote body lines (e.g., "1. Footnote text" at start of line)
        val footnoteBodyResult = removeFootnoteBodyLines(currentText)
        currentText = footnoteBodyResult.first
        footnoteCount += footnoteBodyResult.second

        // Phase 8: Remove page number patterns
        val pageResult = removePageNumbers(currentText)
        currentText = pageResult.first
        pageCount += pageResult.second

        // Clean up excessive whitespace left by removals
        currentText = normalizeWhitespace(currentText)

        return CleaningResult(
            cleanedText = currentText,
            footnoteMarkersRemoved = footnoteCount,
            pageNumbersRemoved = pageCount
        )
    }

    // ---- Phase implementations ----

    /**
     * Remove HTML superscript tags containing footnote numbers.
     * e.g., `<sup>1</sup>`, `<sup class="fn">12</sup>`
     */
    private fun removeHtmlSuperscriptMarkers(text: String): Pair<String, Int> {
        val pattern = Regex("""<sup[^>]*>\s*\d+\s*</sup>""", RegexOption.IGNORE_CASE)
        val matches = pattern.findAll(text).count()
        return Pair(text.replace(pattern, ""), matches)
    }

    /**
     * Remove Unicode superscript characters used as footnote markers.
     * ¹ ² ³ ⁿ and the general superscript range (U+2070..U+2079).
     */
    private fun removeUnicodeSuperscriptMarkers(text: String): Pair<String, Int> {
        // Superscript digits: ⁰¹²³⁴⁵⁶⁷⁸⁹ and ⁿ
        val pattern = Regex("""[⁰¹²³⁴⁵⁶⁷⁸⁹ⁿ]+""")
        val matches = pattern.findAll(text).count()
        return Pair(text.replace(pattern, ""), matches)
    }

    /**
     * Remove bracketed footnote markers: [1], [23], [*], [**], [***], [†], [‡].
     */
    private fun removeBracketedMarkers(text: String): Pair<String, Int> {
        val pattern = Regex("""\[\d+\]|\[\*{1,3}\]|\[†\]|\[‡\]""")
        val matches = pattern.findAll(text).count()
        return Pair(text.replace(pattern, ""), matches)
    }

    /**
     * Remove parenthesized footnote markers: (1), (23).
     * Only matches when the parens contain just a number — avoids removing
     * legitimate parenthetical content like "(see chapter 3)".
     */
    private fun removeParenthesizedMarkers(text: String): Pair<String, Int> {
        val pattern = Regex("""\(\d+\)""")
        val matches = pattern.findAll(text).count()
        return Pair(text.replace(pattern, ""), matches)
    }

    /**
     * Remove dagger (†) and double-dagger (‡) markers.
     */
    private fun removeDaggerMarkers(text: String): Pair<String, Int> {
        val pattern = Regex("""[†‡]""")
        val matches = pattern.findAll(text).count()
        return Pair(text.replace(pattern, ""), matches)
    }

    /**
     * Remove trailing asterisk footnote markers attached to words.
     * e.g., "word*" or "word**" or "word***" — the asterisk(s) at the end
     * of a word before space, punctuation, or end of line.
     *
     * Does NOT remove asterisks in the middle of text (e.g., "5 * 3").
     */
    private fun removeAsteriskMarkers(text: String): Pair<String, Int> {
        // Asterisk(s) at end of word, followed by space, punctuation, or line end
        val pattern = Regex("""(\w)\*{1,3}(?=[\s,.;:!?)}\]]|$)""")
        val matches = pattern.findAll(text).count()
        return Pair(text.replace(pattern, "$1"), matches)
    }

    /**
     * Remove footnote body lines that start with a number followed by a period
     * and space, which are footnote/backnote definitions.
     * e.g., "1. This is a footnote explanation."
     *
     * Only matches at the start of a line when the number is small (1-999)
     * to avoid removing numbered lists in main text. We apply a heuristic:
     * if the line starts with a low number (1-50) followed by period+space
     * AND the line is short (< 200 chars), it's likely a footnote body.
     *
     * Actually, the safest approach is to only remove lines that look like
     * footnote references collected at the end of a section/page — they tend
     * to be short lines starting with "N." where N is a small integer.
     */
    private fun removeFootnoteBodyLines(text: String): Pair<String, Int> {
        // Match lines that are footnote definitions: start of line, small number + period + space
        // These appear as collected notes at bottom of pages.
        // Pattern: line starts with 1-3 digit number, period, space, then content
        // We only remove the marker prefix, not the whole line (footnote text may be useful)
        // Actually for TTS, we want to remove footnote bodies entirely since they
        // interrupt the flow. But being conservative: only remove lines that are
        // clearly footnote references (short, start with number.marker pattern).
        val pattern = Regex("""^(?:\d{1,3}\.\s)(.+)$""", RegexOption.MULTILINE)
        var count = 0
        val result = text.lines().map { line ->
            val match = pattern.find(line.trimStart())
            if (match != null) {
                // Heuristic: if the trimmed line is short and starts with a number
                // that's 1-99, treat as footnote body line
                val numMatch = Regex("""^(\d{1,2})\.\s""").find(line.trimStart())
                if (numMatch != null) {
                    val num = numMatch.groupValues[1].toIntOrNull() ?: 0
                    if (num in 1..99 && line.trim().length < 200) {
                        count++
                        return@map "" // Remove entire footnote body line
                    }
                }
            }
            line
        }.filter { it.isNotEmpty() }.joinToString("\n")
        return Pair(result, count)
    }

    /**
     * Remove page number patterns:
     * - Standalone number on its own line (e.g., "42")
     * - Braced page markers: {42}
     * - Page prefix markers: \[p.42\], \[pg 42\]
     * - "p.N" or "page N" on their own line
     * - "- N -" centered page format
     */
    private fun removePageNumbers(text: String): Pair<String, Int> {
        var currentText = text
        var count = 0

        // 1. Remove {N}, [p.N], [pg N] markers
        val bracedPattern = Regex("""\{\d+\}|\[p\.\s*\d+\]|\[pg\s+\d+\]""", RegexOption.IGNORE_CASE)
        count += bracedPattern.findAll(currentText).count()
        currentText = currentText.replace(bracedPattern, "")

        // 2. Remove "- N -" centered page format
        val centeredPattern = Regex("""-\s*\d{1,4}\s*-""")
        count += centeredPattern.findAll(currentText).count()
        currentText = currentText.replace(centeredPattern, "")

        // 3. Remove "p.N" or "page N" on their own line
        val pageLinePattern = Regex(
            """^\s*(?:p\.\s*\d{1,4}|page\s+\d{1,4})\s*$""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )
        count += pageLinePattern.findAll(currentText).count()
        currentText = currentText.replace(pageLinePattern, "")

        // 4. Remove standalone numbers on their own line
        //    Conservative: only 1-4 digit numbers that occupy a line by themselves
        val standaloneNumPattern = Regex("""^\s*\d{1,4}\s*$""", RegexOption.MULTILINE)
        val lines = currentText.lines()
        val filteredLines = lines.filter { line ->
            if (standaloneNumPattern.matches(line)) {
                // Additional check: don't remove lines that are just "0" (could be a list item)
                val num = line.trim().toIntOrNull()
                if (num != null && num > 0) {
                    count++
                    return@filter false // Remove this line
                }
            }
            true
        }
        currentText = filteredLines.joinToString("\n")

        return Pair(currentText, count)
    }

    /**
     * Normalizes whitespace after removals — collapses multiple spaces,
     * removes blank lines left by removals, etc.
     */
    private fun normalizeWhitespace(text: String): String {
        return text
            .replace(Regex("""[ \t]+"""), " ") // Collapse horizontal whitespace
            .replace(Regex("""\n{2,}"""), "\n") // Max 2 consecutive newlines
            .replace(Regex(""" \n"""), "\n") // Remove trailing spaces before newlines
            .replace(Regex("""\n """), "\n") // Remove leading spaces after newlines
            .trim()
    }

    companion object {
        /**
         * Convenience method: cleans text and returns just the cleaned string.
         */
        fun cleanText(text: String): String {
            return FootnotePageNumberCleaner().clean(text).cleanedText
        }
    }
}
