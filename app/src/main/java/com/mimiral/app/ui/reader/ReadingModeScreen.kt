package com.mimiral.app.ui.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import com.mimiral.app.data.local.settings.TTSSettingsRepository
import com.mimiral.app.data.reader.Sentence
import com.mimiral.app.tts.TTSState
import com.mimiral.app.tts.TTSService
import kotlinx.coroutines.launch

/**
 * Reading Mode screen — reflowable text display for books.
 *
 * Renders extracted text as a LazyColumn of paragraphs with chapter navigation,
 * progress tracking, bookmark support, and TTS read-aloud with word highlighting.
 * Works for EPUB, PDF, TXT, and other text-based formats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingModeScreen(
    bookId: Int,
    onNavigateBack: () -> Unit,
    viewModel: ReadingModeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settingsRepository = remember { ReaderSettingsRepository(context) }
    val textSettings by settingsRepository.textSettings.collectAsState(initial = TextSettings())
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // TTS settings repository for speed/pitch/voice persistence
    val ttsSettingsRepository = remember { TTSSettingsRepository(context) }
    val ttsSettings by ttsSettingsRepository.settings.collectAsState(
        initial = com.mimiral.app.data.local.settings.TTSSettings()
    )

    // Voice picker dialog state
    var showVoicePicker by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()

    // Track the first visible paragraph index for progress reporting
    val firstVisibleParagraphIndex by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex
        }
    }

    // Report visible paragraph changes to ViewModel for progress tracking
    LaunchedEffect(firstVisibleParagraphIndex) {
        viewModel.onParagraphVisible(firstVisibleParagraphIndex)
    }

    // Handle scroll-to requests from ViewModel (chapter navigation, bookmark navigation)
    LaunchedEffect(uiState.scrollToParagraphIndex) {
        val target = uiState.scrollToParagraphIndex
        if (target != null) {
            listState.animateScrollToItem(target)
            viewModel.clearScrollTarget()
        }
    }

    // --- TTS STATE broadcast receiver ---
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == TTSService.ACTION_TTS_STATE) {
                    val stateName = intent.getStringExtra(TTSService.EXTRA_TTS_STATE) ?: "IDLE"
                    viewModel.onTtsStateChanged(stateName)
                }
            }
        }
        val filter = IntentFilter(TTSService.ACTION_TTS_STATE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // --- TTS SENTENCE broadcast receiver ---
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == TTSService.ACTION_TTS_SENTENCE) {
                    val isActive = intent.getBooleanExtra(TTSService.EXTRA_SENTENCE_ACTIVE, false)
                    if (isActive) {
                        val start = intent.getIntExtra(TTSService.EXTRA_SENTENCE_START, 0)
                        val end = intent.getIntExtra(TTSService.EXTRA_SENTENCE_END, 0)
                        val text = intent.getStringExtra(TTSService.EXTRA_SENTENCE_TEXT) ?: ""
                        viewModel.onTtsSentenceChanged(
                            Sentence(start = start, end = end, text = text)
                        )
                    } else {
                        viewModel.onTtsSentenceChanged(null)
                    }
                }
            }
        }
        val filter = IntentFilter(TTSService.ACTION_TTS_SENTENCE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // --- TTS WORD broadcast receiver ---
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
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
        val filter = IntentFilter(TTSService.ACTION_TTS_WORD)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // --- Auto-scroll to keep TTS-highlighted word visible ---
    // When the highlighted word falls in a different paragraph, scroll to it.
    LaunchedEffect(uiState.ttsWordStart) {
        val wordStart = uiState.ttsWordStart
        if (wordStart < 0) return@LaunchedEffect

        // Find which paragraph contains this word offset
        val paragraphs = uiState.paragraphs
        val targetIndex = paragraphs.indexOfFirst { para ->
            wordStart >= para.charOffset && wordStart < para.charOffset + para.text.length
        }
        if (targetIndex >= 0 && targetIndex != listState.firstVisibleItemIndex) {
            // Only scroll if the target is not already visible
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val isVisible = visibleItems.any { it.index == targetIndex }
            if (!isVisible) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    val isTtsPlayingOrPaused = uiState.ttsState == TTSState.PLAYING ||
        uiState.ttsState == TTSState.PAUSED

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.bookTitle.ifBlank { "Reading Mode" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Show current chapter as subtitle
                        val chapterTitle = uiState.chapterTitles.getOrNull(
                            uiState.currentChapterIndex
                        )
                        if (chapterTitle != null) {
                            Text(
                                text = chapterTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Read Aloud button — shows when TTS is not actively playing/paused
                    val isTtsIdle = uiState.ttsState == TTSState.IDLE
                    val isTtsReady = uiState.ttsState == TTSState.READY
                    if (isTtsIdle || isTtsReady) {
                        IconButton(onClick = {
                            val textToRead = viewModel.getFullText()
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
                    // TOC button
                    IconButton(onClick = { viewModel.toggleToc() }) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Table of Contents"
                        )
                    }
                    // Bookmark toggle
                    IconButton(onClick = { viewModel.toggleBookmark() }) {
                        Icon(
                            imageVector = if (uiState.isCurrentPositionBookmarked) {
                                Icons.Default.Bookmark
                            } else {
                                Icons.Default.BookmarkBorder
                            },
                            contentDescription = if (uiState.isCurrentPositionBookmarked) {
                                "Remove bookmark"
                            } else {
                                "Add bookmark"
                            },
                            tint = if (uiState.isCurrentPositionBookmarked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    // Bookmark list
                    IconButton(onClick = { viewModel.toggleBookmarkList() }) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmarks"
                        )
                    }
                    // Text settings
                    IconButton(onClick = { viewModel.toggleTextSettings() }) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = "Text settings"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            if (isTtsPlayingOrPaused) {
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
            } else if (uiState.paragraphs.isNotEmpty()) {
                // Progress bar at the bottom (when TTS is not active)
                Column {
                    LinearProgressIndicator(
                        progress = { (uiState.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Chapter navigation row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chapter ${uiState.currentChapterIndex + 1}" +
                                " of ${uiState.chapterTitles.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${uiState.progressPercent.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                // Loading state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Extracting text content\u2026",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            uiState.error != null -> {
                // Error state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "Could not load text",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            uiState.paragraphs.isEmpty() -> {
                // Empty content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No text content available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                // Reading content — LazyColumn of paragraphs
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = textSettings.marginLeft.dp,
                        end = textSettings.marginRight.dp,
                        top = textSettings.marginTop.dp,
                        bottom = textSettings.marginBottom.dp + 32.dp
                    )
                ) {
                    itemsIndexed(
                        items = uiState.paragraphs,
                        key = { _, paragraph -> paragraph.index }
                    ) { _, paragraph ->
                        ParagraphItem(
                            paragraph = paragraph,
                            fontSize = textSettings.fontSize,
                            lineHeight = textSettings.lineSpacingMultiplier,
                            fontFamily = textSettings.selectedFontFamily,
                            ttsWordStart = uiState.ttsWordStart,
                            ttsWordEnd = uiState.ttsWordEnd
                        )
                    }
                }
            }
        }

        // TOC dialog
        if (uiState.showToc) {
            ReadingModeTocDialog(
                chapterTitles = uiState.chapterTitles,
                currentChapterIndex = uiState.currentChapterIndex,
                onNavigateToChapter = { index ->
                    viewModel.navigateToChapter(index)
                    viewModel.toggleToc()
                },
                onDismiss = { viewModel.toggleToc() }
            )
        }

        // Bookmark list dialog
        if (uiState.showBookmarkList) {
            BookmarkListDialog(
                bookmarks = uiState.bookmarks,
                onNavigateToBookmark = { bookmark ->
                    viewModel.navigateToBookmark(bookmark)
                },
                onDeleteBookmark = { bookmark ->
                    // Delegate to existing bookmark management
                },
                onDismiss = { viewModel.toggleBookmarkList() }
            )
        }

        // Text settings bottom sheet
        if (uiState.showTextSettings) {
            ModalBottomSheetForTextSettings(
                textSettings = textSettings,
                onSettingsChanged = { newSettings ->
                    coroutineScope.launch {
                        settingsRepository.setTextSettings(newSettings)
                    }
                },
                onDismiss = { viewModel.toggleTextSettings() }
            )
        }

        // Voice picker dialog
        if (showVoicePicker) {
            TtsVoicePickerDialog(
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
}

/**
 * A single paragraph rendered with the specified text settings.
 * When TTS is active and a word is highlighted, applies a background highlight
 * to the word range within this paragraph.
 */
