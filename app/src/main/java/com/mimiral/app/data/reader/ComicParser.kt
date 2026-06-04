package com.mimiral.app.data.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Represents a single page in a comic book archive.
 */
data class ComicPage(
    val index: Int,
    val fileName: String,
    val width: Int,
    val height: Int,
    /** File extension of the image (lowercase, without dot). */
    val format: String
)

/**
 * Result of parsing a comic book archive.
 */
data class ComicArchive(
    val filePath: String,
    val format: String, // "CBZ" or "CBR"
    val pages: List<ComicPage>,
    val coverImagePath: String? = null
) {
    val pageCount: Int get() = pages.size
}

/**
 * Parser for comic book archive formats: CBZ (ZIP) and CBR (RAR).
 *
 * CBZ files are parsed directly using Java's ZipInputStream.
 * CBR files require unrar extraction to a temp directory first;
 * falls back to attempting ZIP parsing (some CBR files are actually ZIP-based).
 *
 * Images are sorted by filename to determine reading order.
 * Cover image is always the first page.
 */
class ComicParser @Inject constructor() : AutoCloseable {

    /** Temp directory for CBR extraction (cleaned up on close). */
    private var tempDir: File? = null

    companion object {
        /** Supported image extensions inside comic archives. */
        private val IMAGE_EXTENSIONS = setOf(
            "jpg",
            "jpeg",
            "png",
            "gif",
            "bmp",
            "webp",
            "tiff",
            "tif"
        )

        /**
         * Check if a file extension represents a comic archive format.
         */
        fun isComicExtension(extension: String): Boolean =
            extension.lowercase() in setOf("cbz", "cbr")

        /**
         * Detect if a ZIP entry is an image file.
         */
        fun isImageEntry(entryName: String): Boolean {
            val ext = entryName.substringAfterLast('.', "").lowercase()
            return ext in IMAGE_EXTENSIONS
        }

        /**
         * Natural sort comparator for filenames: splits text into
         * numeric and non-numeric parts so that "page2" sorts before "page10".
         */
        val NATURAL_ORDER_COMPARATOR = Comparator<String> { a, b ->
            val regex = Regex("(\\d+)|(\\D+)")
            val partsA = regex.findAll(a).map { it.value }.toList()
            val partsB = regex.findAll(b).map { it.value }.toList()

            val minLen = minOf(partsA.size, partsB.size)
            for (i in 0 until minLen) {
                val pa = partsA[i]
                val pb = partsB[i]
                val na = pa.toIntOrNull()
                val nb = pb.toIntOrNull()
                val cmp = when {
                    na != null && nb != null -> na.compareTo(nb)
                    na != null -> -1 // numeric before alpha
                    nb != null -> 1
                    else -> pa.compareTo(pb, ignoreCase = true)
                }
                if (cmp != 0) return@Comparator cmp
            }
            partsA.size.compareTo(partsB.size)
        }
    }

