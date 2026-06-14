package com.mimiral.app.tts

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.mimiral.app.data.local.settings.TTSSettingsRepository
import com.mimiral.app.data.reader.Sentence
import com.mimiral.app.data.reader.SentenceBoundaryDetector
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.firstOrNull

enum class TTSState {
    IDLE,
    INITIALIZING,
    READY,
    PLAYING,
    PAUSED,
    STOPPED
}

data class TTSQueueItem(
    val utteranceId: String = UUID.randomUUID().toString(),
    val text: String,
    val locale: Locale = Locale.getDefault(),
    /** Character offset of this utterance's start in the full text, for word-highlight mapping. */
    val startOffset: Int = 0
)

data class TTSSettings(
    var speechRate: Float = 1.0f,
    var pitch: Float = 1.0f,
    var locale: Locale = Locale.getDefault()
)

data class ChapterBoundary(
    val chapterIndex: Int,
    val title: String,
    val startOffset: Int,
    val endOffset: Int,
    val text: String
)

data class ParagraphBoundary(
    val startOffset: Int,
    val endOffset: Int,
    val text: String
)

class TTSManager(
    private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TTSManager"
    }

    private var ttsEngine: TextToSpeech? = null

    @Volatile
    private var _state: TTSState = TTSState.IDLE
    val state: TTSState get() = _state

    private val queue = ArrayDeque<TTSQueueItem>()
    private val queueLock = Any()
    private val settings = TTSSettings()

    /** The utterance currently being spoken (removed from queue but not yet finished). */
    @Volatile
    private var currentItem: TTSQueueItem? = null

    val preprocessor = TTSPreprocessor()

    private val sentenceDetector = SentenceBoundaryDetector()

    private var fullText: String = ""
    private var currentOffset: Int = 0

    private var chapterBoundaries: List<ChapterBoundary> = emptyList()
    private var paragraphBoundaries: List<ParagraphBoundary> = emptyList()

    /** Start offset (in full text) of the utterance currently being spoken. */
    @Volatile
    private var currentUtteranceOffset: Int = 0

    @Volatile
    var onInitialized: ((Boolean) -> Unit)? = null
    val onUtteranceStarted: CopyOnWriteArrayList<(String) -> Unit> = CopyOnWriteArrayList()
    val onUtteranceDone: CopyOnWriteArrayList<(String, Boolean) -> Unit> = CopyOnWriteArrayList()
    val onRangeProgress: CopyOnWriteArrayList<(String, Int, Int) -> Unit> = CopyOnWriteArrayList()
    val onSkip: CopyOnWriteArrayList<(String) -> Unit> = CopyOnWriteArrayList()

    /**
     * Callback fired when the active sentence changes during TTS playback.
     *
     * @param sentence The sentence currently being read, or null if playback
     *                 has stopped/paused and the highlight should be cleared.
     */
    val onSentenceChanged: CopyOnWriteArrayList<(Sentence?) -> Unit> = CopyOnWriteArrayList()

    /**
     * Callback fired when the active word range changes during TTS playback.
     *
     * Provides character-level [start, end) offsets into the full text for the
     * word currently being spoken, or null if playback stopped/paused.
     *
     * Driven by the TTS engine's onRangeStart callback which reports word boundaries.
     */
    val onWordChanged: CopyOnWriteArrayList<(Int, Int) -> Unit> = CopyOnWriteArrayList()

    /** Callback fired when word highlight should be cleared (stop/pause). */
    val onWordCleared: CopyOnWriteArrayList<() -> Unit> = CopyOnWriteArrayList()

    /** Sentences extracted from the currently playing text. */
    @Volatile
    private var currentSentences: List<Sentence> = emptyList()

    /** Index of the sentence currently being read, or -1 if none. */
    @Volatile
    private var currentSentenceIndex: Int = -1

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = ttsEngine ?: return
            applySettings(engine)
            _state = TTSState.READY
            attachUtteranceListener(engine)
            Log.d(TAG, "TTS engine initialized successfully")
            onInitialized?.invoke(true)
        } else {
            _state = TTSState.IDLE
            ttsEngine = null
            Log.e(TAG, "TTS engine initialization failed with status: $status")
            onInitialized?.invoke(false)
        }
    }

    fun initialize(onInitialized: ((Boolean) -> Unit)? = null) {
        if (_state == TTSState.INITIALIZING || _state == TTSState.READY ||
            _state == TTSState.PLAYING || _state == TTSState.PAUSED
        ) {
            Log.d(TAG, "TTS already initialized or initializing, state=$_state")
            onInitialized?.invoke(_state != TTSState.IDLE && _state != TTSState.STOPPED)
            return
        }
        if (onInitialized != null) {
            this.onInitialized = onInitialized
        }
        _state = TTSState.INITIALIZING
        Log.d(TAG, "Initializing TTS engine...")
        ttsEngine = TextToSpeech(context, this)
    }

    fun setFullText(text: String) {
        fullText = text
        currentOffset = 0
    }

    fun updateOffset(offset: Int) {
        currentOffset = offset.coerceIn(0, fullText.length)
    }

    fun setChapterBoundaries(boundaries: List<ChapterBoundary>) {
        chapterBoundaries = boundaries
        Log.d(TAG, "Chapter boundaries set: ${boundaries.size} chapters")
    }

    fun setParagraphBoundaries(boundaries: List<ParagraphBoundary>) {
        paragraphBoundaries = boundaries
        Log.d(TAG, "Paragraph boundaries set: ${boundaries.size} paragraphs")
    }

    fun getCurrentOffset(): Int = currentOffset
    fun getFullText(): String = fullText

    fun play(text: String, locale: Locale = settings.locale) {
        if (text.isBlank()) return
        fullText = text
        currentOffset = 0
        currentUtteranceOffset = 0
        currentItem = null

        // Extract sentence boundaries for highlighting
        currentSentences = sentenceDetector.findSentences(fullText)
        currentSentenceIndex = if (currentSentences.isNotEmpty()) 0 else -1
        Log.d(
            TAG,
            "play: extracted ${currentSentences.size} sentences from ${fullText.length} chars"
        )

        // Enqueue each sentence as its own utterance — Android TTS caps single utterances
        // at TextToSpeech.getMaxSpeechInputLength() (typically 4000 chars). Sending more
        // returns ERROR_INVALID_REQUEST (-8) and produces no audio.
        synchronized(queueLock) {
            if (currentSentences.isNotEmpty()) {
                for (sentence in currentSentences) {
                    val sentenceText = preprocessor.preprocess(sentence.text)
                    if (sentenceText.isNotBlank()) {
                        queue.addLast(
                            TTSQueueItem(
                                text = sentenceText,
                                locale = locale,
                                startOffset = sentence.start
                            )
                        )
                    }
                }
            } else {
                // No sentence boundaries detected; chunk to stay under 4000-char limit
                val processedText = preprocessor.preprocess(text)
                var offset = 0
                while (offset < processedText.length) {
                    val end = minOf(offset + 3500, processedText.length)
                    val chunk = processedText.substring(offset, end).trim()
                    if (chunk.isNotBlank()) {
                        queue.addLast(
                            TTSQueueItem(text = chunk, locale = locale, startOffset = offset)
                        )
                    }
                    offset = end
                }
            }
        }

        // Fire callback for first sentence
        if (currentSentenceIndex >= 0) {
            currentSentences.getOrNull(currentSentenceIndex)?.let { sentence ->
                onSentenceChanged.forEach { cb -> cb(sentence) }
            }
        }

        when (_state) {
            TTSState.READY -> speakNext()
            TTSState.PLAYING, TTSState.PAUSED, TTSState.INITIALIZING -> { }
            TTSState.IDLE -> { Log.w(TAG, "TTS not initialized. Call initialize() first.") }
            TTSState.STOPPED -> { Log.w(TAG, "TTS has been shut down. Call initialize() again.") }
        }
    }

    fun playNow(text: String, locale: Locale = settings.locale) {
        if (text.isBlank()) return
        stop()
        play(text, locale)
    }

    fun playFromOffset(offset: Int) {
        val coerced = offset.coerceIn(0, fullText.length)
        currentOffset = coerced
        val remainingText = fullText.substring(coerced)
        if (remainingText.isBlank()) {
            Log.w(TAG, "playFromOffset: no text remaining at offset $coerced")
            return
        }
        playNow(remainingText)
    }

    fun pause() {
        if (_state != TTSState.PLAYING) return
        // Capture and clear current item before stopping engine so the Done callback
        // (which fires synchronously on some devices) doesn't advance to the next item.
        val item = currentItem
        currentItem = null
        ttsEngine?.stop()
        _state = TTSState.PAUSED
        // Re-enqueue the interrupted utterance at the front so resume restarts it
        // from the beginning rather than jumping to the next sentence.
        if (item != null) {
            synchronized(queueLock) { queue.addFirst(item) }
        }
        currentSentenceIndex = -1
        onSentenceChanged.forEach { cb -> cb(null) }
        onWordCleared.forEach { cb -> cb() }
        Log.d(TAG, "TTS paused, queue size=" + synchronized(queueLock) { queue.size })
    }

    fun resume() {
        if (_state != TTSState.PAUSED) return
        _state = TTSState.READY
        // Restore sentence highlight for current position
        if (currentSentenceIndex >= 0) {
            currentSentences.getOrNull(currentSentenceIndex)?.let { sentence ->
                onSentenceChanged.forEach { cb -> cb(sentence) }
            }
        }
        speakNext()
        Log.d(TAG, "TTS resumed")
    }

    fun stop() {
        ttsEngine?.stop()
        currentItem = null
        synchronized(queueLock) { queue.clear() }
        // Clear sentence highlight on stop
        currentSentenceIndex = -1
        currentSentences = emptyList()
        onSentenceChanged.forEach { cb -> cb(null) }
        onWordCleared.forEach { cb -> cb() }
        if (_state == TTSState.PLAYING || _state == TTSState.PAUSED) {
            _state = TTSState.READY
        }
        Log.d(TAG, "TTS stopped, queue cleared")
    }

    fun setSpeechRate(rate: Float) {
        settings.speechRate = rate.coerceIn(0.1f, 4.0f)
        ttsEngine?.setSpeechRate(settings.speechRate)
    }

    fun setPitch(pitch: Float) {
        settings.pitch = pitch.coerceIn(0.1f, 4.0f)
        ttsEngine?.setPitch(settings.pitch)
    }

    fun setLocale(locale: Locale) {
        settings.locale = locale
        ttsEngine?.language = locale
    }

    fun getAvailableVoices(): Set<Voice> {
        return ttsEngine?.voices ?: emptySet()
    }

    fun setVoice(voice: Voice): Boolean {
        val engine = ttsEngine
        if (engine == null) {
            Log.w(TAG, "setVoice: ttsEngine is null, cannot set voice")
            return false
        }
        val result = engine.setVoice(voice)
        Log.d(TAG, "setVoice: voice='${voice.name}', locale=${voice.locale}, result=$result")
        return result == TextToSpeech.SUCCESS
    }

    /**
     * Reset to the default voice by setting the language back to the default locale.
     * Android TTS doesn't have a direct "clear voice" API, so we set the language
     * which implicitly resets the voice to the default for that locale.
     */
    fun setVoiceToDefault(): Boolean {
        val engine = ttsEngine
        if (engine == null) {
            Log.w(TAG, "setVoiceToDefault: ttsEngine is null")
            return false
        }
        val defaultLocale = Locale.getDefault()
        val result = engine.setVoice(
            engine.voices?.firstOrNull {
                it.locale.language == defaultLocale.language &&
                    it.locale.country == defaultLocale.country &&
                    it.quality >= Voice.QUALITY_NORMAL
            } ?: return false
        )
        Log.d(TAG, "setVoiceToDefault: result=$result")
        return result == TextToSpeech.SUCCESS
    }

    fun getSpeechRate(): Float = settings.speechRate
    fun getPitch(): Float = settings.pitch
    fun getVoiceName(): String = ttsEngine?.voice?.name ?: ""
    fun getAvailableLocales(): List<Locale> {
        return getAvailableVoices()
            .map { it.locale }
            .distinctBy { it.toLanguageTag() }
            .sortedBy { it.displayName }
    }

    fun isLanguageAvailable(locale: Locale): Boolean {
        val engine = ttsEngine ?: return false
        val result = engine.isLanguageAvailable(locale)
        return result == TextToSpeech.LANG_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    fun shutdown() {
        Log.d(TAG, "Shutting down TTS engine")
        ttsEngine?.stop()
        ttsEngine?.shutdown()
        ttsEngine = null
        currentItem = null
        synchronized(queueLock) { queue.clear() }
        currentSentences = emptyList()
        currentSentenceIndex = -1
        _state = TTSState.STOPPED
    }

    // --- Skip controls ---

    fun skipSentenceForward() {
        if (fullText.isBlank()) return
        val sentences = sentenceDetector.findSentences(fullText)
        val nextSentence = sentences.find { it.start > currentOffset }
        if (nextSentence != null) {
            Log.d(TAG, "Skip sentence forward to offset ${nextSentence.start}")
            onSkip.forEach { it("sentence_forward") }
            playFromOffset(nextSentence.start)
        } else {
            Log.d(TAG, "Skip sentence forward: no more sentences, trying paragraph skip")
            if (!skipParagraphForward()) {
                Log.d(TAG, "Skip sentence forward: already at end of text")
            }
        }
    }

    fun skipSentenceBackward() {
        if (fullText.isBlank()) return
        val sentences = sentenceDetector.findSentences(fullText)
        val currentSentence = sentences.find { it.start <= currentOffset && it.end > currentOffset }
        val prevSentence = sentences.lastOrNull { it.end <= currentOffset }
        when {
            currentSentence != null && currentSentence.start < currentOffset -> {
                Log.d(
                    TAG,
                    "Skip sentence backward to current sentence start ${currentSentence.start}"
                )
                onSkip.forEach { it("sentence_backward") }
                playFromOffset(currentSentence.start)
            }
            prevSentence != null -> {
                Log.d(
                    TAG,
                    "Skip sentence backward to previous sentence start ${prevSentence.start}"
                )
                onSkip.forEach { it("sentence_backward") }
                playFromOffset(prevSentence.start)
            }
            else -> {
                Log.d(TAG, "Skip sentence backward: already at beginning")
            }
        }
    }

    fun skipParagraphForward(): Boolean {
        if (fullText.isBlank() || paragraphBoundaries.isEmpty()) return false
        val nextParagraph = paragraphBoundaries.find { it.startOffset > currentOffset }
        return if (nextParagraph != null) {
            Log.d(TAG, "Skip paragraph forward to offset ${nextParagraph.startOffset}")
            onSkip.forEach { it("paragraph_forward") }
            playFromOffset(nextParagraph.startOffset)
            true
        } else {
            Log.d(TAG, "Skip paragraph forward: already at last paragraph")
            false
        }
    }

    fun skipParagraphBackward(): Boolean {
        if (fullText.isBlank() || paragraphBoundaries.isEmpty()) return false
        val currentParagraph = paragraphBoundaryAt(currentOffset)
        val prevParagraph = paragraphBoundaries.lastOrNull { it.endOffset <= currentOffset }
        return when {
            currentParagraph != null && currentParagraph.startOffset < currentOffset -> {
                Log.d(
                    TAG,
                    "Skip paragraph backward to current paragraph " +
                        "start ${currentParagraph.startOffset}"
                )
                onSkip.forEach { it("paragraph_backward") }
                playFromOffset(currentParagraph.startOffset)
                true
            }
            prevParagraph != null -> {
                Log.d(
                    TAG,
                    "Skip paragraph backward to previous paragraph " +
                        "start ${prevParagraph.startOffset}"
                )
                onSkip.forEach { it("paragraph_backward") }
                playFromOffset(prevParagraph.startOffset)
                true
            }
            else -> {
                Log.d(TAG, "Skip paragraph backward: already at first paragraph")
                false
            }
        }
    }

    fun skipChapterForward() {
        if (chapterBoundaries.isEmpty()) return
        val currentChapter = chapterBoundaryAt(currentOffset)
        val nextChapter = if (currentChapter != null) {
            chapterBoundaries.getOrNull(currentChapter.chapterIndex + 1)
        } else {
            chapterBoundaries.firstOrNull { it.startOffset > currentOffset }
        }
        if (nextChapter != null) {
            Log.d(
                TAG,
                "Skip chapter forward to chapter ${nextChapter.chapterIndex}: ${nextChapter.title}"
            )
            onSkip.forEach { it("chapter_forward") }
            playFromOffset(nextChapter.startOffset)
        } else {
            Log.d(TAG, "Skip chapter forward: already at last chapter")
        }
    }

    fun skipChapterBackward() {
        if (chapterBoundaries.isEmpty()) return
        val currentChapter = chapterBoundaryAt(currentOffset)
        if (currentChapter == null) {
            Log.d(TAG, "Skip chapter backward: no current chapter found")
            return
        }
        val atChapterStart = currentOffset <= currentChapter.startOffset + 1
        val prevChapter = if (atChapterStart && currentChapter.chapterIndex > 0) {
            chapterBoundaries.getOrNull(currentChapter.chapterIndex - 1)
        } else {
            currentChapter
        }
        if (prevChapter != null) {
            Log.d(
                TAG,
                "Skip chapter backward to chapter ${prevChapter.chapterIndex}: ${prevChapter.title}"
            )
            onSkip.forEach { it("chapter_backward") }
            playFromOffset(prevChapter.startOffset)
        } else {
            Log.d(TAG, "Skip chapter backward: already at first chapter")
        }
    }

    // --- Private helpers ---

    private fun paragraphBoundaryAt(offset: Int): ParagraphBoundary? {
        return paragraphBoundaries.find { offset >= it.startOffset && offset < it.endOffset }
    }

    private fun chapterBoundaryAt(offset: Int): ChapterBoundary? {
        return chapterBoundaries.find { offset >= it.startOffset && offset < it.endOffset }
    }

    private fun speakNext() {
        val engine = ttsEngine ?: return
        val item = synchronized(queueLock) {
            if (queue.isEmpty()) {
                _state = TTSState.READY
                currentSentenceIndex = -1
                onSentenceChanged.forEach { cb -> cb(null) }
                onWordCleared.forEach { cb -> cb() }
                return
            }
            queue.removeFirst()
        }
        currentItem = item
        currentUtteranceOffset = item.startOffset
        _state = TTSState.PLAYING
        if (engine.language != item.locale) {
            engine.language = item.locale
        }
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            engine.speak(item.text, TextToSpeech.QUEUE_FLUSH, null, item.utteranceId)
        } else {
            val params = java.util.HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = item.utteranceId
            engine.speak(item.text, TextToSpeech.QUEUE_FLUSH, params)
        }
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "speak() failed for utterance " + item.utteranceId)
            onUtteranceDone.forEach { cb -> cb(item.utteranceId, false) }
            speakNext()
        }
    }

    private fun applySettings(engine: TextToSpeech) {
        engine.setSpeechRate(settings.speechRate)
        engine.setPitch(settings.pitch)
        engine.language = settings.locale
        // Route TTS audio through STREAM_MUSIC so volume keys control media volume.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            engine.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
    }

    /**
     * Load persisted settings from a TTSSettingsRepository.
     * Call this after initialization to apply saved preferences.
     * This is a suspend function — call from a coroutine scope to avoid blocking the calling thread.
     */
    suspend fun loadPersistedSettings(repository: TTSSettingsRepository) {
        val persisted = repository.settings.firstOrNull() ?: return
        settings.speechRate = persisted.speechRate
        settings.pitch = persisted.pitch
        if (persisted.localeTag.isNotBlank()) {
            settings.locale = Locale.forLanguageTag(persisted.localeTag)
        }
        ttsEngine?.let { engine ->
            engine.setSpeechRate(persisted.speechRate)
            engine.setPitch(persisted.pitch)
            if (persisted.localeTag.isNotBlank()) {
                engine.language = settings.locale
            }
            if (persisted.voiceName.isNotBlank()) {
                val voice = getAvailableVoices().find { it.name == persisted.voiceName }
                if (voice != null) {
                    setVoice(voice)
                }
            }
        }
    }

    private fun attachUtteranceListener(engine: TextToSpeech) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceId?.let { id -> onUtteranceStarted.forEach { cb -> cb(id) } }
                }
                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { id -> onUtteranceDone.forEach { cb -> cb(id, true) } }
                    if (_state == TTSState.PLAYING || _state == TTSState.READY) { speakNext() }
                }

                @Deprecated("Deprecated in API")
                override fun onError(utteranceId: String?) {
                    onError(utteranceId, TextToSpeech.ERROR)
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    utteranceId?.let { id ->
                        Log.e(TAG, "Utterance error: " + id + ", code=" + errorCode)
                        onUtteranceDone.forEach { cb -> cb(id, false) }
                    }
                    if (_state == TTSState.PLAYING || _state == TTSState.READY) { speakNext() }
                }
                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    // Adjust offsets from utterance-relative to full-text-relative
                    val fullStart = currentUtteranceOffset + start
                    val fullEnd = currentUtteranceOffset + end
                    utteranceId?.let { id ->
                        onRangeProgress.forEach { cb ->
                            cb(
                                id,
                                fullStart,
                                fullEnd
                            )
                        }
                    }
                    updateCurrentSentence(fullStart)
                    onWordChanged.forEach { cb -> cb(fullStart, fullEnd) }
                }
            })
        }
    }

    /**
     * Update the current sentence based on the character offset reported
     * by the TTS engine's onRangeStart callback.
     *
     * Called internally when range progress is reported. Finds which sentence
     * contains the given offset and fires [onSentenceChanged] if it changed.
     */
    private fun updateCurrentSentence(charOffset: Int) {
        if (currentSentences.isEmpty()) return

        // Find which sentence contains this character offset
        val newIndex = currentSentences.indexOfFirst { sentence ->
            charOffset >= sentence.start && charOffset < sentence.end
        }

        if (newIndex >= 0 && newIndex != currentSentenceIndex) {
            currentSentenceIndex = newIndex
            val sentence = currentSentences[newIndex]
            Log.d(
                TAG,
                "Sentence changed: #$newIndex " +
                    "[${sentence.start}-${sentence.end}] " +
                    "\"${sentence.text.take(40)}...\""
            )
            onSentenceChanged.forEach { cb -> cb(sentence) }
        }
    }
}
