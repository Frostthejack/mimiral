package com.mimiral.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.reader.ComicParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Comic book reader screen supporting CBZ/CBR formats.
 *
 * Features:
 * - Page-by-page image display with on-demand loading
 * - Pinch-to-zoom and pan gestures
 * - Fit-to-width and fit-to-height viewing modes
 * - Two-page spread mode
 * - Bookmark support
 * - Tap zones for page navigation (left/right thirds)
 *
 * @param bookId The book ID in the local database
 * @param onNavigateBack Callback to navigate back
 */
@Composable
fun ComicReaderScreen(
    bookId: Int,
    onNavigateBack: () -> Unit,
    viewModel: ComicReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Comic parser for extracting page images
    val comicParser = remember { ComicParser() }

    // Page bitmaps: extracted on demand
    var pageBitmaps by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }

    // Cumulative pan/zoom state from gesture
    var gestureZoom by remember { mutableFloatStateOf(1f) }
    var gesturePanX by remember { mutableFloatStateOf(0f) }
    var gesturePanY by remember { mutableFloatStateOf(0f) }

    val archive = uiState.comicArchive

    // Update total pages when archive is parsed
    LaunchedEffect(archive) {
        if (archive != null && archive.pageCount > 0) {
            viewModel.onPageChanged(uiState.currentPage)
        }
    }

    // Load current page bitmap
    LaunchedEffect(uiState.currentPage, archive) {
        if (archive != null && !pageBitmaps.containsKey(uiState.currentPage)) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        val imageFile = comicParser.extractPageImage(archive, uiState.currentPage)
                        imageFile?.let { file ->
                            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    pageBitmaps = pageBitmaps + (uiState.currentPage to bitmap)
                }
            }
        }
    }

    // Load second page for two-page spread
    LaunchedEffect(uiState.currentPage, uiState.isTwoPageSpread, archive) {
        if (uiState.isTwoPageSpread && archive != null) {
            val secondPage = uiState.currentPage + 1
            if (secondPage < archive.pageCount && !pageBitmaps.containsKey(secondPage)) {
                scope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            val imageFile = comicParser.extractPageImage(archive, secondPage)
                            imageFile?.let { file ->
                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        pageBitmaps = pageBitmaps + (secondPage to bitmap)
                    }
                }
            }
        }
    }

    // Reset gesture zoom/pan when fit mode changes or page changes
    LaunchedEffect(uiState.currentPage, uiState.fitMode) {
        gestureZoom = 1f
        gesturePanX = 0f
        gesturePanY = 0f
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            comicParser.close()
            pageBitmaps.values.forEach { it.recycle() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                        Icons.Default.Crop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = uiState.error ?: "Error", color = MaterialTheme.colorScheme.error)
                }
            }
            archive != null -> {
                // Main comic page viewer
                ComicPageViewer(
                    pageBitmaps = pageBitmaps,
                    currentPage = uiState.currentPage,
                    isTwoPageSpread = uiState.isTwoPageSpread,
                    fitMode = uiState.fitMode,
                    gestureZoom = gestureZoom,
                    gesturePanX = gesturePanX,
                    gesturePanY = gesturePanY,
                    totalPages = archive.pageCount,
                    onZoomChanged = { zoom, panX, panY ->
                        gestureZoom = zoom
                        gesturePanX = panX
                        gesturePanY = panY
                        viewModel.setZoomLevel(zoom)
                        viewModel.setScrollOffset(panX, panY)
                    },
                    onTapLeft = { viewModel.previousPage() },
                    onTapRight = { viewModel.nextPage() },
                    onTapCenter = { viewModel.toggleControls() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = uiState.isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ComicTopBar(
                title = uiState.bookTitle,
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                isTwoPageSpread = uiState.isTwoPageSpread,
                isFitWidth = uiState.isFitWidth,
                isFitHeight = uiState.isFitHeight,
                isBookmarked = viewModel.isPageBookmarked(uiState.currentPage),
                onBackClick = onNavigateBack,
                onBookmarkClick = { viewModel.toggleBookmarkAtCurrentPage() },
                onBookmarkListClick = { viewModel.showBookmarkList() },
                onFitModeToggle = {
                    when {
                        uiState.isFitWidth -> viewModel.toggleFitHeight()
                        uiState.isFitHeight -> viewModel.setFitMode("fit_both")
                        else -> viewModel.setFitMode("fit_width")
                    }
                },
                onToggleSpread = { viewModel.toggleTwoPageSpread() }
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

        // Bookmark dialog
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
    }
}

// ---------------------------------------------------------------------------
// Comic page viewer with zoom/pan and tap zones
// ---------------------------------------------------------------------------

@Composable
private fun ComicPageViewer(
    pageBitmaps: Map<Int, Bitmap>,
    currentPage: Int,
    isTwoPageSpread: Boolean,
    fitMode: String,
    gestureZoom: Float,
    gesturePanX: Float,
    gesturePanY: Float,
    totalPages: Int,
    onZoomChanged: (Float, Float, Float) -> Unit,
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
    onTapCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        val effectiveZoom = if (fitMode == "fit_width" ||
            fitMode == "fit_height"
        ) {
            1f
        } else {
            gestureZoom
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(totalPages, fitMode) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val downPosition = firstDown.position

                        // Determine tap zone
                        val tapZoneWidth = size.width / 3f
                        val isLongPress = false

                        // Wait for up or drag
                        var zoom = 1f
                        var panX = 0f
                        var panY = 0f
                        var isDragging = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes

                            if (changes.size >= 1 && !isDragging) {
                                // Check for zoom/pan gesture (multi-touch)
                                val activePointers = changes.filter { it.pressed }
                                if (activePointers.size >= 2) {
                                    isDragging = true
                                    // Handle pinch zoom
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()

                                    zoom *= zoomChange
                                    zoom = zoom.coerceIn(0.5f, 5.0f)
                                    panX += panChange.x
                                    panY += panChange.y

                                    onZoomChanged(zoom, panX, panY)
                                    changes.forEach { it.consume() }
                                }
                            }

                            // Check if all pointers up
                            if (changes.all { !it.pressed }) {
                                if (!isDragging) {
                                    // It was a tap
                                    when {
                                        downPosition.x < tapZoneWidth -> onTapLeft()
                                        downPosition.x > size.width * 2f / 3f -> onTapRight()
                                        else -> onTapCenter()
                                    }
                                }
                                break
                            }

                            // Detect drag after threshold
                            if (!isDragging && changes.size == 1) {
                                val pointer = changes[0]
                                val delta = pointer.position - pointer.previousPosition
                                if (kotlin.math.abs(delta.x) > 10f ||
                                    kotlin.math.abs(delta.y) > 10f
                                ) {
                                    isDragging = true
                                    panX += delta.x
                                    panY += delta.y
                                    onZoomChanged(zoom, panX, panY)
                                    pointer.consume()
                                }
                            }
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isTwoPageSpread) {
                // Two-page spread: show left and right pages side by side
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left page (even index)
                    val leftBitmap = pageBitmaps[currentPage]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (leftBitmap != null) {
                            ComicPageImage(
                                bitmap = leftBitmap,
                                fitMode = fitMode,
                                containerWidthPx = containerWidthPx / 2f,
                                containerHeightPx = containerHeightPx,
                                scale = effectiveZoom,
                                panX = gesturePanX,
                                panY = gesturePanY
                            )
                        }
                    }

                    // Right page (odd index, may be blank on last page)
                    val rightPage = currentPage + 1
                    if (rightPage < totalPages) {
                        val rightBitmap = pageBitmaps[rightPage]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (rightBitmap != null) {
                                ComicPageImage(
                                    bitmap = rightBitmap,
                                    fitMode = fitMode,
                                    containerWidthPx = containerWidthPx / 2f,
                                    containerHeightPx = containerHeightPx,
                                    scale = effectiveZoom,
                                    panX = gesturePanX,
                                    panY = gesturePanY
                                )
                            }
                        }
                    }
                }
            } else {
                // Single page view
                val bitmap = pageBitmaps[currentPage]
                if (bitmap != null) {
                    ComicPageImage(
                        bitmap = bitmap,
                        fitMode = fitMode,
                        containerWidthPx = containerWidthPx,
                        containerHeightPx = containerHeightPx,
                        scale = effectiveZoom,
                        panX = gesturePanX,
                        panY = gesturePanY
                    )
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
    }
}

