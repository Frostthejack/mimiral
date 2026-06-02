package com.mimiral.app.data.reader

import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.shared.util.Try

/**
 * Result of a chapter text extraction operation.
 */
sealed class ChapterExtractionResult {
    data class Success(
        val text: String,
        val chapterIndex: Int,
        val chapterTitle: String,
        val characterCount: Int
    ) : ChapterExtractionResult()

    data class Error(val message: String, val chapterIndex: Int) : ChapterExtractionResult()
}

/**
 * A cached chapter in the memory pool.
 *
 * @param chapterIndex The chapter's position in the spine.
 * @param title Chapter title.
 * @param text Clean text content (HTML tags removed).
 * @param characterCount Number of characters in the text.
 */
data class CachedChapter(
    val chapterIndex: Int,
    val title: String,
    val text: String,
    val characterCount: Int
)

/**
 * Configuration for the chapter memory pool.
 *
 * @param poolSize Number of chapters to keep in memory (default 3: prev/current/next).
 * @param maxCharactersPerChapter Maximum characters to load per chapter (prevents OOM).
 */
data class ChapterPoolConfig(
    val poolSize: Int = 3,
    val maxCharactersPerChapter: Int = 500_000
) {
    companion object {
        /** Default configuration: 3-chapter pool, 500K chars per chapter. */
        val DEFAULT = ChapterPoolConfig()

        /** Minimal configuration for low-memory devices. */
        val LOW_MEMORY = ChapterPoolConfig(poolSize = 2, maxCharactersPerChapter = 200_000)
    }
}

/**
 * On-demand chapter extractor with a sliding memory pool.
 *
 * Features:
 * - Extracts XHTML content from EPUB ZIP entries on demand
 * - Strips HTML tags to produce clean text strings
 * - Maintains a 3-chapter memory pool (prev/current/next) for smooth navigation
 * - LRU eviction when pool exceeds configured size
 * - Character counting for progress tracking
 * - Thread-safe access via internal mutex
 * - OOM-safe: limits per-chapter character count
 *
 * The memory pool works as follows:
 * - When chapter N is requested, chapters N-1, N, and N+1 are loaded
 * - Previously loaded chapters outside the window are evicted
 * - This ensures smooth forward/backward navigation with minimal memory usage
 *
 * Usage:
 * ```
 * val extractor = ChapterExtractor(epubParser)
 * val result = extractor.getChapter(0)
 * when (result) {
 *     is ChapterExtractionResult.Success -> {
 *         println("Chapter text: ${result.text}")
 *         println("Characters: ${result.characterCount}")
 *     }
 *     is ChapterExtractionResult.Error -> { / handle error / }
 * }
 * // Prefetch next chapter for smooth navigation
 * extractor.prefetch(1)
 * ```
 */
