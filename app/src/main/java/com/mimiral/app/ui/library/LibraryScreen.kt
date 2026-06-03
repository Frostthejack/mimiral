package com.mimiral.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.mimiral.app.data.local.settings.FilterOption
import com.mimiral.app.data.local.settings.SortOption
import com.mimiral.app.data.local.settings.ViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (Int, String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentBooks by viewModel.recentBooks.collectAsState()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    // View toggle button
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector = if (uiState.viewMode == ViewMode.GRID) {
                                Icons.Default.List
                            } else {
                                Icons.Default.GridView
                            },
                            contentDescription = if (uiState.viewMode == ViewMode.GRID) {
                                "Switch to list view"
                            } else {
                                "Switch to grid view"
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshLibrary() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    label = { Text("Search books...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )

                // Sort and Filter row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sort dropdown
                    Box {
                        AssistChip(
                            onClick = { sortMenuExpanded = true },
                            label = { Text("Sort: ${uiState.sortOption.displayName}") }
                        )
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        viewModel.setSortOption(option)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Filter chips (scrollable row)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterOption.entries.forEach { filter ->
                            FilterChip(
                                selected = uiState.filterOption == filter,
                                onClick = { viewModel.setFilterOption(filter) },
                                label = { Text(filter.displayName) }
                            )
                        }
                    }
                }

                // Content
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
                            text = uiState.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    val showRecent = recentBooks.isNotEmpty() &&
                        uiState.searchQuery.isBlank() &&
                        uiState.filterOption == FilterOption.ALL

                    val hasBooks = uiState.books.isNotEmpty() || showRecent

                    if (!hasBooks) {
                        // Empty state
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.5f
                                    )
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (uiState.searchQuery.isNotBlank()) {
                                        "No books match your search"
                                    } else {
                                        "No books found"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (uiState.searchQuery.isNotBlank() || uiState.filterOption != FilterOption.ALL) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Try adjusting your filters",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.7f
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        if (uiState.viewMode == ViewMode.GRID) {
                            GridLibraryContent(
                                books = uiState.books,
                                recentBooks = if (showRecent) recentBooks else emptyList(),
                                onBookClick = onBookClick,
                                onBookLongPress = { /* handled per-item */ }
                            )
                        } else {
                            ListLibraryContent(
                                books = uiState.books,
                                recentBooks = if (showRecent) recentBooks else emptyList(),
                                onBookClick = onBookClick,
                                onBookLongPress = { /* handled per-item */ }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridLibraryContent(
    books: List<BookWithProgress>,
    recentBooks: List<BookWithProgress>,
    onBookClick: (Int, String) -> Unit,
    onBookLongPress: (BookWithProgress) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Continue Reading section
        if (recentBooks.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Continue Reading",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // We can't span items across grid cells easily, so show recent as grid items
            items(recentBooks, key = { "recent_${it.book.id}" }) { bookWithProgress ->
                GridBookItem(
                    bookWithProgress = bookWithProgress,
                    onClick = { onBookClick(bookWithProgress.book.id, bookWithProgress.book.format) },
                    onLongClick = { onBookLongPress(bookWithProgress) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        // All Books section header
        if (recentBooks.isNotEmpty()) {
            item {
                Text(
                    text = "All Books",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        items(books, key = { it.book.id }) { bookWithProgress ->
            GridBookItem(
                bookWithProgress = bookWithProgress,
                onClick = { onBookClick(bookWithProgress.book.id, bookWithProgress.book.format) },
                onLongClick = { onBookLongPress(bookWithProgress) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListLibraryContent(
    books: List<BookWithProgress>,
    recentBooks: List<BookWithProgress>,
    onBookClick: (Int, String) -> Unit,
    onBookLongPress: (BookWithProgress) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Continue Reading section
        if (recentBooks.isNotEmpty()) {
            item {
                Text(
                    text = "Continue Reading",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(recentBooks, key = { "recent_${it.book.id}" }) { bookWithProgress ->
                ListBookItem(
                    bookWithProgress = bookWithProgress,
                    onClick = { onBookClick(bookWithProgress.book.id, bookWithProgress.book.format) },
                    onLongClick = { onBookLongPress(bookWithProgress) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // All Books section
        if (recentBooks.isNotEmpty()) {
            item {
                Text(
                    text = "All Books",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        items(books, key = { it.book.id }) { bookWithProgress ->
            ListBookItem(
                bookWithProgress = bookWithProgress,
                onClick = { onBookClick(bookWithProgress.book.id, bookWithProgress.book.format) },
                onLongClick = { onBookLongPress(bookWithProgress) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridBookItem(
    bookWithProgress: BookWithProgress,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                // Cover image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.67f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val coverPath = bookWithProgress.book.coverPath
                    if (coverPath != null) {
                        AsyncImage(
                            model = coverPath,
                            contentDescription = "Cover: ${bookWithProgress.book.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = when (bookWithProgress.book.format) {
                                "PDF" -> Icons.Default.MenuBook
                                "DJVU" -> Icons.Default.TextFields
                                else -> Icons.Default.Book
                            },
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Info section
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = bookWithProgress.book.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = bookWithProgress.book.author ?: "Unknown Author",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (bookWithProgress.isReading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (bookWithProgress.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // Context menu dropdown
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Add to Collection") },
                onClick = { showContextMenu = false },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { showContextMenu = false },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Info") },
                onClick = { showContextMenu = false },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListBookItem(
    bookWithProgress: BookWithProgress,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
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
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val coverPath = bookWithProgress.book.coverPath
                    if (coverPath != null) {
                        AsyncImage(
                            model = coverPath,
                            contentDescription = "Cover: ${bookWithProgress.book.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = when (bookWithProgress.book.format) {
                                "PDF" -> Icons.Default.MenuBook
                                "DJVU" -> Icons.Default.TextFields
                                else -> Icons.Default.Book
                            },
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Text info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bookWithProgress.book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = bookWithProgress.book.author ?: "Unknown Author",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (bookWithProgress.isReading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (bookWithProgress.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (bookWithProgress.isFinished) {
                                "Finished"
                            } else {
                                "Page ${bookWithProgress.currentPage + 1} of ${bookWithProgress.totalPages} (${bookWithProgress.progressPercent.toInt()}%)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Context menu dropdown
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Add to Collection") },
                onClick = { showContextMenu = false },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { showContextMenu = false },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Info") },
                onClick = { showContextMenu = false },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
        }
    }
}
