package com.mimiral.app.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Represents the state of an EPUB document being parsed.
 */
sealed class EpubState {
    /** No EPUB is loaded. */
    object Idle : EpubState()

    /** An EPUB is loaded and ready for reading. */
    data class Loaded(
        val title: String,
        val author: String?,
        val description: String?,
        val chapterCount: Int,
        val coverPath: String?,
        val filePath: String,
        val totalEstimatedCharacters: Long = 0
    ) : EpubState()

    /** An error occurred while parsing the EPUB. */
    data class Error(val message: String, val cause: Throwable? = null) : EpubState()
}

/**
 * Represents a single chapter in an EPUB.
 */
data class EpubChapter(
    val index: Int,
    val title: String,
    val href: String,
    val mediaType: String = "application/xhtml+xml"
)

/**
 * Table of contents entry with support for nested hierarchy.
 */
data class TocEntry(
    val title: String,
    val href: String,
    val depth: Int = 0,
    val children: List<TocEntry> = emptyList()
)

/**
 * Result of a cover image extraction operation.
 */
sealed class CoverResult {
    data class Success(val bitmap: Bitmap, val mimeType: String) : CoverResult()
    data class NotFound(val message: String = "No cover image found in EPUB") : CoverResult()
    data class Error(val message: String, val cause: Throwable? = null) : CoverResult()
}

/**
 * Lightweight EPUB parser that reads the ZIP structure directly.
 *
 * EPUB is an OCF ZIP container with:
 *   META-INF/container.xml  → points to the OPF file
 *   <opf-path>              → package document with metadata + manifest + spine
 *   NCX or nav document     → table of contents
 *   XHTML files             → chapter content
 *
 * This parser extracts metadata, chapter list, TOC, and raw XHTML content
 * without depending on Readium's internal Asset/Publication APIs.
 *
 * The existing [ChapterExtractor] and [EpubStructuredExtractor] can still be
 * used for structured text extraction from the raw XHTML.
 */
class EpubParser(private val context: Context) {

    companion object {
        private const val TAG = "EpubParser"
    }

    private var currentFile: File? = null
    private var chapters: List<EpubChapter> = emptyList()
    private var tocEntries: List<TocEntry> = emptyList()
    private var metadata: EpubMetadata = EpubMetadata()
    private var manifestItems: Map<String, ManifestItem> = emptyMap()
    private var spineRefs: List<String> = emptyList()
    private var opfBasePath: String = ""
    private val mutex = Mutex()

    var state: EpubState = EpubState.Idle
        private set

    data class EpubMetadata(
        var title: String? = null,
        var author: String? = null,
        var description: String? = null,
        var coverId: String? = null
    )

