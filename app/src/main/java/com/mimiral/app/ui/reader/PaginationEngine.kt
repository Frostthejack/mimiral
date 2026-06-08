package com.mimiral.app.ui.reader

import android.content.Context
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mimiral.app.data.reader.ContentBlock

/**
 * Data class representing text rendering configuration.
 */
data class TextRenderConfig(
    val fontSize: Int = 18,
    val lineSpacingExtra: Float = 8f,
    val lineSpacingMultiplier: Float = 1.2f,
    val marginTop: Dp = 24.dp,
    val marginBottom: Dp = 24.dp,
    val marginLeft: Dp = 24.dp,
    val marginRight: Dp = 24.dp,
    val fontFamily: FontFamily = FontFamily.Default,
    val fontWeight: FontWeight = FontWeight.Normal,
    val customTypeface: Typeface? = null
)

/**
 * A single page of structured content blocks.
 *
 * Each page contains the [ContentBlock]s that fit within the usable viewport
 * height. The [startCharOffset] tracks the character offset from the start
 * of the chapter for progress/bookmark tracking.
 *
 * @param pageIndex Zero-based page index.
 * @param blocks ContentBlocks rendered on this page.
 * @param startCharOffset Character offset from the start of the chapter.
 */
data class ContentPage(
    val pageIndex: Int,
    val blocks: List<ContentBlock>,
    val startCharOffset: Int
) {
    /** Concatenated text of all blocks on this page (legacy compat). */
    val text: String get() = blocks.joinToString("\n\n") { it.text }

    /** Character offset at the start of this page (alias for startCharOffset). */
    val startOffset: Int get() = startCharOffset

    /** Character offset at the end of this page. */
    val endOffset: Int get() = startCharOffset + blocks.sumOf { it.text.length }
}

/**
 * Result of pagination containing all pages and metadata.
 */
data class PaginationResult(
    val pages: List<ContentPage>,
    val totalPages: Int,
    val config: TextRenderConfig
)

/**
 * PaginationEngine uses Android's StaticLayout to compute page boundaries
 * based on screen dimensions, font settings, and margins.
 *
 * Supports two pagination modes:
 * 1. **Structured pagination** ([paginateBlocks]): Takes `List<ContentBlock>`,
 *    measures each block's height based on its type (headings are taller,
 *    quotes have left padding, list items have indent), and distributes
 *    blocks across pages.
 * 2. **Legacy text pagination** ([paginate]): Takes a raw `String`, wraps
 *    it in a single Paragraph block, and falls through to structured pagination.
 */
class PaginationEngine(private val context: Context) {

    private var _paginationResult = mutableStateOf<PaginationResult?>(null)
    val paginationResult: PaginationResult? get() = _paginationResult.value

    companion object {
        /** Heading font size multipliers relative to body fontSize. */
        private val HEADING_SIZE_MULTIPLIERS = mapOf(
            1 to 1.78f, // 32sp at 18sp base
            2 to 1.56f, // 28sp
            3 to 1.22f, // 22sp
            4 to 1.0f, // 18sp (bold)
            5 to 1.0f, // 18sp (bold)
            6 to 1.0f // 18sp (bold)
        )

        /** Additional vertical padding (in dp) for headings (top + bottom). */
        private val HEADING_VERTICAL_PADDING_DP = 16f

        /** Quote left indent in dp. */
        private const val QUOTE_LEFT_INDENT_DP = 24f

        /** List item left indent in dp. */
        private const val LIST_ITEM_LEFT_INDENT_DP = 16f

        /** Rule (horizontal divider) height in dp. */
        private const val RULE_HEIGHT_DP = 32f

        /** Inter-block spacing in dp (vertical padding between blocks). */
        private const val BLOCK_SPACING_DP = 8f

        /** Inter-paragraph spacing in dp. */
        private const val PARAGRAPH_SPACING_DP = 8f
    }