    /**
     * Parse a comic book archive file (CBZ or CBR).
     *
     * @param filePath Absolute path to the comic archive file
     * @return ComicArchive with pages sorted in reading order
     * @throws IOException if the file cannot be read or parsed
     */
    suspend fun parse(filePath: String): ComicArchive = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) throw IOException("Comic archive not found: $filePath")
        if (!file.isFile) throw IOException("Not a file: $filePath")

        val extension = file.extension.lowercase()
        when (extension) {
            "cbz" -> parseCbz(file)
            "cbr" -> parseCbr(file)
            else -> throw IOException("Unsupported comic format: $extension")
        }
    }

    /**
     * Parse a CBZ file (ZIP archive containing images).
     */
    private fun parseCbz(file: File): ComicArchive {
        val pages = mutableListOf<ComicPage>()

        ZipInputStream(BufferedInputStream(file.inputStream())).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isImageEntry(entry.name)) {
                    val fileName = entry.name.substringAfterLast('/')
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(zis, null, options)
                    pages.add(
                        ComicPage(
                            index = 0, // Will be set after sorting
                            fileName = fileName,
                            width = options.outWidth,
                            height = options.outHeight,
                            format = fileName.substringAfterLast('.', "").lowercase()
                        )
                    )
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Sort pages by filename using natural order
        val sortedPages = pages.sortedWith(
            compareBy(NATURAL_ORDER_COMPARATOR) { it.fileName }
        ).mapIndexed { index, page -> page.copy(index = index) }

        return ComicArchive(
            filePath = file.absolutePath,
            format = "CBZ",
            pages = sortedPages
        )
    }

    /**
     * Parse a CBR file (RAR archive containing images).
     *
     * Strategy: extract images to a temp directory using the `unrar` command-line tool,
     * then read back sorted image files. If unrar is not available, falls back to
     * treating the file as ZIP (some CBR files are mislabeled ZIP archives).
     */
    private fun parseCbr(file: File): ComicArchive {
        // Try extracting via unrar
        val extracted = tryExtractWithUnrar(file)
        if (extracted != null) return extracted

        // Fallback: try as ZIP (some .cbr files are actually ZIP-based)
        try {
            return parseCbz(file).copy(format = "CBR")
        } catch (_: Exception) {
            throw IOException(
                "Cannot parse CBR file: '${file.name}'. " +
                    "Install unrar or convert to CBZ format."
            )
        }
    }

    /**
     * Extract a CBR file to temp directory using `unrar` CLI, then build page list.
     */
    private fun tryExtractWithUnrar(file: File): ComicArchive? {
        return try {
            val dir = createTempDir("mimiral_cbr_")
            tempDir = dir

            val process = ProcessBuilder(
                "unrar",
                "e",
                "-o+",
                "-inul",
                file.absolutePath,
                dir.absolutePath
            ).redirectErrorStream(true).start()

            val exitCode = process.waitFor()
            if (exitCode != 0) return null

            val imageFiles = dir.listFiles()?.filter { f ->
                f.isFile && f.extension.lowercase() in IMAGE_EXTENSIONS
            } ?: emptyList()

            val sortedFiles = imageFiles.sortedWith(
                compareBy(NATURAL_ORDER_COMPARATOR) { it.name }
            )

            val pages = sortedFiles.mapIndexed { index, imgFile ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(imgFile.absolutePath, options)
                ComicPage(
                    index = index,
                    fileName = imgFile.name,
                    width = options.outWidth,
                    height = options.outHeight,
                    format = imgFile.extension.lowercase()
                )
            }

            if (pages.isEmpty()) return null

            ComicArchive(
                filePath = file.absolutePath,
                format = "CBR",
                pages = pages,
                coverImagePath = sortedFiles.firstOrNull()?.absolutePath
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract a single page image to a temporary file.
     *
     * For CBZ files, extracts from the ZIP stream.
     * For CBR files, reads from the temp extraction directory.
     *
     * @param archive The parsed comic archive
     * @param pageIndex Zero-based page index
     * @return File pointing to the extracted image, or null on failure
     */
    suspend fun extractPageImage(archive: ComicArchive, pageIndex: Int): File? =
        withContext(Dispatchers.IO) {
            try {
                val page = archive.pages.getOrNull(pageIndex) ?: return@withContext null
                val outputFile = File(
                    createTempDir("mimiral_page_"),
                    "page_$pageIndex.${page.format}"
                )

                when (archive.format) {
                    "CBZ" -> extractFromZip(archive.filePath, page.fileName, outputFile)
                    "CBR" -> extractFromCbr(archive, pageIndex, outputFile)
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

    private fun extractFromZip(zipPath: String, entryName: String, outputFile: File): File? {
        ZipInputStream(BufferedInputStream(File(zipPath).inputStream())).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(entryName, ignoreCase = true)) {
                    FileOutputStream(outputFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    zis.closeEntry()
                    return outputFile
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun extractFromCbr(archive: ComicArchive, pageIndex: Int, outputFile: File): File? {
        val page = archive.pages.getOrNull(pageIndex) ?: return null
        // For CBR files with coverImagePath, we already have extracted files in tempDir
        return if (archive.coverImagePath != null) {
            val extractedFiles = tempDir?.listFiles()?.filter { f ->
                f.isFile && f.extension.lowercase() in IMAGE_EXTENSIONS
            }?.sortedWith(compareBy(NATURAL_ORDER_COMPARATOR) { it.name })
            extractedFiles?.getOrNull(pageIndex)?.let { file ->
                file.copyTo(outputFile, overwrite = true)
                outputFile
            }
        } else {
            null
        }
    }

    /**
     * Load a bitmap for a given page, sampling down if needed.
     */
    suspend fun loadPageBitmap(
        archive: ComicArchive,
        pageIndex: Int,
        sampleSize: Int = 1
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val imageFile = extractPageImage(archive, pageIndex) ?: return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(imageFile.absolutePath, options)
        } catch (_: Exception) {
            null
        }
    }

    override fun close() {
        // Clean up temp directory
        tempDir?.let { dir ->
            try {
                dir.deleteRecursively()
            } catch (_: Exception) {}
        }
        tempDir = null
    }
}
