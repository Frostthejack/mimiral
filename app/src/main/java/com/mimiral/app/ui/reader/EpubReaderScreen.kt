package com.mimiral.app.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.local.settings.ReaderSettings
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import kotlinx.coroutines.launch
import java.text.BreakIterator
import kotlin.math.abs
import kotlin.math.roundToInt

private data class PageTextSelection(
    val text: String = "",
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val startPosition: Offset = Offset.Zero,
    val endPosition: Offset = Offset.Zero,
    val handleStart: Offset = Offset.Zero,
    val handleEnd: Offset = Offset.Zero,
    val isActive: Boolean = false,
    val pageIndex: Int = -1
)

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
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val focusRequester = remember { FocusRequester() }

    val samplePages = remember {
        listOf(
            "Page 1\n\nWelcome to Mimiral Reader.\n\nThis is the first page of your book. Tap the left or right side of the screen to turn pages, or swipe to navigate.",
            "Page 2\n\nTap Zones:\n\n\u2022 Tap the left third of the screen to go to the previous page.\n\u2022 Tap the right third to go to the next page.\n\u2022 Tap the center to show or hide the toolbar.",
            "Page 3\n\nSwipe Gestures:\n\n\u2022 Swipe left to go to the next page.\n\u2022 Swipe right to go to the previous page.\n\nThe page transition uses GPU-accelerated animations for smooth performance.",
            "Page 4\n\nVolume Key Navigation:\n\nVolume keys can also be used to turn pages.\n\nVolume Up = Previous page\nVolume Down = Next page\n\nYou can swap this direction in the reader settings.",
            "Page 5\n\nThis is a demonstration of the EPUB reader screen.\n\nIn production, actual EPUB content would be rendered here with proper pagination from the Readium library.",
            "Page 6\n\nEnd of sample content.\n\nThank you for using Mimiral Reader!"
        )
    }

    val pageCount = samplePages.size

    LaunchedEffect(pageCount) {
        viewModel.setTotalPages(pageCount)
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount }
    )

    var toolbarVisible by remember { mutableStateOf(false) }

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

    val handleVolumeKey: (Int) -> Boolean = remember(settings) { { keyCode ->
        if (!settings.volumeKeyNavigationEnabled) return@remember false
        val isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (!isVolumeUp && !isVolumeDown) return@remember false
        val swap = settings.volumeKeyDirectionSwapped
        val goNext = if (swap) isVolumeUp else isVolumeDown
        val goPrev = if (swap) isVolumeDown else isVolumeUp
        when {
            goNext && pagerState.currentPage < pageCount - 1 -> {
                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                true
            }
            goPrev && pagerState.currentPage > 0 -> {
                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                true
            }
            else -> true
        }
    } }

    val isBookmarked = uiState.isCurrentPageBookmarked
    val currentChapterTitle = uiState.chapters.getOrNull(uiState.currentChapter)?.title ?: "Reader"

    // Text selection state
    var textSelection by remember { mutableStateOf(PageTextSelection()) }
    var showSelectionToolbar by remember { mutableStateOf(false) }
    var showDictionaryPopup by remember { mutableStateOf(false) }
    var dictionaryWord by remember { mutableStateOf("") }
    var dictionaryOffset by remember { mutableStateOf(Offset.Zero) }
    var toolbarOffset by remember { mutableStateOf(Offset.Zero) }
    val pageLayoutResults = remember { mutableStateMapOf<Int, TextLayoutResult?>() }
    val pageSizes = remember { mutableStateMapOf<Int, androidx.compose.ui.unit.IntSize>() }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Mimiral", text)
        clipboard.setPrimaryClip(clip)
        textSelection = PageTextSelection()
        showSelectionToolbar = false
    }

    fun shareText(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share via"))
        textSelection = PageTextSelection()
        showSelectionToolbar = false
    }

    fun highlightSelection(text: String) {
        textSelection = PageTextSelection()
        showSelectionToolbar = false
    }

    fun clearSelection() {
        textSelection = PageTextSelection()
        showSelectionToolbar = false
    }

    fun lookupDictionary(word: String, offset: Offset) {
        dictionaryWord = word
        dictionaryOffset = offset
        showDictionaryPopup = true
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
                        IconButton(onClick = { viewModel.showToc() }) {
                            Icon(Icons.Default.List, contentDescription = "Table of Contents")
                        }
                        IconButton(onClick = { viewModel.toggleBookmark() }) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.showBookmarks() }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                                    val chapter = uiState.chapters.getOrNull(uiState.currentChapter - 1)
                                    if (chapter != null) {
                                        pagerState.animateScrollToPage(chapter.startPage)
                                    }
                                }
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
                                coroutineScope.launch {
                                    val chapter = uiState.chapters.getOrNull(uiState.currentChapter + 1)
                                    if (chapter != null) {
                                        pagerState.animateScrollToPage(chapter.startPage)
                                    }
                                }
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
                .focusRequester(focusRequester)
                .focusTarget()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        handleVolumeKey(keyEvent.nativeKeyEvent.keyCode)
                    } else false
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (textSelection.isActive) clearSelection() }
                    )
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
            } else if (pageCount > 0) {
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
                                alpha = if (abs(pageOffset) < 1f) 1f - (abs(pageOffset) * 0.3f) else 0.7f
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
                                        offset.x > tapZoneWidth * 2 -> {
                                            if (pagerState.currentPage < pageCount - 1) {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            }
                                        }
                                        else -> { toolbarVisible = !toolbarVisible }
                                    }
                                }
                            }
                            .onGloballyPositioned { coords ->
                                pageSizes[pageIndex] = coords.size
                            }
                    ) {
                        SelectableEpubPageContent(
                            pageText = samplePages[pageIndex],
                            pageNumber = pageIndex + 1,
                            totalPages = pageCount,
                            chapterTitle = uiState.chapters.getOrNull(uiState.currentChapter)?.title,
                            selection = textSelection,
                            onSelectionChange = { newSelection ->
                                textSelection = newSelection
                                showSelectionToolbar = newSelection.isActive && newSelection.text.isNotBlank()
                                if (showSelectionToolbar) {
                                    val selCenterX = (newSelection.startPosition.x + newSelection.endPosition.x) / 2f
                                    val selTopY = minOf(newSelection.startPosition.y, newSelection.endPosition.y)
                                    val pageWidth = pageSizes[pageIndex]?.width?.toFloat() ?: 300f
                                    toolbarOffset = Offset(
                                        x = selCenterX.coerceIn(80f, pageWidth - 80f),
                                        y = (selTopY - 80f).coerceAtLeast(10f)
                                    )
                                }
                            },
                            onLongPressWord = { word, offset -> lookupDictionary(word, offset) },
                            onLayoutResult = { result -> pageLayoutResults[pageIndex] = result }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
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

            // Selection handles and toolbar
            if (showSelectionToolbar && textSelection.isActive && textSelection.text.isNotBlank()) {
                Box(
                    modifier = Modifier.offset {
                        IntOffset((textSelection.handleStart.x - 12f).roundToInt(), (textSelection.handleStart.y - 24f).roundToInt())
                    }
                    .pointerInput(textSelection) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            textSelection = textSelection.copy(handleStart = textSelection.handleStart + Offset(dragAmount.x, dragAmount.y))
                        }
                    }
                ) { SelectionHandle() }

                Box(
                    modifier = Modifier.offset {
                        IntOffset((textSelection.handleEnd.x - 12f).roundToInt(), (textSelection.handleEnd.y).roundToInt())
                    }
                    .pointerInput(textSelection) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            textSelection = textSelection.copy(handleEnd = textSelection.handleEnd + Offset(dragAmount.x, dragAmount.y))
                        }
                    }
                ) { SelectionHandle() }

                SelectionToolbar(
                    selectedText = textSelection.text,
                    offset = toolbarOffset,
                    onCopy = { copyToClipboard(textSelection.text) },
                    onShare = { shareText(textSelection.text) },
                    onHighlight = { highlightSelection(textSelection.text) },
                    onDictionary = {
                        lookupDictionary(textSelection.text, Offset(
                            (textSelection.startPosition.x + textSelection.endPosition.x) / 2f,
                            minOf(textSelection.startPosition.y, textSelection.endPosition.y)
                        ))
                    },
                    onDismiss = { clearSelection() }
                )
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
            bookmarks = emptyList(),
            onNavigateToBookmark = { bookmark ->
                viewModel.navigateToBookmark(bookmark.chapterIndex, bookmark.pageNumber)
                coroutineScope.launch { pagerState.animateScrollToPage(bookmark.pageNumber) }
            },
            onDeleteBookmark = { },
            onDismiss = { viewModel.dismissBookmarks() }
        )
    }

    if (showDictionaryPopup) {
        DictionaryPopup(
            word = dictionaryWord,
            offsetX = dictionaryOffset.x,
            offsetY = dictionaryOffset.y,
            onDismiss = { showDictionaryPopup = false }
        )
    }
}

