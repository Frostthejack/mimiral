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
 * - `<li>` inside `<ul>` → [ContentBlock.ListItem] with ordered=false
 * - `<li>` inside `<ol>` → [ContentBlock.ListItem] with ordered=true
 * - `<br>` → paragraph break (splits surrounding text into separate Paragraph blocks)
 *
 * The extractor uses a simple recursive descent approach over a pre-lexed token
 * stream, keeping it pure Kotlin and unit-testable without Android dependencies.
 *
 * Usage:
 * ```
 * val extractor = EpubStructuredExtractor()
 * val blocks = extractor.extract(xhtml)
 * for (block in blocks) {
 *     when (block) {
 *         is ContentBlock.Heading -> println("${"#".repeat(block.level)} ${block.text}")
 *         is ContentBlock.Paragraph -> println(block.spans)
 *         is ContentBlock.Quote -> println("> ${block.text}")
 *         is ContentBlock.ListItem -> println("${if (block.ordered) "${block.index}." else "•"} ${block.text}")
 *     }
 * }
 * ```
 */
class EpubStructuredExtractor {

    /**
     * Extracts structured content blocks from EPUB XHTML.
     *
     * @param html The raw XHTML content from an EPUB chapter.
     * @return List of [ContentBlock]s in document order.
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
        return parseTokens(tokens)
    }

    /**
     * Convenience: extract and return only the plain text, joined by newlines.
     * Useful for TTS or fallback when structured rendering isn't needed.
     */
    fun extractPlainText(html: String): String {
        return extract(html).joinToString("\n") { block ->
            when (block) {
                is ContentBlock.Heading -> block.text
                is ContentBlock.Paragraph -> block.text
                is ContentBlock.Quote -> block.text
                is ContentBlock.ListItem -> block.text
            }
        }
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
                    // Collapse all whitespace (newlines, multi-spaces) to single spaces.
                    // This preserves inline spaces ("Hello ") while removing
                    // formatting whitespace (newlines between block tags).
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
     * Parses the token stream into a list of ContentBlocks.
     *
     * This is a simple top-down parser that walks the flat token list,
     * tracking context (inside blockquote? inside list?) to produce
     * the correct block types.
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
                    // Close tag at top level — skip it
                    ctx.pos++
                }
                is Token.SelfClosingTag -> {
                    // <br> at top level — skip (meaningful only inside paragraphs)
                    ctx.pos++
                }
                is Token.Text -> {
                    // Bare text at top level (not inside any block tag) —
                    // wrap in a paragraph, but skip whitespace-only text
                    if (token.content.isNotBlank()) {
                        blocks.add(ContentBlock.Paragraph(listOf(TextSpan(token.content.trim()))))
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
     * Returns a list of ContentBlocks (may be empty for unrecognized tags, or
     * multiple for list elements). Advances ctx.pos past the consumed tokens.
     */
    private fun parseOpenTag(tag: Token.OpenTag, ctx: ParseContext): List<ContentBlock> {
        return when (tag.name) {
            in HEADING_TAGS -> {
                val level = tag.name.removePrefix("h").toIntOrNull() ?: 1
                ctx.pos++ // skip the open tag
                val text = collectTextUntilClose(tag.name, ctx)
                listOf(ContentBlock.Heading(level = level, text = text))
            }
            "p" -> {
                ctx.pos++ // skip <p>
                val spans = parseSpansUntilClose("p", ctx)
                if (spans.isNotEmpty() && spans.any { it.text.isNotBlank() }) {
                    listOf(ContentBlock.Paragraph(spans))
                } else {
                    emptyList()
                }
            }
            "blockquote" -> {
                ctx.pos++ // skip <blockquote>
                // Collect all inner text (flattening nested structure)
                val innerText = collectTextUntilClose("blockquote", ctx)
                if (innerText.isNotBlank()) {
                    listOf(ContentBlock.Quote(innerText.trim()))
                } else {
                    emptyList()
                }
            }
            "ul", "ol" -> {
                val ordered = tag.name == "ol"
                ctx.pos++ // skip <ul> or <ol>
                parseListItems(ordered, ctx)
            }
            // Container tags: div, section, article — parse children inside
            "div", "section", "article" -> {
                ctx.pos++ // skip open tag
                parseChildrenUntilClose(tag.name, ctx)
            }
            else -> {
                // Not a block tag we handle — skip it (inline tags handled
                // inside parseSpans; other unknown tags are just ignored)
                ctx.pos++
                emptyList()
            }
        }
    }

    /**
     * Parses list items inside a `<ul>` or `<ol>` until the matching close tag.
     * Returns a list of ListItem blocks.
     */
    private fun parseListItems(ordered: Boolean, ctx: ParseContext): List<ContentBlock.ListItem> {
        val items = mutableListOf<ContentBlock.ListItem>()
        var itemIndex = 1

        while (ctx.pos < ctx.tokens.size) {
            val token = ctx.tokens[ctx.pos]

            when {
                token is Token.CloseTag && (token.name == "ul" || token.name == "ol") -> {
                    ctx.pos++ // consume the close tag
                    break
                }
                token is Token.OpenTag && token.name == "li" -> {
                    ctx.pos++ // skip <li>
                    val text = collectTextUntilClose("li", ctx).trim()
                    if (text.isNotEmpty()) {
                        items.add(
                            ContentBlock.ListItem(
                                text = text,
                                ordered = ordered,
                                index = itemIndex
                            )
                        )
                    }
                    itemIndex++
                }
                else -> ctx.pos++ // skip unknown tokens inside list
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
        var depth = 1 // We're already inside the opening tag

        while (ctx.pos < ctx.tokens.size && depth > 0) {
            val token = ctx.tokens[ctx.pos]

            when (token) {
                is Token.OpenTag -> {
                    if (token.name == closeTagName) depth++ // nested same tag
                    ctx.pos++
                }
                is Token.CloseTag -> {
                    if (token.name == closeTagName) {
                        depth--
                        if (depth == 0) {
                            ctx.pos++ // consume the close tag
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
     * Returns a list of [TextSpan] entries.
     */
    private fun parseSpansUntilClose(closeTagName: String, ctx: ParseContext): List<TextSpan> {
        val spans = mutableListOf<TextSpan>()
        val styleStack = mutableListOf<InlineStyle>()
        val currentText = StringBuilder()

        while (ctx.pos < ctx.tokens.size) {
            val token = ctx.tokens[ctx.pos]

            when (token) {
                is Token.OpenTag -> {
                    when (token.name) {
                        in BOLD_TAGS -> {
                            flushText(currentText, spans, styleStack)
                            styleStack.add(InlineStyle.BOLD)
                            ctx.pos++
                        }
                        in ITALIC_TAGS -> {
                            flushText(currentText, spans, styleStack)
                            styleStack.add(InlineStyle.ITALIC)
                            ctx.pos++
                        }
                        else -> {
                            // Unknown inline open tag — skip it, text continues
                            ctx.pos++
                        }
                    }
                }
                is Token.CloseTag -> {
                    when (token.name) {
                        closeTagName -> {
                            flushText(currentText, spans, styleStack)
                            ctx.pos++ // consume close tag
                            return spans
                        }
                        in BOLD_TAGS -> {
                            flushText(currentText, spans, styleStack)
                            styleStack.remove(InlineStyle.BOLD)
                            ctx.pos++
                        }
                        in ITALIC_TAGS -> {
                            flushText(currentText, spans, styleStack)
                            styleStack.remove(InlineStyle.ITALIC)
                            ctx.pos++
                        }
                        else -> ctx.pos++ // skip unknown close tag
                    }
                }
                is Token.Text -> {
                    currentText.append(token.content)
                    ctx.pos++
                }
                is Token.SelfClosingTag -> {
                    if (token.name == "br") {
                        // <br> inside a paragraph: flush current text and add a
                        // newline marker (we split at newlines after flushing)
                        currentText.append("\n")
                    }
                    ctx.pos++
                }
            }
        }

        // Reached end of tokens without finding the close tag
        flushText(currentText, spans, styleStack)
        return spans
    }

    /**
     * Flushes the current text buffer into a TextSpan with the current style stack.
     */
    private fun flushText(
        buf: StringBuilder,
        spans: MutableList<TextSpan>,
        styleStack: List<InlineStyle>
    ) {
        if (buf.isEmpty()) return
        val text = buf.toString()
        buf.clear()

        val isBold = InlineStyle.BOLD in styleStack
        val isItalic = InlineStyle.ITALIC in styleStack

        // Handle <br>-induced line breaks by splitting
        if ("\n" in text) {
            for (segment in text.split("\n")) {
                if (segment.isNotEmpty()) {
                    spans.add(TextSpan(text = segment, bold = isBold, italic = isItalic))
                }
            }
        } else {
            spans.add(TextSpan(text = text, bold = isBold, italic = isItalic))
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
                    ctx.pos++ // consume close tag
                    break
                }
                token is Token.OpenTag -> {
                    val parsed = parseOpenTag(token, ctx)
                    blocks.addAll(parsed)
                }
                token is Token.Text -> {
                    if (token.content.isNotBlank()) {
                        blocks.add(ContentBlock.Paragraph(listOf(TextSpan(token.content.trim()))))
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

        // Remove XML declaration
        result = result.replace(Regex("""<\?xml[^?]*\?>""", RegexOption.IGNORE_CASE), "")

        // Remove DOCTYPE
        result = result.replace(Regex("""<!DOCTYPE[^>]*>""", RegexOption.IGNORE_CASE), "")

        // Remove HTML comments
        result = result.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")

        // Remove CDATA sections
        result = result.replace(Regex("""<!\[CDATA\[.*?\]>""", RegexOption.DOT_MATCHES_ALL), "")

        // Remove script/style/head/nav blocks entirely
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

        // Decode hex entities (&#xHHHH;)
        result = result.replace(Regex("""&#x([0-9a-fA-F]+);""")) { match ->
            runCatching { String(Character.toChars(match.groupValues[1].toInt(16))) }
                .getOrDefault(match.value)
        }

        // Decode numeric entities (&#DDDD;)
        result = result.replace(Regex("""&#(\d+);""")) { match ->
            runCatching { String(Character.toChars(match.groupValues[1].toInt())) }
                .getOrDefault(match.value)
        }

        // Decode named entities using the shared map
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
