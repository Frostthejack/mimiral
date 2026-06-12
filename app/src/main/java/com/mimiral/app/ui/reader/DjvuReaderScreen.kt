package com.mimiral.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.local.entity.BookmarkEntity
import com.mimiral.app.data.reader.DjvuRenderer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DJVU Reader screen with page-by-page bitmap rendering, text layer display,
 * navigation, and bookmark support.
 *
 * Features:
 * - Page-by-page DJVU rendering with on-demand bitmap caching
 * - Text layer display toggle (extracted from DJVU OCR layer)
 * - Tap zones for page navigation
 * - Bookmark support
 * - Graceful fallback for image-based DJVU (no text layer)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjvuReaderScreen(
    bookId: Int,
    onNavigateBack: () -> Unit,
    viewModel: DjvuReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Initialize DJVU renderer
    val djvuRenderer = remember(uiState.filePath) {
        if (uiState.filePath.isNotEmpty()) {
            try {
                DjvuRenderer(java.io.File(uiState.filePath))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // Page bitmap cache
    var pageBitmaps by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    var showTextPanel by remember { mutableStateOf(false) }

    // Update total pages when renderer is ready
    LaunchedEffect(djvuRenderer) {
        if (djvuRenderer != null && djvuRenderer.isOpen) {
            viewModel.setTotalPages(djvuRenderer.pageCount)
            // Check if document has text content
            val hasText = djvuRenderer.hasTextContent
            if (hasText && djvuRenderer.pageCount > 0) {
                withContext(Dispatchers.IO) {
                    val pageText = djvuRenderer.extractPageText(0)
                    if (pageText.hasText) {
                        viewModel.setExtractedText(pageText)
                    }
                }
            }
        }
    }

    // Render current page bitmap
    LaunchedEffect(uiState.currentPage, djvuRenderer) {
        if (djvuRenderer != null && djvuRenderer.isOpen) {
            if (!pageBitmaps.containsKey(uiState.currentPage)) {
                viewModel.setPageLoading(true)
                withContext(Dispatchers.IO) {
                    val screenWidth = 1080 // Default; actual width determined at render time
                    val bitmap = djvuRenderer.renderPageByWidth(uiState.currentPage, screenWidth)
                    if (bitmap != null) {
                        pageBitmaps = pageBitmaps + (uiState.currentPage to (bitmap as Bitmap))
                    }
                }
                viewModel.setPageLoading(false)
            }
        }
    }

    // Cleanup resources
    DisposableEffect(Unit) {
        onDispose {
            djvuRenderer?.close()
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
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = uiState.error ?: "Error", color = MaterialTheme.colorScheme.error)
                }
            }
            djvuRenderer != null && djvuRenderer.isOpen -> {
                if (showTextPanel && uiState.hasTextContent) {
                    // Text mode: show extracted text with scroll
                    DjvuTextPanel(
                        extractedText = uiState.extractedText,
                        currentPage = uiState.currentPage,
                        totalPages = uiState.totalPages,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Image mode: show page bitmap
                    DjvuPageView(
                        djvuRenderer = djvuRenderer,
                        currentPage = uiState.currentPage,
                        pageBitmap = pageBitmaps[uiState.currentPage],
                        isPageLoading = uiState.isPageLoading,
                        modifier = Modifier.fillMaxSize(),
                        onTapLeft = { viewModel.previousPage() },
                        onTapRight = { viewModel.nextPage() },
                        onTapCenter = { viewModel.toggleControls() }
                    )
                }
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = uiState.isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            DjvuTopBar(
                title = uiState.bookTitle,
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                hasTextContent = uiState.hasTextContent,
                showTextPanel = showTextPanel,
                onBackClick = onNavigateBack,
                onBookmarkClick = { viewModel.toggleBookmarkAtCurrentPage() },
                onBookmarkListClick = { viewModel.showBookmarkList() },
                onTextToggleClick = {
                    showTextPanel = !showTextPanel
                },
                isBookmarked = viewModel.isPageBookmarked(uiState.currentPage)
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

        // Bookmark list dialog
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

/**
 * Renders a single DJVU page bitmap with tap zones for navigation.
 * - Left third: previous page
 * - Right third: next page
 * - Center: toggle controls
 */
@Composable
private fun DjvuPageView(
    djvuRenderer: DjvuRenderer,
    currentPage: Int,
    pageBitmap: Bitmap?,
    isPageLoading: Boolean,
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
    onTapCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
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
                    .pointerInput(currentPage) {
                        detectTapGestures { offset ->
                            val tapZoneWidth = size.width / 3f
                            when {
                                offset.x < tapZoneWidth -> onTapLeft()
                                offset.x > (size.width * 2f / 3f) -> onTapRight()
                                else -> onTapCenter()
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(
                        image = pageBitmap.asImageBitmap(),
                        dstSize = IntSize(containerWidth.toInt(), displayHeight.toInt())
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isPageLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
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

/**
 * Text panel for displaying extracted DJVU OCR text.
 */
@Composable
private fun DjvuTextPanel(
    extractedText: String,
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Page ${currentPage + 1} of $totalPages",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (extractedText.isNotBlank()) {
            Text(
                text = extractedText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            Text(
                text = "No text layer available on this page.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
            )
        }
    }
}

/**
 * Top bar for DJVU reader with text toggle support.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DjvuTopBar(
    title: String,
    currentPage: Int,
    totalPages: Int,
    hasTextContent: Boolean,
    showTextPanel: Boolean,
    onBackClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onBookmarkListClick: () -> Unit,
    onTextToggleClick: () -> Unit,
    isBookmarked: Boolean
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (totalPages > 0) "Page ${currentPage + 1} of $totalPages" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xCC1A1A1A),
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        actions = {
            if (hasTextContent) {
                IconButton(onClick = onTextToggleClick) {
                    Icon(
                        imageVector = if (showTextPanel) {
                            Icons.Default.Image
                        } else {
                            Icons.Default.TextFields
                        },
                        contentDescription = if (showTextPanel) "Show page image" else "Show text",
                        tint = if (showTextPanel) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            }
            IconButton(onClick = onBookmarkListClick) {
                Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks")
            }
            IconButton(onClick = onBookmarkClick) {
                Icon(
                    imageVector = if (isBookmarked) {
                        Icons.Default.Bookmark
                    } else {
                        Icons.Default.BookmarkBorder
                    },
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark"
                )
            }
        }
    )
}
