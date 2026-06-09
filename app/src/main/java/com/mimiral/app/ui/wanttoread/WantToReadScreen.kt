package com.mimiral.app.ui.wanttoread

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mimiral.app.data.remote.kavita.KavitaSortDirection
import com.mimiral.app.data.remote.kavita.KavitaWantToReadSeries
import com.mimiral.app.data.remote.kavita.KavitaWantToReadSort

// ── Main Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WantToReadScreen(
    viewModel: WantToReadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToSeries: (Int) -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortMenu by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Want To Read",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // View toggle
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) {
                                Icons.Default.ViewList
                            } else {
                                Icons.Default.ViewModule
                            },
                            contentDescription = if (isGridView) "List view" else "Grid view"
                        )
                    }
                    // Sort
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        KavitaWantToReadSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                onClick = {
                                    val isCurrent = uiState.sortBy == sort
                                    val newDir = if (isCurrent) {
                                        if (uiState.sortDirection ==
                                            KavitaSortDirection.Ascending
                                        ) {
                                            KavitaSortDirection.Descending
                                        } else {
                                            KavitaSortDirection.Ascending
                                        }
                                    } else {
                                        KavitaSortDirection.Ascending
                                    }
                                    viewModel.onSortChanged(sort, newDir)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                    // Cleanup
                    IconButton(onClick = { viewModel.cleanup() }) {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = "Cleanup fully-read"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onClear = { viewModel.onSearchQueryChanged("") }
            )

            // Active sort indicator
            if (uiState.sortBy != KavitaWantToReadSort.SortName ||
                uiState.sortDirection != KavitaSortDirection.Ascending
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = true,
                        onClick = {
                            viewModel.onSortChanged(
                                KavitaWantToReadSort.SortName,
                                KavitaSortDirection.Ascending
                            )
                        },
                        label = {
                            val arrow = if (
                                uiState.sortDirection == KavitaSortDirection.Ascending
                            ) {
                                "↑"
                            } else {
                                "↓"
                            }
                            Text("${uiState.sortBy.label} $arrow")
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear sort",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Content
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading && uiState.series.isEmpty() -> {
                        LoadingState()
                    }
                    uiState.errorMessage != null -> {
                        ErrorState(
                            message = uiState.errorMessage!!,
                            onDismiss = { viewModel.clearError() },
                            onRetry = { viewModel.loadFirstPage() }
                        )
                    }
                    uiState.series.isEmpty() -> {
                        EmptyState()
                    }
                    else -> {
                        if (isGridView) {
                            SeriesGrid(
                                series = uiState.series,
                                onSeriesClick = { onNavigateToSeries(it.id) },
                                onToggleWantToRead = { viewModel.toggleWantToRead(it.id) },
                                isInWantToRead = { viewModel.isInWantToRead(it) }
                            )
                        } else {
                            SeriesList(
                                series = uiState.series,
                                onSeriesClick = { onNavigateToSeries(it.id) },
                                onToggleWantToRead = { viewModel.toggleWantToRead(it.id) },
                                isInWantToRead = { viewModel.isInWantToRead(it) }
                            )
                        }
                    }
                }
            }

            // Pagination bar
            if (uiState.totalCount > 0) {
                PaginationBar(
                    currentPage = uiState.currentPage,
                    totalCount = uiState.totalCount,
                    pageSize = uiState.pageSize,
                    onPreviousPage = { viewModel.loadPreviousPage() },
                    onNextPage = { viewModel.loadNextPage() }
                )
            }
        }
    }
}

// ── Search Bar ──

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search Want To Read...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Search")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

// ── Series Grid ──

@Composable
private fun SeriesGrid(
    series: List<KavitaWantToReadSeries>,
    onSeriesClick: (KavitaWantToReadSeries) -> Unit,
    onToggleWantToRead: (KavitaWantToReadSeries) -> Unit,
    isInWantToRead: (Int) -> Boolean
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(series, key = { "wtr_${it.id}" }) { item ->
            SeriesGridCard(
                series = item,
                onClick = { onSeriesClick(item) },
                onToggleWantToRead = { onToggleWantToRead(item) },
                isInList = isInWantToRead(item.id)
            )
        }
    }
}

