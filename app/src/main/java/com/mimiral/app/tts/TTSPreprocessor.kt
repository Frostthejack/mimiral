package com.mimiral.app.tts

/**
 * Preprocesses text before sending to the TTS engine.
 *
 * Handles special characters that cause TTS engines to produce incorrect
 * or garbled speech: dashes, smart quotes, ligatures, abbreviations, and
 * other typographic characters that EPUB sources commonly contain.
 *
 * Usage:
 * ```
 * val preprocessor = TTSPreprocessor()
 * val cleanText = preprocessor.preprocess(rawText)
 * ttsManager.play(cleanText)
 * ```
 *
 * The preprocessor is stateless and thread-safe. A single instance can be
 * shared across all TTS operations.
 */
class TTSPreprocessor {

    /**
     * Character mapping table for typographic character replacements.
     *
     * Each entry maps a single character to its TTS-safe replacement string.
     * The mapping is ordered so that replacements are applied in a single
     * pass via buildReplacementMap().
     */
    companion object {
        // Dash replacements -> pause (comma or semicolon creates a natural pause)
        val DASH_EM = '\u2014' // — (em dash)
        val DASH_EN = '\u2013' // – (en dash)
        val DASH_FIGURE = '\u2012' // ‒ (figure dash)
        val DASH_HBAR = '\u2015' // ― (horizontal bar)
        val DASH_SWUNG = '\u2015' // ― (swung dash / tilde dash)

        // Smart quote replacements
        val QUOTE_SINGLE_OPEN = '\u2018' // '
        val QUOTE_SINGLE_CLOSE = '\u2019' // '
        val QUOTE_SINGLE_LOW = '\u201a' // ‚
        val QUOTE_SINGLE_HIGH_REV = '\u201b' // ‛
        val QUOTE_DOUBLE_OPEN = '\u201c' // "
        val QUOTE_DOUBLE_CLOSE = '\u201d' // "
        val QUOTE_DOUBLE_LOW = '\u201e' // „
        val QUOTE_DOUBLE_HIGH_REV = '\u201f' // ‟
        val QUOTE_ANGLE_LEFT = '\u00ab' // «
        val QUOTE_ANGLE_RIGHT = '\u00bb' // »
        val QUOTE_ANGLE_SINGLE_LEFT = '\u2039' // ‹
        val QUOTE_ANGLE_SINGLE_RIGHT = '\u203a' // ›
        val PRIME = '\u2032' // ′
        val PRIME_DOUBLE = '\u2033' // ″
        val PRIME_TRIPLE = '\u2034' // ‴

        // Ligature replacements
        val LIGATURE_AE = '\u00c6' // Æ
        val LIGATURE_ae = '\u00e6' // æ
        val LIGATURE_OE = '\u0152' // Œ
        val LIGATURE_oe = '\u0153' // œ
        val LIGATURE_SS = '\u00df' // ß (Eszett / sharp s)
        val LIGATURE_ff = '\ufb00' // ﬀ
        val LIGATURE_fi = '\ufb01' // ﬁ
        val LIGATURE_fl = '\ufb02' // ﬂ
        val LIGATURE_ffi = '\ufb03' // ﬃ
        val LIGATURE_ffl = '\ufb04' // ﬄ
        val LIGATURE_ft = '\ufb05' // ﬅ
        val LIGATURE_st = '\ufb06' // ﬆ

        // Other problematic characters in TTS
        val BULLET = '\u2022' // •
        val ELLIPSIS = '\u2026' // …
        val HYPHENATION = '\u2010' // ‐ (non-breaking hyphen)
        val NON_BREAKING_HYPHEN = '\u2011' // ‑
        val MINUS = '\u2212' // − (minus sign)
        val TIMES = '\u00d7' // × (multiplication sign)
        val DIVISION = '\u00f7' // ÷ (division sign)
        val FRACTION_SLASH = '\u2044' // ⁄ (fraction slash)
        val PER_MILLE = '\u2030' // ‰
        val PRIME_APOSTROPHE = '\u02bc' // ʼ (modifier letter apostrophe)

        // Build the replacement map once as a sorted list of (from, to) pairs.
        val REPLACEMENTS: List<Pair<Char, String>> = listOf(
            // Dashes -> comma creates natural pause in TTS
            Pair(DASH_EM, ", "),
            Pair(DASH_EN, ", "),
            Pair(DASH_FIGURE, ", "),
            Pair(DASH_HBAR, ", "),
            Pair(DASH_SWUNG, ", "),

            // Smart single quotes -> straight single quote
            Pair(QUOTE_SINGLE_OPEN, "'"),
            Pair(QUOTE_SINGLE_CLOSE, "'"),
            Pair(QUOTE_SINGLE_LOW, "'"),
            Pair(QUOTE_SINGLE_HIGH_REV, "'"),

            // Smart double quotes -> straight double quote
            Pair(QUOTE_DOUBLE_OPEN, "\""),
            Pair(QUOTE_DOUBLE_CLOSE, "\""),
            Pair(QUOTE_DOUBLE_LOW, "\""),
            Pair(QUOTE_DOUBLE_HIGH_REV, "\""),

            // Angle quotes
            Pair(QUOTE_ANGLE_LEFT, "\""),
            Pair(QUOTE_ANGLE_RIGHT, "\""),
            Pair(QUOTE_ANGLE_SINGLE_LEFT, "'"),
            Pair(QUOTE_ANGLE_SINGLE_RIGHT, "'"),

            // Primes -> appropriate substitutions
            Pair(PRIME, "'"),
            Pair(PRIME_DOUBLE, "\""),
            Pair(PRIME_TRIPLE, "\"\""),

            // Ligatures -> expanded form
            Pair(LIGATURE_AE, "AE"),
            Pair(LIGATURE_ae, "ae"),
            Pair(LIGATURE_OE, "OE"),
            Pair(LIGATURE_oe, "oe"),
            Pair(LIGATURE_SS, "ss"),
            Pair(LIGATURE_ff, "ff"),
            Pair(LIGATURE_fi, "fi"),
            Pair(LIGATURE_fl, "fl"),
            Pair(LIGATURE_ffi, "ffi"),
            Pair(LIGATURE_ffl, "ffl"),
            Pair(LIGATURE_ft, "ft"),
            Pair(LIGATURE_st, "st"),

            // Other characters
            Pair(BULLET, " "),
            Pair(ELLIPSIS, ", "),
            Pair(HYPHENATION, "-"),
            Pair(NON_BREAKING_HYPHEN, "-"),
            Pair(MINUS, " minus "),
            Pair(TIMES, " times "),
            Pair(DIVISION, " divided by "),
            Pair(FRACTION_SLASH, " / "),
            Pair(PER_MILLE, " per mille "),
            Pair(PRIME_APOSTROPHE, "'")
        )
    }

