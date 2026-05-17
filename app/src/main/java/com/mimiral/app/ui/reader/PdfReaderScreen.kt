package com.mimiral.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.reader.MarginCrop
import com.mimiral.app.data.reader.PdfRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF Reader screen with page navigation, zoom/pan, margin cropping, and bookmarks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    bookId: Int,
    filePath: String,
    onNavigateBack: () -> Unit
) {
    val viewModel: PdfReaderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Initialize with parameters
    LaunchedEffect(bookId, filePath) {
        viewModel.initialize(bookId, filePath)
    }

    // PDF rendering state using wrapper PdfRenderer
    val pdfState = rememberPdfRenderer(filePath)
    LaunchedEffect(pdfState.pageCount) {
        if (pdfState.pageCount > 0) {
            viewModel.setTotalPages(pdfState.pageCount)
        }
    }

    // Auto-detect margins on first load (for scanned PDFs)
    LaunchedEffect(pdfState.renderer, uiState.cropMargins) {
        if (pdfState.renderer != null && uiState.cropMargins == MarginCrop.NONE && uiState.suggestedCrop == null) {
            viewModel.setAutoDetecting(true)
            try {
                val detected = withContext(Dispatchers.Default) {
                    pdfState.renderer?.autoDetectScannedMargins(samplePages = 3) ?: MarginCrop.NONE
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

    // Bookmark list dialog
    if (uiState.showBookmarkList) {
        BookmarkListDialog(
            bookmarks = uiState.bookmarks,
            onNavigateToBookmark = { bookmark ->
                viewModel.goToPage(bookmark.pageNumber)
                viewModel.dismissBookmarkList()
            },
            onDeleteBookmark = { bookmark ->
                viewModel.removeBookmark(bookmark)
            },
            onDismiss = { viewModel.dismissBookmarkList() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        // Main PDF content
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else if (uiState.error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = uiState.error ?: "Unknown error",
                    color = Color.Red,
                    fontSize = 16.sp
                )
            }
        } else if (pdfState.renderer != null) {
            // Scrollable PDF pages with zoom/pan and crop
            PdfPageViewer(
                pdfState = pdfState,
                uiState = uiState,
                onPageChange = { viewModel.onPageChanged(it) },
                onZoomChanged = { viewModel.setZoomLevel(it) },
                onScrollChanged = { x, y -> viewModel.setScrollOffset(x, y) },
                onDoubleTap = { viewModel.toggleFitWidth() },
                onTap = { viewModel.toggleControls() }
            )
        }

        // Top bar with title, back button, bookmark, and crop
        AnimatedVisibility(
            visible = uiState.isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopReaderBar(
                title = uiState.bookTitle,
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
                                pdfState.renderer?.autoDetectScannedMargins(samplePages = 3)
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

        // Bottom controls with page navigation
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
    }
}

/**
 * PDF page viewer with zoom/pan gestures and scrollable pages.
 */
@Composable
private fun PdfPageViewer(
    pdfState: PdfRenderState,
    uiState: PdfReaderUiState,
    onPageChange: (Int) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onScrollChanged: (Float, Float) -> Unit,
    onDoubleTap: () -> Unit,
    onTap: () -> Unit
) {
    val renderer = pdfState.renderer ?: return
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = uiState.currentPage
    )

    // Track page changes from scroll
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex != uiState.currentPage) {
            onPageChange(listState.firstVisibleItemIndex)
        }
    }

    // Zoom/pan state
    var scale by remember { mutableFloatStateOf(uiState.zoomLevel) }
    var offsetX by remember { mutableFloatStateOf(uiState.scrollOffsetX) }
    var offsetY by remember { mutableFloatStateOf(uiState.scrollOffsetY) }

    // Sync external zoom changes
    LaunchedEffect(uiState.zoomLevel) {
        scale = uiState.zoomLevel
        if (uiState.zoomLevel <= 1.05f) {
            offsetX = 0f
            offsetY = 0f
        }
    }

    // Render pages
    val pageBitmaps = remember { mutableStateListOf<Bitmap?>() }
    LaunchedEffect(pdfState.pageCount) {
        pageBitmaps.clear()
        repeat(pdfState.pageCount) { pageBitmaps.add(null) }
        // Render first page
        if (pdfState.pageCount > 0) {
            renderPageWrapper(renderer, 0, uiState.cropMargins, pageBitmaps)
        }
    }

    // Re-render current page on page change or crop change
    LaunchedEffect(uiState.currentPage, uiState.cropMargins) {
        renderPageWrapper(renderer, uiState.currentPage, uiState.cropMargins, pageBitmaps)
        // Pre-render adjacent pages
        val prev = uiState.currentPage - 1
        val next = uiState.currentPage + 1
        if (prev >= 0) renderPageWrapper(renderer, prev, uiState.cropMargins, pageBitmaps)
        if (next < pdfState.pageCount) renderPageWrapper(renderer, next, uiState.cropMargins, pageBitmaps)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onTap = { onTap() }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.5f, 5.0f)
                    scale = newScale
                    onZoomChanged(newScale)

                    if (newScale > 1.05f) {
                        offsetX += pan.x
                        offsetY += pan.y
                        onScrollChanged(offsetX, offsetY)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                        onScrollChanged(0f, 0f)
                    }
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(pageBitmaps) { index, bitmap ->
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Page ${index + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        contentScale = ContentScale.FillWidth
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

private suspend fun renderPageWrapper(
    renderer: PdfRenderer,
    pageIndex: Int,
    cropMargins: MarginCrop,
    pageBitmaps: MutableList<Bitmap?>
) {
    try {
        val bitmap = withContext(Dispatchers.IO) {
            renderer.renderPage(pageIndex = pageIndex, dpi = 160, cropMargins = cropMargins)
        }
        if (pageIndex < pageBitmaps.size) {
            pageBitmaps[pageIndex]?.recycle()
            pageBitmaps[pageIndex] = bitmap
        }
    } catch (_: Exception) {
        // Silently fail for individual page renders
    }
}

/**
 * Top bar with title, back button, bookmark actions, and crop button.
 */
@Composable
private fun TopReaderBar(
    title: String,
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

/**
 * Bottom controls with page navigation buttons, slider, and page display.
 */
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
            // Page slider
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

            // Navigation row
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

                // Page number display
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
                        tint = if (currentPage < totalPages - 1) Color.White else Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            // Progress percentage
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

/**
 * Minimal page indicator shown when controls are hidden.
 */
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

/**
 * Helper composable that manages the PdfRenderer lifecycle.
 */
@Composable
private fun rememberPdfRenderer(filePath: String): PdfRenderState {
    val state = remember { PdfRenderState() }

    DisposableEffect(filePath) {
        val file = File(filePath)
        if (file.exists()) {
            try {
                val renderer = PdfRenderer(file)
                state.renderer = renderer
                state.pageCount = renderer.pageCount
            } catch (e: Exception) {
                state.error = e.message ?: "Failed to open PDF"
            }
        } else {
            state.error = "File not found: $filePath"
        }

        onDispose {
            state.renderer?.close()
        }
    }

    return state
}

private class PdfRenderState {
    var renderer: PdfRenderer? = null
    var pageCount: Int = 0
    var error: String? = null
}
