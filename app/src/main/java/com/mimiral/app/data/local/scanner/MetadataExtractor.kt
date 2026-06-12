package com.mimiral.app.data.local.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.reader.CoverResult
import com.mimiral.app.data.reader.EpubParser
import com.mimiral.app.data.reader.EpubState
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao
) {
    companion object {
        private const val TAG = "MetadataExtractor"
        private const val COVERS_DIR = "covers"
        private const val MAX_COVER_DIM = 512
    }

    suspend fun extractAndUpdate(bookId: Long, pending: PendingBook) {
        withContext(Dispatchers.IO) {
            try {
                val format = pending.format.uppercase()
                Log.d(
                    TAG,
                    "Extracting metadata for bookId=$bookId format=$format " +
                        "file=${pending.filePath}"
                )

                val coverDir = File(context.filesDir, COVERS_DIR)
                if (!coverDir.exists()) coverDir.mkdirs()

                val updatedBook = when (format) {
                    "EPUB" -> extractEpubMetadata(bookId, pending, coverDir)
                    "PDF" -> extractPdfMetadata(bookId, pending, coverDir)
                    else -> extractBasicMetadata(bookId, pending)
                }

                if (updatedBook != null) {
                    bookDao.updateBook(updatedBook)
                    Log.d(
                        TAG,
                        "Metadata updated for bookId=$bookId title='${updatedBook.title}' " +
                            "author='${updatedBook.author}'"
                    )
                } else {
                    Log.w(TAG, "No metadata extracted for bookId=$bookId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract metadata for bookId=$bookId", e)
            }
        }
    }

    private fun extractBasicMetadata(bookId: Long, pending: PendingBook): BookEntity {
        val title = pending.fileName.substringBeforeLast('.').trim()
        return BookEntity(
            id = bookId.toInt(),
            filePath = pending.filePath,
            title = title,
            author = null,
            description = null,
            coverPath = null,
            format = pending.format,
            fileSize = pending.fileSize,
            dateAdded = System.currentTimeMillis(),
            dateModified = pending.dateModified * 1000,
            source = "LOCAL"
        )
    }

    private suspend fun extractEpubMetadata(
        bookId: Long,
        pending: PendingBook,
        coverDir: File
    ): BookEntity {
        val parser = EpubParser(context)
        val state = parser.openFile(pending.filePath)

        if (state !is EpubState.Loaded) {
            Log.w(TAG, "EpubParser failed for ${pending.filePath}: $state")
            parser.close()
            return extractBasicMetadata(bookId, pending)
        }

        val title = state.title.ifBlank {
            pending.fileName.substringBeforeLast('.').trim()
        }
        val author = state.author
        val description = state.description
        val coverPath = extractAndSaveEpubCover(parser, bookId, coverDir)

        parser.close()

        return BookEntity(
            id = bookId.toInt(),
            filePath = pending.filePath,
            title = title,
            author = author,
            description = description,
            coverPath = coverPath,
            format = pending.format,
            fileSize = pending.fileSize,
            dateAdded = System.currentTimeMillis(),
            dateModified = pending.dateModified * 1000,
            source = "LOCAL"
        )
    }

    private suspend fun extractAndSaveEpubCover(
        parser: EpubParser,
        bookId: Long,
        coverDir: File
    ): String? {
        val coverResult = parser.extractCover()
        if (coverResult !is CoverResult.Success) {
            Log.d(TAG, "No cover found for bookId=$bookId")
            return null
        }

        return try {
            val coverFile = File(coverDir, "book_$bookId.jpg")
            val bitmap = coverResult.bitmap
            val scaledBitmap = scaleBitmap(bitmap, MAX_COVER_DIM)
            FileOutputStream(coverFile).use {
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)
            }
            if (scaledBitmap != bitmap) scaledBitmap.recycle()
            Log.d(TAG, "Cover saved for bookId=$bookId: ${coverFile.absolutePath}")
            coverFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cover for bookId=$bookId", e)
            null
        }
    }

    private fun extractPdfMetadata(
        bookId: Long,
        pending: PendingBook,
        coverDir: File
    ): BookEntity {
        val file = resolveFile(pending.filePath) ?: run {
            Log.w(TAG, "Cannot resolve PDF file: ${pending.filePath}")
            return extractBasicMetadata(bookId, pending)
        }

        return try {
            PDDocument.load(file).use { document ->
                val info = document.documentInformation
                val title = info.title?.takeIf { it.isNotBlank() }
                    ?: pending.fileName.substringBeforeLast('.').trim()
                val author = info.author?.takeIf { it.isNotBlank() }
                val description = info.subject?.takeIf { it.isNotBlank() }

                val coverPath = extractPdfCover(file, bookId, coverDir)

                BookEntity(
                    id = bookId.toInt(),
                    filePath = pending.filePath,
                    title = title,
                    author = author,
                    description = description,
                    coverPath = coverPath,
                    format = pending.format,
                    fileSize = pending.fileSize,
                    dateAdded = System.currentTimeMillis(),
                    dateModified = pending.dateModified * 1000,
                    source = "LOCAL"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract PDF metadata for ${pending.filePath}", e)
            extractBasicMetadata(bookId, pending)
        }
    }

    private fun extractPdfCover(file: File, bookId: Long, coverDir: File): String? {
        var fd: ParcelFileDescriptor? = null
        var renderer: AndroidPdfRenderer? = null
        return try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = AndroidPdfRenderer(fd)
            if (renderer.pageCount == 0) {
                return null
            }
            val page = renderer.openPage(0)
            val width = (page.width * 160 / 72f).toInt().coerceAtLeast(1)
            val height = (page.height * 160 / 72f).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(
                bitmap,
                null,
                null,
                AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )
            page.close()

            val scaledBitmap = scaleBitmap(bitmap, MAX_COVER_DIM)
            val coverFile = File(coverDir, "book_$bookId.jpg")
            FileOutputStream(coverFile).use {
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)
            }
            if (scaledBitmap != bitmap) scaledBitmap.recycle()
            Log.d(TAG, "PDF cover saved for bookId=$bookId: ${coverFile.absolutePath}")
            coverFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract PDF cover for bookId=$bookId", e)
            null
        } finally {
            try {
                renderer?.close()
            } catch (_: Exception) { }
            try {
                fd?.close()
            } catch (_: Exception) { }
        }
    }

    private fun resolveFile(filePath: String): File? {
        val directFile = File(filePath)
        if (directFile.exists()) return directFile

        // Try ContentResolver for scoped storage
        return try {
            val uri = android.net.Uri.parse(filePath)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val cacheFile = File(
                context.cacheDir,
                "meta_${filePath.hashCode().toString(16)}"
            )
            FileOutputStream(cacheFile).use { out ->
                inputStream.copyTo(out)
            }
            inputStream.close()
            cacheFile
        } catch (e: Exception) {
            Log.w(TAG, "Cannot resolve file via ContentResolver: $filePath", e)
            null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
