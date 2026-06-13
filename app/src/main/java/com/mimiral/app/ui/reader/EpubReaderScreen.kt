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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.local.settings.PageTurnStyle
import com.mimiral.app.data.local.settings.ReaderSettings
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import com.mimiral.app.data.local.settings.TTSSettingsRepository
import com.mimiral.app.data.reader.Sentence
import com.mimiral.app.tts.TTSService
import com.mimiral.app.tts.TTSState
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    bookId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToReadingMode: () -> Unit = {},
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

    // Paginate text whenever text settings or content changes
    var paginationResult by remember { mutableStateOf<PaginationResult?>(null) }
    LaunchedEffect(chapterText, textSettings, screenWidthPx, screenHeightPx) {
        paginationResult = paginationEngine.paginate(
            text = chapterText,
            config = textSettings.toRenderConfig(),
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx
        )
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

    // Volume key handler
    val handleVolumeKey: (Int) -> Boolean = remember(settings) {
        { keyCode ->
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
                else -> true
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
                context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
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
                        // Read Aloud button — shows when TTS is not actively playing/paused
                        val isTtsIdle = uiState.ttsState == TTSState.IDLE
                        val isTtsReady = uiState.ttsState == TTSState.READY
                        if (isTtsIdle || isTtsReady) {
                            IconButton(onClick = {
                                val textToRead = if (pages.isNotEmpty()) {
                                    pages.joinToString("\n\n") { it.text }
                                } else {
                                    ""
                                }
                                if (textToRead.isNotBlank()) {
                                    TtsControlsHelper.play(context, textToRead)
                                }
                            }) {
                                Icon(
                                    Icons.Default.RecordVoiceOver,
                                    contentDescription = "Read Aloud"
                                )
                            }
                        }
                        // Reading Mode button
                        IconButton(onClick = onNavigateToReadingMode) {
                            Icon(Icons.Default.MenuBook, contentDescription = "Reading Mode")
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
                        val pageText = pages.getOrNull(pageIndex)?.text ?: ""

                        EpubPageContent(
                            pageText = pageText,
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
    pageNumber: Int,
    totalPages: Int,
    chapterTitle: String? = null,
    textSettings: TextSettings = TextSettings(),
    highlights: List<ReaderHighlight> = emptyList(),
    ttsSentence: Sentence? = null,
    ttsWordStart: Int = -1,
    ttsWordEnd: Int = -1,
    onLongPress: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Auto-scroll to keep the highlighted TTS word visible in the viewport.
    // Approximates pixel position from character ratio in the text.
    LaunchedEffect(ttsWordStart, ttsWordEnd) {
        if (ttsWordStart >= 0 && ttsWordEnd > ttsWordStart && pageText.isNotEmpty()) {
            val charRatio = ttsWordStart.toFloat() / pageText.length.toFloat()
            // Estimate total content height from line count and font size
            val lineHeightDp = textSettings.fontSize * textSettings.lineSpacingMultiplier
            val estimatedLineHeightPx: Float = with(density) {
                lineHeightDp.dp.toPx()
            }
            val approximateLineCount: Float =
                (pageText.count { it == '\n' } + 1).toFloat()
            val totalHeightPx: Float = approximateLineCount * estimatedLineHeightPx
            val targetPx: Int = (charRatio * totalHeightPx).toInt()
            // Scroll to bring the target into view, centering it if possible
            val viewportHeight = scrollState.maxValue
            val centeredTarget = (targetPx - viewportHeight / 2).coerceIn(0, scrollState.maxValue)
            if (kotlin.math.abs(scrollState.value - centeredTarget) > viewportHeight / 4) {
                kotlinx.coroutines.delay(50) // small delay to avoid jank
                scrollState.animateScrollTo(centeredTarget)
            }
        }
    }

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

        HighlightableText(
            text = pageText,
            highlights = highlights,
            textSettings = textSettings,
            ttsSentence = ttsSentence,
            ttsWordStart = ttsWordStart,
            ttsWordEnd = ttsWordEnd,
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
