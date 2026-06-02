package com.mimiral.app.data.reader

import java.text.BreakIterator

/**
 * Represents a single sentence extracted from text.
 *
 * @param start Start offset (inclusive) in the original text.
 * @param end   End offset (exclusive) in the original text.
 * @param text  The sentence text, trimmed of leading/trailing whitespace.
 */
data class Sentence(
    val start: Int,
    val end: Int,
    val text: String
)

/**
 * Detects sentence boundaries using Android's ICU-backed [BreakIterator].
 *
 * Handles common edge cases via ICU's built-in sentence-break rules:
 * - Abbreviations (Dr., Mr., Mrs., etc.) do not trigger sentence breaks.
 * - Ellipses (...) are treated as part of the current sentence unless
 *   followed by a capital letter or sentence terminator.
 * - Decimal numbers (e.g., 3.14) are not split.
 * - Quotation marks after sentence terminators are included in the sentence.
 *
 * Usage:
 * ```
 * val detector = SentenceBoundaryDetector()
 * val sentences = detector.findSentences("Hello world! How are you? I'm fine.")
 * // => [Sentence(0,12,"Hello world!"), Sentence(12,27,"How are you?"), Sentence(27,37,"I'm fine.")]
 * ```
 */
class SentenceBoundaryDetector(
    private val locale: java.util.Locale = java.util.Locale.getDefault()
) {

    /**
     * Find all sentence boundaries in the given text.
     *
     * @param text The input text to analyze.
     * @return A list of [Sentence] objects representing each detected sentence.
     *         Returns an empty list if the text is blank.
     */
    fun findSentences(text: String): List<Sentence> {
        if (text.isBlank()) return emptyList()

        val breakIterator = BreakIterator.getSentenceInstance(locale)
        breakIterator.setText(text)

        val sentences = mutableListOf<Sentence>()
        var start = breakIterator.first()
        var end = breakIterator.next()

        while (end != BreakIterator.DONE) {
            val sentenceText = text.substring(start, end).trim()
            if (sentenceText.isNotEmpty()) {
                sentences.add(
                    Sentence(
                        start = start,
                        end = end,
                        text = sentenceText
                    )
                )
            }
            start = end
            end = breakIterator.next()
        }

        return sentences
    }

    /**
     * Find the sentence that contains the given character offset.
     *
     * @param text   The input text to analyze.
     * @param offset The character offset to locate the sentence for.
     * @return The [Sentence] containing the offset, or null if the text is
     *         blank or the offset is out of bounds.
     */
    fun findSentenceAt(text: String, offset: Int): Sentence? {
        if (text.isBlank() || offset < 0 || offset > text.length) return null

        val sentences = findSentences(text)
        return sentences.find { offset >= it.start && offset < it.end }
    }

    /**
     * Count the number of sentences in the given text.
     *
     * @param text The input text to analyze.
     * @return The number of sentences detected.
     */
    fun countSentences(text: String): Int {
        if (text.isBlank()) return 0

        val breakIterator = BreakIterator.getSentenceInstance(locale)
        breakIterator.setText(text)

        var count = 0
        var end = breakIterator.next()
        while (end != BreakIterator.DONE) {
            count++
            end = breakIterator.next()
        }
        return count
    }

    /**
     * Split text into sentence-level substrings without offset information.
     * Convenience method equivalent to `findSentences(text).map { it.text }`.
     *
     * @param text The input text to split.
     * @return A list of sentence strings.
     */
    fun splitSentences(text: String): List<String> {
        return findSentences(text).map { it.text }
    }
}
