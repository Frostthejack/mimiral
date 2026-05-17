package com.mimiral.app.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * PDF renderer wrapper around Android's platform PdfRenderer.
 *
 * Provides page rendering, text extraction, password handling,
 * and on-demand page rendering for large PDFs.
 *
 * Supports two constructors:
 * - PdfRenderer(context) — for use with open(filePath)
 * - PdfRenderer(file) — for direct file opening (backward compat)
 */
class PdfRenderer : AutoCloseable {

    private var pdfRenderer: AndroidPdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var openedFilePath: String? = null

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
        // Context available for future use (e.g., content resolver)
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
     */
    fun open(filePath: String): Result<Unit> {
        return try {
            close()
            val file = File(filePath)
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
     * Extract text from a page using the platform PdfRenderer.Page.getText() API (API 26+).
     *
     * @param pageIndex Zero-based page index
     * @return PageText containing extracted text and metadata
     */
    suspend fun extractPageText(pageIndex: Int): PageText = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext PageText("", false, pageIndex, 0, 0)
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
            return@withContext PageText("", false, pageIndex, 0, 0)
        }

        try {
            val page = renderer.openPage(pageIndex)
            try {
                val text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        page.text
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    ""
                }
                PageText(
                    text = text,
                    hasText = text.isNotBlank(),
                    pageIndex = pageIndex,
                    pageWidth = page.width,
                    pageHeight = page.height
                )
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            PageText("", false, pageIndex, 0, 0)
        }
    }

    /**
     * Check if a page has extractable text content.
     */
    suspend fun hasTextOnPage(pageIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext false
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext false

        try {
            val page = renderer.openPage(pageIndex)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        page.text.isNotBlank()
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    false
                }
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            false
        }
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
