package com.mimiral.app.ui.discover

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.mimiral.app.data.remote.ChapterDto
import com.mimiral.app.data.remote.VolumeDto
import com.mimiral.app.data.remote.kavita.KavitaMarkReadUiState
import com.mimiral.app.data.remote.kavita.MarkReadOperation

// ── Series List Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KavitaSeriesScreen(
    viewModel: KavitaSeriesViewModel = hiltViewModel(),
    markReadViewModel: KavitaMarkReadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToReader: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val markReadState by markReadViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.selectedSeries?.name ?: "Kavita Library",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.selectedVolume != null) {
                            viewModel.clearVolumeSelection()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Series-level mark read/unread menu (only when viewing volumes)
                    if (uiState.selectedSeries != null && uiState.selectedVolume == null) {
                        SeriesMarkReadMenu(
                            seriesId = uiState.selectedSeries!!.id,
                            markReadViewModel = markReadViewModel
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.volumes.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading series...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                uiState.errorMessage != null -> {
                    ErrorState(
                        message = uiState.errorMessage!!,
                        onDismiss = { viewModel.clearError() },
                        onRetry = { uiState.selectedVolume?.let { viewModel.reloadVolumes() } }
                    )
                }

                uiState.volumes.isEmpty() -> {
                    EmptyState()
                }

                uiState.selectedVolume != null -> {
                    VolumeDetailView(
                        volume = uiState.selectedVolume!!,
                        viewModel = viewModel,
                        markReadViewModel = markReadViewModel,
                        onNavigateToReader = onNavigateToReader
                    )
                }

                else -> {
                    VolumesListView(
                        volumes = uiState.volumes,
                        seriesDetail = uiState.seriesDetail,
                        seriesId = uiState.selectedSeries?.id ?: 0,
                        onVolumeClick = { viewModel.selectVolume(it) },
                        markReadViewModel = markReadViewModel
                    )
                }
            }

            // Show marking-in-progress indicator
            if (markReadState.isMarking) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Updating...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Series-level Mark Read/Unread Dropdown Menu ──

@Composable
private fun SeriesMarkReadMenu(
    seriesId: Int,
    markReadViewModel: KavitaMarkReadViewModel
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Mark all as read") },
                onClick = {
                    expanded = false
                    markReadViewModel.markSeriesRead(seriesId)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Mark all as unread") },
                onClick = {
                    expanded = false
                    markReadViewModel.markSeriesUnread(seriesId)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

// ── Volumes List View (within a series) ──

@Composable
private fun VolumesListView(
    volumes: List<VolumeDto>,
    seriesDetail: com.mimiral.app.data.remote.SeriesDetailDto?,
    seriesId: Int,
    onVolumeClick: (VolumeDto) -> Unit,
    markReadViewModel: KavitaMarkReadViewModel
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Series header card
        item {
            SeriesHeaderCard(seriesDetail = seriesDetail)
        }

        // Volumes header
        item {
            Text(
                text = "Volumes (${volumes.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        items(volumes, key = { "vol_${it.id}" }) { volume ->
            VolumeCard(
                volume = volume,
                seriesId = seriesId,
                onClick = { onVolumeClick(volume) },
                markReadViewModel = markReadViewModel
            )
        }

        // Specials section
        if (seriesDetail?.specials?.isNotEmpty() == true) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Specials",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(seriesDetail.specials, key = { "special_${it.id}" }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    seriesId = seriesId,
                    markReadViewModel = markReadViewModel,
                    onClick = { /* Navigate to reader */ }
                )
            }
        }
    }
}

@Composable
private fun SeriesHeaderCard(
    seriesDetail: com.mimiral.app.data.remote.SeriesDetailDto?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LibraryBooks,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Series Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (seriesDetail != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StatChip(
                                label = "Total chapters",
                                value = "${seriesDetail.totalCount}"
                            )
                            StatChip(
                                label = "Unread",
                                value = "${seriesDetail.unreadCount}"
                            )
                        }
                    }
                }
            }
            if (seriesDetail != null) {
                val readCount = seriesDetail.totalCount - seriesDetail.unreadCount
                val progress = if (seriesDetail.totalCount > 0) {
                    readCount.toFloat() / seriesDetail.totalCount
                } else {
                    0f
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$readCount / ${seriesDetail.totalCount} chapters read",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

// ── Volume Card ──

@Composable
private fun VolumeCard(
    volume: VolumeDto,
    seriesId: Int,
    onClick: () -> Unit,
    markReadViewModel: KavitaMarkReadViewModel
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isFullyRead = volume.pages > 0 && volume.pagesRead >= volume.pages

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Volume cover
            Box(
                modifier = Modifier
                    .size(56.dp, 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val coverUrl = volume.coverImage
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "Cover: ${volume.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = volume.name.ifBlank { "Volume ${volume.number}" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${volume.pages} pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${volume.chapters.size} chapters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (volume.pagesRead > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val progress = if (volume.pages > 0) {
                        volume.pagesRead.toFloat() / volume.pages
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${volume.pagesRead}/${volume.pages} pages read",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Context menu for volume
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Volume options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (isFullyRead) {
                        DropdownMenuItem(
                            text = { Text("Mark as unread") },
                            onClick = {
                                menuExpanded = false
                                markReadViewModel.markVolumeUnread(volume.id, seriesId)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Mark as read") },
                            onClick = {
                                menuExpanded = false
                                markReadViewModel.markVolumeRead(volume.id, seriesId)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Volume Detail View (shows chapters) ──

@Composable
private fun VolumeDetailView(
    volume: VolumeDto,
    viewModel: KavitaSeriesViewModel,
    markReadViewModel: KavitaMarkReadViewModel,
    onNavigateToReader: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val isFullyRead = volume.pages > 0 && volume.pagesRead >= volume.pages

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Volume header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = volume.name.ifBlank { "Volume ${volume.number}" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${volume.chapters.size} chapters \u2022 ${volume.pages} pages",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (volume.wordCount > 0) {
                            Text(
                                text = "${volume.wordCount} words",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val hoursText = buildString {
                            if (volume.minHoursToRead > 0) {
                                append("${volume.minHoursToRead}h")
                                if (volume.maxHoursToRead > volume.minHoursToRead) {
                                    append("-${volume.maxHoursToRead}h")
                                }
                                append(" read")
                            }
                        }
                        if (hoursText.isNotEmpty()) {
                            Text(
                                text = hoursText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Mark read/unread button for volume
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    if (isFullyRead) {
                                        markReadViewModel.markVolumeUnread(
                                            volume.id, volume.seriesId
                                        )
                                    } else {
                                        markReadViewModel.markVolumeRead(
                                            volume.id, volume.seriesId
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isFullyRead) {
                                        Icons.Default.RadioButtonUnchecked
                                    } else {
                                        Icons.Default.CheckCircle
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isFullyRead) "Mark unread" else "Mark read"
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Chapter list (expandable)
        if (expanded) {
            items(volume.chapters, key = { "ch_${it.id}" }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    seriesId = volume.seriesId,
                    markReadViewModel = markReadViewModel,
                    onClick = {
                        val chapterTitle = chapter.title.ifBlank {
                            chapter.titleName ?: "Chapter ${chapter.number}"
                        }
                        viewModel.downloadChapter(
                            chapterId = chapter.id,
                            title = chapterTitle,
                            seriesId = volume.seriesId
                        ) { bookId, format ->
                            if (bookId != null) {
                                val route = when (format?.uppercase()) {
                                    "PDF" -> "pdf_reader/$bookId"
                                    "CBZ", "CBR" -> "comic_reader/$bookId"
                                    else -> "epub_reader/$bookId" // default to EPUB reader
                                }
                                onNavigateToReader(route)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: ChapterDto,
    seriesId: Int,
    markReadViewModel: KavitaMarkReadViewModel,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isFullyRead = chapter.pages > 0 && chapter.pagesRead >= chapter.pages

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title.ifBlank {
                        chapter.titleName ?: "Chapter ${chapter.number}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${chapter.pages} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (chapter.isSpecial) {
                        Text(
                            text = "Special",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (chapter.pagesRead > 0) {
                        Text(
                            text = "${chapter.pagesRead}/${chapter.pages} read",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isFullyRead) {
                        Text(
                            text = "\u2713 Read",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            // Chapter context menu
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Chapter options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (!isFullyRead) {
                        DropdownMenuItem(
                            text = { Text("Mark as read") },
                            onClick = {
                                menuExpanded = false
                                markReadViewModel.markChapterRead(chapter.id, seriesId)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        // Catch-up option: mark chapters up to this one
                        DropdownMenuItem(
                            text = { Text("Mark up to here as read") },
                            onClick = {
                                menuExpanded = false
                                markReadViewModel.markChapterUntilRead(
                                    seriesId = seriesId,
                                    chapterId = chapter.id
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.LibraryBooks,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Shared States ──

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CollectionsBookmark,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No volumes found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This series has no volumes or chapters yet.",
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connection Error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
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
