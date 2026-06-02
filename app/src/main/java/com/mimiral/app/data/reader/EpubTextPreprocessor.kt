package com.mimiral.app.data.reader

import java.util.Locale

/**
 * Result of preprocessing a single EPUB chapter.
 *
 * @param chapterIndex Zero-based chapter index.
 * @param title       Chapter title from the TOC or derived.
 * @param rawText     The plain text extracted from HTML, with sentence boundaries preserved.
 * @param sentences   List of [Sentence] objects detected in the extracted text.
 * @param charCount   Total character count of the extracted text.
 */
data class EpubChapterText(
    val chapterIndex: Int,
    val title: String,
    val rawText: String,
    val sentences: List<Sentence>,
    val charCount: Int = rawText.length
)

/**
 * Configuration for the EPUB text preprocessing pipeline.
 *
 * @param preserveLineBreaks Whether to preserve paragraph-level line breaks.
 * @param removeHeaders      Whether to remove header content (h1-h6) from output.
 * @param locale             Locale for sentence boundary detection.
 */
data class EpubPreprocessConfig(
    val preserveLineBreaks: Boolean = true,
    val removeHeaders: Boolean = false,
    val locale: Locale = Locale.getDefault()
)

/**
 * EPUB text preprocessing pipeline for TTS consumption.
 *
 * Takes raw HTML/XHTML content from EPUB chapters and produces clean plain text
 * with sentence boundaries preserved. The pipeline:
 *
 * 1. Strips script, style, head, nav tags and HTML comments.
 * 2. Decodes HTML entities (named, numeric, hex).
 * 3. Converts HTML to plain text by stripping remaining tags.
 * 4. Normalizes whitespace.
 * 5. Optionally preserves paragraph-level line breaks.
 * 6. Detects sentence boundaries using ICU BreakIterator.
 *
 * This class uses pure Kotlin for HTML parsing and does not depend on Android
 * framework classes, making it fully unit-testable without Robolectric.
 */
