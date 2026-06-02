package com.mimiral.app.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.reader.MarginCrop
import com.mimiral.app.data.reader.PdfRenderer
import com.mimiral.app.data.reader.SelectionState
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PDF Reader screen with text selection, selection handles, copy/share, and bookmarks.
 *
 * Features:
 * - Page-by-page PDF rendering with on-demand bitmap caching
 * - Long press to initiate text selection on text-based PDFs
 * - Draggable selection handles for adjusting selection range
 * - Copy and share selected text
 * - Bookmark current page with title and note
 * - Bookmark list with navigation
 * - Graceful fallback for image-based PDFs (no text selection)
 *
 * @param bookId The book ID in the local database
 * @param onNavigateBack Callback to navigate back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    bookId: Int,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PdfReaderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Initialize ViewModel with bookId
    LaunchedEffect(bookId) {
        // ViewModel loads book data in init block via SavedStateHandle
        // but we also set it explicitly in case the SavedStateHandle doesn't have it
        if (uiState.bookId != bookId) {
            // The ViewModel's init block already loads by SavedStateHandle bookId
        }
    }

    // PDF renderer state
    val pdfRenderer = remember(uiState.filePath) {
        if (uiState.filePath.isNotEmpty()) {
            try {
                PdfRenderer(java.io.File(uiState.filePath))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // Local UI state for selection (not in ViewModel to avoid recomposition overhead)
    var selectionState by remember { mutableStateOf(SelectionState()) }
    var showSelectionMenu by remember { mutableStateOf(false) }
    var pageBitmaps by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    var hasTextOnCurrentPage by remember { mutableStateOf(true) }

    // Update total pages when renderer is ready
    LaunchedEffect(pdfRenderer) {
        if (pdfRenderer != null && pdfRenderer.isOpen) {
            viewModel.setTotalPages(pdfRenderer.pageCount)
        }
    }

    // Render current page bitmap
    LaunchedEffect(uiState.currentPage, pdfRenderer, uiState.cropMargins) {
        if (pdfRenderer != null && pdfRenderer.isOpen) {
            if (!pageBitmaps.containsKey(uiState.currentPage)) {
                withContext(Dispatchers.IO) {
                    val size = pdfRenderer.getPageSize(uiState.currentPage) ?: return@withContext
                    val scale = 2f
                    val bitmap = pdfRenderer.renderPageAtSize(
                        uiState.currentPage,
                        (size.first * scale).toInt(),
                        (size.second * scale).toInt(),
                        uiState.cropMargins
                    )
                    if (bitmap != null) {
                        pageBitmaps = pageBitmaps + (uiState.currentPage to bitmap)
                    }
                }
            }
            // Check text content
            hasTextOnCurrentPage = pdfRenderer.hasTextOnPage(uiState.currentPage)
            viewModel.setHasTextContent(hasTextOnCurrentPage)
        }
    }

    // Clear selection on page change
    LaunchedEffect(uiState.currentPage) {
        selectionState = SelectionState()
        showSelectionMenu = false
    }

    // Auto-detect margins on first load (for scanned PDFs)
    LaunchedEffect(pdfRenderer, uiState.cropMargins) {
        if (pdfRenderer != null && pdfRenderer.isOpen && uiState.cropMargins == MarginCrop.NONE && uiState.suggestedCrop == null) {
            viewModel.setAutoDetecting(true)
            try {
                val detected = withContext(Dispatchers.Default) {
                    pdfRenderer.autoDetectScannedMargins(samplePages = 3)
                }
                if (detected.hasCrop()) {
                    viewModel.setSuggestedCrop(detected)
                }
            } catch (_: Exception) {
                // Auto-detect failure is non-fatal
            } finally {
                viewModel.setAutoDetecting(false)
            }
        }
    }

    // Re-render current page when crop margins change
    LaunchedEffect(uiState.cropMargins) {
        if (pdfRenderer != null && pdfRenderer.isOpen && uiState.cropMargins.hasCrop()) {
            withContext(Dispatchers.IO) {
                val size = pdfRenderer.getPageSize(uiState.currentPage) ?: return@withContext
                val scale = 2f
                val bitmap = pdfRenderer.renderPageAtSize(
                    uiState.currentPage,
                    (size.first * scale).toInt(),
                    (size.second * scale).toInt(),
                    uiState.cropMargins
                )
                if (bitmap != null) {
                    pageBitmaps = pageBitmaps + (uiState.currentPage to bitmap)
                }
            }
        }
    }

    // Cleanup resources
    DisposableEffect(Unit) {
        onDispose {
            pdfRenderer?.close()
            pageBitmaps.values.forEach { it.recycle() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = uiState.error ?: "Error", color = Color.Red)
                }
            }
            pdfRenderer != null && pdfRenderer.isOpen -> {
                // PDF page viewer with text selection
                PdfPageView(
                    pdfRenderer = pdfRenderer,
                    currentPage = uiState.currentPage,
                    pageBitmap = pageBitmaps[uiState.currentPage],
                    hasTextContent = hasTextOnCurrentPage,
                    selectionState = selectionState,
                    onSelectionChange = { selectionState = it },
                    onShowSelectionMenu = { showSelectionMenu = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Selection toolbar overlay
        if (showSelectionMenu && selectionState.hasSelection) {
            SelectionToolbar(
                modifier = Modifier.align(Alignment.TopCenter),
                selectedText = selectionState.selectedText,
                onCopy = {
                    copyToClipboard(context, selectionState.selectedText)
                    showSelectionMenu = false
                    selectionState = SelectionState()
                },
                onShare = {
                    shareText(context, selectionState.selectedText)
                    showSelectionMenu = false
                    selectionState = SelectionState()
                },
                onBookmark = {
                    viewModel.addBookmark(uiState.currentPage)
                    showSelectionMenu = false
                    selectionState = SelectionState()
                },
                onDismiss = {
                    showSelectionMenu = false
                    selectionState = SelectionState()
                }
            )
        }

        // Top bar
        AnimatedVisibility(
            visible = uiState.isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopReaderBar(
                title = uiState.bookTitle,
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                onBackClick = onNavigateBack,
                onBookmarkClick = { viewModel.toggleBookmarkAtCurrentPage() },
                onBookmarkListClick = { viewModel.showBookmarkList() },
                onCropClick = { viewModel.toggleCropSettings() },
                isBookmarked = viewModel.isPageBookmarked(uiState.currentPage)
            )
        }

        // Crop settings panel
        AnimatedVisibility(
            visible = uiState.showCropSettings,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            CropSettingsPanel(
                cropMargins = uiState.cropMargins,
                autoDetecting = uiState.autoDetecting,
                suggestedCrop = uiState.suggestedCrop,
                onCropChanged = { viewModel.setCropMargins(it) },
                onUniformCropChanged = { viewModel.setUniformCrop(it) },
                onResetCrop = { viewModel.resetCrop() },
                onAutoDetect = {
                    scope.launch {
                        viewModel.setAutoDetecting(true)
                        try {
                            val detected = withContext(Dispatchers.Default) {
                                pdfRenderer?.autoDetectScannedMargins(samplePages = 3)
                                    ?: MarginCrop.NONE
                            }
                            if (detected.hasCrop()) {
                                viewModel.setCropMargins(detected)
                            }
                        } catch (_: Exception) {
                            // Non-fatal
                        } finally {
                            viewModel.setAutoDetecting(false)
                        }
                    }
                },
                onApplySuggested = { viewModel.applySuggestedCrop() },
                onDismiss = { viewModel.hideCropSettings() }
            )
        }

        // Suggested crop banner
        if (uiState.suggestedCrop != null && !uiState.showCropSettings) {
            SuggestedCropBanner(
                suggestedCrop = uiState.suggestedCrop!!,
                onApply = { viewModel.applySuggestedCrop() },
                onDismiss = { viewModel.setSuggestedCrop(MarginCrop.NONE) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
            )
        }

        // Bottom controls
        AnimatedVisibility(
            visible = uiState.isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomReaderControls(
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                progressPercent = uiState.progressPercent,
                onPreviousPage = { viewModel.previousPage() },
                onNextPage = { viewModel.nextPage() },
                onPageSelected = { viewModel.goToPage(it) }
            )
        }

        // Minimal page indicator when controls are hidden
        if (!uiState.isControlsVisible && uiState.totalPages > 0) {
            PageIndicator(
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }

        // Bookmark add/edit dialog
        if (uiState.showBookmarkList) {
            BookmarkListDialog(
                bookmarks = uiState.bookmarks,
                onNavigateToBookmark = { bookmark: BookmarkEntity ->
                    viewModel.goToPage(bookmark.pageNumber)
                    viewModel.dismissBookmarkList()
                },
                onDeleteBookmark = { bookmark: BookmarkEntity ->
                    viewModel.removeBookmark(bookmark)
                },
                onDismiss = { viewModel.dismissBookmarkList() }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Page viewer
// ---------------------------------------------------------------------------

/**
 * Renders a single PDF page with text selection support.
 * Long press initiates selection on text-based PDFs.
 * Selection handles appear for adjusting the range.
 * Image-based PDFs skip text selection (graceful fallback).
 */
@Composable
private fun PdfPageView(
    pdfRenderer: PdfRenderer,
    currentPage: Int,
    pageBitmap: Bitmap?,
    hasTextContent: Boolean,
    selectionState: SelectionState,
    onSelectionChange: (SelectionState) -> Unit,
    onShowSelectionMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.background(Color(0xFF1A1A1A))
    ) {
        if (pageBitmap != null) {
            val containerWidth = constraints.maxWidth.toFloat()
            val bitmapWidth = pageBitmap.width.toFloat()
            val bitmapHeight = pageBitmap.height.toFloat()
            val scale = containerWidth / bitmapWidth
            val displayHeight = bitmapHeight * scale

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { displayHeight.toDp() })
            ) {
                // Page bitmap canvas with selection drawing
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (hasTextContent) {
                                Modifier.pointerInput(currentPage) {
                                    detectTapGestures(
                                        onLongPress = { offset ->
                                            onSelectionChange(
                                                SelectionState(
                                                    isActive = true,
                                                    pageIndex = currentPage,
                                                    selectedText = "",
                                                    startHandlePosition = PointF(offset.x, offset.y),
                                                    endHandlePosition = PointF(offset.x, offset.y)
                                                )
                                            )
                                            onShowSelectionMenu()
                                        }
                                    )
                                }
                            } else {
                                Modifier // Image-based PDF: no text selection
                            }
                        )
                ) {
                    drawImage(
                        image = pageBitmap.asImageBitmap(),
                        dstSize = IntSize(containerWidth.toInt(), displayHeight.toInt())
                    )
                    // Draw selection highlight overlays
                    if (selectionState.hasSelection && selectionState.pageIndex == currentPage) {
                        drawSelectionHighlights(selectionState, scale)
                    }
                }

                // Selection handles (draggable)
                if (selectionState.hasSelection && selectionState.pageIndex == currentPage) {
                    selectionState.startHandlePosition?.let { pos ->
                        SelectionHandle(
                            position = Offset(pos.x, pos.y),
                            isStart = true,
                            scale = scale,
                            onDrag = { delta ->
                                val newPos = PointF(pos.x + delta.x, pos.y + delta.y)
                                onSelectionChange(selectionState.copy(startHandlePosition = newPos))
                            }
                        )
                    }
                    selectionState.endHandlePosition?.let { pos ->
                        SelectionHandle(
                            position = Offset(pos.x, pos.y),
                            isStart = false,
                            scale = scale,
                            onDrag = { delta ->
                                val newPos = PointF(pos.x + delta.x, pos.y + delta.y)
                                onSelectionChange(selectionState.copy(endHandlePosition = newPos))
                            }
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading page...",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Selection rendering
// ---------------------------------------------------------------------------

/** Draw semi-transparent blue highlight rectangles for the selection. */
private fun DrawScope.drawSelectionHighlights(state: SelectionState, scale: Float) {
    val highlightColor = Color(0x442196F3)
    for (bounds in state.bounds) {
        drawRect(
            color = highlightColor,
            topLeft = Offset(bounds.left * scale, bounds.top * scale),
            size = Size(bounds.width() * scale, bounds.height() * scale)
        )
    }
}

// ---------------------------------------------------------------------------
// Selection handle
// ---------------------------------------------------------------------------

/** A draggable circular handle for adjusting selection boundaries. */
@Composable
private fun SelectionHandle(
    position: Offset,
    isStart: Boolean,
    scale: Float,
    onDrag: (Offset) -> Unit
) {
    val handleSize = 20.dp
    val handleColor = Color(0xFF2196F3)
    val density = LocalDensity.current
    val handleOffset = with(density) { handleSize.toPx() / 2 }.roundToInt()

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (position.x * scale).roundToInt() - handleOffset,
                    (position.y * scale).roundToInt() - handleOffset
                )
            }
            .size(handleSize)
            .clip(CircleShape)
            .background(handleColor)
            .pointerInput(isStart) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
    )
}

// ---------------------------------------------------------------------------
// Selection toolbar
// ---------------------------------------------------------------------------

/** Floating toolbar with copy, share, bookmark, and dismiss actions. */
@Composable
private fun SelectionToolbar(
    modifier: Modifier = Modifier,
    selectedText: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onBookmark: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            IconButton(onClick = onBookmark) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "Bookmark selection")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun TopReaderBar(
    title: String,
    currentPage: Int,
    totalPages: Int,
    onBackClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onBookmarkListClick: () -> Unit,
    onCropClick: () -> Unit,
    isBookmarked: Boolean
) {
    Surface(
        color = Color.Black.copy(alpha = 0.75f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            // Page indicator
            Text(
                text = if (totalPages > 0) "${currentPage + 1} / $totalPages" else "—",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            IconButton(onClick = onCropClick) {
                Icon(
                    imageVector = Icons.Default.Crop,
                    contentDescription = "Crop margins",
                    tint = Color.White
                )
            }

            IconButton(onClick = onBookmarkListClick) {
                Icon(
                    imageVector = Icons.Default.Bookmarks,
                    contentDescription = "Bookmark list",
                    tint = Color.White
                )
            }

            IconButton(onClick = onBookmarkClick) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    tint = if (isBookmarked) Color(0xFFFFD700) else Color.White
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom controls
// ---------------------------------------------------------------------------

@Composable
private fun BottomReaderControls(
    currentPage: Int,
    totalPages: Int,
    progressPercent: Float,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onPageSelected: (Int) -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.75f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (totalPages > 1) {
                Slider(
                    value = currentPage.toFloat(),
                    onValueChange = { onPageSelected(it.toInt()) },
                    valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onPreviousPage,
                    enabled = currentPage > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous page",
                        tint = if (currentPage > 0) Color.White else Color.White.copy(alpha = 0.3f)
                    )
                }

                Text(
                    text = if (totalPages > 0) "${currentPage + 1} / $totalPages" else "—",
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onNextPage,
                    enabled = currentPage < totalPages - 1
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next page",
                        tint = if (currentPage < totalPages - 1) {
                            Color.White
                        } else {
                            Color.White.copy(
                                alpha = 0.3f
                            )
                        }
                    )
                }
            }

            if (totalPages > 0) {
                Text(
                    text = "${progressPercent.toInt()}%",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page indicator
// ---------------------------------------------------------------------------

@Composable
private fun PageIndicator(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Text(
            text = if (totalPages > 0) "${currentPage + 1} / $totalPages" else "—",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("PDF Selection", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share text"))
}

// ---------------------------------------------------------------------------
// Crop settings panel
// ---------------------------------------------------------------------------

/**
 * Crop settings panel with a uniform margin crop slider and auto-detect button.
 * Slider controls uniform crop (same value for all sides) from 0-30%.
 */
@Composable
private fun CropSettingsPanel(
    cropMargins: MarginCrop,
    autoDetecting: Boolean,
    suggestedCrop: MarginCrop?,
    onCropChanged: (MarginCrop) -> Unit,
    onUniformCropChanged: (Int) -> Unit,
    onResetCrop: () -> Unit,
    onAutoDetect: () -> Unit,
    onApplySuggested: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.85f),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .padding(vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Margin Crop",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Divider(color = Color.White.copy(alpha = 0.2f))

            // Uniform crop slider
            val uniformCrop = cropMargins.left
            Text(
                text = "Crop: $uniformCrop%",
                color = Color.White,
                fontSize = 14.sp
            )
            Slider(
                value = uniformCrop.toFloat(),
                onValueChange = { onUniformCropChanged(it.toInt()) },
                valueRange = 0f..30f,
                steps = 29,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )

            // Individual side controls
            Text(
                text = "Individual sides",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )

            CropSideSlider("Left", cropMargins.left) { newVal ->
                onCropChanged(cropMargins.copy(left = newVal))
            }
            CropSideSlider("Top", cropMargins.top) { newVal ->
                onCropChanged(cropMargins.copy(top = newVal))
            }
            CropSideSlider("Right", cropMargins.right) { newVal ->
                onCropChanged(cropMargins.copy(right = newVal))
            }
            CropSideSlider("Bottom", cropMargins.bottom) { newVal ->
                onCropChanged(cropMargins.copy(bottom = newVal))
            }

            Divider(color = Color.White.copy(alpha = 0.2f))

            // Auto-detect button
            Button(
                onClick = onAutoDetect,
                enabled = !autoDetecting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                if (autoDetecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoMode,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Auto-detect margins")
            }

            // Suggested crop action
            if (suggestedCrop != null) {
                Button(
                    onClick = onApplySuggested,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply suggested: ${suggestedCrop.left}%")
                }
            }

            // Reset button
            OutlinedButton(
                onClick = onResetCrop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset crop")
            }

            Spacer(modifier = Modifier.weight(1f))

            // Info text
            Text(
                text = "Crop removes white borders from PDF pages for better reading on small screens. 0% = no cropping.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun CropSideSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
            Text(
                text = "$value%",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..50f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

/**
 * Banner shown when auto-detected crop is available but not yet applied.
 */
@Composable
private fun SuggestedCropBanner(
    suggestedCrop: MarginCrop,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1B5E20).copy(alpha = 0.9f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCut,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Margins detected (${suggestedCrop.left}% crop). Apply?",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onApply) {
                Text("Apply", color = Color(0xFF81C784), fontSize = 13.sp)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
