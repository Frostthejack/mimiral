package com.mimiral.app.data.local.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.os.ParcelFileDescriptor
import com.mimiral.app.data.local.dao.BookDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.streamer.Streamer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * Result of a metadata extraction operation.
 */
sealed class MetadataResult {
    /** Metadata was successfully extracted. */
    data class Success(
        val title: String,
        val author: String? = null,
        val description: String? = null,
        val coverPath: String? = null
    ) : MetadataResult()

    /** Extraction failed. */
    data class Error(val message: String, val cause: Throwable? = null) : MetadataResult()
}

/**
 * Lazy metadata extractor for discovered ebook files.
 *
 * Extracts title, author, description, and cover image from supported formats:
 * - EPUB: Parses the OPF manifest via direct ZIP + Readium Streamer fallback
 * - PDF: Reads document info dictionary for title/author, rasterizes page 1 as cover
 * - FB2: Parses FictionBook XML for title-info/author/annotation
 * - MOBI/AZW3: Parses PalmDB header + EXTH records for title/author
 * - Other: Falls back to filename as title
 *
 * Extracted covers are saved to the app's internal files directory under
 * "covers/" and the path is stored in the database.
 */
@Singleton
class MetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao
) {
    companion object {
        /** Subdirectory within app files for storing cover images. */
        private const val COVERS_DIR = "covers"

        /** Cover image quality (0-100) for JPEG compression. */
        private const val COVER_QUALITY = 85

        /** Maximum cover dimension in pixels. */
        private const val MAX_COVER_DIMENSION = 512

        // Format constants
        private const val FORMAT_EPUB = "EPUB"
        private const val FORMAT_PDF = "PDF"
        private const val FORMAT_FB2 = "FB2"
        private const val FORMAT_MOBI = "MOBI"
        private const val FORMAT_AZW = "AZW"
        private const val FORMAT_AZW3 = "AZW3"

        // MOBI EXTH record types
        private const val EXTH_TITLE = 503
        private const val EXTH_AUTHOR = 100
        private const val EXTH_DESCRIPTION = 101
        private const val EXTH_HEADER_MAGIC = 0x45585448 // "EXTH"
    }

    /**
     * Extracts metadata for a book and updates the database entry.
     *
     * This is the main entry point called by [FileScanner] after a new
     * file is inserted into the database.
     *
     * @param bookId The database ID of the book to update
     * @param pending The pending book data from the scanner
     */
    suspend fun extractAndUpdate(bookId: Long, pending: PendingBook) {
        withContext(Dispatchers.IO) {
            try {
                val result = when (pending.format) {
                    FORMAT_EPUB -> extractEpubMetadata(pending.filePath)
                    FORMAT_PDF -> extractPdfMetadata(pending.filePath)
                    FORMAT_FB2 -> extractFb2Metadata(pending.filePath)
                    FORMAT_MOBI, FORMAT_AZW, FORMAT_AZW3 -> extractMobiMetadata(pending.filePath)
                    else -> extractGenericMetadata(pending)
                }

                when (result) {
                    is MetadataResult.Success -> {
                        val existingBook = bookDao.getBookById(bookId.toInt())
                        if (existingBook != null) {
                            val updated = existingBook.copy(
                                title = result.title,
                                author = result.author,
                                coverPath = result.coverPath
                            )
                            bookDao.updateBook(updated)
                        }
                    }
                    is MetadataResult.Error -> {
                        // Keep the placeholder title; metadata can be retried later
                    }
                }
            } catch (e: Exception) {
                // Extraction failed; book remains with placeholder metadata
            }
        }
    }

    // -------------------------------------------------------------------------
    // EPUB
    // -------------------------------------------------------------------------

    /**
     * Extracts metadata from an EPUB file.
     *
     * EPUBs are ZIP archives containing an OPF manifest. This method:
     * 1. Opens the EPUB as a ZIP file
     * 2. Locates the OPF file via the META-INF/container.xml
     * 3. Parses the OPF for dc:title, dc:creator, dc:description
     * 4. Extracts the cover image if referenced in the manifest
     *
     * Falls back to Readium Streamer for more complex EPUBs.
     */
    private fun extractEpubMetadata(filePath: String): MetadataResult {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return MetadataResult.Error("File not found: $filePath")
            }

            // Try direct ZIP parsing first (faster than Readium for basic metadata)
            val zipFile = ZipFile(file)
            try {
                // Find the OPF path from META-INF/container.xml
                val containerEntry = zipFile.getEntry("META-INF/container.xml")
                if (containerEntry == null) {
                    return extractEpubViaReadium(file)
                }

                val containerXml = zipFile.getInputStream(containerEntry).bufferedReader().readText()
                val opfPath = parseOpfPathFromContainer(containerXml)
                    ?: return extractEpubViaReadium(file)

                // Parse the OPF manifest
                val opfEntry = zipFile.getEntry(opfPath)
                if (opfEntry == null) {
                    return extractEpubViaReadium(file)
                }

                val opfXml = zipFile.getInputStream(opfEntry).bufferedReader().readText()
                val title = extractXmlTagContent(opfXml, "dc:title")
                    ?: file.nameWithoutExtension
                val author = extractXmlTagContent(opfXml, "dc:creator")
                val description = extractXmlTagContent(opfXml, "dc:description")

                // Try to extract cover image
                val coverPath = extractEpubCover(zipFile, opfXml, opfPath, file.nameWithoutExtension)

                MetadataResult.Success(
                    title = title,
                    author = author,
                    description = description,
                    coverPath = coverPath
                )
            } finally {
                zipFile.close()
            }
        } catch (e: Exception) {
            MetadataResult.Error("Failed to extract EPUB metadata: ${e.message}", e)
        }
    }

    /**
     * Fallback EPUB extraction using Readium Streamer.
     * Handles EPUBs with complex structures that direct ZIP parsing can't handle.
     */
    private fun extractEpubViaReadium(file: File): MetadataResult {
        return try {
            val streamer = Streamer(context)
            val publication = streamer.open(file, allowUserInteraction = false).getOrNull()
                ?: return MetadataResult.Error("Readium could not open EPUB")

            val metadata = publication.metadata
            val title = metadata.title ?: file.nameWithoutExtension
            val author = metadata.authors.firstOrNull()?.name
            val description = metadata.description

            // Try to extract cover via Readium
            val coverPath = try {
                val coverBitmap = publication.cover()
                if (coverBitmap != null) {
                    saveCoverBitmap(coverBitmap, file.nameWithoutExtension)
                } else null
            } catch (_: Exception) {
                null
            }

            MetadataResult.Success(
                title = title,
                author = author,
                description = description,
                coverPath = coverPath
            )
        } catch (e: Exception) {
            MetadataResult.Error("Readium extraction failed: ${e.message}", e)
        }
    }

    /**
     * Extracts the OPF file path from META-INF/container.xml.
     */
    private fun parseOpfPathFromContainer(containerXml: String): String? {
        val regex = Regex("<rootfile[^>]+full-path\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        return regex.find(containerXml)?.groupValues?.get(1)
    }

    /**
     * Extracts the text content of an XML tag, handling namespaces.
     */
    private fun extractXmlTagContent(xml: String, tagName: String): String? {
        // Try with namespace prefix
        val nsRegex = Regex("<[^:]*:$tagName[^>]*>([^<]+)</[^:]*:$tagName>", RegexOption.IGNORE_CASE)
        val nsMatch = nsRegex.find(xml)
        if (nsMatch != null) return nsMatch.groupValues[1].trim()

        // Try without namespace prefix
        val plainRegex = Regex("<$tagName[^>]*>([^<]+)</$tagName>", RegexOption.IGNORE_CASE)
        val plainMatch = plainRegex.find(xml)
        if (plainMatch != null) return plainMatch.groupValues[1].trim()

        return null
    }

    /**
     * Extracts the cover image from an EPUB ZIP archive.
     *
     * Looks for a cover image referenced in the OPF manifest's
     * <meta name="cover"> item or the first <item> with media-type image/*.
     */
    private fun extractEpubCover(
        zipFile: ZipFile,
        opfXml: String,
        opfPath: String,
        bookName: String
    ): String? {
        return try {
            // Find cover item ID from OPF meta tag
            val coverIdRegex = Regex("<meta[^>]+name\\s*=\\s*[\"']cover[\"'][^>]+content\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            val coverId = coverIdRegex.find(opfXml)?.groupValues?.get(1)

            // Find the href for the cover item
            val coverHref = if (coverId != null) {
                val itemRegex = Regex("<item[^>]+id\\s*=\\s*[\"']${Regex.escape(coverId)}[\"'][^>]+href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                itemRegex.find(opfXml)?.groupValues?.get(1)
            } else {
                // Fallback: find first image item
                val imgRegex = Regex("<item[^>]+media-type\\s*=\\s*[\"']image/(jpeg|png|gif|svg\\+xml)[\"'][^>]+href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                imgRegex.find(opfXml)?.groupValues?.get(2)
            }

            if (coverHref != null) {
                // Resolve relative path from OPF location
                val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") else ""
                val coverPath = if (opfDir.isNotEmpty()) "$opfDir/$coverHref" else coverHref

                val coverEntry = zipFile.getEntry(coverPath)
                    ?: zipFile.getEntry(coverHref)
                    ?: return null

                val extension = coverHref.substringAfterLast(".", "jpg").lowercase()
                val outputFormat = when (extension) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }

                val inputStream = zipFile.getInputStream(coverEntry)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap != null) {
                    saveCoverBitmapWithFormat(bitmap, bookName, outputFormat)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // PDF
    // -------------------------------------------------------------------------

    /**
     * Extracts metadata from a PDF file.
     *
     * - Title/author are read from the PDF Info dictionary (/Title, /Author)
     * - Cover is generated by rasterizing the first page at thumbnail resolution
     */
    private fun extractPdfMetadata(filePath: String): MetadataResult {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return MetadataResult.Error("File not found: $filePath")
            }

            // Try to read PDF Info dictionary for title/author
            val (pdfTitle, pdfAuthor) = readPdfInfo(file)
            val title = pdfTitle ?: file.nameWithoutExtension

            // Rasterize first page as cover
            val coverPath = extractPdfCover(file)

            MetadataResult.Success(
                title = title,
                author = pdfAuthor,
                coverPath = coverPath
            )
        } catch (e: Exception) {
            MetadataResult.Error("Failed to extract PDF metadata: ${e.message}", e)
        }
    }

    /**
     * Reads PDF Info dictionary for /Title and /Author fields.
     * Best-effort text extraction from the binary PDF stream.
     */
    private fun readPdfInfo(file: File): Pair<String?, String?> {
        return try {
            // Read a portion of the file (first 50KB should contain the Info dict)
            val bytes = if (file.length() > 50_000) {
                val buf = ByteArray(50_000)
                FileInputStream(file).use { it.read(buf) }
                buf
            } else {
                file.readBytes()
            }
            val pdfText = String(bytes, Charsets.ISO_8859_1)

            val title = extractPdfInfoField(pdfText, "Title")
            val author = extractPdfInfoField(pdfText, "Author")
            Pair(title, author)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    private fun extractPdfInfoField(pdfText: String, fieldName: String): String? {
        return try {
            val keyIndex = pdfText.indexOf("/$fieldName")
            if (keyIndex < 0) return null

            val afterKey = pdfText.substring(keyIndex + fieldName.length + 1).trimStart()
            when {
                // Literal string in parentheses: (The Title)
                afterKey.startsWith("(") -> {
                    val sb = StringBuilder()
                    var i = 1
                    while (i < afterKey.length) {
                        val c = afterKey[i]
                        when {
                            c == ')' && (i == 1 || afterKey[i - 1] != '\\') -> break
                            c == '\\' && i + 1 < afterKey.length -> {
                                sb.append(afterKey[i + 1])
                                i++
                            }
                            else -> sb.append(c)
                        }
                        i++
                    }
                    sb.toString().trim().ifBlank { null }
                }
                // Hex string in angle brackets: <FEFF0054...>
                afterKey.startsWith("<") -> {
                    val endIdx = afterKey.indexOf('>')
                    if (endIdx > 1) {
                        val hex = afterKey.substring(1, endIdx).replace("\\s".toRegex(), "")
                        try {
                            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            String(bytes, Charsets.UTF_16BE).trim().ifBlank { null }
                        } catch (_: Exception) { null }
                    } else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Rasterizes the first page of a PDF as a cover image.
     */
    private fun extractPdfCover(file: File): String? {
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: AndroidPdfRenderer? = null

        return try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = AndroidPdfRenderer(fileDescriptor)

            if (pdfRenderer.pageCount == 0) return null

            val page = pdfRenderer.openPage(0)
            try {
                val scale = (MAX_COVER_DIMENSION.toFloat() / maxOf(page.width, page.height))
                    .coerceAtMost(1.0f)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)

                val matrix = android.graphics.Matrix()
                matrix.setScale(scale, scale)

                page.render(bitmap, null, matrix, AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                saveCoverBitmap(bitmap, file.nameWithoutExtension)
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            null
        } finally {
            try { pdfRenderer?.close() } catch (_: Exception) {}
            try { fileDescriptor?.close() } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------------------------
    // FB2
    // -------------------------------------------------------------------------

    /**
     * Extracts metadata from a FictionBook 2 (FB2) file.
     *
     * Parses the XML to extract:
     * - title-info > book-title
     * - title-info > author (first-name + last-name or nickname)
     * - title-info > annotation (first paragraph as description)
     */
    private fun extractFb2Metadata(filePath: String): MetadataResult {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return MetadataResult.Error("File not found: $filePath")
            }

            val factory = XMLInputFactory.newInstance()
            // Disable DTDs for security (XXE prevention)
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)

            val reader = factory.createXMLStreamReader(FileInputStream(file))

            var title: String? = null
            var author: String? = null
            var description: String? = null
            var insideTitleInfo = false
            var insideAuthor = false
            var insideAnnotation = false
            val authorParts = mutableListOf<String>()

            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        when (val localName = reader.localName) {
                            "title-info" -> insideTitleInfo = true
                            "book-title" -> if (insideTitleInfo) {
                                title = reader.getElementText().trim()
                            }
                            "author", "Author" -> if (insideTitleInfo) {
                                insideAuthor = true
                                authorParts.clear()
                            }
                            "first-name" -> if (insideAuthor) {
                                val text = reader.getElementText().trim()
                                if (text.isNotEmpty()) authorParts.add(text)
                            }
                            "last-name" -> if (insideAuthor) {
                                val text = reader.getElementText().trim()
                                if (text.isNotEmpty()) authorParts.add(text)
                            }
                            "nickname" -> if (insideAuthor) {
                                val text = reader.getElementText().trim()
                                if (text.isNotEmpty()) authorParts.add(text)
                            }
                            "annotation" -> if (insideTitleInfo) {
                                insideAnnotation = true
                            }
                            "p" -> if (insideAnnotation) {
                                val text = reader.getElementText().trim()
                                if (text.isNotEmpty()) {
                                    description = text
                                    insideAnnotation = false
                                }
                            }
                        }
                    }
                    XMLStreamConstants.END_ELEMENT -> {
                        when (reader.localName) {
                            "title-info" -> {
                                insideTitleInfo = false
                                if (title != null && authorParts.isNotEmpty()) {
                                    author = authorParts.joinToString(" ")
                                }
                            }
                            "author", "Author" -> {
                                insideAuthor = false
                                if (authorParts.isNotEmpty()) {
                                    author = authorParts.joinToString(" ")
                                }
                            }
                            "annotation" -> insideAnnotation = false
                        }
                    }
                }

                if (title != null && author != null && description != null) break
            }
            reader.close()

            MetadataResult.Success(
                title = title ?: file.nameWithoutExtension,
                author = author,
                description = description,
                coverPath = null
            )
        } catch (e: Exception) {
            MetadataResult.Error("Failed to extract FB2 metadata: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // MOBI / AZW / AZW3
    // -------------------------------------------------------------------------

    /**
     * Extracts metadata from a MOBI/AZW/AZW3 file.
     *
     * Reads the PalmDB header for the database name and the MOBI EXTH
     * extension header for title (record 503) and author (record 100).
     */
    private fun extractMobiMetadata(filePath: String): MetadataResult {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return MetadataResult.Error("File not found: $filePath")
            }

            val bytes = file.readBytes()
            if (bytes.size < 78) {
                return MetadataResult.Success(
                    title = file.nameWithoutExtension,
                    author = null,
                    coverPath = null
                )
            }

            // Verify PalmDB/MOBI magic at offset 60
            val magic = String(bytes, 60, 8, Charsets.US_ASCII)
            if (magic != "TEXtREAd" && magic != "BOOKMOBI") {
                val mobiCheck = String(bytes, 60, 4, Charsets.US_ASCII)
                if (mobiCheck != "MOBI") {
                    return MetadataResult.Success(
                        title = file.nameWithoutExtension,
                        author = null,
                        coverPath = null
                    )
                }
            }

            // Read database name from PalmDB header (32 bytes at offset 0)
            val dbName = String(bytes, 0, minOf(32, bytes.size), Charsets.US_ASCII)
                .trimEnd('\u0000')
                .trim()

            // Read MOBI EXTH header for richer metadata
            val (mobiTitle, mobiAuthor, mobiDescription) = readMobiExthHeader(bytes)

            MetadataResult.Success(
                title = mobiTitle ?: dbName.ifBlank { file.nameWithoutExtension },
                author = mobiAuthor,
                description = mobiDescription,
                coverPath = null
            )
        } catch (e: Exception) {
            MetadataResult.Error("Failed to extract MOBI metadata: ${e.message}", e)
        }
    }

    /**
     * Reads MOBI EXTH extension header for title, author, and description.
     *
     * The EXTH header follows the MOBI header (which starts at offset 60).
     * Record types: 503=title, 100=author, 101=description.
     */
    private fun readMobiExthHeader(bytes: ByteArray): Triple<String?, String?, String?> {
        return try {
            if (bytes.size < 84) return Triple(null, null, null)

            // MOBI header length is at offset 80 (2 bytes, little-endian)
            val mobiHeaderLen = (bytes[80].toInt() and 0xFF) or
                    ((bytes[81].toInt() and 0xFF) shl 8)
            val exthStart = 60 + mobiHeaderLen

            if (bytes.size < exthStart + 12) return Triple(null, null, null)

            // Check EXTH magic number
            val exthMagic = (bytes[exthStart].toInt() and 0xFF) or
                    ((bytes[exthStart + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[exthStart + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[exthStart + 3].toInt() and 0xFF) shl 24)

            if (exthMagic != EXTH_HEADER_MAGIC) return Triple(null, null, null)

            val recordCount = (bytes[exthStart + 8].toInt() and 0xFF) or
                    ((bytes[exthStart + 9].toInt() and 0xFF) shl 8) or
                    ((bytes[exthStart + 10].toInt() and 0xFF) shl 16) or
                    ((bytes[exthStart + 11].toInt() and 0xFF) shl 24)

            var offset = exthStart + 12
            var title: String? = null
            var author: String? = null
            var description: String? = null

            for (i in 0 until recordCount) {
                if (offset + 8 > bytes.size) break

                val recType = (bytes[offset].toInt() and 0xFF) or
                        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 3].toInt() and 0xFF) shl 24)
                val recLen = (bytes[offset + 4].toInt() and 0xFF) or
                        ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                        ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 7].toInt() and 0xFF) shl 24)

                if (offset + recLen > bytes.size) break

                val recData = bytes.copyOfRange(offset + 8, offset + recLen)
                val recText = String(recData, Charsets.UTF_8).trimEnd('\u0000').trim()

                when (recType) {
                    EXTH_TITLE -> title = recText
                    EXTH_AUTHOR -> author = recText
                    EXTH_DESCRIPTION -> description = recText
                }

                if (title != null && author != null && description != null) break
                offset += recLen
            }

            Triple(title, author, description)
        } catch (e: Exception) {
            Triple(null, null, null)
        }
    }

    // -------------------------------------------------------------------------
    // Generic fallback
    // -------------------------------------------------------------------------

    /**
     * Extracts generic metadata for unsupported formats.
     * Uses the filename as the title.
     */
    private fun extractGenericMetadata(pending: PendingBook): MetadataResult {
        val title = pending.fileName.substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
            .trim()

        return MetadataResult.Success(
            title = title,
            author = null,
            coverPath = null
        )
    }

    // -------------------------------------------------------------------------
    // Cover image helpers
    // -------------------------------------------------------------------------

    private fun saveCoverBitmap(bitmap: Bitmap, bookName: String): String? {
        return saveCoverBitmapWithFormat(bitmap, bookName, Bitmap.CompressFormat.JPEG)
    }

    private fun saveCoverBitmapWithFormat(
        bitmap: Bitmap,
        bookName: String,
        format: Bitmap.CompressFormat
    ): String? {
        return try {
            val coversDir = File(context.filesDir, COVERS_DIR)
            if (!coversDir.exists()) {
                coversDir.mkdirs()
            }

            // Scale down if necessary
            val scaledBitmap = if (bitmap.width > MAX_COVER_DIMENSION || bitmap.height > MAX_COVER_DIMENSION) {
                val scale = (MAX_COVER_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height))
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }

            val extension = when (format) {
                Bitmap.CompressFormat.PNG -> "png"
                Bitmap.CompressFormat.WEBP -> "webp"
                else -> "jpg"
            }

            val sanitizedName = bookName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val coverFile = File(coversDir, "${sanitizedName}_cover.$extension")

            FileOutputStream(coverFile).use { out ->
                scaledBitmap.compress(format, COVER_QUALITY, out)
            }

            // Clean up if we created a scaled copy
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }

            coverFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