    /**
     * Builds a HashMap lookup table from the REPLACEMENTS list.
     */
    private val replacementMap: HashMap<Char, String> =
        HashMap<Char, String>(REPLACEMENTS.size).apply {
            for ((from, to) in REPLACEMENTS) {
                put(from, to)
            }
        }

    /**
     * Common abbreviations that TTS engines may struggle with.
     */
    val ABBREVIATIONS: Map<String, String> = mapOf(
        "Mr" to "Mister",
        "Mrs" to "Missus",
        "Ms" to "Miss",
        "Dr" to "Doctor",
        "Prof" to "Professor",
        "Sr" to "Senior",
        "Jr" to "Junior",
        "St" to "Saint",
        "etc" to "etcetera",
        "i.e" to "that is",
        "e.g" to "for example",
        "vs" to "versus",
        "approx" to "approximately",
        "dept" to "department",
        "est" to "established",
        "govt" to "government",
        "natl" to "national",
        "org" to "organization",
        "intl" to "international",
        "info" to "information",
        "min" to "minutes",
        "max" to "maximum",
        "temp" to "temperature",
        "gov" to "government",
        "inc" to "incorporated",
        "corp" to "corporation",
        "ltd" to "limited",
        "assn" to "association"
    )

    /**
     * Preprocesses the given text for TTS consumption.
     *
     * Performs the following transformations in order:
     * 1. Character replacement pass (smart quotes, dashes, ligatures, etc.)
     * 2. Abbreviation expansion pass
     * 3. Whitespace normalization
     *
     * @param text The raw text (already HTML-stripped) to preprocess.
     * @return TTS-safe text with special characters replaced/expanded.
     */
    fun preprocess(text: String): String {
        if (text.isBlank()) return text

        val afterChars = replaceCharacters(text)
        val afterAbbr = expandAbbreviations(afterChars)
        return normalizeWhitespace(afterAbbr)
    }

    /**
     * Replaces special characters using the character mapping table.
     */
    fun replaceCharacters(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (ch in text) {
            val replacement = replacementMap[ch]
            if (replacement != null) {
                sb.append(replacement)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Expands known abbreviations to their full forms.
     *
     * Matches abbreviations followed by a period at word boundaries
     * to avoid false positives (e.g., "org" in "organize" is not expanded).
     */
    fun expandAbbreviations(text: String): String {
        var result = text
        for ((abbr, expansion) in ABBREVIATIONS) {
            val pattern = Regex(
                "(?i)(?<=^|[^a-zA-Z])${Regex.escape(abbr)}\\.(?=$|[^a-zA-Z])"
            )
            result = pattern.replace(result, expansion)
        }
        return result
    }

    /**
     * Normalizes whitespace - collapses multiple spaces, trims.
     */
    fun normalizeWhitespace(text: String): String {
        return text.replace(Regex(" +"), " ").trim()
    }

    /**
     * Checks whether the text contains characters that would be modified.
     */
    fun containsSpecialCharacters(text: String): Boolean {
        for (ch in text) {
            if (replacementMap.containsKey(ch)) return true
        }
        return false
    }

    /**
     * Returns the set of characters this preprocessor handles.
     */
    fun getHandledCharacters(): Set<Char> {
        return replacementMap.keys.toSet()
    }
}