    /**
     * Paginate a list of ContentBlocks using block-type-aware height measurement.
     *
     * Each block is measured with its appropriate font size and padding:
     * - Headings use scaled font sizes and extra vertical padding
     * - Quotes have left indent reducing usable width
     * - List items have left indent reducing usable width
     * - Rules occupy a fixed height
     */
    fun paginateBlocks(
        blocks: List<ContentBlock>,
        config: TextRenderConfig,
        screenWidthPx: Int,
        screenHeightPx: Int
    ): PaginationResult {
        if (blocks.isEmpty()) {
            val result = PaginationResult(
                pages = listOf(ContentPage(0, emptyList(), 0)),
                totalPages = 1,
                config = config
            )
            _paginationResult = mutableStateOf(result)
            return result
        }

        val marginLeftPx = dpToPx(config.marginLeft)
        val marginRightPx = dpToPx(config.marginRight)
        val marginTopPx = dpToPx(config.marginTop)
        val marginBottomPx = dpToPx(config.marginBottom)

        val usableWidth = screenWidthPx - marginLeftPx - marginRightPx
        val usableHeight = screenHeightPx - marginTopPx - marginBottomPx

        if (usableWidth <= 0 || usableHeight <= 0) {
            val result = PaginationResult(
                pages = listOf(ContentPage(0, blocks, 0)),
                totalPages = 1,
                config = config
            )
            _paginationResult = mutableStateOf(result)
            return result
        }

        // Compute per-block heights
        val blockHeights = blocks.map { block ->
            measureBlockHeight(block, config, usableWidth)
        }

        // Distribute blocks across pages
        val pages = distributeBlocksToPages(blocks, blockHeights, usableHeight)

        val result = PaginationResult(
            pages = pages,
            totalPages = pages.size,
            config = config
        )
        _paginationResult = mutableStateOf(result)
        return result
    }

    /**
     * Legacy paginate: takes raw text, wraps in a Paragraph block,
     * and delegates to [paginateBlocks].
     */
    fun paginate(
        text: String,
        config: TextRenderConfig,
        screenWidthPx: Int,
        screenHeightPx: Int
    ): PaginationResult {
        if (text.isBlank()) {
            val result = PaginationResult(
                pages = listOf(ContentPage(0, emptyList(), 0)),
                totalPages = 1,
                config = config
            )
            _paginationResult = mutableStateOf(result)
            return result
        }

        // Split text into paragraphs for block-level measurement
        val blocks = text.split(Regex("\\n\\s*\\n"))
            .mapIndexed { index, paragraph ->
                val trimmed = paragraph.trim()
                if (trimmed.isNotEmpty()) {
                    ContentBlock.Paragraph(
                        index = index,
                        text = trimmed,
                        isBold = false,
                        spans = emptyList()
                    )
                } else {
                    null
                }
            }
            .filterNotNull()

        return paginateBlocks(blocks, config, screenWidthPx, screenHeightPx)
    }

    /**
     * Measures the height (in pixels) of a single ContentBlock.
     *
     * Uses StaticLayout to compute the exact rendered height for text blocks,
     * and fixed heights for non-text blocks (rules).
     *
     * For headings, uses a larger font size based on the heading level.
     * For quotes and list items, reduces usable width for the indent.
     */
    private fun measureBlockHeight(
        block: ContentBlock,
        config: TextRenderConfig,
        usableWidthPx: Int
    ): Int {
        val density = context.resources.displayMetrics.density

        return when (block) {
            is ContentBlock.Rule -> {
                // Fixed height for horizontal divider
                (RULE_HEIGHT_DP * density).toInt()
            }

            is ContentBlock.Heading -> {
                val sizeMultiplier = HEADING_SIZE_MULTIPLIERS[block.level] ?: 1.0f
                val headingFontSize = (config.fontSize * sizeMultiplier).toInt()
                val paint = createTextPaint(config, fontSizeOverride = headingFontSize)
                val text = block.text.ifBlank { return BLOCK_SPACING_DP.toInt() }
                val layout = StaticLayout.Builder
                    .obtain(text, 0, text.length, paint, usableWidthPx)
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(config.lineSpacingExtra, config.lineSpacingMultiplier)
                    .setIncludePad(false)
                    .build()
                val textHeight = layout.height
                val paddingPx = (HEADING_VERTICAL_PADDING_DP * density).toInt()
                textHeight + paddingPx
            }

            is ContentBlock.Quote -> {
                val paint = createTextPaint(config)
                val text = block.text.ifBlank { return BLOCK_SPACING_DP.toInt() }
                val indentPx = (QUOTE_LEFT_INDENT_DP * density).toInt()
                val width = usableWidthPx - indentPx
                val layout = StaticLayout.Builder
                    .obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(config.lineSpacingExtra, config.lineSpacingMultiplier)
                    .setIncludePad(false)
                    .build()
                layout.height + (PARAGRAPH_SPACING_DP * density).toInt()
            }

            is ContentBlock.ListItem -> {
                val paint = createTextPaint(config)
                val prefix = if (block.order > 0) "${block.order}. " else "• "
                val text = "$prefix${block.text}"
                val indentPx = (LIST_ITEM_LEFT_INDENT_DP * density).toInt()
                val width = usableWidthPx - indentPx
                val layout = StaticLayout.Builder
                    .obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(config.lineSpacingExtra, config.lineSpacingMultiplier)
                    .setIncludePad(false)
                    .build()
                layout.height + (BLOCK_SPACING_DP * density).toInt()
            }

            is ContentBlock.Paragraph -> {
                val paint = createTextPaint(
                    config,
                    boldOverride = block.isBold
                )
                val text = block.text.ifBlank { return BLOCK_SPACING_DP.toInt() }
                val layout = StaticLayout.Builder
                    .obtain(text, 0, text.length, paint, usableWidthPx)
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(config.lineSpacingExtra, config.lineSpacingMultiplier)
                    .setIncludePad(false)
                    .build()
                layout.height + (PARAGRAPH_SPACING_DP * density).toInt()
            }
        }
    }

