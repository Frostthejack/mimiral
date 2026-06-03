package com.mimiral.app.ui.reader

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.local.settings.ReaderSettings
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Fb2ReaderScreen(
    bookId: Int,
    onNavigateBack: () -> Unit,
    viewModel: Fb2ReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settingsRepository = remember { ReaderSettingsRepository(context) }
    val settings by settingsRepository.settings.collectAsState(initial = ReaderSettings())
    val textSettings by settingsRepository.textSettings.collectAsState(initial = TextSettings())
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val focusRequester = remember { FocusRequester() }
    val paginationEngine = remember { PaginationEngine(context) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    val chapterText = remember {
        """
        Chapter 1: The Beginning

        Welcome to Mimiral FB2 Reader.

        This is the beginning of your FictionBook 2 document. The text is paginated using
        Android's StaticLayout engine, which calculates line breaks and page boundaries
        based on the screen dimensions, font size, line spacing, and margins.

        Tap the left or right side of the screen to turn pages,
            or swipe between pages. Volume keys can also be used if that setting is enabled.

        FictionBook 2 (FB2) is an XML-based ebook format. This parser supports:
        - Plain .fb2 files (XML)
        - Compressed .fb2.zip archives
        - Embedded base64 images
        - Sections, paragraphs, titles, subtitles
        - Poetry (verse lines)
        - Annotations
        - Author metadata (first, middle, last name)

        End of sample content. FB2 support is now available in Mimiral!
        """.trimIndent()
    }

    var paginationResult by remember { mutableStateOf<PaginationResult?>(null) }
    LaunchedEffect(textSettings, screenWidthPx, screenHeightPx) {
        paginationResult = paginationEngine.paginate(
            text = chapterText,
            config = textSettings.toRenderConfig(),
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx
        )
    }

    val pages = paginationResult?.pages ?: emptyList()
    val pageCount = pages.size.coerceAtLeast(1)

    LaunchedEffect(pageCount) {
        viewModel.setTotalPages(pageCount)
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount }
    )

    var toolbarVisible by remember { mutableStateOf(false) }
    var showTextSettings by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val handleVolumeKey: (Int) -> Boolean = remember(settings) {
        { keyCode ->
            if (!settings.volumeKeyNavigationEnabled) return@remember false
            val isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
            val isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            if (!isVolumeUp && !isVolumeDown) return@remember false

            val swap = settings.volumeKeyDirectionSwapped
            val goNext = if (swap) isVolumeUp else isVolumeDown
            val goPrev = if (swap) isVolumeDown else isVolumeUp

            when {
                goNext && pagerState.currentPage < pageCount - 1 -> {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            pagerState.currentPage + 1
                        )
                    }
                    true
                }
                goPrev && pagerState.currentPage > 0 -> {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            pagerState.currentPage - 1
                        )
                    }
                    true
                }
                else -> true
            }
        }
    }

    val isBookmarked = uiState.isCurrentPageBookmarked
    val currentChapterTitle = uiState.chapters.getOrNull(uiState.currentChapter)?.title
        ?: "FB2 Reader"

    val onTextSettingsChanged: (TextSettings) -> Unit = remember(settingsRepository) {
        { newSettings ->
            coroutineScope.launch { settingsRepository.setTextSettings(newSettings) }
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
                                text = buildFb2ProgressText(uiState),
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
            if (toolbarVisible) {
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
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        handleVolumeKey(keyEvent.nativeKeyEvent.keyCode)
                    } else {
                        false
                    }
                }
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    key = { index -> index }
                ) { pageIndex ->
                    val pageOffset = (
                        (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                        )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = if (abs(pageOffset) < 1f) {
                                    1f - (abs(pageOffset) * 0.3f)
                                } else {
                                    0.7f
                                }
                                val scale = 1f - (abs(pageOffset) * 0.03f).coerceIn(0f, 0.1f)
                                scaleX = scale
                                scaleY = scale
                                translationX = pageOffset * size.width * -0.05f
                                clip = true
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val tapZoneWidth = size.width / 3f
                                    when {
                                        offset.x < tapZoneWidth -> {
                                            if (pagerState.currentPage > 0) {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(
                                                        pagerState.currentPage - 1
                                                    )
                                                }
                                            }
                                        }
                                        offset.x > (size.width * 2f / 3f) -> {
                                            if (pagerState.currentPage < pageCount - 1) {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(
                                                        pagerState.currentPage + 1
                                                    )
                                                }
                                            }
                                        }
                                        else -> { toolbarVisible = !toolbarVisible }
                                    }
                                }
                            }
                    ) {
                        val pageText = pages.getOrNull(pageIndex)?.text ?: ""
                        val pageStartOffset = pages.getOrNull(pageIndex)?.startOffset ?: 0

                        EpubPageContent(
                            pageText = pageText,
                            pageNumber = pageIndex + 1,
                            totalPages = pageCount,
                            chapterTitle = if (pageIndex == 0) {
                                uiState.chapters.getOrNull(uiState.currentChapter)?.title
                            } else {
                                null
                            },
                            textSettings = textSettings
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
                        modifier = Modifier.width(100.dp).padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }

    if (uiState.showToc) {
        TableOfContentsDialog(
            chapters = uiState.chapters,
            currentChapterIndex = uiState.currentChapter,
            onNavigateToChapter = { chapterIndex ->
                viewModel.navigateToChapter(chapterIndex)
                coroutineScope.launch {
                    val chapter = uiState.chapters.getOrNull(chapterIndex)
                    if (chapter != null) pagerState.animateScrollToPage(chapter.startPage)
                }
            },
            onDismiss = { viewModel.dismissToc() }
        )
    }

    if (uiState.showBookmarks) {
        BookmarkListDialog(
            bookmarks = uiState.bookmarks,
            onNavigateToBookmark = { bookmark ->
                viewModel.navigateToBookmark(bookmark.chapterIndex, bookmark.pageNumber)
                coroutineScope.launch { pagerState.animateScrollToPage(bookmark.pageNumber) }
            },
            onDismiss = { viewModel.dismissBookmarks() }
        )
    }

    if (showTextSettings) {
        TextSettingsPanel(
            settings = textSettings,
            onSettingsChanged = onTextSettingsChanged,
            onDismiss = { showTextSettings = false }
        )
    }
}

private fun buildFb2ProgressText(uiState: Fb2ReaderUiState): String {
    val progress = uiState.progress
    return if (progress.totalCharacters > 0) {
        "Page ${uiState.currentPage + 1} of ${uiState.totalPages}" +
            " - ${progress.progressPercent.toInt()}%"
    } else {
        "Page ${uiState.currentPage + 1} of ${uiState.totalPages}"
    }
}
