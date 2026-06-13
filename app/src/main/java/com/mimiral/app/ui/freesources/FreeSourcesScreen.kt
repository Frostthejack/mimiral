package com.mimiral.app.ui.freesources

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mimiral.app.data.remote.opds.FreeSource
import com.mimiral.app.data.remote.opds.OpdsEntry
import com.mimiral.app.data.remote.opds.OpdsFeed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeSourcesScreen(
    viewModel: FreeSourcesViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (uiState.isBrowsing) {
                                uiState.selectedSource?.displayName ?: "Free Sources"
                            } else {
                                "Free Sources"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (uiState.isBrowsing && uiState.feedStack.isNotEmpty()) {
                            Text(
                                text = uiState.feedStack.last().title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (uiState.isBrowsing) {
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                        }
                    }
                },
                actions = {
                    if (uiState.isBrowsing) {
                        IconButton(
                            onClick = {
                                val source = uiState.selectedSource
                                if (source != null) {
                                    viewModel.selectSource(source)
                                    viewModel.browseSource()
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.errorMessage != null -> {
                    ErrorRetryCard(
                        message = uiState.errorMessage!!,
                        onRetry = { viewModel.clearError() },
                        onDismiss = { viewModel.clearError() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.isLoadingFeed -> {
                    LoadingView()
                }
                !uiState.isBrowsing -> {
                    SourceSelectionView(
                        sources = uiState.sources,
                        onSourceSelected = { viewModel.selectSource(it) }
                    )
                }
                uiState.isSearching -> {
                    LoadingView(message = "Searching...")
                }
                uiState.searchResults.isNotEmpty() -> {
                    SearchResultsView(
                        entries = uiState.searchResults,
                        downloadingIds = uiState.downloadingIds,
                        downloadedIds = uiState.downloadedIds,
                        onEntryClick = { entry ->
                            if (entry.isNavigationEntry) {
                                viewModel.navigateToEntry(entry)
                            }
                        },
                        onDownloadClick = { viewModel.downloadEntry(it) }
                    )
                }
                uiState.currentFeed != null -> {
                    FeedBrowseView(
                        feed = uiState.currentFeed!!,
                        downloadingIds = uiState.downloadingIds,
                        downloadedIds = uiState.downloadedIds,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                        onSearch = { viewModel.search() },
                        onEntryClick = { entry ->
                            if (entry.isNavigationEntry) {
                                viewModel.navigateToEntry(entry)
                            }
                        },
                        onDownloadClick = { viewModel.downloadEntry(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorRetryCard(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = "Error loading feed",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

// ---- Source Selection ----

@Composable
private fun SourceSelectionView(
    sources: List<FreeSource>,
    onSourceSelected: (FreeSource) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Browse Free Books",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Search and download free books from curated online libraries.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(sources, key = { it.name }) { source ->
            SourceCard(
                source = source,
                onClick = { onSourceSelected(source) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Explore sources",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Looking for more sources?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Add custom OPDS catalogs from Calibre, Komga, " +
                                "or any OPDS-compatible server in a future update.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: FreeSource,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (source) {
                            FreeSource.PROJECT_GUTENBERG -> Icons.Default.MenuBook
                            FreeSource.STANDARD_EBOOKS -> Icons.Default.AutoStories
                            FreeSource.OPEN_LIBRARY -> Icons.Default.LibraryBooks
                        },
                        contentDescription = "${source.displayName} icon",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- Feed Browse View ----

@Composable
private fun FeedBrowseView(
    feed: OpdsFeed,
    downloadingIds: Set<String>,
    downloadedIds: Set<String>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onEntryClick: (OpdsEntry) -> Unit,
    onDownloadClick: (OpdsEntry) -> Unit
) {
    val navEntries = feed.entries.filter { it.isNavigationEntry }
    val bookEntries = feed.entries.filter { !it.isNavigationEntry }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Search bar
        item {
            SearchBarRow(
                query = searchQuery,
                onQueryChanged = onSearchQueryChanged,
                onSearch = onSearch,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Feed subtitle
        if (!feed.subtitle.isNullOrBlank()) {
            item {
                Text(
                    text = feed.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // Navigation groups
        feed.navigationGroups.forEach { group ->
            item {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(group.entries, key = { "grp_${it.id}" }) { entry ->
                if (entry.isNavigationEntry) {
                    NavigationEntryRow(
                        entry = entry,
                        onClick = { onEntryClick(entry) }
                    )
                } else {
                    BookEntryCard(
                        entry = entry,
                        isDownloading = entry.id in downloadingIds,
                        isDownloaded = entry.id in downloadedIds,
                        onClick = { onEntryClick(entry) },
                        onDownload = { onDownloadClick(entry) }
                    )
                }
            }
        }

        // Navigation entries (sub-categories)
        if (navEntries.isNotEmpty()) {
            if (bookEntries.isNotEmpty()) {
                item {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            items(navEntries, key = { "nav_${it.id}" }) { entry ->
                NavigationEntryRow(
                    entry = entry,
                    onClick = { onEntryClick(entry) }
                )
            }
        }

        // Book entries
        if (bookEntries.isNotEmpty()) {
            if (navEntries.isNotEmpty() || feed.navigationGroups.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "Books (${bookEntries.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            } else if (!feed.subtitle.isNullOrBlank()) {
                item {
                    Text(
                        text = "Books (${bookEntries.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            items(bookEntries, key = { "book_${it.id}" }) { entry ->
                BookEntryCard(
                    entry = entry,
                    isDownloading = entry.id in downloadingIds,
                    isDownloaded = entry.id in downloadedIds,
                    onClick = { onEntryClick(entry) },
                    onDownload = { onDownloadClick(entry) }
                )
            }
        }

        // Navigation links at feed level
        if (feed.navigationLinks.isNotEmpty()) {
            item {
                Text(
                    text = "Sections",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(feed.navigationLinks, key = { "navlink_${it.href}" }) { link ->
                NavigationLinkRow(
                    title = link.title ?: link.href,
                    onClick = {
                        // TODO: Implement URL-based navigation
                        // Parse link.href to determine if it's a relative or absolute OPDS URL.
                        // If relative, resolve against the current feed URL.
                        // Then call viewModel.browseFeed(resolvedUrl, link.title) to navigate.
                        // Example: viewModel.browseFeed(link.href, link.title ?: link.href)
                    }
                )
            }
        }

        // Empty feed
        if (feed.entries.isEmpty() &&
            feed.navigationGroups.isEmpty() &&
            feed.navigationLinks.isEmpty()
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "No results found",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This section is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Try searching or browse a different category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ---- Search Results View ----

@Composable
private fun SearchResultsView(
    entries: List<OpdsEntry>,
    downloadingIds: Set<String>,
    downloadedIds: Set<String>,
    onEntryClick: (OpdsEntry) -> Unit,
    onDownloadClick: (OpdsEntry) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Search Results (${entries.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Try a different search term.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { "search_${it.id}" }) { entry ->
                    BookEntryCard(
                        entry = entry,
                        isDownloading = entry.id in downloadingIds,
                        isDownloaded = entry.id in downloadedIds,
                        onClick = { onEntryClick(entry) },
                        onDownload = { onDownloadClick(entry) }
                    )
                }
            }
        }
    }
}

// ---- Shared Components ----

@Composable
private fun SearchBarRow(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("Search books...") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            }
        )
        Button(
            onClick = onSearch,
            enabled = query.isNotBlank()
        ) {
            Text("Search")
        }
    }
}

@Composable
private fun NavigationEntryRow(
    entry: OpdsEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (!entry.summary.isNullOrBlank()) {
                Text(
                    text = entry.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NavigationLinkRow(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BookEntryCard(
    entry: OpdsEntry,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit
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
            verticalAlignment = Alignment.Top
        ) {
            // Cover thumbnail
            Box(
                modifier = Modifier
                    .size(72.dp, 100.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val thumbnailUrl = entry.thumbnailUrl ?: entry.coverImageUrl
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = "Cover: ${entry.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (entry.authors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entry.authors.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!entry.summary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.summary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Download button / status
                when {
                    isDownloading -> {
                        FilledTonalButton(onClick = {}, enabled = false) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Downloading...")
                        }
                    }
                    isDownloaded -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Downloaded",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Downloaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    entry.downloadLinks.isNotEmpty() || entry.acquisitionLinks.isNotEmpty() -> {
                        FilledTonalButton(onClick = onDownload) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download",
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingView(message: String = "Loading...") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = "Loading",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
