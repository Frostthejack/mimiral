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
 * Represents a single page of paginated text.
 */
data class PageBreak(
    val pageIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val text: String
)

/**
 * Result of pagination containing all pages and metadata.
 */
data class PaginationResult(
    val pages: List<PageBreak>,
    val totalPages: Int,
    val config: TextRenderConfig
)

/**
 * PaginationEngine uses Android's StaticLayout to compute page boundaries
 * based on screen dimensions, font settings, and margins.
 */
class PaginationEngine(private val context: Context) {

    private var _paginationResult = mutableStateOf<PaginationResult?>(null)
    val paginationResult: PaginationResult? get() = _paginationResult.value

    /**
     * Paginate the given text using the provided configuration and screen dimensions.
     */
    fun paginate(
        text: String,
        config: TextRenderConfig,
        screenWidthPx: Int,
        screenHeightPx: Int
    ): PaginationResult {
        if (text.isBlank()) {
            val result = PaginationResult(
                pages = listOf(PageBreak(0, 0, 0, "")),
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
                pages = listOf(PageBreak(0, 0, text.length, text)),
                totalPages = 1,
                config = config
            )
            _paginationResult = mutableStateOf(result)
            return result
        }

        val paint = createTextPaint(config)
        val pages = computePages(text, paint, usableWidth, usableHeight, config)

        val result = PaginationResult(
            pages = pages,
            totalPages = pages.size,
            config = config
        )
        _paginationResult = mutableStateOf(result)
        return result
    }

    /**
     * Compute page breaks using StaticLayout line-based pagination.
     */
    private fun computePages(
        text: String,
        paint: TextPaint,
        usableWidth: Int,
        usableHeight: Int,
        config: TextRenderConfig
    ): List<PageBreak> {
        val pages = mutableListOf<PageBreak>()
        var currentOffset = 0
        var pageIndex = 0

        while (currentOffset < text.length) {
            val remainingText = text.substring(currentOffset)
            val layout = StaticLayout.Builder
                .obtain(remainingText, 0, remainingText.length, paint, usableWidth)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(config.lineSpacingExtra, config.lineSpacingMultiplier)
                .setIncludePad(false)
                .build()

            if (layout.lineCount == 0) {
                pages.add(PageBreak(pageIndex, currentOffset, text.length, remainingText))
                break
            }

            // Calculate how many lines fit in the usable height
            var lineCount = 0
            var lineHeightSum = 0f
            for (i in 0 until layout.lineCount) {
                val lineBottom = layout.getLineBottom(i)
                val lineTop = layout.getLineTop(i)
                val lineHeight = lineBottom - lineTop
                if (lineHeightSum + lineHeight > usableHeight && lineCount > 0) {
                    break
                }
                lineHeightSum += lineHeight
                lineCount++
            }

            if (lineCount == 0) {
                lineCount = 1
            }

            // Find the character offset at the end of the last fitting line
            val lastLine = lineCount - 1
            val endLineOffset = layout.getLineEnd(lastLine)
            val pageEndOffset = currentOffset + endLineOffset

            val clampedEnd = pageEndOffset.coerceAtMost(text.length)
            val pageText = text.substring(currentOffset, clampedEnd)

            pages.add(PageBreak(pageIndex, currentOffset, clampedEnd, pageText))

            currentOffset = clampedEnd
            pageIndex++

            // Safety: prevent infinite loop
            if (endLineOffset == 0) {
                break
            }
        }

        return pages.ifEmpty {
            listOf(PageBreak(0, 0, text.length, text))
        }
    }

    /**
     * Create a TextPaint configured with the current text settings.
     */
    private fun createTextPaint(config: TextRenderConfig): TextPaint {
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.textSize = spToPx(config.fontSize.toFloat())
        paint.color = android.graphics.Color.BLACK

        if (config.customTypeface != null) {
            paint.typeface = config.customTypeface
        } else {
            paint.typeface = when (config.fontFamily) {
                FontFamily.Serif -> Typeface.SERIF
                FontFamily.SansSerif -> Typeface.SANS_SERIF
                FontFamily.Monospace -> Typeface.MONOSPACE
                else -> Typeface.DEFAULT
            }
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