@Composable
private fun SelectionHandle() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(24.dp).background(color = MaterialTheme.colorScheme.primary, shape = CircleShape))
        Box(modifier = Modifier.width(2.dp).height(16.dp).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun SelectionToolbar(
    selectedText: String,
    offset: Offset,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onHighlight: () -> Unit,
    onDictionary: () -> Unit,
    onDismiss: () -> Unit
) {
    val displayText = if (selectedText.length > 30) selectedText.take(30) + "..." else selectedText

    Box(modifier = Modifier.offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).widthIn(max = 200.dp)
                )
                HorizontalDivider()
                Row(modifier = Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onHighlight, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Highlight, contentDescription = "Highlight", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDictionary, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MenuBook, contentDescription = "Dictionary", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableEpubPageContent(
    pageText: String,
    pageNumber: Int,
    totalPages: Int,
    chapterTitle: String? = null,
    selection: PageTextSelection,
    onSelectionChange: (PageTextSelection) -> Unit,
    onLongPressWord: (String, Offset) -> Unit,
    onLayoutResult: (TextLayoutResult) -> Unit
) {
    val scrollState = rememberScrollState()
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var selectionStart by remember { mutableIntStateOf(-1) }
    var selectionEnd by remember { mutableIntStateOf(-1) }
    var isSelecting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
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

        Text(
            text = pageText,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp, fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(pageText) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            layoutResult?.let { layout ->
                                val charOffset = layout.getOffsetForPosition(offset)
                                if (charOffset >= 0 && charOffset < pageText.length) {
                                    val wordBounds = getWordBounds(pageText, charOffset)
                                    if (wordBounds != null) {
                                        val (wordStart, wordEnd) = wordBounds
                                        val word = pageText.substring(wordStart, wordEnd).trim()
                                        if (word.isNotEmpty()) {
                                            isSelecting = true
                                            selectionStart = wordStart
                                            selectionEnd = wordEnd
                                            val startLine = layout.getLineForOffset(wordStart)
                                            val endLine = layout.getLineForOffset(wordEnd - 1)
                                            val startX = layout.getHorizontalPosition(wordStart, true)
                                            val startY = layout.getLineTop(startLine)
                                            val endX = layout.getHorizontalPosition(wordEnd - 1, false)
                                            val endY = layout.getLineTop(endLine)
                                            val startBottom = layout.getLineBottom(startLine).toFloat()
                                            val endBottom = layout.getLineBottom(endLine).toFloat()
                                            onSelectionChange(PageTextSelection(
                                                text = word, startOffset = wordStart, endOffset = wordEnd,
                                                startPosition = Offset(startX, startY), endPosition = Offset(endX, endY),
                                                handleStart = Offset(startX, startBottom), handleEnd = Offset(endX, endBottom),
                                                isActive = true, pageIndex = pageNumber - 1
                                            ))
                                            onLongPressWord(word, offset)
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
                .pointerInput(pageText) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            layoutResult?.let { layout ->
                                val charOffset = layout.getOffsetForPosition(offset)
                                if (charOffset >= 0 && charOffset < pageText.length) {
                                    isSelecting = true
                                    selectionStart = charOffset
                                    selectionEnd = charOffset
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            if (isSelecting) {
                                change.consume()
                                layoutResult?.let { layout ->
                                    val charOffset = layout.getOffsetForPosition(change.position)
                                    if (charOffset >= 0 && charOffset <= pageText.length) {
                                        selectionEnd = charOffset
                                        val start = minOf(selectionStart, selectionEnd)
                                        val end = maxOf(selectionStart, selectionEnd)
                                        val selectedText = pageText.substring(start, end).trim()
                                        if (selectedText.isNotEmpty()) {
                                            val startLine = layout.getLineForOffset(start)
                                            val endLine = layout.getLineForOffset(end - 1)
                                            val startX = layout.getHorizontalPosition(start, true)
                                            val startY = layout.getLineTop(startLine)
                                            val endX = layout.getHorizontalPosition(end - 1, false)
                                            val endY = layout.getLineTop(endLine)
                                            val startBottom = layout.getLineBottom(startLine).toFloat()
                                            val endBottom = layout.getLineBottom(endLine).toFloat()
                                            onSelectionChange(PageTextSelection(
                                                text = selectedText, startOffset = start, endOffset = end,
                                                startPosition = Offset(startX, startY), endPosition = Offset(endX, endY),
                                                handleStart = Offset(startX, startBottom), handleEnd = Offset(endX, endBottom),
                                                isActive = true, pageIndex = pageNumber - 1
                                            ))
                                        }
                                    }
                                }
                            }
                        },
                        onDragEnd = { isSelecting = false }
                    )
                },
            onTextLayout = { result ->
                layoutResult = result
                onLayoutResult(result)
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "$pageNumber",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).wrapContentWidth(Alignment.CenterHorizontally)
        )
    }
}

private fun getWordBounds(text: String, offset: Int): Pair<Int, Int>? {
    if (text.isBlank() || offset < 0 || offset > text.length) return null
    val breakIterator = BreakIterator.getWordInstance()
    breakIterator.setText(text)
    val start = breakIterator.preceding(offset).takeIf { it != BreakIterator.DONE } ?: return null
    val end = breakIterator.following(offset).takeIf { it != BreakIterator.DONE } ?: return null
    val word = text.substring(start, end).trim()
    if (word.isEmpty() || word.all { !it.isLetterOrDigit() }) return null
    return Pair(start, end)
}

private fun buildProgressText(uiState: ReaderUiState): String {
    val pageText = "Page ${uiState.currentPage + 1} of ${uiState.totalPages}"
    val percentText = "${uiState.progress.progressPercent.toInt()}%"
    return "$pageText \u00b7 $percentText"
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
                    Switch(checked = settings.volumeKeyNavigationEnabled, onCheckedChange = onVolumeKeyToggle)
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
                            if (settings.volumeKeyDirectionSwapped) "Vol Up = Next, Vol Down = Prev" else "Vol Up = Prev, Vol Down = Next",
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
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
