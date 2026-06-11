package com.mimiral.app.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF renderer wrapper around Android's platform PdfRenderer.
 *
 * Provides page rendering, margin detection, and on-demand page rendering for large PDFs.
 * Text extraction is powered by Apache PDFBox (PDFTextStripper).
 * Structured extraction (headings, bold, quotes) is powered by PdfStructuredExtractor.
 *
 * Supports two constructors:
 * - PdfRenderer(context) — for use with open(filePath)
 * - PdfRenderer(file) — for direct file opening (backward compat)
 */
class PdfRenderer : AutoCloseable {

    /** Structured extractor for font-metadata-based heading detection. */
    private val structuredExtractor = PdfStructuredExtractor()

    private var pdfRenderer: AndroidPdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var openedFilePath: String? = null
    private var cachedFile: File? = null
    private var context: Context? = null

    /** Total number of pages in the opened document. */
    val pageCount: Int
        get() = pdfRenderer?.pageCount ?: 0

    /** Whether the document has extractable text content. */
    var hasTextContent: Boolean = true
        private set

    /** Whether the document is currently open. */
    val isOpen: Boolean get() = pdfRenderer != null

    // ---- Constructors ----

    /**
     * Constructor for use with open(filePath) pattern.
     */
    constructor(context: Context) {
        // Context available for content resolver and cache access
        this.context = context.applicationContext
    }