@Composable
private fun SeriesGridCard(
    series: KavitaWantToReadSeries,
    onClick: () -> Unit,
    onToggleWantToRead: () -> Unit,
    isInList: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Cover image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (series.coverImage != null) {
                    AsyncImage(
                        model = series.coverImage,
                        contentDescription = series.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // WTR toggle chip
                IconButton(
                    onClick = onToggleWantToRead,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isInList) {
                            Icons.Default.BookmarkRemove
                        } else {
                            Icons.Default.BookmarkAdd
                        },
                        contentDescription = if (isInList) "Remove from WTR" else "Add to WTR",
                        tint = if (isInList) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            // Title + progress
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (series.pages > 0) {
                    LinearProgressIndicator(
                        progress = { series.progressFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ── Series List ──

@Composable
private fun SeriesList(
    series: List<KavitaWantToReadSeries>,
    onSeriesClick: (KavitaWantToReadSeries) -> Unit,
    onToggleWantToRead: (KavitaWantToReadSeries) -> Unit,
    isInWantToRead: (Int) -> Boolean
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(series, key = { "wtr_${it.id}" }) { item ->
            SeriesListRow(
                series = item,
                onClick = { onSeriesClick(item) },
                onToggleWantToRead = { onToggleWantToRead(item) },
                isInList = isInWantToRead(item.id)
            )
        }
    }
}

@Composable
private fun SeriesListRow(
    series: KavitaWantToReadSeries,
    onClick: () -> Unit,
    onToggleWantToRead: () -> Unit,
    isInList: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover thumbnail
            Box(
                modifier = Modifier
                    .size(56.dp, 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (series.coverImage != null) {
                    AsyncImage(
                        model = series.coverImage,
                        contentDescription = series.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title + info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${series.pages} pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (series.pagesRead > 0) {
                    LinearProgressIndicator(
                        progress = { series.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // WTR toggle
            IconButton(onClick = onToggleWantToRead) {
                Icon(
                    imageVector = if (isInList) {
                        Icons.Default.BookmarkRemove
                    } else {
                        Icons.Outlined.BookmarkAdd
                    },
                    contentDescription = if (isInList) "Remove from WTR" else "Add to WTR",
                    tint = if (isInList) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

// ── Pagination Bar ──

@Composable
private fun PaginationBar(
    currentPage: Int,
    totalCount: Int,
    pageSize: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
) {
    val totalPages = (totalCount + pageSize - 1) / pageSize
    val hasPrevious = currentPage > 0
    val hasNext = currentPage + 1 < totalPages

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onPreviousPage,
            enabled = hasPrevious
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
            Text("Prev")
        }

        Text(
            text = "Page ${currentPage + 1} of $totalPages",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TextButton(
            onClick = onNextPage,
            enabled = hasNext
        ) {
            Text("Next")
            Icon(Icons.Default.ChevronRight, contentDescription = "Next")
        }
    }
}

// ── State Views ──

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading Want To Read...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.BookmarkAdd,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No series in Want To Read",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add series from the Discover tab",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

// ── Want To Read Toggle Chip (for Series Detail) ──

/**
 * Toggle chip for adding/removing a series from Want To Read.
 * Designed to be embedded in the KavitaSeriesScreen series header.
 */
@Composable
fun WantToReadToggleChip(
    seriesId: Int,
    viewModel: WantToReadViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val isInList by viewModel.wantToReadSeriesIds.collectAsState()
    val isAdded = isInList.contains(seriesId)

    FilterChip(
        selected = isAdded,
        onClick = { viewModel.toggleWantToRead(seriesId) },
        label = {
            Text(if (isAdded) "In Want To Read" else "Want To Read")
        },
        leadingIcon = {
            Icon(
                imageVector = if (isAdded) {
                    Icons.Default.BookmarkAdd
                } else {
                    Icons.Outlined.BookmarkAdd
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

// ── Extension: KavitaWantToReadSort label ──

private val KavitaWantToReadSort.label: String
    get() = when (this) {
        KavitaWantToReadSort.SortName -> "Name"
        KavitaWantToReadSort.CreatedDate -> "Date Added"
        KavitaWantToReadSort.LastChapterAdded -> "Last Updated"
        KavitaWantToReadSort.Pages -> "Pages"
    }
