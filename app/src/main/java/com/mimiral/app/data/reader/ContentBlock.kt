package com.mimiral.app.data.reader

/**
 * Sealed class representing a structured content block extracted from a document.
 *
 * Used by both EPUB (HTML-heading-based) and PDF (font-metadata-based) extractors
 * to represent the semantic structure of a document's content.
 *
 * Each block carries its text content and enough metadata for the reading-mode
 * UI to render headings, paragraphs, quotes, and formatted spans appropriately.
 */
sealed class ContentBlock {
    /** Zero-based index of this block in the page/chapter sequence. */
    abstract val index: Int

    /** The text content of this block. */
    abstract val text: String

    /**
     * A heading block detected from large font size, bold font, or ALL CAPS lines.
     *
     * @param level Heading level (1 = largest/most important, 6 = smallest).
     *               Derived proportionally from font-size ratio to body font.
     */
    data class Heading(
        override val index: Int,
        override val text: String,
        val level: Int
    ) : ContentBlock()

    /**
     * A normal paragraph block — body text in the default font.
     *
     * @param isBold Whether the paragraph uses a bold/semibold font throughout.
     * @param spans Inline spans (bold, italic) within the paragraph text.
     *              Each span describes a contiguous range with formatting.
     */
    data class Paragraph(
        override val index: Int,
        override val text: String,
        val isBold: Boolean = false,
        val spans: List<TextSpan> = emptyList()
    ) : ContentBlock()

    /**
     * A block quote — indented text, typically in italic font.
     */
    data class Quote(
        override val index: Int,
        override val text: String
    ) : ContentBlock()

    /**
     * A list item block.
     *
     * @param order 1-based item number, or 0 for unordered (bullet).
     */
    data class ListItem(
        override val index: Int,
        override val text: String,
        val order: Int = 0
    ) : ContentBlock()

    /**
     * A horizontal rule / separator.
     */
    data class Rule(
        override val index: Int
    ) : ContentBlock() {
        override val text: String get() = ""
    }
}

/**
 * Represents an inline span within a ContentBlock.Paragraph.
 *
 * @param start Inclusive start offset within the paragraph text.
 * @param end Exclusive end offset within the paragraph text.
 * @param isBold Whether this span should be rendered as bold.
 * @param isItalic Whether this span should be rendered as italic.
 */
data class TextSpan(
    val start: Int,
    val end: Int,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
)
