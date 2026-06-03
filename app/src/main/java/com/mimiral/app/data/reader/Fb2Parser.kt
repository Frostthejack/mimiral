package com.mimiral.app.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * State of an FB2 document being parsed.
 */
sealed class Fb2State {
    object Idle : Fb2State()

    data class Loaded(
        val title: String,
        val author: String?,
        val description: String?,
        val chapterCount: Int,
        val coverPath: String?,
        val filePath: String,
        val annotation: String?
    ) : Fb2State()

    data class Error(val message: String, val cause: Throwable? = null) : Fb2State()
}

/**
 * A single section (chapter) in an FB2 document.
 */
data class Fb2Section(
    val id: String?,
    val title: String,
    val paragraphs: List<String>
)

/**
 * FB2 embedded binary image (base64-encoded).
 */
data class Fb2Image(
    val id: String,
    val contentType: String,
    val data: ByteArray
) {
    fun toBitmap(): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (_: Exception) {
            null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Fb2Image) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Result of FB2 cover extraction.
 */
sealed class Fb2CoverResult {
    data class Success(val bitmap: Bitmap, val mimeType: String) : Fb2CoverResult()
    data class NotFound(val message: String = "No cover image found in FB2") : Fb2CoverResult()
    data class Error(val message: String, val cause: Throwable? = null) : Fb2CoverResult()
}

/**
 * FictionBook 2 (FB2) format parser.
 *
 * Supports .fb2 (XML) and .fb2.zip files.
 * Extracts title, author, sections/paragraphs, and embedded base64 images.
 * Thread-safe via internal mutex.
 */
class Fb2Parser(private val context: Context) {

    private var currentFile: File? = null
    private var sections: List<Fb2Section> = emptyList()
    private var images: Map<String, Fb2Image> = emptyMap()
    private var bookTitle: String = ""
    private var bookAuthor: String? = null
    private var bookAnnotation: String? = null
    private var coverImageId: String? = null
    private val mutex = Mutex()

    var state: Fb2State = Fb2State.Idle
        private set

    suspend fun openFile(file: File): Fb2State = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists()) {
                    val error = Fb2State.Error("File not found: ${file.absolutePath}")
                    state = error
                    return@withContext error
                }
                if (!file.canRead()) {
                    val error = Fb2State.Error("Cannot read file: ${file.absolutePath}")
                    state = error
                    return@withContext error
                }

                closeInternal()
                currentFile = file

                val inputStream = if (file.name.lowercase().endsWith(".fb2.zip")) {
                    openFb2Zip(file)
                } else {
                    FileInputStream(file)
                }

                inputStream.use { stream -> parseFb2Xml(stream) }

                val loaded = Fb2State.Loaded(
                    title = bookTitle.ifBlank { file.nameWithoutExtension },
                    author = bookAuthor,
                    description = bookAnnotation,
                    chapterCount = sections.size,
                    coverPath = coverImageId,
                    filePath = file.absolutePath,
                    annotation = bookAnnotation
                )
                state = loaded
                loaded
            } catch (e: Exception) {
                closeInternal()
                val error = Fb2State.Error("Failed to parse FB2: ${e.message}", e)
                state = error
                error
            }
        }
    }

    suspend fun getSections(): List<Fb2Section> = mutex.withLock { sections }

    suspend fun getImages(): Map<String, Fb2Image> = mutex.withLock { images }

    suspend fun getTitle(): String = mutex.withLock { bookTitle }

    suspend fun getAuthor(): String? = mutex.withLock { bookAuthor }

    suspend fun getAnnotation(): String? = mutex.withLock { bookAnnotation }

    fun getCurrentFile(): File? = currentFile

    suspend fun extractCover(): Fb2CoverResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val id = coverImageId
                if (id != null) {
                    val image = images[id]
                    if (image != null) {
                        val bitmap = image.toBitmap()
                        if (bitmap != null) {
                            return@withContext Fb2CoverResult.Success(
                                bitmap = bitmap,
                                mimeType = image.contentType
                            )
                        }
                    }
                }
                Fb2CoverResult.NotFound()
            } catch (e: Exception) {
                Fb2CoverResult.Error("Failed to extract FB2 cover: ${e.message}", e)
            }
        }
    }

    suspend fun close() = mutex.withLock {
        closeInternal()
        state = Fb2State.Idle
    }

    // ---- Private helpers ----

    private fun openFb2Zip(file: File): InputStream {
        val zipStream = ZipInputStream(BufferedInputStream(FileInputStream(file)))
        var entry = zipStream.nextEntry
        while (entry != null) {
            if (entry.name.lowercase().endsWith(".fb2")) {
                return zipStream
            }
            entry = zipStream.nextEntry
        }
        zipStream.close()
        throw Exception("No .fb2 file found in ZIP archive: ${file.name}")
    }

    private fun parseFb2Xml(inputStream: InputStream) {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        val sectionsList = mutableListOf<Fb2Section>()
        val imagesMap = mutableMapOf<String, Fb2Image>()

        var title = ""
        var firstName = ""
        var middleName = ""
        var lastName = ""
        var nickname = ""
        var annotationText = ""
        var localCoverImageId: String? = null

        var currentSectionId: String? = null
        var currentSectionTitle = ""
        var currentSectionParagraphs = mutableListOf<String>()
        var currentParagraphText = StringBuilder()
        var currentImageId: String? = null
        var currentImageContentType = ""
        var currentImageBase64 = StringBuilder()

        var inTitle = false
        var inParagraph = false
        var inSection = false
        var inBinary = false
        var inAnnotation = false
        var inCoverpage = false
        var inBookTitle = false
        var inFirstName = false
        var inMiddleName = false
        var inLastName = false
        var inNickname = false
        var inSubtitle = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "section" -> {
                            inSection = true
                            currentSectionId = parser.getAttributeValue(null, "id")
                            currentSectionTitle = ""
                            currentSectionParagraphs = mutableListOf()
                            currentParagraphText = StringBuilder()
                        }
                        "title" -> {
                            inTitle = true
                            currentSectionTitle = ""
                        }
                        "p" -> {
                            inParagraph = true
                            currentParagraphText = StringBuilder()
                        }
                        "empty-line" -> {
                            if (inSection) currentSectionParagraphs.add("")
                        }
                        "v" -> {
                            if (inParagraph && currentParagraphText.isNotEmpty()) {
                                currentParagraphText.append("\n")
                            }
                        }
                        "subtitle" -> {
                            inSubtitle = true
                            if (inParagraph && currentParagraphText.isNotEmpty()) {
                                currentParagraphText.append("\n")
                            }
                        }
                        "emphasis", "strong", "strikethrough", "code", "sub", "sup", "a" -> {
                            // Strip formatting tags - extract text content only
                        }
                        "image" -> {
                            val href = parser.getAttributeValue(null, "href")
                                ?: parser.getAttributeValue(null, "l:href")
                                ?: parser.getAttributeValue(null, "xlink:href")
                            if (href != null && href.startsWith("#")) {
                                val imgId = href.substring(1)
                                if (inCoverpage && localCoverImageId == null) {
                                    localCoverImageId = imgId
                                }
                                currentParagraphText.append("[image:$imgId]")
                            }
                        }
                        "binary" -> {
                            inBinary = true
                            currentImageId = parser.getAttributeValue(null, "id")
                            currentImageContentType = parser.getAttributeValue(null, "content-type")
                                ?: "image/jpeg"
                            currentImageBase64 = StringBuilder()
                        }
                        "annotation" -> {
                            inAnnotation = true
                            annotationText = ""
                        }
                        "coverpage" -> { inCoverpage = true }
                        "book-title" -> { inBookTitle = true; title = "" }
                        "first-name" -> { inFirstName = true; firstName = "" }
                        "middle-name" -> { inMiddleName = true; middleName = "" }
                        "last-name" -> { inLastName = true; lastName = "" }
                        "nickname" -> { inNickname = true; nickname = "" }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    when {
                        inBinary -> currentImageBase64.append(text.trim())
                        inAnnotation -> annotationText += text
                        inTitle -> {
                            if (inSection) currentSectionTitle += text else title += text
                        }
                        inParagraph -> currentParagraphText.append(text)
                        inSubtitle -> currentParagraphText.append(text)
                        inBookTitle -> title += text
                        inFirstName -> firstName += text
                        inMiddleName -> middleName += text
                        inLastName -> lastName += text
                        inNickname -> nickname += text
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "section" -> {
                            val remaining = currentParagraphText.toString().trim()
                            if (remaining.isNotEmpty()) currentSectionParagraphs.add(remaining)
                            val sectionTitle = currentSectionTitle.trim().ifBlank {
                                "Section ${sectionsList.size + 1}"
                            }
                            sectionsList.add(
                                Fb2Section(
                                    id = currentSectionId,
                                    title = sectionTitle,
                                    paragraphs = currentSectionParagraphs.toList()
                                )
                            )
                            inSection = false
                        }
                        "title" -> inTitle = false
                        "p" -> {
                            inParagraph = false
                            val t = currentParagraphText.toString().trim()
                            if (t.isNotEmpty()) currentSectionParagraphs.add(t)
                        }
                        "subtitle" -> {
                            inSubtitle = false
                            val t = currentParagraphText.toString().trim()
                            if (t.isNotEmpty()) currentSectionParagraphs.add(t)
                        }
                        "binary" -> {
                            inBinary = false
                            if (currentImageId != null) {
                                val b64 = currentImageBase64.toString().trim()
                                if (b64.isNotEmpty()) {
                                    try {
                                        val decoded = Base64.decode(b64, Base64.DEFAULT)
                                        imagesMap[currentImageId!!] = Fb2Image(
                                            id = currentImageId!!,
                                            contentType = currentImageContentType,
                                            data = decoded
                                        )
                                    } catch (_: Exception) {
                                        // Skip malformed binary data
                                    }
                                }
                            }
                            currentImageId = null
                            currentImageBase64 = StringBuilder()
                        }
                        "annotation" -> inAnnotation = false
                        "coverpage" -> inCoverpage = false
                        "book-title" -> inBookTitle = false
                        "first-name" -> inFirstName = false
                        "middle-name" -> inMiddleName = false
                        "last-name" -> inLastName = false
                        "nickname" -> inNickname = false
                    }
                }
            }
            eventType = parser.next()
        }

        // Prefer coverpage-referenced image; fall back to first binary
        coverImageId = localCoverImageId ?: if (imagesMap.isNotEmpty()) {
            imagesMap.keys.first()
        } else {
            null
        }

        val authorParts = listOf(
            firstName.trim(),
            middleName.trim(),
            lastName.trim(),
            nickname.trim()
        ).filter { it.isNotEmpty() }
        val authorName = if (authorParts.isNotEmpty()) authorParts.joinToString(" ") else null

        bookTitle = title.trim()
        bookAuthor = authorName
        bookAnnotation = annotationText.trim().ifBlank { null }
        sections = sectionsList
        images = imagesMap
    }

    private fun closeInternal() {
        currentFile = null
        sections = emptyList()
        images = emptyMap()
        bookTitle = ""
        bookAuthor = null
        bookAnnotation = null
        coverImageId = null
    }
}
