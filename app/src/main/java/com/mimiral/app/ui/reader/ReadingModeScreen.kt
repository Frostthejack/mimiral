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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import com.mimiral.app.data.local.settings.TTSSettingsRepository
import com.mimiral.app.data.reader.ContentBlock
import com.mimiral.app.data.reader.Sentence
import com.mimiral.app.data.reader.TextSpan
import com.mimiral.app.tts.TTSService
import com.mimiral.app.tts.TTSState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val configuration = LocalConfiguration.current
    val paginationEngine = remember { PaginationEngine(context) }

    // Fallback dimensions (full screen) until the actual content area is known.
    // Using float density here avoids the .toInt() truncation bug (2.625 → 2) that
    // caused page heights to be computed ~25% too small on high-density devices.
    val displayDensity = context.resources.displayMetrics.density
    val fallbackWidthPx = remember(configuration) {
        (configuration.screenWidthDp * displayDensity).toInt()
    }
    val fallbackHeightPx = remember(configuration) {
        (configuration.screenHeightDp * displayDensity).toInt()
    }

    // Actual content area size — populated by onSizeChanged after first layout.
    // Pagination re-runs whenever these change so pages fit the real available space.
    var contentWidthPx by remember { mutableIntStateOf(0) }
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var lastPaginatedFont by remember { mutableIntStateOf(-1) }
    var lastPaginatedWidth by remember { mutableIntStateOf(0) }
    var lastPaginatedHeight by remember { mutableIntStateOf(0) }
    var isPaginating by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.chapters, textSettings.fontSize, contentWidthPx, contentHeightPx) {
        if (uiState.chapters.isEmpty()) return@LaunchedEffect
        val w = contentWidthPx.takeIf { it > 0 } ?: fallbackWidthPx
        val h = contentHeightPx.takeIf { it > 0 } ?: fallbackHeightPx
        if (w <= 0 || h <= 0) return@LaunchedEffect
        if (textSettings.fontSize == lastPaginatedFont &&
            w == lastPaginatedWidth &&
            h == lastPaginatedHeight &&
            uiState.pages.isNotEmpty()
        ) {
            return@LaunchedEffect
        }
        lastPaginatedFont = textSettings.fontSize
        lastPaginatedWidth = w
        lastPaginatedHeight = h
        isPaginating = true

        val capturedChapters = uiState.chapters
        val renderConfig = textSettings.toRenderConfig()

        val allPages = withContext(Dispatchers.Default) {
            val result = mutableListOf<ReadingPage>()
            var globalPageIndex = 0

            for (chapter in capturedChapters) {
                // Use structured pagination when contentBlocks are available
                if (chapter.contentBlocks.isNotEmpty()) {
                    val paginated = paginationEngine.paginateBlocks(
                        blocks = chapter.contentBlocks,
                        config = renderConfig,
                        screenWidthPx = w,
                        screenHeightPx = h
                    )

                    for (page in paginated.pages) {
                        val pageText = page.blocks.joinToString("\n\n") { it.text }
                        result.add(
                            ReadingPage(
                                pageIndex = globalPageIndex++,
                                chapterIndex = chapter.index,
                                text = pageText,
                                startCharOffset = page.startCharOffset,
                                contentBlocks = page.blocks
                            )
                        )
                    }
                } else {
                    // Fallback to text-based pagination for chapters without blocks
                    val chapterText = chapter.paragraphs.joinToString("\n\n") { it.text }
                    if (chapterText.isBlank()) continue

                    val paginated = paginationEngine.paginate(
                        text = chapterText,
                        config = renderConfig,
                        screenWidthPx = w,
                        screenHeightPx = h
                    )

                    for (page in paginated.pages) {
                        result.add(
                            ReadingPage(
                                pageIndex = globalPageIndex++,
                                chapterIndex = chapter.index,
                                text = page.blocks.joinToString("\n\n") { it.text },
                                startCharOffset = page.startCharOffset,
                                contentBlocks = page.blocks
                            )
                        )
                    }
                }
            }
            result
        }

        viewModel.setPages(allPages)
        isPaginating = false
    }

    // Pager state
    val pageCount = uiState.pages.size
    val pagerState = rememberPagerState(
        pageCount = { pageCount },
        initialPage = uiState.currentPageIndex.coerceAtMost(pageCount - 1).coerceAtLeast(0)
    )

    // Report page changes to ViewModel
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }

    // Restore saved page once pagination is first complete (fires when pageCount goes from 0 → N)
    LaunchedEffect(pageCount) {
        if (pageCount > 0 && uiState.currentPageIndex > 0 &&
            uiState.currentPageIndex < pageCount &&
            pagerState.currentPage == 0
        ) {
            pagerState.scrollToPage(uiState.currentPageIndex)
        }
    }

    // Handle page-scroll requests (from chapter/bookmark navigation)
    LaunchedEffect(uiState.currentPageIndex) {
        if (pagerState.currentPage != uiState.currentPageIndex &&
            uiState.currentPageIndex in 0 until pageCount
        ) {
            pagerState.animateScrollToPage(uiState.currentPageIndex)
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

        // In paginated mode, find which page contains this word offset
        // and scroll to that page if not already visible
        val pages = uiState.pages
        val targetPage = pages.indexOfFirst { page ->
            wordStart >= page.startCharOffset &&
                wordStart < page.startCharOffset + page.text.length
        }
        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(targetPage)
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
                    // Read Aloud button — shows when TTS is idle, ready, or stopped
                    val isTtsIdle = uiState.ttsState == TTSState.IDLE
                    val isTtsReady = uiState.ttsState == TTSState.READY
                    val isTtsStopped = uiState.ttsState == TTSState.STOPPED
                    if (isTtsIdle || isTtsReady || isTtsStopped) {
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
            } else if (uiState.pages.isNotEmpty()) {
                Column {
                    LinearProgressIndicator(
                        progress = { (uiState.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Page numbers + chapter info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Page ${uiState.currentPageIndex + 1} of ${uiState.totalPages}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Ch. ${uiState.currentChapterIndex + 1}" +
                                " of ${uiState.chapterTitles.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0 &&
                        (size.width != contentWidthPx || size.height != contentHeightPx)
                    ) {
                        contentWidthPx = size.width
                        contentHeightPx = size.height
                    }
                }
        ) {
        when {
            uiState.isLoading || isPaginating -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.isLoading) {
                                "Extracting text content\u2026"
                            } else {
                                "Preparing pages\u2026"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
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

            uiState.pages.isEmpty() && uiState.chapters.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { index -> index }
                ) { pageIndex ->
                    val page = uiState.pages.getOrNull(pageIndex)
                    if (page != null) {
                        val ttsPageOffset = if (
                            uiState.ttsStartPage >= 0 &&
                                pageIndex >= uiState.ttsStartPage
                        ) {
                            uiState.ttsPageOffsets
                                .getOrNull(pageIndex - uiState.ttsStartPage) ?: -1
                        } else {
                            -1
                        }
                        PageTextContent(
                            text = page.text,
                            contentBlocks = page.contentBlocks,
                            fontSize = textSettings.fontSize,
                            lineHeight = textSettings.lineSpacingMultiplier,
                            fontFamily = textSettings.selectedFontFamily,
                            marginLeft = textSettings.marginLeft,
                            marginRight = textSettings.marginRight,
                            marginTop = textSettings.marginTop,
                            marginBottom = textSettings.marginBottom,
                            ttsWordStart = uiState.ttsWordStart,
                            ttsWordEnd = uiState.ttsWordEnd,
                            ttsPageOffset = ttsPageOffset
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
                    val targetPage = viewModel.navigateToChapter(index)
                    viewModel.toggleToc()
                    if (targetPage >= 0 && targetPage < pageCount) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                },
                onDismiss = { viewModel.toggleToc() }
            )
        }

        // Bookmark list dialog
        if (uiState.showBookmarkList) {
            BookmarkListDialog(
                bookmarks = uiState.bookmarks,
                onNavigateToBookmark = { bookmark ->
                    val targetPage = viewModel.navigateToBookmark(bookmark)
                    if (targetPage >= 0 && targetPage < pageCount) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                },
                onDeleteBookmark = { _ -> },
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
        } // end outer Box
    }
}

/**
 * Renders a single page of text content with rich Typography.
 *
 * When contentBlocks is non-empty, renders each block with appropriate
 * typography styles (headings, quotes, list items, paragraphs with spans).
 * Falls back to flat bodyLarge text when no blocks are available.
 *
 * All blocks respect the user's font size setting — base sizes are scaled
 * proportionally. TTS word highlighting is applied as an overlay SpanStyle
 * on top of existing formatting within the relevant block.
 */
@Composable
private fun PageTextContent(
    text: String,
    contentBlocks: List<ContentBlock> = emptyList(),
    fontSize: Int,
    lineHeight: Float,
    fontFamily: ReaderFontFamily,
    marginLeft: Int,
    marginRight: Int,
    marginTop: Int,
    marginBottom: Int,
    ttsWordStart: Int = -1,
    ttsWordEnd: Int = -1,
    ttsPageOffset: Int = -1
) {
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    // Font size scale factor: user setting relative to default (18sp)
    val fontScale = fontSize.toFloat() / 18f

    // Base text style for paragraphs
    val baseParagraphStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSize.toFloat().sp,
        lineHeight = (fontSize.toFloat() * lineHeight).sp,
        fontFamily = fontFamily.fontFamily
    )

    // Pre-compute fallback paragraphs (for when contentBlocks is empty)
    val fallbackParagraphs = remember(text) {
        text.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    // Cumulative char offsets of each content block within this page's text.
    // Used to convert the TTS absolute word offset to a block-local offset.
    val blockStartOffsets = remember(contentBlocks) {
        var off = 0
        contentBlocks.map { block ->
            val s = off
            off += block.text.length + 2 // +2 for the "\n\n" separator between blocks
            s
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = marginLeft.dp,
                end = marginRight.dp,
                top = marginTop.dp,
                bottom = marginBottom.dp
            ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            vertical = 8.dp
        )
    ) {
        if (contentBlocks.isNotEmpty()) {
            itemsIndexed(
                items = contentBlocks,
                key = { index, _ -> index }
            ) { blockIndex, block ->
                // Convert TTS absolute offset to block-local offset for accurate highlighting.
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
                                fontFamily = fontFamily.fontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            2 -> MaterialTheme.typography.headlineMedium.copy(
                                fontSize = (28f * fontScale).sp,
                                lineHeight = (28f * fontScale * lineHeight).sp,
                                fontFamily = fontFamily.fontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            3 -> MaterialTheme.typography.titleMedium.copy(
                                fontSize = (22f * fontScale).sp,
                                lineHeight = (22f * fontScale * lineHeight).sp,
                                fontFamily = fontFamily.fontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            else -> MaterialTheme.typography.bodyLarge.copy(
                                fontSize = (18f * fontScale).sp,
                                lineHeight = (18f * fontScale * lineHeight).sp,
                                fontFamily = fontFamily.fontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val annotated = applyTtsHighlight(
                            block.text, localTtsStart, localTtsEnd, highlightColor
                        )
                        Text(
                            text = annotated,
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
                        val annotated = buildParagraphAnnotatedString(
                            text = block.text,
                            spans = block.spans,
                            ttsWordStart = localTtsStart,
                            ttsWordEnd = localTtsEnd,
                            highlightColor = highlightColor
                        )
                        Text(
                            text = annotated,
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
                            fontFamily = fontFamily.fontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val annotated = applyTtsHighlight(
                            block.text, localTtsStart, localTtsEnd, highlightColor
                        )
                        Text(
                            text = annotated,
                            style = quoteStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }

                    is ContentBlock.ListItem -> {
                        val prefix = if (block.order > 0) "${block.order}. " else "• "
                        val fullText = "$prefix${block.text}"
                        // Shift by prefix length since the prefix is not in block.text
                        val listStart =
                            if (localTtsStart >= 0) localTtsStart + prefix.length else -1
                        val listEnd =
                            if (localTtsEnd >= 0) localTtsEnd + prefix.length else -1
                        val annotated = applyTtsHighlight(
                            fullText, listStart, listEnd, highlightColor
                        )
                        Text(
                            text = annotated,
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
        } else {
            // Fallback: flat text rendering (original behavior)
            itemsIndexed(
                items = fallbackParagraphs,
                key = { index, _ -> index }
            ) { _, paragraphText ->
                val annotatedText = applyTtsHighlight(
                    paragraphText,
                    ttsWordStart,
                    ttsWordEnd,
                    highlightColor
                )
                Text(
                    text = annotatedText,
                    style = baseParagraphStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Apply TTS word highlight to text if start/end offsets are valid.
 */
private fun applyTtsHighlight(
    text: String,
    ttsWordStart: Int,
    ttsWordEnd: Int,
    highlightColor: Color
): AnnotatedString {
    if (ttsWordStart < 0 || ttsWordEnd <= ttsWordStart) {
        return AnnotatedString(text)
    }
    val localEnd = ttsWordEnd.coerceAtMost(text.length)
    val localStart = ttsWordStart.coerceAtLeast(0).coerceAtMost(localEnd)
    if (localStart < localEnd) {
        return buildAnnotatedString {
            append(text)
            addStyle(
                style = SpanStyle(background = highlightColor),
                start = localStart,
                end = localEnd
            )
        }
    }
    return AnnotatedString(text)
}

/**
 * Build an AnnotatedString for a Paragraph, applying inline bold/italic spans
 * and TTS word highlight on top.
 */
private fun buildParagraphAnnotatedString(
    text: String,
    spans: List<TextSpan>,
    ttsWordStart: Int,
    ttsWordEnd: Int,
    highlightColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        append(text)

        // Apply inline spans (bold, italic)
        for (span in spans) {
            val spanStyle = SpanStyle(
                fontWeight = if (span.isBold) {
                    FontWeight.Bold
                } else {
                    null
                },
                fontStyle = if (span.isItalic) {
                    FontStyle.Italic
                } else {
                    null
                }
            )
            addStyle(
                style = spanStyle,
                start = span.start.coerceIn(0, text.length),
                end = span.end.coerceIn(0, text.length)
            )
        }

        // Apply TTS highlight on top of existing styles
        if (ttsWordStart >= 0 && ttsWordEnd > ttsWordStart) {
            val localEnd = ttsWordEnd.coerceAtMost(text.length)
            val localStart = ttsWordStart.coerceAtLeast(0).coerceAtMost(localEnd)
            if (localStart < localEnd) {
                addStyle(
                    style = SpanStyle(background = highlightColor),
                    start = localStart,
                    end = localEnd
                )
            }
        }
    }
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