    /**
     * Distributes ContentBlocks across pages based on their measured heights.
     *
     * Blocks are assigned to pages greedily: each page gets as many blocks
     * as fit within [usableHeightPx]. A single block that exceeds the page
     * height is placed on its own page (not split).
     */
    private fun distributeBlocksToPages(
        blocks: List<ContentBlock>,
        blockHeights: List<Int>,
        usableHeightPx: Int
    ): List<ContentPage> {
        val pages = mutableListOf<ContentPage>()
        var currentPageBlocks = mutableListOf<ContentBlock>()
        var currentPageHeight = 0
        var pageIndex = 0
        var charOffset = 0

        for (i in blocks.indices) {
            val block = blocks[i]
            val height = blockHeights[i]

            // Does this block fit on the current page?
            if (currentPageBlocks.isEmpty() || currentPageHeight + height <= usableHeightPx) {
                currentPageBlocks.add(block)
                currentPageHeight += height
            } else {
                // Flush current page and start a new one with this block
                if (currentPageBlocks.isNotEmpty()) {
                    pages.add(
                        ContentPage(
                            pageIndex = pageIndex++,
                            blocks = currentPageBlocks.toList(),
                            startCharOffset = charOffset
                        )
                    )
                    charOffset += currentPageBlocks.sumOf { it.text.length }
                }
                currentPageBlocks = mutableListOf(block)
                currentPageHeight = height
            }
        }

        // Flush remaining blocks
        if (currentPageBlocks.isNotEmpty()) {
            pages.add(
                ContentPage(
                    pageIndex = pageIndex,
                    blocks = currentPageBlocks.toList(),
                    startCharOffset = charOffset
                )
            )
        }

        return pages.ifEmpty {
            listOf(ContentPage(0, emptyList(), 0))
        }
    }

    /**
     * Create a TextPaint configured with the current text settings.
     *
     * @param fontSizeOverride If set, use this font size instead of config.fontSize.
     * @param boldOverride If true, apply bold typeface.
     */
    private fun createTextPaint(
        config: TextRenderConfig,
        fontSizeOverride: Int? = null,
        boldOverride: Boolean = false
    ): TextPaint {
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.textSize = spToPx((fontSizeOverride ?: config.fontSize).toFloat())
        paint.color = android.graphics.Color.BLACK

        val baseTypeface = if (config.customTypeface != null) {
            config.customTypeface
        } else {
            when (config.fontFamily) {
                FontFamily.Serif -> Typeface.SERIF
                FontFamily.SansSerif -> Typeface.SANS_SERIF
                FontFamily.Monospace -> Typeface.MONOSPACE
                else -> Typeface.DEFAULT
            }
        }

        paint.typeface = if (boldOverride) {
            Typeface.create(baseTypeface, Typeface.BOLD)
        } else {
            baseTypeface
        }

        return paint
    }

    /**
     * Load a custom TTF/OTF font from assets.
     */
    fun loadCustomFont(assetPath: String): Typeface? {
        return try {
            Typeface.createFromAsset(context.assets, assetPath)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load a custom TTF/OTF font from a file path.
     */
    fun loadCustomFontFromFile(filePath: String): Typeface? {
        return try {
            Typeface.createFromFile(filePath)
        } catch (e: Exception) {
            null
        }
    }

    private fun dpToPx(dp: Dp): Int {
        return (dp.value * context.resources.displayMetrics.density).toInt()
    }

    private fun spToPx(sp: Float): Float {
        return sp * context.resources.displayMetrics.scaledDensity
    }
}
