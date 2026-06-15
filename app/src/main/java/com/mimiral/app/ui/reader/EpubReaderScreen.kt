package com.mimiral.app.ui.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.local.settings.PageTurnStyle
import com.mimiral.app.data.local.settings.ReaderSettings
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import com.mimiral.app.data.local.settings.TTSSettingsRepository
import com.mimiral.app.data.reader.ContentBlock
import com.mimiral.app.data.reader.Sentence
import com.mimiral.app.data.reader.TextSpan
import com.mimiral.app.tts.TTSService
import com.mimiral.app.tts.TTSState
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    bookId: Int,
    onNavigateBack: () -> Unit,
    viewModel: EpubReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settingsRepository = remember { ReaderSettingsRepository(context) }
    val settings by settingsRepository.settings.collectAsState(initial = ReaderSettings())
    val textSettings by settingsRepository.textSettings.collectAsState(initial = TextSettings())
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val currentPageTurnStyle = try {
        PageTurnStyle.valueOf(settings.pageTurnStyleName)
    } catch (_: IllegalArgumentException) {
        PageTurnStyle.SLIDE
    }

    val focusRequester = remember { FocusRequester() }

    // Pagination engine
    val paginationEngine = remember(context) { PaginationEngine(context) }

    // Screen dimensions for pagination
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    // Sample chapter text for pagination (simulated EPUB content)
    // Falls back to placeholder text if book content has not loaded yet
    val chapterText = remember(uiState.bookContent) {
        uiState.bookContent ?: buildString {
            append("Welcome to Mimiral Reader\n\n")
            append("Chapter 1: The Beginning\n\n")
            append("This is sample text for the reader view...\n\n")
            // ... more hardcoded text
            append("Loading book content...\n")
        }
    }

    // Paginate whenever content or text settings change — prefer structured blocks when available.
    // Run on Default dispatcher to avoid blocking the UI thread during StaticLayout measurement.
    var paginationResult by remember { mutableStateOf<PaginationResult?>(null) }
    LaunchedEffect(chapterText, textSettings, screenWidthPx, screenHeightPx) {
        val capturedBlocks = uiState.contentBlocks
        val capturedText = chapterText
        val renderConfig = textSettings.toRenderConfig()
        paginationResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            if (capturedBlocks.isNotEmpty()) {
                paginationEngine.paginateBlocks(
                    blocks = capturedBlocks,
                    config = renderConfig,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx
                )
            } else {
                paginationEngine.paginate(
                    text = capturedText,
                    config = renderConfig,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx
                )
            }
        }
    }

    // Apply default font family from preferences on first load
    val defaultFontApplied = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!defaultFontApplied.value) {
            val defaultFamilyName = settings.defaultFontFamilyName
            val defaultFamily = try {
                ReaderFontFamily.valueOf(defaultFamilyName)
            } catch (_: IllegalArgumentException) {
                ReaderFontFamily.DEFAULT
            }
            if (defaultFamily != ReaderFontFamily.DEFAULT) {
                val updated = textSettings.copy(selectedFontFamily = defaultFamily)
                settingsRepository.setTextSettings(updated)
            }
            defaultFontApplied.value = true
        }
    }

    val pages = paginationResult?.pages ?: emptyList()
    val pageCount = pages.size.coerceAtLeast(1)

    // Sync total pages to ViewModel
    LaunchedEffect(pageCount) {
        viewModel.setTotalPages(pageCount)
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount }
    )

    var toolbarVisible by remember { mutableStateOf(false) }
    var showTextSettings by remember { mutableStateOf(false) }

    // TTS highlight offset tracking: map page indices to their start offset within
    // the textToRead string that was passed to TtsControlsHelper.play().
    var ttsStartPage by remember { mutableIntStateOf(-1) }
    var ttsPageOffsets by remember { mutableStateOf(emptyList<Int>()) }

    // Track page changes and notify ViewModel
    LaunchedEffect(pagerState.currentPage) {
        val chapterIndex = uiState.currentChapter
        viewModel.onPageTurn(
            chapterIndex = chapterIndex,
            characterOffset = pagerState.currentPage.toLong() * 500L,
            totalCharacters = pageCount.toLong() * 500L,
            pageNumber = pagerState.currentPage,
            lastReadPosition = "page:${pagerState.currentPage}"
        )
    }

    // Request focus for key events
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Volume key handler — yields to system volume control while TTS is active
    val ttsActiveForVolume =
        uiState.ttsState == TTSState.PLAYING || uiState.ttsState == TTSState.PAUSED
    val handleVolumeKey: (Int) -> Boolean = remember(settings, ttsActiveForVolume) {
        { keyCode ->
            // When TTS is playing/paused, let the system handle volume keys so the
            // user can adjust media volume without turning pages.
            if (ttsActiveForVolume) return@remember false

            if (!settings.volumeKeyNavigationEnabled) {
                return@remember false
            }

            val isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
            val isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

            if (!isVolumeUp && !isVolumeDown) {
                return@remember false
            }

            val swap = settings.volumeKeyDirectionSwapped
            val goNext = if (swap) isVolumeUp else isVolumeDown
            val goPrev = if (swap) isVolumeDown else isVolumeUp

            when {
                goNext && pagerState.currentPage < pageCount - 1 -> {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                    true
                }
                goPrev && pagerState.currentPage > 0 -> {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                    true
                }
                else -> false
            }
        }
    }

    // Route volume keys to STREAM_MUSIC while TTS is active so the hardware buttons
    // control audio volume rather than being ignored.
    val activity = context as? android.app.Activity
    DisposableEffect(ttsActiveForVolume) {
        if (ttsActiveForVolume) {
            activity?.setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC)
        }
        onDispose {
            if (ttsActiveForVolume) {
                activity?.setVolumeControlStream(android.media.AudioManager.USE_DEFAULT_STREAM_TYPE)
            }
        }
    }

    val isBookmarked = uiState.isCurrentPageBookmarked
    val currentChapterTitle = uiState.chapters.getOrNull(uiState.currentChapter)?.title ?: "Reader"

    // --- TTS state tracking ---
    val ttsSettingsRepository = remember { TTSSettingsRepository(context) }
    val ttsSettings by ttsSettingsRepository.settings.collectAsState(
        initial = com.mimiral.app.data.local.settings.TTSSettings()
    )

    // Register all three TTS broadcast receivers in a single DisposableEffect
    // to reduce lifecycle overhead and keep related setup/teardown together.
    DisposableEffect(context) {
        // Sentence receiver — synchronized highlighting
        val sentenceReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == TTSService.ACTION_TTS_SENTENCE) {
                    val isActive = intent.getBooleanExtra(TTSService.EXTRA_SENTENCE_ACTIVE, false)
                    if (isActive) {
                        val start = intent.getIntExtra(TTSService.EXTRA_SENTENCE_START, 0)
                        val end = intent.getIntExtra(TTSService.EXTRA_SENTENCE_END, 0)
                        val text = intent.getStringExtra(TTSService.EXTRA_SENTENCE_TEXT) ?: ""
                        viewModel.onTtsSentenceChanged(
                            TtsSentence(start = start, end = end, text = text)
                        )
                    } else {
                        viewModel.onTtsSentenceChanged(null)
                    }
                }
            }
        }

        // Word receiver — karaoke-style highlighting
        val wordReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == TTSService.ACTION_TTS_WORD) {
                    val isActive = intent.getBooleanExtra(TTSService.EXTRA_WORD_ACTIVE, false)
                    if (isActive) {
                        val start = intent.getIntExtra(TTSService.EXTRA_WORD_START, -1)
                        val end = intent.getIntExtra(TTSService.EXTRA_WORD_END, -1)
                        viewModel.onTtsWordChanged(start, end)
                    } else {
                        viewModel.onTtsWordCleared()
                    }
                }
            }
        }

        // State receiver — TTS engine lifecycle tracking
        val stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == TTSService.ACTION_TTS_STATE) {
                    val stateName = intent.getStringExtra(TTSService.EXTRA_TTS_STATE) ?: "IDLE"
                    viewModel.onTtsStateChanged(stateName)
                }
            }
        }

        // Helper to handle API 33+ RECEIVER_NOT_EXPORTED flag
        fun registerTtsReceiver(receiver: BroadcastReceiver, action: String) {
            val filter = IntentFilter(action)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    filter,
                    android.content.Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(receiver, filter)
            }
        }

        registerTtsReceiver(sentenceReceiver, TTSService.ACTION_TTS_SENTENCE)
        registerTtsReceiver(wordReceiver, TTSService.ACTION_TTS_WORD)
        registerTtsReceiver(stateReceiver, TTSService.ACTION_TTS_STATE)

        onDispose {
            context.unregisterReceiver(sentenceReceiver)
            context.unregisterReceiver(wordReceiver)
            context.unregisterReceiver(stateReceiver)
        }
    }

    // Voice picker dialog state
    var showVoicePicker by remember { mutableStateOf(false) }

    // Text settings change handler - persists via DataStore
    val onTextSettingsChanged: (TextSettings) -> Unit = remember(settingsRepository) {
        { newSettings ->
            coroutineScope.launch {
                settingsRepository.setTextSettings(newSettings)
            }
        }
    }

    // Get current page text for highlighting
    val currentPageText = if (pages.isNotEmpty()) {
        pages.getOrNull(pagerState.currentPage)?.text ?: ""
    } else {
        ""
    }

    // Filter highlights for current chapter
    val currentChapterHighlights = remember(uiState.highlights, uiState.currentChapter) {
        uiState.highlights.filter { it.chapterIndex == uiState.currentChapter }
    }

    // Long press handler
    val onTextLongPress: (String, Int, Int) -> Unit = remember(viewModel) {
        { selectedText, startOffset, endOffset ->
            viewModel.onTextLongPressed(selectedText, startOffset, endOffset)
        }
    }

    Scaffold(
        topBar = {
            if (toolbarVisible) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentChapterTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = buildProgressText(uiState),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.saveCurrentProgress()
                            onNavigateBack()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Read Aloud button — shows when TTS is idle, ready, or stopped
                        val isTtsIdle = uiState.ttsState == TTSState.IDLE
                        val isTtsReady = uiState.ttsState == TTSState.READY
                        val isTtsStopped = uiState.ttsState == TTSState.STOPPED
                        if (isTtsIdle || isTtsReady || isTtsStopped) {
                            IconButton(onClick = {
                                // Build text from the current page forward (capped at 150 K chars to
                                // stay under Android's ~1 MB binder transaction limit), and record the
                                // start offset of each page within that string so word highlights can
                                // be mapped back to the correct position inside each block's text.
                                val offsets = mutableListOf<Int>()
                                val textToRead = if (pages.isNotEmpty()) {
                                    buildString {
                                        var cumOffset = 0
                                        for (i in pagerState.currentPage until pages.size) {
                                            val t = pages.getOrNull(i)?.text ?: break
                                            if (isNotEmpty()) {
                                                append("\n\n")
                                                cumOffset += 2
                                            }
                                            offsets.add(cumOffset)
                                            append(t)
                                            cumOffset += t.length
                                            if (length > 150_000) break
                                        }
                                    }
                                } else {
                                    ""
                                }
                                if (textToRead.isNotBlank()) {
                                    ttsStartPage = pagerState.currentPage
                                    ttsPageOffsets = offsets
                                    TtsControlsHelper.play(context, textToRead, currentChapterTitle)
                                }
                            }) {
                                Icon(
                                    Icons.Default.RecordVoiceOver,
                                    contentDescription = "Read Aloud"
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.showToc() }) {
                            Icon(Icons.Default.List, contentDescription = "Table of Contents")
                        }
                        IconButton(onClick = { viewModel.toggleBookmark() }) {
                            Icon(
                                imageVector = if (isBookmarked) {
                                    Icons.Default.Bookmark
                                } else {
                                    Icons.Default.BookmarkBorder
                                },
                                contentDescription = if (isBookmarked) {
                                    "Remove bookmark"
                                } else {
                                    "Add bookmark"
                                },
                                tint = if (isBookmarked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        IconButton(onClick = { showTextSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Text Settings")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (uiState.ttsState == TTSState.PLAYING || uiState.ttsState == TTSState.PAUSED) {
                // TTS controls bar — shown during active TTS playback/pause
                TtsControlsBar(
                    ttsState = uiState.ttsState,
                    currentSpeed = ttsSettings.speechRate,
                    currentPitch = ttsSettings.pitch,
                    currentVoiceName = ttsSettings.voiceName,
                    onPlayPause = {
                        TtsControlsHelper.toggle(context)
                    },
                    onStop = {
                        TtsControlsHelper.stop(context)
                    },
                    onSpeedChanged = { newSpeed ->
                        TtsControlsHelper.setSpeed(context, newSpeed)
                        coroutineScope.launch {
                            ttsSettingsRepository.setSpeechRate(newSpeed)
                        }
                    },
                    onPitchChanged = { newPitch ->
                        TtsControlsHelper.setPitch(context, newPitch)
                        coroutineScope.launch {
                            ttsSettingsRepository.setPitch(newPitch)
                        }
                    },
                    onVoicePickerOpen = { showVoicePicker = true }
                )
            } else if (toolbarVisible) {
                NavigationBar {
                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            if (viewModel.hasPreviousChapter()) {
                                viewModel.previousChapter()
                                coroutineScope.launch {
                                    val chapter = uiState.chapters.getOrNull(
                                        uiState.currentChapter - 1
                                    )
                                    if (chapter != null) {
                                        pagerState.animateScrollToPage(chapter.startPage)
                                    }
                                }
                            }
                        },
                        enabled = viewModel.hasPreviousChapter(),
                        icon = {
                            Icon(
                                Icons.Default.ArrowBackIos,
                                contentDescription = "Previous chapter"
                            )
                        },
                        label = { Text("Prev Ch") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { viewModel.showToc() },
                        icon = { Text("${pagerState.currentPage + 1}/$pageCount") },
                        label = { Text("Page") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            if (viewModel.hasNextChapter()) {
                                viewModel.nextChapter()
                                coroutineScope.launch {
                                    val chapter = uiState.chapters.getOrNull(
                                        uiState.currentChapter + 1
                                    )
                                    if (chapter != null) {
                                        pagerState.animateScrollToPage(chapter.startPage)
                                    }
                                }
                            }
                        },
                        enabled = viewModel.hasNextChapter(),
                        icon = {
                            Icon(
                                Icons.Default.ArrowForwardIos,
                                contentDescription = "Next chapter"
                            )
                        },
                        label = { Text("Next Ch") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .focusRequester(focusRequester)
                .focusTarget()
                .onKeyEvent { keyEvent ->
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
                        keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
                    ) {
                        handleVolumeKey(keyCode)
                    } else {
                        false
                    }
                }
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Error: ${uiState.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else if (pageCount > 0 && pages.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { index -> index },
                    pageSpacing = when (currentPageTurnStyle) {
                        PageTurnStyle.CURL -> 8.dp
                        else -> 0.dp
                    }
                ) { pageIndex ->
                    val pageOffset = (
                        (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                        )

                    val useEffects = currentPageTurnStyle != PageTurnStyle.NONE

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (useEffects) {
                                    when (currentPageTurnStyle) {
                                        PageTurnStyle.CURL -> {
                                            Modifier.graphicsLayer {
                                                alpha = if (abs(pageOffset) < 1f) {
                                                    1f - (abs(pageOffset) * 0.15f)
                                                } else {
                                                    0.85f
                                                }
                                                val scale =
                                                    1f - (abs(pageOffset) * 0.08f).coerceIn(
                                                        0f,
                                                        0.15f
                                                    )
                                                scaleX = scale
                                                scaleY = scale
                                                translationX =
                                                    pageOffset * size.width * -0.12f
                                                rotationY = pageOffset.coerceIn(-1f, 1f) * -15f
                                                cameraDistance = 33f
                                                clip = true
                                            }
                                        }
                                        PageTurnStyle.SLIDE -> {
                                            Modifier.graphicsLayer {
                                                alpha = if (abs(pageOffset) < 1f) {
                                                    1f - (abs(pageOffset) * 0.3f)
                                                } else {
                                                    0.7f
                                                }
                                                val scale =
                                                    1f - (abs(pageOffset) * 0.03f).coerceIn(
                                                        0f,
                                                        0.1f
                                                    )
                                                scaleX = scale
                                                scaleY = scale
                                                translationX =
                                                    pageOffset * size.width * -0.05f
                                                clip = true
                                            }
                                        }
                                        else -> Modifier
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { offset ->
                                        // Calculate approximate character offset from tap position
                                        val avgCharWidth = 8f
                                        val charOffset = (offset.x / avgCharWidth).toInt()
                                            .coerceIn(0, currentPageText.length)

                                        // Select a word around the tapped position
                                        val wordStart = currentPageText.lastIndexOf(
                                            ' ',
                                            charOffset
                                                .coerceAtMost(
                                                    (currentPageText.length - 1).coerceAtLeast(0)
                                                )
                                        ).let { if (it == -1) 0 else it + 1 }
                                        val wordEnd = currentPageText.indexOf(' ', charOffset)
                                            .let { if (it == -1) currentPageText.length else it }

                                        val selectedText = currentPageText.substring(
                                            wordStart,
                                            wordEnd
                                        ).trim()
                                        if (selectedText.isNotEmpty()) {
                                            onTextLongPress(selectedText, wordStart, wordEnd)
                                        }
                                    },
                                    onTap = { offset ->
                                        val zoneFraction = settings.tapZoneSize / 100f
                                        val tapZoneWidth = size.width * zoneFraction
                                        val inverted = settings.tapZonesInverted

                                        // Determine which zones are active
                                        val leftActive = settings.tapZoneLeftEnabled
                                        val rightActive = settings.tapZoneRightEnabled
                                        val centerActive = settings.tapZoneCenterEnabled

                                        when {
                                            // Left zone tap
                                            offset.x < tapZoneWidth && leftActive -> {
                                                val goNext = inverted
                                                if (goNext) {
                                                    if (pagerState.currentPage < pageCount - 1) {
                                                        coroutineScope.launch {
                                                            pagerState.animateScrollToPage(
                                                                pagerState.currentPage + 1
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    if (pagerState.currentPage > 0) {
                                                        coroutineScope.launch {
                                                            pagerState.animateScrollToPage(
                                                                pagerState.currentPage - 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            // Right zone tap
                                            offset.x > (size.width - tapZoneWidth) &&
                                                rightActive -> {
                                                val goPrev = inverted
                                                if (goPrev) {
                                                    if (pagerState.currentPage > 0) {
                                                        coroutineScope.launch {
                                                            pagerState.animateScrollToPage(
                                                                pagerState.currentPage - 1
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    if (pagerState.currentPage < pageCount - 1) {
                                                        coroutineScope.launch {
                                                            pagerState.animateScrollToPage(
                                                                pagerState.currentPage + 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            // Center zone tap
                                            else -> {
                                                if (centerActive) {
                                                    toolbarVisible = !toolbarVisible
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        val page = pages.getOrNull(pageIndex)
                        val pageText = page?.text ?: ""
                        val pageBlocks = page?.blocks ?: emptyList()

                        // Compute this page's start offset within the textToRead string that
                        // was passed to TTS. Used to convert full-text word positions to
                        // block-local positions for accurate karaoke highlighting.
                        val ttsPageOffset = if (ttsStartPage >= 0 && pageIndex >= ttsStartPage) {
                            ttsPageOffsets.getOrNull(pageIndex - ttsStartPage) ?: -1
                        } else {
                            -1
                        }

                        EpubPageContent(
                            pageText = pageText,
                            contentBlocks = pageBlocks,
                            pageNumber = pageIndex + 1,
                            totalPages = pageCount,
                            chapterTitle = if (pageIndex == 0) {
                                uiState.chapters.getOrNull(
                                    uiState.currentChapter
                                )?.title
                            } else {
                                null
                            },
                            textSettings = textSettings,
                            highlights = currentChapterHighlights,
                            ttsSentence = if (ttsSettings.highlightWhileReading) {
                                uiState.currentTtsSentence
                            } else {
                                null
                            },
                            ttsWordStart = if (ttsSettings.highlightWhileReading) {
                                uiState.currentTtsWordStart
                            } else {
                                -1
                            },
                            ttsWordEnd = if (ttsSettings.highlightWhileReading) {
                                uiState.currentTtsWordEnd
                            } else {
                                -1
                            },
                            ttsPageOffset = ttsPageOffset,
                            onLongPress = onTextLongPress
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No content to display",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            if (!toolbarVisible && pageCount > 0) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / $pageCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                    LinearProgressIndicator(
                        progress = (pagerState.currentPage + 1).toFloat() / pageCount.toFloat(),
                        modifier = Modifier
                            .width(100.dp)
                            .padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }

    // Table of Contents dialog
    if (uiState.showToc) {
        TableOfContentsDialog(
            chapters = uiState.chapters,
            currentChapterIndex = uiState.currentChapter,
            onNavigateToChapter = { chapterIndex ->
                viewModel.navigateToChapter(chapterIndex)
                coroutineScope.launch {
                    val chapter = uiState.chapters.getOrNull(chapterIndex)
                    if (chapter != null) {
                        pagerState.animateScrollToPage(chapter.startPage)
                    }
                }
            },
            onDismiss = { viewModel.dismissToc() }
        )
    }

    // Bookmarks dialog
    if (uiState.showBookmarks) {
        BookmarkListDialog(
            bookmarks = uiState.bookmarks,
            onNavigateToBookmark = { bookmark ->
                viewModel.navigateToBookmark(bookmark.chapterIndex, bookmark.pageNumber)
                coroutineScope.launch {
                    pagerState.animateScrollToPage(bookmark.pageNumber)
                }
            },
            onDeleteBookmark = { bookmark ->
                viewModel.deleteBookmark(bookmark)
            },
            onDismiss = { viewModel.dismissBookmarks() }
        )
    }

    // Text Settings dialog
    if (showTextSettings) {
        TextSettingsPanel(
            settings = textSettings,
            onSettingsChanged = onTextSettingsChanged,
            onDismiss = { showTextSettings = false },
            paginationEngine = paginationEngine
        )
    }

    // Highlight color picker bottom sheet
    if (uiState.showHighlightColorPicker) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissHighlightColorPicker() }
        ) {
            HighlightColorSheet(
                onColorSelected = { highlightColor ->
                    viewModel.saveHighlight(highlightColor.hex)
                },
                onDismiss = { viewModel.dismissHighlightColorPicker() }
            )
        }
    }

    // Voice picker dialog
    if (showVoicePicker) {
        VoicePickerDialog(
            currentVoiceName = ttsSettings.voiceName,
            onVoiceSelected = { voiceName ->
                TtsControlsHelper.setVoice(context, voiceName)
                coroutineScope.launch {
                    ttsSettingsRepository.setVoiceName(voiceName)
                }
                showVoicePicker = false
            },
            onDismiss = { showVoicePicker = false }
        )
    }
}

private fun buildProgressText(uiState: ReaderUiState): String {
    val pageText = "Page ${uiState.currentPage + 1} of ${uiState.totalPages}"
    val percentText = "${uiState.progress.progressPercent.toInt()}%"
    return "$pageText \u00b7 $percentText"
}

@Composable
private fun EpubPageContent(
    pageText: String,
    contentBlocks: List<ContentBlock> = emptyList(),
    pageNumber: Int,
    totalPages: Int,
    chapterTitle: String? = null,
    textSettings: TextSettings = TextSettings(),
    highlights: List<ReaderHighlight> = emptyList(),
    ttsSentence: Sentence? = null,
    ttsWordStart: Int = -1,
    ttsWordEnd: Int = -1,
    ttsPageOffset: Int = -1,
    onLongPress: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val fontScale = textSettings.fontSize.toFloat() / 18f
    val lineHeight = textSettings.lineSpacingMultiplier

    // Auto-scroll to keep highlighted TTS word visible (only for plain-text fallback path).
    LaunchedEffect(ttsWordStart, ttsWordEnd) {
        if (contentBlocks.isNotEmpty()) return@LaunchedEffect
        val localStart = if (ttsPageOffset >= 0 && ttsWordStart >= 0) {
            ttsWordStart - ttsPageOffset
        } else {
            ttsWordStart
        }
        if (localStart >= 0 && ttsWordEnd > ttsWordStart && pageText.isNotEmpty()) {
            val charRatio = localStart.toFloat() / pageText.length.toFloat()
            val lineHeightDp = textSettings.fontSize * lineHeight
            val estimatedLineHeightPx: Float = with(density) { lineHeightDp.dp.toPx() }
            val approximateLineCount: Float = (pageText.count { it == '\n' } + 1).toFloat()
            val totalHeightPx: Float = approximateLineCount * estimatedLineHeightPx
            val targetPx: Int = (charRatio * totalHeightPx).toInt()
            val viewportHeight = scrollState.maxValue
            val centeredTarget = (targetPx - viewportHeight / 2).coerceIn(0, scrollState.maxValue)
            if (kotlin.math.abs(scrollState.value - centeredTarget) > viewportHeight / 4) {
                kotlinx.coroutines.delay(50)
                scrollState.animateScrollTo(centeredTarget)
            }
        }
    }

    if (contentBlocks.isNotEmpty()) {
        val baseParagraphStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = textSettings.fontSize.toFloat().sp,
            lineHeight = (textSettings.fontSize.toFloat() * lineHeight).sp,
            fontFamily = textSettings.selectedFontFamily.fontFamily
        )

        // Precompute each block's start offset within the page's text
        // (blocks joined with "\n\n"). Used to convert full-text TTS word
        // positions to block-local positions for accurate highlighting.
        val blockStartOffsets = remember(contentBlocks) {
            var off = 0
            contentBlocks.map { block ->
                val s = off
                off += block.text.length + 2 // +2 for "\n\n" separator between blocks
                s
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    start = textSettings.marginLeft.dp,
                    end = textSettings.marginRight.dp,
                    top = textSettings.marginTop.dp,
                    bottom = textSettings.marginBottom.dp
                )
        ) {
            if (chapterTitle != null && pageNumber == 1) {
                item {
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
                }
            }
            itemsIndexed(contentBlocks, key = { index, _ -> index }) { blockIndex, block ->
                // Convert full-text TTS word positions to positions within this block.
                // blockStartInText is the block's start offset within textToRead.
                // Only highlight if the current word falls inside this block's range.
                val blockLocalOffset = blockStartOffsets.getOrNull(blockIndex) ?: 0
                val blockStartInText =
                    if (ttsPageOffset >= 0) ttsPageOffset + blockLocalOffset else -1
                val showBlockHighlight = blockStartInText >= 0 &&
                    ttsWordStart >= blockStartInText &&
                    ttsWordStart < blockStartInText + block.text.length
                val localTtsStart = if (showBlockHighlight) ttsWordStart - blockStartInText else -1
                val localTtsEnd = if (showBlockHighlight) ttsWordEnd - blockStartInText else -1

                when (block) {
                    is ContentBlock.Heading -> {
                        val headingStyle = when (block.level) {
                            1 -> MaterialTheme.typography.headlineLarge.copy(
                                fontSize = (32f * fontScale).sp,
                                lineHeight = (32f * fontScale * lineHeight).sp,
                                fontFamily = textSettings.selectedFontFamily.fontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            2 -> MaterialTheme.typography.headlineMedium.copy(
                                fontSize = (28f * fontScale).sp,
                                lineHeight = (28f * fontScale * lineHeight).sp,
                                fontFamily = textSettings.selectedFontFamily.fontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            3 -> MaterialTheme.typography.titleMedium.copy(
                                fontSize = (22f * fontScale).sp,
                                lineHeight = (22f * fontScale * lineHeight).sp,
                                fontFamily = textSettings.selectedFontFamily.fontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            else -> MaterialTheme.typography.bodyLarge.copy(
                                fontSize = (18f * fontScale).sp,
                                lineHeight = (18f * fontScale * lineHeight).sp,
                                fontFamily = textSettings.selectedFontFamily.fontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = applyEpubTtsHighlight(
                                block.text,
                                localTtsStart,
                                localTtsEnd,
                                highlightColor
                            ),
                            style = headingStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                    is ContentBlock.Paragraph -> {
                        val style = if (block.isBold) {
                            baseParagraphStyle.copy(fontWeight = FontWeight.Bold)
                        } else {
                            baseParagraphStyle
                        }
                        Text(
                            text = buildEpubParagraphAnnotatedString(
                                block.text,
                                block.spans,
                                localTtsStart,
                                localTtsEnd,
                                highlightColor
                            ),
                            style = style,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                    is ContentBlock.Quote -> {
                        val quoteStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = (16f * fontScale).sp,
                            lineHeight = (16f * fontScale * lineHeight).sp,
                            fontStyle = FontStyle.Italic,
                            fontFamily = textSettings.selectedFontFamily.fontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = applyEpubTtsHighlight(
                                block.text,
                                localTtsStart,
                                localTtsEnd,
                                highlightColor
                            ),
                            style = quoteStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                    is ContentBlock.ListItem -> {
                        // Displayed text has a prefix ("• " or "N. ") that is not in block.text,
                        // so shift localTts offsets right by the prefix length.
                        val prefix = if (block.order > 0) "${block.order}. " else "• "
                        Text(
                            text = applyEpubTtsHighlight(
                                "$prefix${block.text}",
                                if (localTtsStart >= 0) localTtsStart + prefix.length else -1,
                                if (localTtsEnd >= 0) localTtsEnd + prefix.length else -1,
                                highlightColor
                            ),
                            style = baseParagraphStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                        )
                    }
                    is ContentBlock.Rule -> {
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "$pageNumber",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    start = textSettings.marginLeft.dp,
                    end = textSettings.marginRight.dp,
                    top = textSettings.marginTop.dp,
                    bottom = textSettings.marginBottom.dp
                )
                .verticalScroll(scrollState)
        ) {
            if (chapterTitle != null && pageNumber == 1) {
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
            }

            // Convert full-text TTS positions to page-local positions.
            val pageLocalTtsStart = if (ttsPageOffset >= 0 && ttsWordStart >= 0) {
                ttsWordStart - ttsPageOffset
            } else {
                ttsWordStart
            }
            val pageLocalTtsEnd = if (ttsPageOffset >= 0 && ttsWordEnd >= 0) {
                ttsWordEnd - ttsPageOffset
            } else {
                ttsWordEnd
            }
            HighlightableText(
                text = pageText,
                highlights = highlights,
                textSettings = textSettings,
                ttsSentence = ttsSentence,
                ttsWordStart = pageLocalTtsStart,
                ttsWordEnd = pageLocalTtsEnd,
                onLongPress = onLongPress,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "$pageNumber",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )
        }
    }
}

private fun applyEpubTtsHighlight(
    text: String,
    ttsWordStart: Int,
    ttsWordEnd: Int,
    highlightColor: Color
): AnnotatedString {
    if (ttsWordStart < 0 || ttsWordEnd <= ttsWordStart) return AnnotatedString(text)
    val localEnd = ttsWordEnd.coerceAtMost(text.length)
    val localStart = ttsWordStart.coerceAtLeast(0).coerceAtMost(localEnd)
    return if (localStart < localEnd) {
        buildAnnotatedString {
            append(text)
            addStyle(SpanStyle(background = highlightColor), localStart, localEnd)
        }
    } else {
        AnnotatedString(text)
    }
}

private fun buildEpubParagraphAnnotatedString(
    text: String,
    spans: List<TextSpan>,
    ttsWordStart: Int,
    ttsWordEnd: Int,
    highlightColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        for (span in spans) {
            val spanStyle = SpanStyle(
                fontWeight = if (span.isBold) FontWeight.Bold else null,
                fontStyle = if (span.isItalic) FontStyle.Italic else null
            )
            addStyle(
                style = spanStyle,
                start = span.start.coerceIn(0, text.length),
                end = span.end.coerceIn(0, text.length)
            )
        }
        if (ttsWordStart >= 0 && ttsWordEnd > ttsWordStart) {
            val localEnd = ttsWordEnd.coerceAtMost(text.length)
            val localStart = ttsWordStart.coerceAtLeast(0).coerceAtMost(localEnd)
            if (localStart < localEnd) {
                addStyle(SpanStyle(background = highlightColor), localStart, localEnd)
            }
        }
    }
}

@Composable
fun ReaderSettingsDialog(
    settings: ReaderSettings,
    onDismiss: () -> Unit,
    onVolumeKeyToggle: (Boolean) -> Unit,
    onDirectionSwapToggle: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reader Settings") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Volume key navigation")
                    Switch(
                        checked = settings.volumeKeyNavigationEnabled,
                        onCheckedChange = onVolumeKeyToggle
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Swap volume direction")
                        Text(
                            if (settings.volumeKeyDirectionSwapped) {
                                "Vol Up = Next, Vol Down = Prev"
                            } else {
                                "Vol Up = Prev, Vol Down = Next"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.volumeKeyDirectionSwapped,
                        onCheckedChange = onDirectionSwapToggle,
                        enabled = settings.volumeKeyNavigationEnabled
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
