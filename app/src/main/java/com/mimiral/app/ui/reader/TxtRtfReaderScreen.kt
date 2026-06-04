package com.mimiral.app.ui.reader

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.local.settings.ReaderSettings
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import com.mimiral.app.data.reader.EpubChapter
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxtRtfReaderScreen(
    bookId: Int,
    onNavigateBack: () -> Unit,
    viewModel: TxtRtfReaderViewModel = hiltViewModel()
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

    // Paginate current chapter text
    val chapterText = viewModel.getChapterText(uiState.currentChapter)
    var paginationResult by remember { mutableStateOf<PaginationResult?>(null) }
    LaunchedEffect(
        chapterText,
        textSettings,
        screenWidthPx,
        screenHeightPx,
        uiState.currentChapter
    ) {
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
            lastReadPosition = "page:" + pagerState.currentPage
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
                                text = uiState.chapters.getOrNull(uiState.currentChapter)?.title
                                    ?: uiState.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = buildTxtRtfProgressText(uiState),
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
                                imageVector = if (uiState.isCurrentPageBookmarked) {
                                    Icons.Default.Bookmark
                                } else {
                                    Icons.Default.BookmarkBorder
                                },
                                contentDescription = if (uiState.isCurrentPageBookmarked) {
                                    "Remove bookmark"
                                } else {
                                    "Add bookmark"
                                },
                                tint = if (uiState.isCurrentPageBookmarked) {
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
                                    if (chapter != null) pagerState.animateScrollToPage(0)
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
                        icon = { Text((pagerState.currentPage + 1).toString() + "/" + pageCount) },
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
                                    if (chapter != null) pagerState.animateScrollToPage(0)
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
                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
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
                        text = "Error: " + uiState.error,
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
                    val pageOffset = (pagerState.currentPage - pageIndex) +
                        pagerState.currentPageOffsetFraction
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
                        TxtRtfPageContent(
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
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = (pagerState.currentPage + 1).toString() + " / " + pageCount,
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
            chapters = uiState.chapters.map {
                EpubChapter(
                    index = it.index,
                    title = it.title,
                    href = ""
                )
            },
            currentChapterIndex = uiState.currentChapter,
            onNavigateToChapter = { chapterIndex ->
                viewModel.navigateToChapter(chapterIndex)
                coroutineScope.launch { pagerState.animateScrollToPage(0) }
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
            onDeleteBookmark = { bookmark -> viewModel.deleteBookmark(bookmark) },
            onDismiss = { viewModel.dismissBookmarks() }
        )
    }

    if (showTextSettings) {
        TextSettingsPanel(
            settings = textSettings,
            onSettingsChanged = onTextSettingsChanged,
            onDismiss = { showTextSettings = false },
            paginationEngine = paginationEngine
        )
    }
}

@Composable
private fun TxtRtfPageContent(
    pageText: String,
    pageNumber: Int,
    totalPages: Int,
    chapterTitle: String? = null,
    textSettings: TextSettings = TextSettings()
) {
    val scrollState = rememberScrollState()
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
        if (chapterTitle != null) {
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        Text(
            text = pageText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = textSettings.fontSize.sp,
                lineHeight = (textSettings.fontSize * textSettings.lineSpacingMultiplier).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun buildTxtRtfProgressText(uiState: TxtRtfUiState): String {
    val pageText = "Page " + (uiState.currentPage + 1) + " of " + uiState.totalPages
    val percentText = uiState.progress.progressPercent.toInt().toString() + "%"
    return pageText + " \u00b7 " + percentText
}
