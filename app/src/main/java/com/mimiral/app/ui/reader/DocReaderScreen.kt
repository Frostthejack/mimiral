package com.mimiral.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocReaderScreen(
    bookId: Int,
    onNavigateBack: () -> Unit,
    viewModel: DocReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settingsRepository = remember { ReaderSettingsRepository(context) }
    val settings by settingsRepository.settings.collectAsState(initial = ReaderSettings())
    val textSettings by settingsRepository.textSettings.collectAsState(initial = TextSettings())
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val paginationEngine = remember { PaginationEngine(context) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    val currentChapterText = remember(uiState.currentChapter, uiState.fullText) {
        uiState.chapters.getOrNull(uiState.currentChapter)?.text ?: uiState.fullText
    }

    var paginationResult by remember { mutableStateOf<PaginationResult?>(null) }

    LaunchedEffect(currentChapterText, textSettings, screenWidthPx, screenHeightPx) {
        if (currentChapterText.isNotBlank()) {
            paginationResult = paginationEngine.paginate(
                text = currentChapterText,
                config = textSettings.toRenderConfig(),
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx
            )
        }
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
        viewModel.onPageTurn(pagerState.currentPage)
    }

    val currentChapterTitle = uiState.chapters.getOrNull(uiState.currentChapter)?.title
        ?: uiState.title.ifBlank { "Reader" }

    val onTextSettingsChanged: (TextSettings) -> Unit = remember(settingsRepository) {
        { newSettings ->
            coroutineScope.launch {
                settingsRepository.setTextSettings(newSettings)
            }
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
                            val author = uiState.author
                            if (author != null) {
                                Text(
                                    text = author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.showToc() }) {
                            Icon(Icons.Default.List, contentDescription = "Chapters")
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
                                coroutineScope.launch { pagerState.scrollToPage(0) }
                            }
                        },
                        enabled = viewModel.hasPreviousChapter(),
                        icon = { Icon(Icons.Default.ArrowBackIos, contentDescription = "Previous chapter") },
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
                                coroutineScope.launch { pagerState.scrollToPage(0) }
                            }
                        },
                        enabled = viewModel.hasNextChapter(),
                        icon = { Icon(Icons.Default.ArrowForwardIos, contentDescription = "Next chapter") },
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
            } else if (pageCount >= 1 && pages.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { index -> "$bookId-${uiState.currentChapter}-$index" }
                ) { pageIndex ->
                    val pageOffset = (
                        (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                        )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = kotlin.math.max(0.7f, 1f - (abs(pageOffset) * 0.3f))
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
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            }
                                        }
                                        offset.x > (size.width * 2f / 3f) -> {
                                            if (pagerState.currentPage < pageCount - 1) {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            }
                                        }
                                        else -> toolbarVisible = !toolbarVisible
                                    }
                                }
                            }
                    ) {
                        val pageText = pages.getOrNull(pageIndex)?.text ?: ""
                        DocPageContent(
                            pageText = pageText,
                            pageNumber = pageIndex + 1,
                            totalPages = pageCount,
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
                        text = "No content to display.\nThe document may be empty or in an unsupported format.",
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
                }
            }
        }
    }

    if (uiState.showToc) {
        val epubStyleChapters = uiState.chapters.map { ch ->
            EpubChapter(ch.index, ch.title, ch.startPage, ch.endPage)
        }
        TableOfContentsDialog(
            chapters = epubStyleChapters,
            currentChapterIndex = uiState.currentChapter,
            onNavigateToChapter = { chapterIndex ->
                viewModel.navigateToChapter(chapterIndex)
                coroutineScope.launch { pagerState.scrollToPage(0) }
            },
            onDismiss = { viewModel.dismissToc() }
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
private fun DocPageContent(
    pageText: String,
    pageNumber: Int,
    totalPages: Int,
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
        Text(
            text = pageText,
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = (textSettings.fontSize * textSettings.lineSpacingMultiplier).sp,
                fontSize = textSettings.fontSize.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "$pageNumber / $totalPages",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp)
        )
    }
}
