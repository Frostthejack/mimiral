package com.mimiral.app.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mimiral.app.MainActivity
import com.mimiral.app.R
import java.util.Locale

class TTSService : Service() {

    companion object {
        private const val TAG = "TTSService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tts_playback"

        const val ACTION_PLAY = "com.mimiral.app.tts.ACTION_PLAY"
        const val ACTION_PAUSE = "com.mimiral.app.tts.ACTION_PAUSE"
        const val ACTION_RESUME = "com.mimiral.app.tts.ACTION_RESUME"
        const val ACTION_STOP = "com.mimiral.app.tts.ACTION_STOP"
        const val ACTION_TOGGLE = "com.mimiral.app.tts.ACTION_TOGGLE"
        const val ACTION_START_SLEEP_TIMER = "com.mimiral.app.tts.ACTION_START_SLEEP_TIMER"
        const val ACTION_CANCEL_SLEEP_TIMER = "com.mimiral.app.tts.ACTION_CANCEL_SLEEP_TIMER"

        // Skip actions
        const val ACTION_SKIP_SENTENCE_FORWARD = "com.mimiral.app.tts.ACTION_SKIP_SENTENCE_FORWARD"
        const val ACTION_SKIP_SENTENCE_BACKWARD = "com.mimiral.app.tts.ACTION_SKIP_SENTENCE_BACKWARD"
        const val ACTION_SKIP_PARAGRAPH_FORWARD = "com.mimiral.app.tts.ACTION_SKIP_PARAGRAPH_FORWARD"
        const val ACTION_SKIP_PARAGRAPH_BACKWARD = "com.mimiral.app.tts.ACTION_SKIP_PARAGRAPH_BACKWARD"
        const val ACTION_SKIP_CHAPTER_FORWARD = "com.mimiral.app.tts.ACTION_SKIP_CHAPTER_FORWARD"
        const val ACTION_SKIP_CHAPTER_BACKWARD = "com.mimiral.app.tts.ACTION_SKIP_CHAPTER_BACKWARD"

        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_LOCALE = "extra_locale"
        const val EXTRA_SLEEP_MINUTES = "extra_sleep_minutes"

        // Broadcast sent when sleep timer state changes
        const val ACTION_SLEEP_TIMER_STATE = "com.mimiral.app.tts.ACTION_SLEEP_TIMER_STATE"
        const val EXTRA_SLEEP_REMAINING_SECONDS = "extra_sleep_remaining_seconds"
        const val EXTRA_SLEEP_TIMER_ACTIVE = "extra_sleep_timer_active"

        // Broadcast sent when a skip occurs
        const val ACTION_SKIP_PERFORMED = "com.mimiral.app.tts.ACTION_SKIP_PERFORMED"
        const val EXTRA_SKIP_TYPE = "extra_skip_type"

        /** Broadcast: sentence-level progress for TTS highlighting. */
        const val ACTION_TTS_SENTENCE = "com.mimiral.app.tts.ACTION_TTS_SENTENCE"
        const val EXTRA_SENTENCE_START = "extra_sentence_start"
        const val EXTRA_SENTENCE_END = "extra_sentence_end"
        const val EXTRA_SENTENCE_TEXT = "extra_sentence_text"
        const val EXTRA_SENTENCE_ACTIVE = "extra_sentence_active"

        fun createPlayIntent(context: Context, text: String? = null): Intent {
            return Intent(context, TTSService::class.java).apply {
                action = ACTION_PLAY
                text?.let { putExtra(EXTRA_TEXT, it) }
            }
        }

        fun createPauseIntent(context: Context): Intent {
            return Intent(context, TTSService::class.java).apply { action = ACTION_PAUSE }
        }

        fun createResumeIntent(context: Context): Intent {
            return Intent(context, TTSService::class.java).apply { action = ACTION_RESUME }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, TTSService::class.java).apply { action = ACTION_STOP }
        }

        fun createToggleIntent(context: Context): Intent {
            return Intent(context, TTSService::class.java).apply { action = ACTION_TOGGLE }
        }

        fun createSleepTimerIntent(context: Context, minutes: Int): Intent {
            return Intent(context, TTSService::class.java).apply {
                action = ACTION_START_SLEEP_TIMER
                putExtra(EXTRA_SLEEP_MINUTES, minutes)
            }
        }

        fun createCancelSleepTimerIntent(context: Context): Intent {
            return Intent(context, TTSService::class.java).apply { action = ACTION_CANCEL_SLEEP_TIMER }
        }