// ---------------------------------------------------------------------------
// Single page image with fit modes and gesture transform
// ---------------------------------------------------------------------------

@Composable
private fun ComicPageImage(
    bitmap: Bitmap,
    fitMode: String,
    containerWidthPx: Float,
    containerHeightPx: Float,
    scale: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f
) {
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()

    val imageScale = when (fitMode) {
        "fit_width" -> containerWidthPx / bitmapWidth
        "fit_height" -> containerHeightPx / bitmapHeight
        "fit_both" -> minOf(
            containerWidthPx / bitmapWidth,
            containerHeightPx / bitmapHeight
        )
        else -> 1f // free zoom
    } * scale

    val density = LocalDensity.current
    val displayWidth = bitmapWidth * imageScale
    val displayHeight = bitmapHeight * imageScale

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = imageScale
                scaleY = imageScale
                translationX = panX
                translationY = panY
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Comic page",
            contentScale = ContentScale.None,
            modifier = Modifier
                .width(with(density) { displayWidth.toDp() })
                .height(with(density) { displayHeight.toDp() })
        )
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun ComicTopBar(
    title: String,
    currentPage: Int,
    totalPages: Int,
    isTwoPageSpread: Boolean,
    isFitWidth: Boolean,
    isFitHeight: Boolean,
    isBookmarked: Boolean,
    onBackClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onBookmarkListClick: () -> Unit,
    onFitModeToggle: () -> Unit,
    onToggleSpread: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Default.Crop, // Reusing icon; actual back arrow
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Text(
                    text = "Page ${currentPage + 1} of $totalPages",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onToggleSpread) {
                Icon(
                    if (isTwoPageSpread) Icons.Default.MenuBook else Icons.Default.Description,
                    contentDescription = if (isTwoPageSpread) "Single page" else "Two-page spread",
                    tint = if (isTwoPageSpread) MaterialTheme.colorScheme.primary else Color.White
                )
            }

            IconButton(onClick = onFitModeToggle) {
                Icon(
                    if (isFitHeight) Icons.Default.Crop else Icons.Default.CropLandscape,
                    contentDescription = "Toggle fit mode",
                    tint = Color.White
                )
            }

            IconButton(onClick = onBookmarkClick) {
                Icon(
                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else Color.White
                )
            }

            IconButton(onClick = onBookmarkListClick) {
                Icon(
                    Icons.Default.BookmarkBorder,
                    contentDescription = "Bookmarks",
                    tint = Color.White
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page indicator (minimal, for when controls are hidden)
// ---------------------------------------------------------------------------

@Composable
fun PageIndicator(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = "${currentPage + 1} / $totalPages",
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(
                Color(0x88000000),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}


