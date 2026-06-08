package com.mimiral.app.data.reader

/**
 * Extracts structured [ContentBlock]s from EPUB XHTML content.
 *
 * Unlike [ChapterExtractor.stripHtmlTags] which flattens everything to a plain
 * string, this extractor preserves the semantic structure of the source HTML:
 *
 * - `<h1>`–`<h6>` → [ContentBlock.Heading] with level 1–6
 * - `<p>` with `<strong>`/`<b>`/`<em>`/`<i>` → [ContentBlock.Paragraph] with [TextSpan]s
 * - `<blockquote>` → [ContentBlock.Quote]
 * - `<li>` inside `<ul>` → [ContentBlock.ListItem] with order=0
 * - `<li>` inside `<ol>` → [ContentBlock.ListItem] with 1-based order
 * - `<hr>` → [ContentBlock.Rule]
 *
 * The extractor uses a simple recursive descent approach over a pre-lexed token
 * stream, keeping it pure Kotlin and unit-testable without Android dependencies.
 *
 * Usage:
 * ```
 * val extractor = EpubStructuredExtractor()
 * val blocks = extractor.extract(xhtml)
 * // blocks: List<ContentBlock> — Heading, Paragraph, Quote, etc.
 * ```
 */
class EpubStructuredExtractor {

    /**
     * Extracts structured content blocks from EPUB XHTML.
     *
     * @param html The raw XHTML content from an EPUB chapter.
     * @return List of [ContentBlock]s in document order, each with a sequential index.
     */
    fun extract(html: String): List<ContentBlock> {
        if (html.isBlank()) return emptyList()

        // Strip non-content elements first (same as EpubTextPreprocessor)
        val cleaned = stripNonContent(html)
        if (cleaned.isBlank()) return emptyList()

        // Tokenize into a stream of open tags, close tags, and text segments
        val tokens = tokenize(cleaned)
        if (tokens.isEmpty()) return emptyList()

        // Parse the token stream into structured blocks
        val rawBlocks = parseTokens(tokens)

        // Assign sequential indices
        return rawBlocks.mapIndexed { index, block ->
            when (block) {
                is ContentBlock.Heading -> block.copy(index = index)
                is ContentBlock.Paragraph -> block.copy(index = index)
                is ContentBlock.Quote -> block.copy(index = index)
                is ContentBlock.ListItem -> block.copy(index = index)
                is ContentBlock.Rule -> block.copy(index = index)
            }
        }
    }

    /**
     * Convenience: extract and return only the plain text, joined by newlines.
     * Useful for TTS or fallback when structured rendering isn't needed.
     */
    fun extractPlainText(html: String): String {
        return extract(html).joinToString("\n") { block -> block.text }
    }

    // ---- Tokenizer ----

    /** A single token from the HTML stream. */
    internal sealed class Token {
        /** An opening tag, e.g. `<p class="indent">`. [name] is lowercased. */
        data class OpenTag(val name: String, val attrs: String = "") : Token()

        /** A closing tag, e.g. `</p>`. [name] is lowercased. */
        data class CloseTag(val name: String) : Token()

        /** A self-closing tag, e.g. `<br/>`. [name] is lowercased. */
        data class SelfClosingTag(val name: String) : Token()

        /** A run of text content between tags. */
        data class Text(val content: String) : Token()
    }