    data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: List<String> = emptyList()
    )

    suspend fun openFile(filePath: String): EpubState = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val directFile = File(filePath)
                val file = if (directFile.exists()) {
                    directFile
                } else {
                    // Scoped storage: copy to cache via ContentResolver
                    try {
                        val uri = android.net.Uri.parse(filePath)
                        android.util.Log.d("EpubParser", "Opening URI: $uri, scheme=${uri.scheme}")
                        val inputStream = try {
                            context.contentResolver.openInputStream(uri)
                        } catch (_: SecurityException) {
                            null
                        }
                        val resolvedStream = inputStream ?: run {
                            // Fallback for SAF content URIs: resolve via DocumentsContract tree
                            if (uri.scheme?.lowercase() == "content") {
                                try {
                                    val treeDocId = android.provider.DocumentsContract
                                        .getTreeDocumentId(uri)
                                    val treeUri = android.provider.DocumentsContract
                                        .buildTreeDocumentUri(
                                            uri.authority,
                                            treeDocId
                                        )
                                    val treeDoc = androidx.documentfile.provider
                                        .DocumentFile.fromTreeUri(
                                            context,
                                            treeUri
                                        )
                                    if (treeDoc != null && treeDoc.exists()) {
                                        val docId = android.provider.DocumentsContract
                                            .getDocumentId(uri)
                                        var current: DocumentFile? = treeDoc
                                        val pathStr = docId.split(":")
                                            .lastOrNull() ?: ""
                                        val segments = pathStr.split("/")
                                            .filter { it.isNotBlank() }
                                        for (segment in segments) {
                                            val decoded = try {
                                                android.net.Uri.decode(segment)
                                            } catch (_: Exception) { segment }
                                            current = current?.listFiles()
                                                ?.find { file ->
                                                    file.name == decoded ||
                                                        decodedName(file) == decoded
                                                }
                                            if (current == null) break
                                        }
                                        current?.takeIf {
                                            it.exists() && it.isFile
                                        }?.let { found ->
                                            context.contentResolver
                                                .openInputStream(found.uri)
                                        }
                                    } else {
                                        null
                                    }
                                } catch (_: Exception) { null }
                            } else {
                                null
                            }
                        }
                        resolvedStream ?: return@withContext EpubState.Error(
                            "Cannot open file: $filePath"
                        ).also { state = it }
                        val cacheFile = java.io.File(
                            context.cacheDir,
                            "epub_cache_${filePath.hashCode().toString(16)}.epub"
                        )
                        java.io.FileOutputStream(cacheFile).use {
                                out: java.io.OutputStream ->
                            resolvedStream.copyTo(out)
                        }
                        resolvedStream.close()
                        cacheFile
                    } catch (e: Exception) {
                        android.util.Log.e("EpubParser", "Failed to open URI: $filePath", e)
                        return@withContext EpubState.Error(
                            "Failed to access file: ${e.message}"
                        ).also { state = it }
                    }
                }
                openFileInternal(file, filePath)
            } catch (e: Exception) {
                val error = EpubState.Error("Failed to open EPUB: ${e.message}")
                state = error
                error
            }
        }
    }

    suspend fun openFile(file: File): EpubState = mutex.withLock {
        withContext(Dispatchers.IO) {
            openFileInternal(file, file.absolutePath)
        }
    }

    private fun openFileInternal(file: File, originalPath: String): EpubState {
        return try {
            if (!file.exists()) {
                val error = EpubState.Error("File not found: ${file.absolutePath}")
                state = error
                return error
            }

            if (!file.canRead()) {
                val error = EpubState.Error("Cannot read file: ${file.absolutePath}")
                state = error
                return error
            }

            closeInternal()
            currentFile = file

            ZipFile(file).use { zip ->
                // Step 1: Read META-INF/container.xml to find OPF path
                val opfPath = findOpfPath(zip)
                    ?: return EpubState.Error(
                        "Could not find OPUB package document (OPF)"
                    ).also { state = it }

                // Step 2: Parse OPF for metadata, manifest, and spine
                val opfEntry = zip.getEntry(opfPath)
                    ?: return EpubState.Error(
                        "OPF file not found in EPUB: $opfPath"
                    ).also { state = it }

                val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
                opfBasePath = if (opfPath.contains("/")) {
                    opfPath.substringBeforeLast("/") + "/"
                } else {
                    ""
                }

                parseOpf(opfXml)

                // Step 3: Build chapter list from spine
                chapters = buildChaptersFromSpine()
                Log.d(TAG, "openFile: parsed ${chapters.size} chapters from spine")
                for (ch in chapters) {
                    Log.d(TAG, "  chapter[${ch.index}]: title='${ch.title}' href='${ch.href}'")
                }

                // Step 4: Try to extract TOC (NCX or nav document)
                tocEntries = extractToc(zip)
            }

            val loaded = EpubState.Loaded(
                title = metadata.title ?: file.nameWithoutExtension,
                author = metadata.author,
                description = metadata.description,
                chapterCount = chapters.size,
                coverPath = metadata.coverId?.let { resolveHref(it) },
                filePath = file.absolutePath,
                totalEstimatedCharacters = chapters.size.toLong() * 1000L
            )
            Log.d(
                TAG,
                "openFile: success title='${loaded.title}' chapters=${loaded.chapterCount}"
            )
            state = loaded
            loaded
        } catch (e: IOException) {
            closeInternal()
            val error = EpubState.Error("Failed to open EPUB: ${e.message}", e)
            state = error
            error
        } catch (e: Exception) {
            closeInternal()
            val error = EpubState.Error("Unexpected error parsing EPUB: ${e.message}", e)
            state = error
            error
        }
    }

    suspend fun getChapters(): List<EpubChapter> = mutex.withLock {
        return@withLock chapters
    }

    suspend fun getTableOfContents(): List<TocEntry> = mutex.withLock {
        return@withLock tocEntries
    }

    fun getCurrentFile(): File? = currentFile

    /**
     * Returns the raw XHTML content for a chapter by its index.
     * This is used by ChapterExtractor and EpubStructuredExtractor.
     */
    suspend fun getChapterXhtml(chapterIndex: Int): String? = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val file = currentFile ?: return@withContext null
                if (chapterIndex < 0 || chapterIndex >= chapters.size) {
                    Log.w(
                        TAG,
                        "getChapterXhtml: chapterIndex $chapterIndex " +
                            "out of bounds (size=${chapters.size})"
                    )
                    return@withContext null
                }
                val chapter = chapters[chapterIndex]
                val resolvedHref = resolveHref(chapter.href)
                Log.d(
                    TAG,
                    "getChapterXhtml[$chapterIndex]: href='${chapter.href}' " +
                        "resolved='$resolvedHref' opfBase='$opfBasePath'"
                )

                ZipFile(file).use { zip ->
                    // Try the resolved path directly, then with opfBasePath prefix
                    val entry = zip.getEntry(resolvedHref)
                        ?: zip.getEntry(opfBasePath + resolvedHref)
                    if (entry == null) {
                        // List available entries for debugging
                        val available = zip.entries().asSequence()
                            .map { it.name }
                            .filter { it.contains(chapter.href.substringBeforeLast(".")) }
                            .take(5)
                            .toList()
                        Log.w(
                            TAG,
                            "getChapterXhtml[$chapterIndex]: entry not found for " +
                                "'$resolvedHref' or '${opfBasePath + resolvedHref}'. " +
                                "Similar entries: $available"
                        )
                        return@withContext null
                    }

                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    Log.d(TAG, "getChapterXhtml[$chapterIndex]: loaded ${text.length} chars")
                    text
                }
            } catch (e: Exception) {
                Log.e(TAG, "getChapterXhtml[$chapterIndex]: error", e)
                null
            }
        }
    }

    /**
     * Returns the raw bytes of an internal resource by its href path.
     * Used for cover image extraction.
     */
    suspend fun getResourceBytes(href: String): ByteArray? = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val file = currentFile ?: return@withContext null
                val resolvedHref = resolveHref(href)

                ZipFile(file).use { zip ->
                    val entry = zip.getEntry(resolvedHref)
                        ?: zip.getEntry(opfBasePath + resolvedHref)
                        ?: return@withContext null

                    zip.getInputStream(entry).readBytes()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun extractCover(): CoverResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val file = currentFile
                    ?: return@withContext CoverResult.Error("No EPUB loaded")

                ZipFile(file).use { zip ->
                    // Strategy 1: Check manifest items with cover-image property
                    val coverItem = manifestItems.values.firstOrNull {
                        it.properties.contains("cover-image")
                    }
                    if (coverItem != null) {
                        val bytes = loadZipEntryBytes(zip, coverItem.href)
                        if (bytes != null) {
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                return@withContext CoverResult.Success(
                                    bitmap = bitmap,
                                    mimeType = coverItem.mediaType
                                )
                            }
                        }
                    }

                    // Strategy 2: Check for cover via metadata cover id
                    val coverId = metadata.coverId
                    if (coverId != null) {
                        val coverManifestItem = manifestItems[coverId]
                        if (coverManifestItem != null) {
                            val bytes = loadZipEntryBytes(zip, coverManifestItem.href)
                            if (bytes != null) {
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) {
                                    return@withContext CoverResult.Success(
                                        bitmap = bitmap,
                                        mimeType = coverManifestItem.mediaType
                                    )
                                }
                            }
                        }
                    }

                    // Strategy 3: Search for common cover image file names
                    val coverNames = listOf("cover", "Cover", "COVER", "cover-image", "cover_image")
                    for ((_, item) in manifestItems) {
                        if (!item.mediaType.startsWith("image/")) continue
                        val nameWithoutExt = item.href.substringAfterLast("/").substringBeforeLast(
                            "."
                        )
                        if (nameWithoutExt in coverNames) {
                            val bytes = loadZipEntryBytes(zip, item.href)
                            if (bytes != null) {
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) {
                                    return@withContext CoverResult.Success(
                                        bitmap = bitmap,
                                        mimeType = item.mediaType
                                    )
                                }
                            }
                        }
                    }

                    CoverResult.NotFound()
                }
            } catch (e: Exception) {
                CoverResult.Error("Failed to extract cover: ${e.message}", e)
            }
        }
    }

    suspend fun close() = mutex.withLock {
        closeInternal()
        state = EpubState.Idle
    }

    // ---- Private: ZIP helpers ----

    private fun loadZipEntryBytes(zip: ZipFile, href: String): ByteArray? {
        val resolvedHref = resolveHref(href)
        val entry = zip.getEntry(resolvedHref)
            ?: zip.getEntry(opfBasePath + resolvedHref)
            ?: return null
        return zip.getInputStream(entry).readBytes()
    }

    private fun decodedName(file: DocumentFile): String {
        return file.uri.lastPathSegment?.let { seg ->
            try { android.net.Uri.decode(seg) } catch (_: Exception) { seg }
        } ?: ""
    }

    private fun resolveHref(href: String): String {
        if (href.startsWith("/") || href.startsWith("http://") || href.startsWith("https://")) {
            return href
        }
        return opfBasePath + href
    }

    // ---- Private: OPF parsing ----

    private fun findOpfPath(zip: ZipFile): String? {
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
        val xml = zip.getInputStream(containerEntry).bufferedReader().readText()

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val fullPath = parser.getAttributeValue(null, "full-path")
                if (fullPath != null) return fullPath
            }
            eventType = parser.next()
        }
        return null
    }

    private fun parseOpf(xml: String) {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val manifest = mutableMapOf<String, ManifestItem>()
        val spine = mutableListOf<String>()
        var inMetadata = false
        var inManifest = false
        var inSpine = false
        var currentText = StringBuilder()
        var currentTag = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name
                    when {
                        tagName == "metadata" -> inMetadata = true
                        tagName == "manifest" -> inManifest = true
                        tagName == "spine" -> inSpine = true
                    }
                    if (inManifest && tagName == "item") {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                        val properties = parser.getAttributeValue(null, "properties") ?: ""
                        manifest[id] = ManifestItem(
                            id = id,
                            href = href,
                            mediaType = mediaType,
                            properties = properties.split("\\s+".toRegex())
                                .filter { it.isNotBlank() }
                        )
                    }
                    if (inSpine && tagName == "itemref") {
                        val idref = parser.getAttributeValue(null, "idref")
                        if (idref != null) spine.add(idref)
                    }
                    currentTag = tagName
                    currentText = StringBuilder()
                }
                XmlPullParser.TEXT -> {
                    currentText.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    val tagName = parser.name
                    val text = currentText.toString().trim()
                    if (inMetadata) {
                        when (tagName) {
                            "dc:title" -> metadata.title = text
                            "dc:creator" -> metadata.author = text
                            "dc:description" -> metadata.description = text
                            "meta" -> {
                                val name = parser.getAttributeValue(null, "name") ?: ""
                                val content = parser.getAttributeValue(null, "content") ?: ""
                                if (name == "cover") metadata.coverId = content
                            }
                        }
                    }
                    when {
                        tagName == "metadata" -> inMetadata = false
                        tagName == "manifest" -> inManifest = false
                        tagName == "spine" -> inSpine = false
                    }
                }
            }
            eventType = parser.next()
        }

        manifestItems = manifest
        spineRefs = spine
    }

    private fun buildChaptersFromSpine(): List<EpubChapter> {
        return spineRefs.mapIndexed { index, idref ->
            val item = manifestItems[idref]
            val href = item?.href ?: ""
            val title = item?.id?.replace("_", " ")?.replace("-", " ")?.capitalizeWords()
                ?: "Chapter ${index + 1}"
            EpubChapter(
                index = index,
                title = title,
                href = href,
                mediaType = item?.mediaType ?: "application/xhtml+xml"
            )
        }
    }

    // ---- Private: TOC extraction ----

    private fun extractToc(zip: ZipFile): List<TocEntry> {
        // Try NCX first (EPUB 2 style)
        val ncxItem = manifestItems.values.firstOrNull {
            it.mediaType == "application/x-dtbncx+xml"
        }
        if (ncxItem != null) {
            val ncxEntry = zip.getEntry(resolveHref(ncxItem.href))
            if (ncxEntry != null) {
                val xml = zip.getInputStream(ncxEntry).bufferedReader().readText()
                return parseNcxToc(xml)
            }
        }

        // Try nav document (EPUB 3 style)
        val navItem = manifestItems.values.firstOrNull {
            it.properties.contains("nav")
        }
        if (navItem != null) {
            val navEntry = zip.getEntry(resolveHref(navItem.href))
            if (navEntry != null) {
                val xml = zip.getInputStream(navEntry).bufferedReader().readText()
                return parseNavToc(xml)
            }
        }

        return emptyList()
    }

    private fun parseNcxToc(xml: String): List<TocEntry> {
        val entries = mutableListOf<TocEntry>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inNavPoint = false
            var currentLabel = ""
            var currentSrc = ""
            var currentDepth = 0
            var depthStack = mutableListOf<Int>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "navPoint" -> {
                                inNavPoint = true
                                currentLabel = ""
                                currentSrc = ""
                                depthStack.add(currentDepth)
                                currentDepth++
                            }
                            "text" -> {
                                if (inNavPoint) {
                                    // Read the text content
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inNavPoint && parser.name == null) {
                            // This is text content of the current element
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "text" -> {
                                // Already handled via nextText
                            }
                            "navPoint" -> {
                                if (currentSrc.isNotBlank()) {
                                    entries.add(
                                        TocEntry(
                                            title = currentLabel,
                                            href = currentSrc,
                                            depth = depthStack.lastOrNull() ?: 0
                                        )
                                    )
                                }
                                depthStack.removeLastOrNull()
                                currentDepth = depthStack.lastOrNull() ?: 0
                                inNavPoint = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Return whatever we parsed
        }
        return entries
    }

    private fun parseNavToc(xml: String): List<TocEntry> {
        val entries = mutableListOf<TocEntry>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inNavOl = false
            var inLi = false
            var inA = false
            var currentLabel = ""
            var currentHref = ""
            var depth = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "nav" -> { /* EPUB 3 nav element */ }
                            "ol" -> {
                                inNavOl = true
                                depth++
                            }
                            "li" -> {
                                inLi = true
                                currentLabel = ""
                                currentHref = ""
                            }
                            "a" -> {
                                if (inLi) {
                                    inA = true
                                    currentHref = parser.getAttributeValue(null, "href") ?: ""
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inA) {
                            currentLabel += (parser.text ?: "").trim()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "a" -> inA = false
                            "li" -> {
                                if (currentHref.isNotBlank()) {
                                    entries.add(
                                        TocEntry(
                                            title = currentLabel,
                                            href = currentHref,
                                            depth = depth - 1
                                        )
                                    )
                                }
                                inLi = false
                            }
                            "ol" -> {
                                depth--
                                inNavOl = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Return whatever we parsed
        }
        return entries
    }

    private fun closeInternal() {
        currentFile = null
        chapters = emptyList()
        tocEntries = emptyList()
        metadata = EpubMetadata()
        manifestItems = emptyMap()
        spineRefs = emptyList()
        opfBasePath = ""
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
