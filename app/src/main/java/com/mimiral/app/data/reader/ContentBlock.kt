package com.mimiral.app.data.reader

/**
 * A styled span of text within a [ContentBlock.Paragraph].
 *
 * Each span represents a contiguous run of text with consistent formatting.
 * Multiple spans within a paragraph can represent mixed bold/italic styling
 * extracted from HTML elements like `<strong>`, `<b>`, `<em>`, and `<i>`.
 *
 * @param text The text content of this span.
 * @param bold Whether this span should be rendered in bold.
 * @param italic Whether this span should be rendered in italic.
 */
data class TextSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false
)

/**
 * Sealed class hierarchy representing structured content extracted from book HTML.
 *
 * Each subclass corresponds to a distinct semantic element found in EPUB XHTML:
 * - [Heading]: `<h1>` through `<h6>` elements
 * - [Paragraph]: `<p>` elements with inline formatting preserved as [TextSpan]s
 * - [Quote]: `<blockquote>` elements
 * - [ListItem]: `<li>` elements within `<ul>` or `<ol>`
 *
 * The hierarchy is designed so that a `List<ContentBlock>` replaces raw `String`
 * output, giving the UI layer enough structure to render headings at different
 * sizes, apply bold/italic spans, style quotes distinctly, and prefix list items
 * with appropriate markers.
 */
sealed class ContentBlock {

    /**
     * A heading element extracted from `<h1>` through `<h6>` tags.
     *
     * @param level Heading level, 1–6 (maps from h1–h6).
     * @param text The heading text content.
     */
    data class Heading(
        val level: Int,
        val text: String
    ) : ContentBlock() {
        init {
            require(level in 1..6) { "Heading level must be 1-6, got $level" }
        }
    }

    /**
     * A paragraph element extracted from `<p>` tags, preserving inline formatting.
     *
     * Inline `<strong>`/`<b>` and `<em>`/`<i>` tags are preserved as [TextSpan]
     * entries, while the overall paragraph structure is maintained.
     *
     * @param spans The styled text spans within this paragraph.
     */
    data class Paragraph(
        val spans: List<TextSpan>
    ) : ContentBlock() {
        /** Convenience: the plain text of this paragraph (all spans concatenated). */
        val text: String get() = spans.joinToString("") { it.text }

        /** Whether this paragraph contains any styled (bold or italic) spans. */
        val hasStyle: Boolean get() = spans.any { it.bold || it.italic }
    }

    /**
     * A block quote element extracted from `<blockquote>` tags.
     *
     * @param text The quote text content.
     */
    data class Quote(
        val text: String
    ) : ContentBlock()

    /**
     * A list item element extracted from `<li>` tags.
     *
     * The [ordered] flag indicates whether the parent list was `<ol>` or `<ul>`,
     * and [index] provides the 1-based position for ordered lists.
     *
     * @param text The list item text content.
     * @param ordered Whether this item belongs to an ordered list (`<ol>`).
     * @param index 1-based position within the list (for ordered lists).
     */
    data class ListItem(
        val text: String,
        val ordered: Boolean,
        val index: Int
    ) : ContentBlock()
}