    /**
     * Splits HTML into a flat list of tokens (open tags, close tags, self-closing
     * tags, and text). Handles attributes, self-closing syntax, and entity decoding.
     */
    internal fun tokenize(html: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val tagPattern = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*?)(/?)>""")
        var lastIndex = 0

        for (match in tagPattern.findAll(html)) {
            // Text before this tag
            if (match.range.first > lastIndex) {
                val text = html.substring(lastIndex, match.range.first)
                if (text.isNotEmpty()) {
                    val decoded = decodeEntities(text)
                    val normalized = decoded.replace(Regex("\\s+"), " ")
                    if (normalized.isNotEmpty()) {
                        tokens.add(Token.Text(normalized))
                    }
                }
            }

            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2].lowercase()
            val attrs = match.groupValues[3]
            val isSelfClosing = match.groupValues[4] == "/"

            when {
                isClosing -> tokens.add(Token.CloseTag(tagName))
                isSelfClosing -> tokens.add(Token.SelfClosingTag(tagName))
                else -> tokens.add(Token.OpenTag(tagName, attrs))
            }

            lastIndex = match.range.last + 1
        }

        // Trailing text after the last tag
        if (lastIndex < html.length) {
            val text = html.substring(lastIndex)
            if (text.isNotEmpty()) {
                val decoded = decodeEntities(text)
                val normalized = decoded.replace(Regex("\\s+"), " ")
                if (normalized.isNotEmpty()) {
                    tokens.add(Token.Text(normalized))
                }
            }
        }

        return tokens
    }

    // ---- Parser ----

    /**
     * Internal text-based span for parsing. Converted to offset-based
     * [TextSpan] when producing [ContentBlock.Paragraph].
     */
    private data class ParsedSpan(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false
    )

    /**
     * Parses the token stream into a list of ContentBlocks (with placeholder index 0;
     * indices are assigned by [extract] after parsing).
     */
    private fun parseTokens(tokens: List<Token>): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val ctx = ParseContext(tokens)
        ctx.pos = 0

        while (ctx.pos < tokens.size) {
            val token = tokens[ctx.pos]

            when (token) {
                is Token.OpenTag -> {
                    val parsed = parseOpenTag(token, ctx)
                    blocks.addAll(parsed)
                }
                is Token.CloseTag -> {
                    ctx.pos++
                }
                is Token.SelfClosingTag -> {
                    if (token.name == "hr") {
                        blocks.add(ContentBlock.Rule(index = 0))
                    }
                    ctx.pos++
                }
                is Token.Text -> {
                    if (token.content.isNotBlank()) {
                        val plainText = token.content.trim()
                        blocks.add(
                            ContentBlock.Paragraph(
                                index = 0,
                                text = plainText,
                                isBold = false,
                                spans = emptyList()
                            )
                        )
                    }
                    ctx.pos++
                }
            }
        }

        return blocks
    }

    /**
     * Context state for the recursive parser.
     */
    private class ParseContext(
        val tokens: List<Token>,
        var pos: Int = 0
    )

    /**
     * Handles an open tag at the current position.
     * Returns a list of ContentBlocks. Advances ctx.pos past the consumed tokens.
     */
    private fun parseOpenTag(tag: Token.OpenTag, ctx: ParseContext): List<ContentBlock> {
        return when (tag.name) {
            in HEADING_TAGS -> {
                val level = tag.name.removePrefix("h").toIntOrNull() ?: 1
                ctx.pos++
                val text = collectTextUntilClose(tag.name, ctx)
                listOf(ContentBlock.Heading(index = 0, text = text, level = level))
            }
            "p" -> {
                ctx.pos++
                val parsedSpans = parseSpansUntilClose("p", ctx)
                parsedSpansToParagraph(parsedSpans)
            }
            "blockquote" -> {
                ctx.pos++
                val innerText = collectTextUntilClose("blockquote", ctx)
                if (innerText.isNotBlank()) {
                    listOf(ContentBlock.Quote(index = 0, text = innerText.trim()))
                } else {
                    emptyList()
                }
            }
            "ul", "ol" -> {
                val ordered = tag.name == "ol"
                ctx.pos++
                parseListItems(ordered, ctx)
            }
            "div", "section", "article" -> {
                ctx.pos++
                parseChildrenUntilClose(tag.name, ctx)
            }
            else -> {
                ctx.pos++
                emptyList()
            }
        }
    }

    /**
     * Converts parsed text-based spans to a [ContentBlock.Paragraph].
     * Text-based [ParsedSpan]s are converted to offset-based [TextSpan]s.
     *
     * Bold detection: if ALL spans are bold, the paragraph itself is marked bold
     * and no individual TextSpans are emitted (reduces redundant markup).
     */
    private fun parsedSpansToParagraph(
        parsedSpans: List<ParsedSpan>
    ): List<ContentBlock.Paragraph> {
        if (parsedSpans.isEmpty() || parsedSpans.all { it.text.isBlank() }) {
            return emptyList()
        }

        // Filter out blank spans
        val nonBlank = parsedSpans.filter { it.text.isNotBlank() }
        if (nonBlank.isEmpty()) return emptyList()

        // Build full text and offset-based TextSpans
        val fullText = nonBlank.joinToString("") { it.text }
        val allBold = nonBlank.isNotEmpty() && nonBlank.all { it.bold }

        val textSpans = mutableListOf<TextSpan>()
        if (!allBold) {
            // Only emit spans for styled text; plain text doesn't need a span
            var offset = 0
            for (span in nonBlank) {
                if (span.bold || span.italic) {
                    textSpans.add(
                        TextSpan(
                            start = offset,
                            end = offset + span.text.length,
                            isBold = span.bold,
                            isItalic = span.italic
                        )
                    )
                }
                offset += span.text.length
            }
        }

        return listOf(
            ContentBlock.Paragraph(
                index = 0,
                text = fullText,
                isBold = allBold,
                spans = textSpans
            )
        )
    }

    /**
     * Parses list items inside a `<ul>` or `<ol>` until the matching close tag.
     * Returns a list of ListItem blocks.
     */
    private fun parseListItems(ordered: Boolean, ctx: ParseContext): List<ContentBlock.ListItem> {
        val items = mutableListOf<ContentBlock.ListItem>()
        var itemNumber = 1

        while (ctx.pos < ctx.tokens.size) {
            val token = ctx.tokens[ctx.pos]

            when {
                token is Token.CloseTag && (token.name == "ul" || token.name == "ol") -> {
                    ctx.pos++
                    break
                }
                token is Token.OpenTag && token.name == "li" -> {
                    ctx.pos++
                    val text = collectTextUntilClose("li", ctx).trim()
                    if (text.isNotEmpty()) {
                        items.add(
                            ContentBlock.ListItem(
                                index = 0,
                                text = text,
                                order = if (ordered) itemNumber else 0
                            )
                        )
                    }
                    itemNumber++
                }
                else -> ctx.pos++
            }
        }

        return items
    }

    /**
     * Collects all text content between the current position and the matching
     * close tag, skipping over any nested tags (their text is flattened).
     */
    private fun collectTextUntilClose(closeTagName: String, ctx: ParseContext): String {
        val parts = mutableListOf<String>()
        var depth = 1

        while (ctx.pos < ctx.tokens.size && depth > 0) {
            val token = ctx.tokens[ctx.pos]

            when (token) {
                is Token.OpenTag -> {
                    if (token.name == closeTagName) depth++
                    ctx.pos++
                }
                is Token.CloseTag -> {
                    if (token.name == closeTagName) {
                        depth--
                        if (depth == 0) {
                            ctx.pos++
                            break
                        }
                    }
                    ctx.pos++
                }
                is Token.Text -> {
                    parts.add(token.content)
                    ctx.pos++
                }
                is Token.SelfClosingTag -> {
                    if (token.name == "br") parts.add("\n")
                    ctx.pos++
                }
            }
        }

        return parts.joinToString("").replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Parses inline content inside a `<p>` tag, preserving bold/italic spans.
     * Returns a list of [ParsedSpan] entries (text-based; converted later).
     */
    private fun parseSpansUntilClose(closeTagName: String, ctx: ParseContext): List<ParsedSpan> {
        val spans = mutableListOf<ParsedSpan>()
        val styleStack = mutableListOf<InlineStyle>()
        val currentText = StringBuilder()

        while (ctx.pos < ctx.tokens.size) {
            val token = ctx.tokens[ctx.pos]

            when (token) {
                is Token.OpenTag -> {
                    when (token.name) {
                        in BOLD_TAGS -> {
                            flushSpanText(currentText, spans, styleStack)
                            styleStack.add(InlineStyle.BOLD)
                            ctx.pos++
                        }
                        in ITALIC_TAGS -> {
                            flushSpanText(currentText, spans, styleStack)
                            styleStack.add(InlineStyle.ITALIC)
                            ctx.pos++
                        }
                        else -> {
                            ctx.pos++
                        }
                    }
                }
                is Token.CloseTag -> {
                    when (token.name) {
                        closeTagName -> {
                            flushSpanText(currentText, spans, styleStack)
                            ctx.pos++
                            return spans
                        }
                        in BOLD_TAGS -> {
                            flushSpanText(currentText, spans, styleStack)
                            styleStack.remove(InlineStyle.BOLD)
                            ctx.pos++
                        }
                        in ITALIC_TAGS -> {
                            flushSpanText(currentText, spans, styleStack)
                            styleStack.remove(InlineStyle.ITALIC)
                            ctx.pos++
                        }
                        else -> ctx.pos++
                    }
                }
                is Token.Text -> {
                    currentText.append(token.content)
                    ctx.pos++
                }
                is Token.SelfClosingTag -> {
                    if (token.name == "br") {
                        currentText.append("\n")
                    }
                    ctx.pos++
                }
            }
        }

        flushSpanText(currentText, spans, styleStack)
        return spans
    }

    /**
     * Flushes the current text buffer into a [ParsedSpan] with the current style stack.
     */
    private fun flushSpanText(
        buf: StringBuilder,
        spans: MutableList<ParsedSpan>,
        styleStack: List<InlineStyle>
    ) {
        if (buf.isEmpty()) return
        val text = buf.toString()
        buf.clear()

        val isBold = InlineStyle.BOLD in styleStack
        val isItalic = InlineStyle.ITALIC in styleStack

        if ("\n" in text) {
            for (segment in text.split("\n")) {
                if (segment.isNotEmpty()) {
                    spans.add(ParsedSpan(text = segment, bold = isBold, italic = isItalic))
                }
            }
        } else {
            spans.add(ParsedSpan(text = text, bold = isBold, italic = isItalic))
        }
    }

    /**
     * Parses children inside a container element (div, section, article) until
     * the matching close tag. Returns blocks found inside.
     */
    private fun parseChildrenUntilClose(
        closeTagName: String,
        ctx: ParseContext
    ): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()

        while (ctx.pos < ctx.tokens.size) {
            val token = ctx.tokens[ctx.pos]

            when {
                token is Token.CloseTag && token.name == closeTagName -> {
                    ctx.pos++
                    break
                }
                token is Token.OpenTag -> {
                    val parsed = parseOpenTag(token, ctx)
                    blocks.addAll(parsed)
                }
                token is Token.SelfClosingTag -> {
                    if (token.name == "hr") {
                        blocks.add(ContentBlock.Rule(index = 0))
                    }
                    ctx.pos++
                }
                token is Token.Text -> {
                    if (token.content.isNotBlank()) {
                        val plainText = token.content.trim()
                        blocks.add(
                            ContentBlock.Paragraph(
                                index = 0,
                                text = plainText,
                                isBold = false,
                                spans = emptyList()
                            )
                        )
                    }
                    ctx.pos++
                }
                else -> ctx.pos++
            }
        }

        return blocks
    }

    // ---- Pre-processing ----

    /** Tags whose content should be entirely removed. */
    private val removeTags = setOf("script", "style", "head", "nav")

    /**
     * Strips non-content elements (script, style, head, nav, comments, etc.)
     * from the HTML before tokenizing.
     */
    private fun stripNonContent(html: String): String {
        var result = html

        result = result.replace(Regex("""<\?xml[^?]*\?>""", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("""<!DOCTYPE[^>]*>""", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        result = result.replace(Regex("""<!\[CDATA\[.*?\]>""", RegexOption.DOT_MATCHES_ALL), "")

        for (tag in removeTags) {
            result = result.replace(
                Regex(
                    """<$tag[^>]*>.*?</$tag>""",
                    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
                ),
                ""
            )
        }

        return result
    }

    // ---- Entity decoding ----

    /**
     * Decodes common HTML entities in text content.
     * Reuses the entity map from [EpubTextPreprocessor.Companion.HTML_ENTITY_MAP].
     */
    private fun decodeEntities(text: String): String {
        var result = text

        result = result.replace(Regex("""&#x([0-9a-fA-F]+);""")) { match ->
            runCatching { String(Character.toChars(match.groupValues[1].toInt(16))) }
                .getOrDefault(match.value)
        }

        result = result.replace(Regex("""&#(\d+);""")) { match ->
            runCatching { String(Character.toChars(match.groupValues[1].toInt())) }
                .getOrDefault(match.value)
        }

        result = result.replace(Regex("""&([a-zA-Z][a-zA-Z0-9]*);""")) { match ->
            val name = match.groupValues[1].lowercase()
            EpubTextPreprocessor.HTML_ENTITY_MAP[name] ?: match.value
        }

        return result
    }

    // ---- Constants ----

    private companion object {
        val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        val BOLD_TAGS = setOf("strong", "b")
        val ITALIC_TAGS = setOf("em", "i")
    }

    /** Tracks inline style state during span parsing. */
    private enum class InlineStyle {
        BOLD, ITALIC
    }
}