        fun createSkipSentenceForwardIntent(context: Context): Intent {
            return Intent(context, TTSService::class.java).apply { action = ACTION_SKIP_SENTENCE_FORWARD }
        }

        fun createSkipSentenceBackwardIntent(context: Context): Intent {
            return Intent(context, TTSService::class.java).apply { action = ACTION_SKIP_SENTENCE_BACKWARD }
        }

        fun createSkipParagraphForwardIntent(context: Context): Intent {
            return Intent(context, TTSService::class.java).apply { action = ACTION_SKIP_PARAGRAPH_FORWARD }
        }

        fun createSkipParagraphBackwardIntent(context: Context): Intent {
            return Intent(context, TTSService::class.java).apply { action = ACTION_SKIP_PARAGRAPH_BACKWARD }
        }

        fun createSkipChapterForwardIntent(context: Context): Intent {
            return Intent(context, TTSService::class.java).apply { action = ACTION_SKIP_CHAPTER_FORWARD }
        }

        fun createSkipChapterBackwardIntent(context: Context): Intent {
            return Intent(context, TTSService::class.java).apply { action = ACTION_SKIP_CHAPTER_BACKWARD }
        }
    }

    private val binder = TTSBinder()
    private var ttsManager: TTSManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var notificationManager: NotificationManager? = null
    private var currentText: String = ""

    // Sleep timer state
    private var sleepTimerActive = false
    private var sleepTimerRemainingSeconds = 0
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null

    inner class TTSBinder : Binder() {
        fun getService(): TTSService = this@TTSService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        ttsManager = TTSManager(applicationContext).apply {
            onInitialized = { success ->
                if (success) {
                    Log.d(TAG, "TTS engine ready in service")
                } else {
                    Log.e(TAG, "TTS engine failed to initialize in service")
                    stopSelf()
                }
            }
            onUtteranceStarted.add { id -> Log.d(TAG, "Utterance started: $id") }
            onUtteranceDone.add { id, success ->
                Log.d(TAG, "Utterance done: $id, success=$success")
            }
            onSkip.add { skipType ->
                broadcastSkipPerformed(skipType)
            }
            onSentenceChanged.add { sentence ->
                broadcastSentence(sentence)
            }
        }
        ttsManager?.initialize()

        initMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")

        when (intent?.action) {
            ACTION_PLAY -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                if (text.isNotBlank()) {
                    currentText = text
                }
                handlePlay()
            }
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStop()
            ACTION_TOGGLE -> handleToggle()
            ACTION_START_SLEEP_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_SLEEP_MINUTES, 0)
                if (minutes > 0) startSleepTimer(minutes)
            }
            ACTION_CANCEL_SLEEP_TIMER -> cancelSleepTimer()
            ACTION_SKIP_SENTENCE_FORWARD -> handleSkipSentenceForward()
            ACTION_SKIP_SENTENCE_BACKWARD -> handleSkipSentenceBackward()
            ACTION_SKIP_PARAGRAPH_FORWARD -> handleSkipParagraphForward()
            ACTION_SKIP_PARAGRAPH_BACKWARD -> handleSkipParagraphBackward()
            ACTION_SKIP_CHAPTER_FORWARD -> handleSkipChapterForward()
            ACTION_SKIP_CHAPTER_BACKWARD -> handleSkipChapterBackward()
            else -> {
                if (ttsManager?.state == TTSState.PLAYING) {
                    showNotification()
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        cancelSleepTimerInternal()
        ttsManager?.stop()
        ttsManager?.shutdown()
        ttsManager = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    // --- Public API for setting boundary data ---

    fun setFullText(text: String) {
        ttsManager?.setFullText(text)
    }

    fun setChapterBoundaries(boundaries: List<ChapterBoundary>) {
        ttsManager?.setChapterBoundaries(boundaries)
    }

    fun setParagraphBoundaries(boundaries: List<ParagraphBoundary>) {
        ttsManager?.setParagraphBoundaries(boundaries)
    }

    // --- Sleep Timer ---

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimerInternal()
        sleepTimerActive = true
        sleepTimerRemainingSeconds = minutes * 60
        Log.d(TAG, "Sleep timer started: $minutes minutes ($sleepTimerRemainingSeconds seconds)")

        broadcastSleepTimerState()

        sleepTimerRunnable = object : Runnable {
            override fun run() {
                if (!sleepTimerActive) return
                sleepTimerRemainingSeconds--
                if (sleepTimerRemainingSeconds <= 0) {
                    Log.d(TAG, "Sleep timer expired — stopping TTS")
                    showNotification()
                    handleStop()
                    cancelSleepTimerInternal()
                    return
                }
                updateNotificationWithSleepTimer()
                sleepTimerHandler.postDelayed(this, 1000)
            }
        }
        sleepTimerHandler.postDelayed(sleepTimerRunnable!!, 1000)
        showNotification()
    }

    fun cancelSleepTimer() {
        cancelSleepTimerInternal()
    }

    private fun cancelSleepTimerInternal() {
        sleepTimerRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        sleepTimerRunnable = null
        val wasActive = sleepTimerActive
        sleepTimerActive = false
        sleepTimerRemainingSeconds = 0
        if (wasActive) {
            Log.d(TAG, "Sleep timer cancelled")
            broadcastSleepTimerState()
            showNotification()
        }
    }

    private fun broadcastSleepTimerState() {
        val broadcastIntent = Intent(ACTION_SLEEP_TIMER_STATE).apply {
            putExtra(EXTRA_SLEEP_TIMER_ACTIVE, sleepTimerActive)
            putExtra(EXTRA_SLEEP_REMAINING_SECONDS, sleepTimerRemainingSeconds)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun broadcastSkipPerformed(skipType: String) {
        val broadcastIntent = Intent(ACTION_SKIP_PERFORMED).apply {
            putExtra(EXTRA_SKIP_TYPE, skipType)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun broadcastSentence(sentence: com.mimiral.app.data.reader.Sentence?) {
        val broadcastIntent = Intent(ACTION_TTS_SENTENCE).apply {
            putExtra(EXTRA_SENTENCE_ACTIVE, sentence != null)
            if (sentence != null) {
                putExtra(EXTRA_SENTENCE_START, sentence.start)
                putExtra(EXTRA_SENTENCE_END, sentence.end)
                putExtra(EXTRA_SENTENCE_TEXT, sentence.text)
            }
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun updateNotificationWithSleepTimer() {
        if (sleepTimerActive) {
            notificationManager?.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    fun getSleepTimerRemainingSeconds(): Int = sleepTimerRemainingSeconds
    fun isSleepTimerActive(): Boolean = sleepTimerActive

    // --- Public API for bound clients ---

    fun play(text: String) {
        currentText = text
        handlePlay()
    }

    fun pause() = handlePause()
    fun resume() = handleResume()
    fun stop() = handleStop()

    fun getTTSState(): TTSState = ttsManager?.state ?: TTSState.IDLE
    fun getTTSManager(): TTSManager? = ttsManager

    // --- Skip handlers ---

    private fun handleSkipSentenceForward() {
        Log.d(TAG, "Skip sentence forward")
        ttsManager?.skipSentenceForward()
    }

    private fun handleSkipSentenceBackward() {
        Log.d(TAG, "Skip sentence backward")
        ttsManager?.skipSentenceBackward()
    }

    private fun handleSkipParagraphForward() {
        Log.d(TAG, "Skip paragraph forward")
        ttsManager?.skipParagraphForward()
    }

    private fun handleSkipParagraphBackward() {
        Log.d(TAG, "Skip paragraph backward")
        ttsManager?.skipParagraphBackward()
    }

    private fun handleSkipChapterForward() {
        Log.d(TAG, "Skip chapter forward")
        ttsManager?.skipChapterForward()
    }

    private fun handleSkipChapterBackward() {
        Log.d(TAG, "Skip chapter backward")
        ttsManager?.skipChapterBackward()
    }

    // --- Internal handlers ---

    private fun handlePlay() {
        val mgr = ttsManager ?: return
        if (currentText.isBlank()) {
            Log.w(TAG, "handlePlay: no text to play")
            return
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        mgr.play(currentText)
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        showNotification()
    }

    private fun handlePause() {
        val mgr = ttsManager ?: return
        mgr.pause()
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        showNotification()
    }

    private fun handleResume() {
        val mgr = ttsManager ?: return
        mgr.resume()
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        showNotification()
    }

    private fun handleStop() {
        val mgr = ttsManager ?: return
        mgr.stop()
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleToggle() {
        when (ttsManager?.state) {
            TTSState.PLAYING -> handlePause()
            TTSState.PAUSED -> handleResume()
            TTSState.READY -> {
                if (currentText.isNotBlank()) handlePlay()
            }
            else -> Log.d(TAG, "handleToggle: state=${ttsManager?.state}, ignoring")
        }
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tts_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.tts_notification_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val state = ttsManager?.state ?: TTSState.IDLE
        val isPlaying = state == TTSState.PLAYING

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = buildNotificationText()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tts_notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(contentIntent)
            .setDeleteIntent(createPendingIntent(ACTION_STOP))
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

        // Add action buttons based on state
        when (state) {
            TTSState.PLAYING -> {
                builder.addAction(
                    buildAction(
                        android.R.drawable.ic_media_previous,
                        getString(R.string.tts_notif_prev_chapter),
                        ACTION_SKIP_CHAPTER_BACKWARD
                    )
                )
                builder.addAction(
                    buildAction(
                        android.R.drawable.ic_media_rew,
                        getString(R.string.tts_notif_prev_sentence),
                        ACTION_SKIP_SENTENCE_BACKWARD
                    )
                )
                builder.addAction(
                    buildAction(
                        android.R.drawable.ic_media_pause,
                        getString(R.string.tts_notification_pause),
                        ACTION_PAUSE
                    )
                )
                builder.addAction(
                    buildAction(
                        android.R.drawable.ic_media_ff,
                        getString(R.string.tts_notif_next_sentence),
                        ACTION_SKIP_SENTENCE_FORWARD
                    )
                )
                builder.addAction(
                    buildAction(
                        android.R.drawable.ic_media_next,
                        getString(R.string.tts_notif_next_chapter),
                        ACTION_SKIP_CHAPTER_FORWARD
                    )
                )
            }
            TTSState.PAUSED -> {
                builder.addAction(
                    buildAction(
                        android.R.drawable.ic_media_previous,
                        getString(R.string.tts_notif_prev_chapter),
                        ACTION_SKIP_CHAPTER_BACKWARD
                    )
                )
                builder.addAction(
                    buildAction(
                        android.R.drawable.ic_media_rew,
                        getString(R.string.tts_notif_prev_sentence),
                        ACTION_SKIP_SENTENCE_BACKWARD
                    )
                )
                builder.addAction(
                    buildAction(
                        android.R.drawable.ic_media_play,
                        getString(R.string.tts_notification_play),
                        ACTION_RESUME
                    )
                )
                builder.addAction(
                    buildAction(
                        android.R.drawable.ic_media_ff,
                        getString(R.string.tts_notif_next_sentence),
                        ACTION_SKIP_SENTENCE_FORWARD
                    )
                )
                builder.addAction(
                    buildAction(
                        android.R.drawable.ic_media_next,
                        getString(R.string.tts_notif_next_chapter),
                        ACTION_SKIP_CHAPTER_FORWARD
                    )
                )
            }
            else -> {
                builder.addAction(
                    buildAction(
                        android.R.drawable.ic_media_play,
                        getString(R.string.tts_notification_play),
                        ACTION_PLAY
                    )
                )
                builder.addAction(
                    buildAction(
                        android.R.drawable.ic_delete,
                        getString(R.string.tts_notification_stop),
                        ACTION_STOP
                    )
                )
            }
        }

        // Attach MediaStyle
        val session = mediaSession
        if (session != null) {
            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(2, 3)
                .setShowCancelButton(true)
                .setCancelButtonIntent(createPendingIntent(ACTION_STOP))
            builder.setStyle(mediaStyle)
        }

        return builder.build()
    }

    private fun buildAction(icon: Int, title: String, action: String): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(icon, title, createPendingIntent(action)).build()
    }

    private fun buildNotificationText(): String {
        val baseText = if (currentText.isNotBlank()) {
            currentText.take(80) + if (currentText.length > 80) "..." else ""
        } else {
            getString(R.string.tts_notification_text)
        }

        return if (sleepTimerActive && sleepTimerRemainingSeconds > 0) {
            "$baseText \u00b7 ${formatSleepTime(sleepTimerRemainingSeconds)}"
        } else {
            baseText
        }
    }

    private fun formatSleepTime(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private fun showNotification() {
        notificationManager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TTSService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // --- MediaSession ---

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "TTSSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { handlePlay() }
                override fun onPause() { handlePause() }
                override fun onStop() { handleStop() }
                override fun onSkipToNext() { handleSkipChapterForward() }
                override fun onSkipToPrevious() { handleSkipChapterBackward() }
            })
            isActive = true
        }
        updatePlaybackState(PlaybackStateCompat.STATE_NONE)
    }

    private fun updatePlaybackState(@PlaybackStateCompat.State state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, 0L, 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }
}