    /**
     * Constructor for direct file opening.
     * Immediately opens the PDF file.
     */
    constructor(file: File) {
        try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor = fd
            val renderer = AndroidPdfRenderer(fd)
            pdfRenderer = renderer
            openedFilePath = file.absolutePath
            hasTextContent = true // Assume text; UI layer will detect
        } catch (e: Exception) {
            throw e
        }
    }

    // ---- Open/Close ----

    /**
     * Open a PDF file from a file path.
     * On Android 10+ with scoped storage, direct File access may fail for shared
     * storage paths. This method falls back to copying the file to app-private
     * cache via ContentResolver when direct access is denied.
     */
    fun open(filePath: String): Result<Unit> {
        return try {
            close()
            val file = resolveFile(filePath)
            if (!file.exists()) {
                return Result.failure(FileNotFoundException("PDF file not found: $filePath"))
            }
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor = fd
            try {
                val renderer = AndroidPdfRenderer(fd)
                pdfRenderer = renderer
                openedFilePath = filePath
                hasTextContent = true
                Result.success(Unit)
            } catch (e: Exception) {
                fd.close()
                fileDescriptor = null
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolve a file path to a usable File object.
     * If the path is directly accessible (app-private storage), returns it as-is.
     * If not (shared storage on Android 10+), copies to app-private cache first.
     */
    private fun resolveFile(filePath: String): File {
        val directFile = File(filePath)
        if (directFile.exists()) return directFile

        // Direct access failed — try copying from shared storage via ContentResolver
        val ctx = context ?: return directFile
        try {
            val uri = android.net.Uri.parse(filePath)
            val inputStream = ctx.contentResolver.openInputStream(uri) ?: return directFile
            val cacheFile = File(ctx.cacheDir, "pdf_cache_${filePath.hashCode().toString(16)}.pdf")
            FileOutputStream(cacheFile).use { out ->
                inputStream.copyTo(out)
            }
            inputStream.close()
            cachedFile = cacheFile
            return cacheFile
        } catch (e: Exception) {
            return directFile
        }
    }

    // ---- Rendering ----

    /**
     * Render a page to a bitmap at the specified DPI.
     *
     * @param pageIndex Zero-based page index
     * @param dpi Target DPI (default 160)
     * @return Rendered bitmap, or null if rendering failed
     */
    fun renderPage(pageIndex: Int, dpi: Int = 160): Bitmap? {
        val renderer = pdfRenderer ?: return null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null

        return try {
            val page = renderer.openPage(pageIndex)
            try {
                val width = (page.width * dpi / 72f).toInt().coerceAtLeast(1)
                val height = (page.height * dpi / 72f).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Render a page to a bitmap at the specified dimensions.
     */
    fun renderPageAtSize(pageIndex: Int, width: Int, height: Int): Bitmap? {
        val renderer = pdfRenderer ?: return null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null

        return try {
            val page = renderer.openPage(pageIndex)
            try {
                val bitmap = Bitmap.createBitmap(
                    width.coerceAtLeast(1),
                    height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---- Text Extraction ----

    /**
     * Extract text from a page.
     *
     * NOTE: Android's PdfRenderer.Page does not have a text property.
     * Text extraction requires a separate library (e.g., PDFBox).
     * Currently returns empty text for Phase 1.
     *
     * @param pageIndex Zero-based page index
     * @return PageText containing extracted text and metadata
     */
    suspend fun extractPageText(pageIndex: Int): PageText = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext PageText("", false, pageIndex, 0, 0)
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
            return@withContext PageText("", false, pageIndex, 0, 0)
        }

        val filePath = openedFilePath ?: return@withContext PageText(
            "",
            false,
            pageIndex,
            0,
            0
        )

        try {
            val page = renderer.openPage(pageIndex)
            val pageWidth = page.width
            val pageHeight = page.height
            page.close()

            // Use PDFBox for text extraction — resolve file for scoped storage
            val file = resolveFile(filePath)
            if (!file.exists()) {
                PageText("", false, pageIndex, pageWidth, pageHeight)
            } else {
                var result = PageText("", false, pageIndex, pageWidth, pageHeight)
                PDDocument.load(file).use { document ->
                    if (pageIndex < document.numberOfPages) {
                        val stripper = PDFTextStripper()
                        stripper.startPage = pageIndex + 1 // 1-indexed
                        stripper.endPage = pageIndex + 1
                        val text = stripper.getText(document).trim()
                        result = PageText(
                            text = text,
                            hasText = text.isNotEmpty(),
                            pageIndex = pageIndex,
                            pageWidth = pageWidth,
                            pageHeight = pageHeight
                        )
                    }
                }
                result
            }
        } catch (e: Exception) {
            PageText("", false, pageIndex, 0, 0)
        }
    }

    /**
     * Check if a page has extractable text content.
     */
    suspend fun hasTextOnPage(pageIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val result = extractPageText(pageIndex)
        result.hasText
    }

    /**
     * Extract structured content blocks from a page using font metadata.
     *
     * Unlike [extractPageText] which returns raw text, this method returns
     * [ContentBlock] instances (Heading, Paragraph, Quote, etc.) detected
     * from font sizes, font names, and text patterns.
     *
     * @param pageIndex Zero-based page index.
     * @return List of [ContentBlock] for the page, or empty list on error.
     */
    suspend fun extractStructuredPageText(pageIndex: Int): List<ContentBlock> =
        withContext(Dispatchers.IO) {
            val renderer = pdfRenderer ?: return@withContext emptyList()
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext emptyList()

            val filePath = openedFilePath ?: return@withContext emptyList()
            val file = resolveFile(filePath)
            if (!file.exists()) return@withContext emptyList()

            structuredExtractor.extractPage(file, pageIndex)
        }

    /**
     * Get the dimensions of a page in PostScript points (1/72 inch).
     */
    fun getPageSize(pageIndex: Int): Pair<Int, Int>? {
        val renderer = pdfRenderer ?: return null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
        val page = renderer.openPage(pageIndex)
        try {
            return Pair(page.width, page.height)
        } finally {
            page.close()
        }
    }

    /**
     * Render a page to a bitmap at the specified DPI with optional margin cropping.
     *
     * @param pageIndex Zero-based page index
     * @param dpi Target DPI (default 160)
     * @param cropMargins MarginCrop specifying how much to crop from each side (0-50%)
     * @return Rendered bitmap, or null if rendering failed
     */
    suspend fun renderPage(
        pageIndex: Int,
        dpi: Int = 160,
        cropMargins: MarginCrop = MarginCrop.NONE
    ): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        try {
            val page = renderer.openPage(pageIndex)
            try {
                val width = (page.width * dpi / 72f).toInt().coerceAtLeast(1)
                val height = (page.height * dpi / 72f).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                if (cropMargins.hasCrop()) {
                    cropBitmap(bitmap, cropMargins)
                } else {
                    bitmap
                }
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Render a page to a bitmap at the specified dimensions with optional margin cropping.
     */
    suspend fun renderPageAtSize(
        pageIndex: Int,
        width: Int,
        height: Int,
        cropMargins: MarginCrop = MarginCrop.NONE
    ): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        try {
            val page = renderer.openPage(pageIndex)
            try {
                val bitmap = Bitmap.createBitmap(
                    width.coerceAtLeast(1),
                    height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                if (cropMargins.hasCrop()) {
                    cropBitmap(bitmap, cropMargins)
                } else {
                    bitmap
                }
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Detect margins on a page by scanning for non-white borders.
     * Returns a MarginCrop with the detected margins as percentages.
     */
    suspend fun detectMargins(
        pageIndex: Int,
        sampleDpi: Int = 72,
        threshold: Int = 240
    ): MarginCrop = withContext(Dispatchers.Default) {
        val renderer = pdfRenderer ?: return@withContext MarginCrop.NONE
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext MarginCrop.NONE

        try {
            val page = renderer.openPage(pageIndex)
            try {
                val width = (page.width * sampleDpi / 72f).toInt().coerceAtLeast(1)
                val height = (page.height * sampleDpi / 72f).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val margins = findContentBounds(bitmap, threshold)
                bitmap.recycle()

                MarginCrop(
                    left = (margins.left.toFloat() / width * 100).toInt().coerceIn(0, 50),
                    top = (margins.top.toFloat() / height * 100).toInt().coerceIn(0, 50),
                    right = ((width - margins.right).toFloat() / width * 100).toInt().coerceIn(
                        0,
                        50
                    ),
                    bottom = ((height - margins.bottom).toFloat() / height * 100).toInt().coerceIn(
                        0,
                        50
                    )
                )
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            MarginCrop.NONE
        }
    }

    /**
     * Auto-detect margins for a scanned PDF by sampling multiple pages.
     * Returns the maximum margins found across sampled pages.
     */
    suspend fun autoDetectScannedMargins(
        samplePages: Int = 3,
        threshold: Int = 240
    ): MarginCrop = withContext(Dispatchers.Default) {
        val renderer = pdfRenderer ?: return@withContext MarginCrop.NONE
        val pageCount = renderer.pageCount
        if (pageCount == 0) return@withContext MarginCrop.NONE

        val pagesToSample = minOf(samplePages, pageCount)
        val step = if (pagesToSample > 1) pageCount / pagesToSample else 0

        var maxLeft = 0
        var maxTop = 0
        var maxRight = 0
        var maxBottom = 0

        for (i in 0 until pagesToSample) {
            val pageIdx = (i * step).coerceAtMost(pageCount - 1)
            val margins = detectMargins(pageIdx, sampleDpi = 72, threshold)
            maxLeft = maxOf(maxLeft, margins.left)
            maxTop = maxOf(maxTop, margins.top)
            maxRight = maxOf(maxRight, margins.right)
            maxBottom = maxOf(maxBottom, margins.bottom)
        }

        MarginCrop(
            left = maxLeft.coerceIn(0, 50),
            top = maxTop.coerceIn(0, 50),
            right = maxRight.coerceIn(0, 50),
            bottom = maxBottom.coerceIn(0, 50)
        )
    }

    // ---- Private helpers ----

    private fun findContentBounds(bitmap: Bitmap, threshold: Int): android.graphics.Rect {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Find top margin
        var top = 0
        outer@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (!isWhite(pixels[y * width + x], threshold)) {
                    top = y
                    break@outer
                }
            }
        }

        // Find bottom margin
        var bottom = height
        outer@ for (y in height - 1 downTo top) {
            for (x in 0 until width) {
                if (!isWhite(pixels[y * width + x], threshold)) {
                    bottom = y + 1
                    break@outer
                }
            }
        }

        // Find left margin
        var left = 0
        outer@ for (x in 0 until width) {
            for (y in top until bottom) {
                if (!isWhite(pixels[y * width + x], threshold)) {
                    left = x
                    break@outer
                }
            }
        }

        // Find right margin
        var right = width
        outer@ for (x in width - 1 downTo left) {
            for (y in top until bottom) {
                if (!isWhite(pixels[y * width + x], threshold)) {
                    right = x + 1
                    break@outer
                }
            }
        }

        return android.graphics.Rect(left, top, right, bottom)
    }

    private fun isWhite(pixel: Int, threshold: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return r >= threshold && g >= threshold && b >= threshold
    }

    private fun cropBitmap(bitmap: Bitmap, crop: MarginCrop): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val left = (width * crop.left / 100f).toInt()
        val top = (height * crop.top / 100f).toInt()
        val right = (width * (100 - crop.right) / 100f).toInt()
        val bottom = (height * (100 - crop.bottom) / 100f).toInt()

        val cropWidth = (right - left).coerceAtLeast(1)
        val cropHeight = (bottom - top).coerceAtLeast(1)

        return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
    }

    // ---- Lifecycle ----

    override fun close() {
        try {
            pdfRenderer?.close()
        } catch (_: Exception) {}
        pdfRenderer = null

        try {
            fileDescriptor?.close()
        } catch (_: Exception) {}
        fileDescriptor = null

        openedFilePath = null
    }
}

/**
 * Represents extracted text from a PDF page.
 */
data class PageText(
    val text: String,
    val hasText: Boolean,
    val pageIndex: Int,
    val pageWidth: Int,
    val pageHeight: Int
)

/**
 * Represents the current text selection state on a PDF page.
 */
data class SelectionState(
    val isActive: Boolean = false,
    val pageIndex: Int = -1,
    val selectedText: String = "",
    val bounds: List<RectF> = emptyList(),
    val startHandlePosition: android.graphics.PointF? = null,
    val endHandlePosition: android.graphics.PointF? = null
) {
    val hasSelection: Boolean get() = isActive && selectedText.isNotEmpty()
}

/**
 * Exception thrown when a PDF is password-protected.
 */
class PdfPasswordException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Represents margin crop values as percentages (0-50) for each side.
 */
data class MarginCrop(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    fun hasCrop(): Boolean = left > 0 || top > 0 || right > 0 || bottom > 0

    companion object {
        val NONE = MarginCrop(0, 0, 0, 0)
        fun uniform(percent: Int) = MarginCrop(percent, percent, percent, percent)
    }
}
