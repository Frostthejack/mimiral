package com.mimiral.app.ui.discover

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.remote.kavita.KavitaBookmarkDto
import com.mimiral.app.data.remote.kavita.KavitaBookmarkGroup
import com.mimiral.app.data.remote.kavita.KavitaChapterBookmarkGroup
import com.mimiral.app.data.remote.kavita.KavitaVolumeBookmarkGroup

/**
 * Screen for browsing all Kavita bookmarks grouped by series > volume > chapter.
 * Supports search/filter, export, expand/collapse navigation, and bookmark removal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KavitaBookmarksScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (bookId: Int, format: String) -> Unit = { _, _ -> },
    viewModel: KavitaBookmarksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showSearchBar by remember { mutableStateOf(false) }

    // Show export snackbar
    LaunchedEffect(uiState.exportSuccess) {
        if (uiState.exportSuccess && uiState.exportPath != null) {
            snackbarHostState.showSnackbar("Exported to ${uiState.exportPath}")
            viewModel.resetExportState()
        }
    }

    // Show error snackbar
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            snackbarHostState.showSnackbar(uiState.errorMessage!!)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (showSearchBar) {
                        IconButton(onClick = {
                            showSearchBar = false
                            viewModel.setFilterQuery("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { viewModel.expandAll() }) {
                            Icon(Icons.Default.ExpandMore, contentDescription = "Expand all")
                        }
                        IconButton(onClick = { viewModel.collapseAll() }) {
                            Icon(Icons.Default.ExpandLess, contentDescription = "Collapse all")
                        }
                        IconButton(
                            onClick = {
                                val dir = java.io.File(
                                    android.os.Environment.getExternalStorageDirectory(),
                                    "Download"
                                )
                                viewModel.exportBookmarks(dir)
                            },
                            enabled = !uiState.isExporting
                        ) {
                            if (uiState.isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Download, contentDescription = "Export")
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            if (showSearchBar) {
                OutlinedTextField(
                    value = uiState.filterQuery,
                    onValueChange = { viewModel.setFilterQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Filter by series, volume, or chapter...") },
                    leadingIcon = {
                        Icon(Icons.Default.FilterList, contentDescription = null)
                    },
                    singleLine = true
                )
            }

            // Content
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.groupedBookmarks.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.filterQuery.isNotBlank()) {
                                "No bookmarks match your filter"
                            } else {
                                "No bookmarks yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (uiState.filterQuery.isBlank()) {
                            Text(
                                text = "Bookmarks you create while reading will appear here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.7f
                                )
                            )
                        }
                    }
                }
            } else {
                // Bookmark count
                Text(
                    text = "${uiState.bookmarks.size} bookmarks in ${uiState.groupedBookmarks.size} series",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Grouped bookmark list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.groupedBookmarks,
                        key = { it.seriesId }
                    ) { seriesGroup ->
                        SeriesBookmarkGroup(
                            group = seriesGroup,
                            isExpanded = seriesGroup.seriesId in uiState.expandedSeries,
                            expandedVolumes = uiState.expandedVolume,
                            onToggleSeries = { viewModel.toggleSeriesExpanded(seriesGroup.seriesId) },
                            onToggleVolume = { volumeId ->
                                viewModel.toggleVolumeExpanded(seriesGroup.seriesId, volumeId)
                            },
                            onRemoveBookmark = { viewModel.removeBookmark(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesBookmarkGroup(
    group: KavitaBookmarkGroup,
    isExpanded: Boolean,
    expandedVolumes: Set<String>,
    onToggleSeries: () -> Unit,
    onToggleVolume: (Int) -> Unit,
    onRemoveBookmark: (KavitaBookmarkDto) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            // Series header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleSeries)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.seriesName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${group.volumes.sumOf { v -> v.chapters.sumOf { c -> c.bookmarks.size } }} bookmarks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.ExpandLess
                    } else {
                        Icons.Default.ExpandMore
                    },
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded content
            if (isExpanded) {
                HorizontalDivider()
                group.volumes.forEach { volumeGroup ->
                    VolumeBookmarkGroup(
                        group = volumeGroup,
                        seriesId = group.seriesId,
                        isExpanded = "${group.seriesId}_${volumeGroup.volumeId}" in expandedVolumes,
                        onToggleVolume = { onToggleVolume(volumeGroup.volumeId) },
                        onRemoveBookmark = onRemoveBookmark
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeBookmarkGroup(
    group: KavitaVolumeBookmarkGroup,
    seriesId: Int,
    isExpanded: Boolean,
    onToggleVolume: () -> Unit,
    onRemoveBookmark: (KavitaBookmarkDto) -> Unit
) {
    Column(modifier = Modifier.animateContentSize()) {
        // Volume header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleVolume)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.volumeName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.chapters.sumOf { it.bookmarks.size }} bookmarks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (isExpanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Expanded chapters
        if (isExpanded) {
            group.chapters.forEach { chapterGroup ->
                ChapterBookmarkGroup(
                    group = chapterGroup,
                    onRemoveBookmark = onRemoveBookmark
                )
            }
        }
    }
}

@Composable
private fun ChapterBookmarkGroup(
    group: KavitaChapterBookmarkGroup,
    onRemoveBookmark: (KavitaBookmarkDto) -> Unit
) {
    Column(modifier = Modifier.padding(start = 32.dp)) {
        // Chapter header
        Text(
            text = group.chapterName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        // Individual bookmarks
        group.bookmarks.forEach { bookmark ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Page ${bookmark.page + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                if (bookmark.lastModified != null) {
                    Text(
                        text = bookmark.lastModified,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                IconButton(
                    onClick = { onRemoveBookmark(bookmark) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove bookmark",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
