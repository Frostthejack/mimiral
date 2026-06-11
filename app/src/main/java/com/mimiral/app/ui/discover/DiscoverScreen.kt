package com.mimiral.app.ui.discover

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mimiral.app.data.remote.kavita.KavitaLibrary

/**
 * Discover screen for browsing Kavita libraries via REST API.
 *
 * Navigation flow:
 * 1. Libraries list -> tap a library
 * 2. Series list -> tap a series
 * 3. Series detail (volumes, metadata, cover)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel = hiltViewModel(),
    onNavigateBackToDrawer: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onNavigateToKavitaSeries: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentNav = uiState.navigationStack.lastOrNull()

    Scaffold(
        topBar = {
            DiscoverTopBar(
                currentNav = currentNav,
                canGoBack = uiState.navigationStack.size > 1,
                onBackClick = {
                    if (uiState.navigationStack.size > 1) {
                        viewModel.navigateBack()
                    } else {
                        onNavigateBackToDrawer()
                    }
                },
                onOpenDrawer = onOpenDrawer
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentNav) {
                null, is DiscoverNavItem.Libraries -> {
                    LibrariesContent(
                        libraries = uiState.libraries,
                        isLoading = uiState.isLoadingLibraries,
                        error = uiState.error,
                        onLibraryClick = { viewModel.selectLibrary(it) },
                        onRetry = { viewModel.loadLibraries() }
                    )
                }
                is DiscoverNavItem.SeriesInLibrary -> {
                    SeriesListContent(
                        library = uiState.selectedLibrary,
                        series = uiState.series,
                        isLoading = uiState.isLoadingSeries,
                        error = uiState.error,
                        onSeriesClick = { seriesItem ->
                            onNavigateToKavitaSeries(seriesItem.id)
                        },
                        onRetry = {
                            uiState.selectedLibrary?.let { viewModel.selectLibrary(it) }
                        }
                    )
                }
                is DiscoverNavItem.SeriesDetail -> {
                    SeriesDetailContent(
                        series = uiState.selectedSeries,
                        isLoading = uiState.isLoadingMetadata,
                        error = uiState.error,
                        onRetry = {
                            val sel = uiState.selectedSeries
                            if (sel != null) {
                                viewModel.selectSeries(
                                    SeriesItem(
                                        id = sel.id,
                                        name = sel.name,
                                        description = sel.description,
                                        pages = sel.pages,
                                        format = sel.format,
                                        formatLabel = sel.formatLabel,
                                        coverUrl = sel.coverUrl
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverTopBar(
    currentNav: DiscoverNavItem?,
    canGoBack: Boolean,
    onBackClick: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val title = when (currentNav) {
        null -> "Discover"
        is DiscoverNavItem.Libraries -> "Discover"
        is DiscoverNavItem.SeriesInLibrary -> currentNav.library.name
        is DiscoverNavItem.SeriesDetail -> currentNav.seriesName
    }

    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            } else {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = "Open navigation menu"
                    )
                }
            }
        }
    )
}

/**
 * Libraries list content.
 */
@Composable
private fun LibrariesContent(
    libraries: List<KavitaLibrary>,
    isLoading: Boolean,
    error: String?,
    onLibraryClick: (KavitaLibrary) -> Unit,
    onRetry: () -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            ErrorContent(message = error, onRetry = onRetry)
        }
        libraries.isEmpty() -> {
            EmptyContent(
                message = "No Kavita libraries found",
                subtitle = "Make sure your Kavita server is configured " +
                    "and has libraries"
            )
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(libraries) { library ->
                    LibraryCard(
                        library = library,
                        onClick = { onLibraryClick(library) }
                    )
                }
            }
        }
    }
}

/**
 * Series list content for a selected library.
 */
@Composable
private fun SeriesListContent(
    library: KavitaLibrary?,
    series: List<SeriesItem>,
    isLoading: Boolean,
    error: String?,
    onSeriesClick: (SeriesItem) -> Unit,
    onRetry: () -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            ErrorContent(message = error, onRetry = onRetry)
        }
        series.isEmpty() -> {
            EmptyContent(
                message = "No series found",
                subtitle = "This library has no series yet"
            )
        }
        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(series) { s ->
                    SeriesGridCard(
                        series = s,
                        onClick = { onSeriesClick(s) }
                    )
                }
            }
        }
    }
}

/**
 * Series detail content with volumes and metadata.
 */
@Composable
private fun SeriesDetailContent(
    series: SeriesDetail?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            ErrorContent(message = error, onRetry = onRetry)
        }
        series == null -> {
            EmptyContent(message = "No series selected")
        }
        else -> {
            SeriesDetailView(series = series)
        }
    }
}

/**
 * Library card in the libraries list.
 */
@Composable
private fun LibraryCard(
    library: KavitaLibrary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = library.typeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.CollectionsBookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Series card in the grid view.
 */
@Composable
private fun SeriesGridCard(
    series: SeriesItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            val coverUrl = series.coverUrl
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover for ${series.name}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.667f)
                        .clip(
                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        ),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.667f)
                        .clip(
                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (series.pages > 0) {
                    Text(
                        text = "${series.pages} pages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = series.formatLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Series detail view with cover, metadata, and volumes list.
 */
@Composable
private fun SeriesDetailView(series: SeriesDetail) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SeriesHeader(series = series)
        }

        if (series.volumes.isNotEmpty()) {
            item {
                Text(
                    text = "Volumes (${series.volumes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(series.volumes) { volume ->
                VolumeCard(volume = volume)
            }
        }
    }
}

/**
 * Series header with cover image and metadata.
 */
@Composable
private fun SeriesHeader(series: SeriesDetail) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val coverUrl = series.coverUrl
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Cover for ${series.name}",
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(0.667f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(0.667f)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = series.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            MetadataRow(label = "Format", value = series.formatLabel)
            if (series.pages > 0) {
                MetadataRow(label = "Pages", value = "${series.pages}")
            }
            if (!series.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = series.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * A single volume card in the series detail.
 */
@Composable
private fun VolumeCard(volume: VolumeItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val coverUrl = volume.coverUrl
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover for ${volume.name}",
                    modifier = Modifier
                        .width(48.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Book,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = volume.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (volume.pages > 0) {
                    Text(
                        text = "${volume.pages} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (volume.chapterCount > 0) {
                    Text(
                        text = "${volume.chapterCount} chapters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Metadata label-value row.
 */
@Composable
private fun MetadataRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Error content with retry button.
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

/**
 * Empty state content.
 */
@Composable
private fun EmptyContent(
    message: String,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.LibraryBooks,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