class ChapterExtractor(
    private val epubParser: EpubParser,
    private val config: ChapterPoolConfig = ChapterPoolConfig.DEFAULT
) {

    private val mutex = Mutex()

    /**
     * Memory pool: maps chapter index to cached chapter content.
     * Uses LinkedHashMap for ordered iteration (oldest first).
     */
    private val memoryPool = LinkedHashMap<Int, CachedChapter>(config.poolSize + 1, 0.75f, false)

    /**
     * Total character count across all chapters (computed lazily).
     */
    private var totalCharacters: Long = 0

    /**
     * Whether total character count has been computed.
     */
    private var totalCharactersComputed: Boolean = false

    /**
     * Extracts and returns the text content of a specific chapter.
     *
     * If the chapter is already in the memory pool, returns cached content.
     * Otherwise, reads the XHTML from the EPUB, strips HTML tags, and caches it.
     * After loading, prefetches adjacent chapters for smooth navigation.
     *
     * @param chapterIndex Zero-based chapter index.
     * @return [ChapterExtractionResult.Success] with clean text, or
     *         [ChapterExtractionResult.Error] on failure.
     */
    suspend fun getChapter(chapterIndex: Int): ChapterExtractionResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                // Check memory pool first
                memoryPool[chapterIndex]?.let { cached ->
                    return@withContext ChapterExtractionResult.Success(
                        text = cached.text,
                        chapterIndex = cached.chapterIndex,
                        chapterTitle = cached.title,
                        characterCount = cached.characterCount
                    )
                }

                // Load from EPUB
                val result = loadAndCacheChapter(chapterIndex)
                if (result is ChapterExtractionResult.Success) {
                    // Prefetch adjacent chapters
                    prefetchAdjacent(chapterIndex)
                }
                result
            } catch (e: Exception) {
                ChapterExtractionResult.Error(
                    "Failed to extract chapter $chapterIndex: ${e.message}",
                    chapterIndex
                )
            }
        }
    }

    /**
     * Prefetches a chapter into the memory pool without returning its content.
     *
     * Call this proactively to ensure smooth navigation. For example, when
     * the user is reading chapter N, prefetch chapter N+1.
     *
     * @param chapterIndex Zero-based chapter index to prefetch.
     */
    suspend fun prefetch(chapterIndex: Int) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (chapterIndex >= 0 && !memoryPool.containsKey(chapterIndex)) {
                    loadAndCacheChapter(chapterIndex)
                }
            } catch (_: Exception) {
                // Prefetch failures are non-fatal; the chapter will be loaded on demand
            }
        }
    }

    /**
     * Returns the current contents of the memory pool.
     *
     * Useful for debugging and monitoring memory usage.
     */
    suspend fun getPoolSnapshot(): List<CachedChapter> = mutex.withLock {
        return@withLock memoryPool.values.toList()
    }

    /**
     * Returns the total number of chapters in the loaded EPUB.
     */
    suspend fun getChapterCount(): Int = mutex.withLock {
        return@withLock epubParser.getChapters().size
    }

    /**
     * Returns the total estimated character count across all chapters.
     *
     * This is computed lazily: the first call will iterate through all chapters
     * to count characters. Subsequent calls return the cached total.
     */
    suspend fun getTotalCharacterCount(): Long = mutex.withLock {
        if (!totalCharactersComputed) {
            computeTotalCharacters()
        }
        return@withLock totalCharacters
    }

    /**
     * Clears the memory pool, releasing all cached chapter content.
     *
     * Call this when switching books or when memory pressure is detected.
     */
    suspend fun clearPool() = mutex.withLock {
        memoryPool.clear()
    }

    // ---- Private implementation ----

    /**
     * Loads a chapter from the EPUB, strips HTML, caches it, and returns the result.
     */
    private suspend fun loadAndCacheChapter(chapterIndex: Int): ChapterExtractionResult {
        val pub = epubParser.getPublication()
            ?: return ChapterExtractionResult.Error("No EPUB loaded", chapterIndex)

        val chapters = epubParser.getChapters()
        if (chapterIndex < 0 || chapterIndex >= chapters.size) {
            return ChapterExtractionResult.Error(
                "Chapter index $chapterIndex out of bounds (0..${chapters.size - 1})",
                chapterIndex
            )
        }

        val chapter = chapters[chapterIndex]

        // Find the reading order link for this chapter
        val link = pub.readingOrder.getOrNull(chapterIndex)
            ?: return ChapterExtractionResult.Error(
                "No spine item for chapter $chapterIndex",
                chapterIndex
            )

        // Read the XHTML content from the publication resource
        val resource = pub.get(link)
            ?: return ChapterExtractionResult.Error(
                "Failed to get resource for chapter ${chapter.title}",
                chapterIndex
            )

        val readResult = resource.read()
        val bytes = when (readResult) {
            is Try.Success -> readResult.value
            is Try.Failure -> {
                return ChapterExtractionResult.Error(
                    "Failed to read resource for chapter ${chapter.title}: ${readResult.value.message}",
                    chapterIndex
                )
            }
        }

        if (bytes.isEmpty()) {
            return ChapterExtractionResult.Error(
                "Empty resource for chapter ${chapter.title}",
                chapterIndex
            )
        }

        val xhtml = String(bytes, Charsets.UTF_8)

        // Strip HTML tags and normalize whitespace
        val cleanText = stripHtmlTags(xhtml)

        // Apply character limit to prevent OOM on extremely large chapters
        val truncatedText = if (cleanText.length > config.maxCharactersPerChapter) {
            cleanText.substring(0, config.maxCharactersPerChapter)
        } else {
            cleanText
        }

        // Cache in memory pool
        val cached = CachedChapter(
            chapterIndex = chapterIndex,
            title = chapter.title,
            text = truncatedText,
            characterCount = truncatedText.length
        )
        memoryPool[chapterIndex] = cached

        // Evict oldest entries if pool exceeds size
        evictIfNeeded()

        return ChapterExtractionResult.Success(
            text = truncatedText,
            chapterIndex = chapterIndex,
            chapterTitle = chapter.title,
            characterCount = truncatedText.length
        )
    }

    /**
     * Prefetches chapters adjacent to the given index.
     */
    private suspend fun prefetchAdjacent(chapterIndex: Int) {
        val chapters = epubParser.getChapters()
        val poolIndices = mutableSetOf<Int>()

        // Build the ideal pool window: prev/current/next
        for (offset in -1..1) {
            val idx = chapterIndex + offset
            if (idx in chapters.indices) {
                poolIndices.add(idx)
            }
        }

        // Load missing chapters in the window
        for (idx in poolIndices) {
            if (!memoryPool.containsKey(idx)) {
                try {
                    loadAndCacheChapter(idx)
                } catch (_: Exception) {
                    // Non-fatal: will load on demand
                }
            }
        }

        // Evict chapters outside the window
        val toEvict = memoryPool.keys.filter { it !in poolIndices }
        for (key in toEvict) {
            memoryPool.remove(key)
        }
    }

    /**
     * Evicts oldest entries from the pool if it exceeds the configured size.
     */
    private fun evictIfNeeded() {
        while (memoryPool.size > config.poolSize) {
            val oldestKey = memoryPool.keys.first()
            memoryPool.remove(oldestKey)
        }
    }

    /**
     * Computes total character count across all chapters.
     */
    private suspend fun computeTotalCharacters() {
        val chapters = epubParser.getChapters()
        var total = 0L

        for (i in chapters.indices) {
            val cached = memoryPool[i]
            if (cached != null) {
                total += cached.characterCount
            } else {
                // Load chapter to count characters
                try {
                    val result = loadAndCacheChapterSilent(i)
                    if (result != null) {
                        total += result.characterCount
                    }
                } catch (_: Exception) {
                    // Skip chapters that fail to load
                }
            }
        }

        totalCharacters = total
        totalCharactersComputed = true
    }

    /**
     * Loads a chapter silently (for character counting).
     * Returns null on failure instead of an error result.
     */
    private suspend fun loadAndCacheChapterSilent(chapterIndex: Int): CachedChapter? {
        val pub = epubParser.getPublication() ?: return null
        val chapters = epubParser.getChapters()
        if (chapterIndex !in chapters.indices) return null

        val chapter = chapters[chapterIndex]
        val link = pub.readingOrder.getOrNull(chapterIndex) ?: return null

        val resource = pub.get(link) ?: return null

        val bytes = when (val readResult = resource.read()) {
            is Try.Success -> readResult.value
            is Try.Failure -> return null
        }
        if (bytes.isEmpty()) return null

        val xhtml = String(bytes, Charsets.UTF_8)
        val cleanText = stripHtmlTags(xhtml)
        val truncatedText = if (cleanText.length > config.maxCharactersPerChapter) {
            cleanText.substring(0, config.maxCharactersPerChapter)
        } else {
            cleanText
        }

        val cached = CachedChapter(
            chapterIndex = chapterIndex,
            title = chapter.title,
            text = truncatedText,
            characterCount = truncatedText.length
        )
        memoryPool[chapterIndex] = cached
        evictIfNeeded()
        return cached
    }

    companion object {

        /**
         * Strips HTML tags from XHTML content and normalizes whitespace.
         *
         * This method handles:
         * - Standard HTML tags (<p>, <div>, <span>, <h1>-<h6>, <br>, etc.)
         * - Self-closing tags (<img />, <br />, <hr />)
         * - HTML entities (&amp; &lt; &gt; &nbsp; &#160; etc.)
         * - Script and style blocks (removed entirely)
         * - CDATA sections
         * - Excessive whitespace normalization
         *
         * The result is a clean text string suitable for display and TTS.
         *
         * @param html The raw XHTML content from an EPUB chapter.
         * @return Clean text with HTML tags removed and whitespace normalized.
         */
        fun stripHtmlTags(html: String): String {
            if (html.isBlank()) return ""

            var text = html

            // Remove CDATA wrappers
            text = text.replace(Regex("""<!\[CDATA\[.*?\]\]>""", RegexOption.DOT_MATCHES_ALL), "")

            // Remove script blocks entirely
            text = text.replace(
                Regex(
                    """<script[^>]*>.*?</script>""",
                    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
                ),
                ""
            )

            // Remove style blocks entirely
            text = text.replace(
                Regex(
                    """<style[^>]*>.*?</style>""",
                    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
                ),
                ""
            )

            // Remove HTML comments
            text = text.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")

            // Convert <br>, <br/>, <br /> to newlines
            text = text.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")

            // Convert <p> and </p> to double newlines (paragraph breaks)
            text = text.replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n\n")
            text = text.replace(Regex("""<p[^>]*>""", RegexOption.IGNORE_CASE), "")

            // Convert heading tags to newlines with text
            text = text.replace(Regex("""</h[1-6]>""", RegexOption.IGNORE_CASE), "\n\n")
            text = text.replace(Regex("""<h[1-6][^>]*>""", RegexOption.IGNORE_CASE), "")

            // Convert <div> and </div> to newlines
            text = text.replace(Regex("""</div>""", RegexOption.IGNORE_CASE), "\n")
            text = text.replace(Regex("""<div[^>]*>""", RegexOption.IGNORE_CASE), "")

            // Convert list items
            text = text.replace(Regex("""<li[^>]*>""", RegexOption.IGNORE_CASE), "\n\u2022 ")
            text = text.replace(Regex("""</li>""", RegexOption.IGNORE_CASE), "")

            // Remove all remaining HTML tags
            text = text.replace(Regex("""<[^>]+>"""), "")

            // Decode common HTML entities
            text = decodeHtmlEntities(text)

            // Normalize whitespace: collapse multiple spaces, preserve paragraph breaks
            text = text.replace(Regex("""[ \t]+"""), " ") // Collapse horizontal whitespace
            text = text.replace(Regex("""\n{3,}"""), "\n\n") // Max 2 consecutive newlines
            text = text.replace(Regex(""" +\n"""), "\n") // Remove trailing spaces before newlines
            text = text.replace(Regex("""\n +"""), "\n") // Remove leading spaces after newlines

            return text.trim()
        }

        /**
         * Decodes common HTML entities to their character equivalents.
         */
        private fun decodeHtmlEntities(text: String): String {
            return text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&#8211;", "\u2013") // en-dash
                .replace("&#8212;", "\u2014") // em-dash
                .replace("&#8216;", "\u2018") // left single quote
                .replace("&#8217;", "\u2019") // right single quote
                .replace("&#8220;", "\u201C") // left double quote
                .replace("&#8221;", "\u201D") // right double quote
                .replace("&#8230;", "\u2026") // ellipsis
                .replace("&#8217;", "\u2019") // right single quotation mark
                .replace("&#8222;", "\u201E") // double low-9 quotation mark
                .replace("&#176;", "\u00B0") // degree sign
                .replace("&#215;", "\u00D7") // multiplication sign
                .replace("&#247;", "\u00F7") // division sign
                .replace("&#169;", "\u00A9") // copyright
                .replace("&#174;", "\u00AE") // registered trademark
                .replace("&#8482;", "\u2122") // trademark
                .replace("&#8226;", "\u2022") // bullet
                .replace("&#8203;", "") // zero-width space (remove)
                .replace("&#8201;", " ") // thin space
                .replace("&#8194;", " ") // en space
                .replace("&#8195;", " ") // em space
                // Handle numeric entities: &#123;
                .replace(Regex("""&#(\d+);""")) { matchResult ->
                    val code = matchResult.groupValues[1].toIntOrNull()
                    if (code != null && code in 0..0x10FFFF) {
                        String(Character.toChars(code))
                    } else {
                        matchResult.value
                    }
                }
                // Handle hex entities: &#xAB;
                .replace(Regex("""&#x([0-9a-fA-F]+);""")) { matchResult ->
                    val code = matchResult.groupValues[1].toIntOrNull(16)
                    if (code != null && code in 0..0x10FFFF) {
                        String(Character.toChars(code))
                    } else {
                        matchResult.value
                    }
                }
        }
    }
}