@Composable
private fun ParagraphItem(
    paragraph: ReadingParagraph,
    fontSize: Int,
    lineHeight: Float,
    fontFamily: ReaderFontFamily,
    ttsWordStart: Int = -1,
    ttsWordEnd: Int = -1
) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSize.toFloat().sp,
        lineHeight = (fontSize.toFloat() * lineHeight).sp,
        fontFamily = fontFamily.fontFamily
    )

    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    // Build annotated string with word highlight if applicable
    val annotatedText = if (ttsWordStart >= 0 && ttsWordEnd > ttsWordStart) {
        // Calculate word positions relative to this paragraph
        val paraStart = paragraph.charOffset
        val paraEnd = paraStart + paragraph.text.length

        // Check if the highlighted word overlaps this paragraph
        val wordStartInPara = ttsWordStart - paraStart
        val wordEndInPara = ttsWordEnd - paraStart

        if (wordStartInPara < paragraph.text.length && wordEndInPara > 0) {
            // Clamp to paragraph bounds
            val clampedStart = wordStartInPara.coerceIn(0, paragraph.text.length)
            val clampedEnd = wordEndInPara.coerceIn(0, paragraph.text.length)

            if (clampedStart < clampedEnd) {
                buildAnnotatedString {
                    append(paragraph.text)
                    addStyle(
                        style = SpanStyle(
                            background = highlightColor
                        ),
                        start = clampedStart,
                        end = clampedEnd
                    )
                }
            } else {
                AnnotatedString(paragraph.text)
            }
        } else {
            AnnotatedString(paragraph.text)
        }
    } else {
        AnnotatedString(paragraph.text)
    }

    Text(
        text = annotatedText,
        style = textStyle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

/**
 * TOC dialog for Reading Mode — simpler than the EPUB-specific one,
 * uses plain chapter titles instead of EpubChapter objects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingModeTocDialog(
    chapterTitles: List<String>,
    currentChapterIndex: Int,
    onNavigateToChapter: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Table of Contents")
            }
        },
        text = {
            if (chapterTitles.isEmpty()) {
                Text(
                    "No chapters found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    itemsIndexed(
                        items = chapterTitles,
                        key = { index, _ -> index }
                    ) { index, title ->
                        val isCurrent = index == currentChapterIndex
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { onNavigateToChapter(index) },
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.width(32.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                text = "${chapterTitles.size} chapters",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

/**
 * Simple modal bottom sheet for text settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModalBottomSheetForTextSettings(
    textSettings: TextSettings,
    onSettingsChanged: (TextSettings) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        TextSettingsPanel(
            settings = textSettings,
            onSettingsChanged = onSettingsChanged,
            onDismiss = onDismiss
        )
    }
}

/**
 * Voice picker dialog for TTS voices.
 * Lists available voices from the TTS engine and allows selection.
 */
@Composable
private fun TtsVoicePickerDialog(
    currentVoiceName: String,
    onVoiceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    VoicePickerDialog(
        currentVoiceName = currentVoiceName,
        onVoiceSelected = onVoiceSelected,
        onDismiss = onDismiss
    )
}