class EpubTextPreprocessor(
    private val config: EpubPreprocessConfig = EpubPreprocessConfig()
) {
    private val sentenceDetector = SentenceBoundaryDetector(config.locale)

    // Patterns for stripping non-content elements
    private val scriptTagPattern = Regex(
        "<script[^>]*>.*?</script>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val styleTagPattern = Regex(
        "<style[^>]*>.*?</style>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val headTagPattern = Regex(
        "<head[^>]*>.*?</head>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val navTagPattern = Regex(
        "<nav[^>]*>.*?</nav>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val commentPattern = Regex(
        "<!--.*?-->",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    private val doctypePattern = Regex(
        "<!DOCTYPE[^>]*>",
        RegexOption.IGNORE_CASE
    )
    private val xmlDeclPattern = Regex(
        "<\\?xml[^?]*\\?>",
        RegexOption.IGNORE_CASE
    )
    private val cdataPattern = Regex(
        "<!\\[CDATA\\[.*?\\]\\]>",
        RegexOption.DOT_MATCHES_ALL
    )

    // Pattern to match any HTML tag
    private val htmlTagPattern = Regex("<[^>]+>")

    // Pattern for whitespace normalization
    private val multiSpacePattern = Regex(" {2,}")
    private val multiNewlinePattern = Regex("\n{3,}")

    // HTML entity patterns
    private val namedEntityPattern = Regex("&([a-zA-Z][a-zA-Z0-9]*);")
    private val numericEntityPattern = Regex("&#(\\d+);")
    private val hexEntityPattern = Regex("&#x([0-9a-fA-F]+);")

    // Block-level tags that should produce line breaks
    private val blockTags = setOf(
        "p", "div", "br", "h1", "h2", "h3", "h4", "h5", "h6",
        "li", "tr", "blockquote", "pre", "section", "article",
        "ul", "ol", "table", "thead", "tbody", "tfoot", "hr"
    )

    /**
     * Processes a single EPUB chapter HTML content into clean plain text.
     */
    fun processChapter(
        html: String,
        chapterIndex: Int = 0,
        title: String = ""
    ): EpubChapterText {
        if (html.isBlank()) {
            return EpubChapterText(chapterIndex, title, "", emptyList())
        }
        val cleaned = stripUnwantedTags(html)
        val withBreaks = convertHtmlToText(cleaned)
        val normalized = normalizeWhitespace(withBreaks)
        val sentences = sentenceDetector.findSentences(normalized)
        return EpubChapterText(chapterIndex, title, normalized, sentences)
    }

    /**
     * Processes multiple chapters in batch.
     */
    fun processChapters(
        chapters: List<Triple<String, Int, String>>
    ): List<EpubChapterText> {
        return chapters.map { (html, index, title) ->
            processChapter(html, index, title)
        }
    }

    /**
     * Extracts plain text from HTML without sentence detection.
     */
    fun extractText(html: String): String {
        if (html.isBlank()) return ""
        val cleaned = stripUnwantedTags(html)
        val withBreaks = convertHtmlToText(cleaned)
        return normalizeWhitespace(withBreaks)
    }

    /**
     * Returns the number of sentences in the given HTML content.
     */
    fun countSentences(html: String): Int {
        val text = extractText(html)
        return sentenceDetector.countSentences(text)
    }

    // ---- Private helpers ----

    private fun stripUnwantedTags(html: String): String {
        var result = html
        result = xmlDeclPattern.replace(result, "")
        result = doctypePattern.replace(result, "")
        result = commentPattern.replace(result, "")
        result = cdataPattern.replace(result, "")
        result = scriptTagPattern.replace(result, "")
        result = styleTagPattern.replace(result, "")
        result = headTagPattern.replace(result, "")
        result = navTagPattern.replace(result, "")
        return result
    }

    private fun convertHtmlToText(html: String): String {
        var text = html

        // Replace block-level closing tags with newlines for paragraph separation
        for (tag in blockTags) {
            text = text.replace(
                Regex("</$tag>", RegexOption.IGNORE_CASE),
                "\n"
            )
        }

        // Handle self-closing br tags
        text = text.replace(
            Regex("<br\\s*/?>", RegexOption.IGNORE_CASE),
            "\n"
        )

        // Decode HTML entities before stripping tags
        text = decodeHtmlEntities(text)

        // Strip all remaining HTML tags
        text = htmlTagPattern.replace(text, "")

        return text
    }

    /**
     * Decodes HTML entities: named (&amp;), numeric (&#8212;), and hex (&#x2014;).
     */
    private fun decodeHtmlEntities(text: String): String {
        var result = text

        // Decode hex entities first (&#xHHHH;)
        result = hexEntityPattern.replace(result) { match ->
            val hex = match.groupValues[1]
            try {
                val codePoint = hex.toInt(16)
                String(Character.toChars(codePoint))
            } catch (e: Exception) {
                match.value // leave as-is if invalid
            }
        }

        // Decode numeric entities (&#DDDD;)
        result = numericEntityPattern.replace(result) { match ->
            val dec = match.groupValues[1]
            try {
                val codePoint = dec.toInt()
                String(Character.toChars(codePoint))
            } catch (e: Exception) {
                match.value // leave as-is if invalid
            }
        }

        // Decode named entities (&amp; &lt; &gt; &quot; etc.)
        result = namedEntityPattern.replace(result) { match ->
            val name = match.groupValues[1].lowercase()
            HTML_ENTITY_MAP[name] ?: match.value
        }

        return result
    }

    private fun normalizeWhitespace(text: String): String {
        if (text.isBlank()) return ""
        val lines = text.split("\n")
        val normalizedLines = lines.map { line ->
            multiSpacePattern.replace(line.trim(), " ")
        }.filter { it.isNotEmpty() }

        return if (config.preserveLineBreaks) {
            val joined = normalizedLines.joinToString("\n")
            multiNewlinePattern.replace(joined, "\n\n")
        } else {
            normalizedLines.joinToString(" ")
        }
    }

    companion object {
        /**
         * Map of HTML named entities to their character equivalents.
         * Covers all standard HTML 4.0 entities plus common extensions.
         */
        val HTML_ENTITY_MAP: Map<String, String> = mapOf(
            // XML predefined entities
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",

            // Common typographic entities
            "nbsp" to " ",
            "ensp" to " ",
            "emsp" to " ",
            "thinsp" to " ",
            "ndash" to "\u2013",
            "mdash" to "\u2014",
            "lsquo" to "\u2018",
            "rsquo" to "\u2019",
            "sbquo" to "\u201A",
            "ldquo" to "\u201C",
            "rdquo" to "\u201D",
            "bdquo" to "\u201E",
            "lsaquo" to "\u2039",
            "rsaquo" to "\u203A",
            "laquo" to "\u00AB",
            "raquo" to "\u00BB",
            "hellip" to "\u2026",
            "bull" to "\u2022",
            "prime" to "\u2032",
            "Prime" to "\u2033",
            "oline" to "\u203E",
            "frasl" to "\u2044",

            // Currency
            "cent" to "\u00A2",
            "pound" to "\u00A3",
            "yen" to "\u00A5",
            "euro" to "\u20AC",

            // Mathematical
            "times" to "\u00D7",
            "divide" to "\u00F7",
            "plusmn" to "\u00B1",
            "ne" to "\u2260",
            "le" to "\u2264",
            "ge" to "\u2265",
            "asymp" to "\u2248",
            "equiv" to "\u2261",
            "infin" to "\u221E",
            "prod" to "\u220F",
            "sum" to "\u2211",
            "radic" to "\u221A",
            "int" to "\u222B",
            "part" to "\u2202",
            "nabla" to "\u2207",
            "prop" to "\u221D",
            "there4" to "\u2234",
            "sim" to "\u223C",
            "cong" to "\u2245",
            "cap" to "\u2229",
            "cup" to "\u222A",
            "sub" to "\u2282",
            "sup" to "\u2283",
            "sube" to "\u2286",
            "supe" to "\u2287",
            "isin" to "\u2208",
            "notin" to "\u2209",
            "empty" to "\u2205",
            "ang" to "\u2220",
            "perp" to "\u22A5",

            // Greek letters (common)
            "alpha" to "\u03B1",
            "beta" to "\u03B2",
            "gamma" to "\u03B3",
            "delta" to "\u03B4",
            "epsilon" to "\u03B5",
            "theta" to "\u03B8",
            "lambda" to "\u03BB",
            "mu" to "\u03BC",
            "pi" to "\u03C0",
            "sigma" to "\u03C3",
            "phi" to "\u03C6",
            "omega" to "\u03C9",
            "Alpha" to "\u0391",
            "Beta" to "\u0392",
            "Gamma" to "\u0393",
            "Delta" to "\u0394",
            "Theta" to "\u0398",
            "Lambda" to "\u039B",
            "Pi" to "\u03A0",
            "Sigma" to "\u03A3",
            "Phi" to "\u03A6",
            "Omega" to "\u03A9",

            // Accented characters (common)
            "aacute" to "\u00E1",
            "eacute" to "\u00E9",
            "iacute" to "\u00ED",
            "oacute" to "\u00F3",
            "uacute" to "\u00FA",
            "agrave" to "\u00E0",
            "egrave" to "\u00E8",
            "igrave" to "\u00EC",
            "ograve" to "\u00F2",
            "ugrave" to "\u00F9",
            "acirc" to "\u00E2",
            "ecirc" to "\u00EA",
            "icirc" to "\u00EE",
            "ocirc" to "\u00F4",
            "ucirc" to "\u00FB",
            "auml" to "\u00E4",
            "euml" to "\u00EB",
            "iuml" to "\u00EF",
            "ouml" to "\u00F6",
            "uuml" to "\u00FC",
            "atilde" to "\u00E3",
            "ntilde" to "\u00F1",
            "otilde" to "\u00F5",
            "ccedil" to "\u00E7",
            "Ccedil" to "\u00C7",
            "szlig" to "\u00DF",
            "aelig" to "\u00E6",
            "AElig" to "\u00C6",
            "oelig" to "\u0153",
            "OElig" to "\u0152",
            "aring" to "\u00E5",
            "Aring" to "\u00C5",
            "eth" to "\u00F0",
            "ETH" to "\u00D0",
            "thorn" to "\u00FE",
            "THORN" to "\u00DE",
            "oslash" to "\u00F8",
            "Oslash" to "\u00D8",

            // Ligatures
            "filig" to "\uFB01",
            "fllig" to "\uFB02",

            // Misc
            "copy" to "\u00A9",
            "reg" to "\u00AE",
            "trade" to "\u2122",
            "sect" to "\u00A7",
            "para" to "\u00B6",
            "micro" to "\u00B5",
            "middot" to "\u00B7",
            "dagger" to "\u2020",
            "Dagger" to "\u2021",
            "permil" to "\u2030",
            "lsaquo" to "\u2039",
            "rsaquo" to "\u203A",
            "spades" to "\u2660",
            "clubs" to "\u2663",
            "hearts" to "\u2665",
            "diams" to "\u2666"
        )
    }
}
