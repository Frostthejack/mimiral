package com.mimiral.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.remote.kavita.KavitaFavoriteAuthor
import com.mimiral.app.data.remote.kavita.KavitaGenreBreakdown
import com.mimiral.app.data.remote.kavita.KavitaPagesPerYear
import com.mimiral.app.data.remote.kavita.KavitaReadingActivity
import com.mimiral.app.data.remote.kavita.KavitaReadingPace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDashboardScreen(
    onOpenDrawer: () -> Unit = {},
    onNavigateToSetup: () -> Unit = {},
    viewModel: StatsDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kavita Stats") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "Open navigation menu"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !uiState.isLoading && !uiState.isRefreshing
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh stats"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.error != null) {
                ErrorRetryCard(
                    message = uiState.error!!,
                    onRetry = { viewModel.retry() },
                    onDismiss = { viewModel.clearError() },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (!uiState.hasKavitaServer) {
                NoKavitaServerState(
                    onNavigateToSetup = onNavigateToSetup,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.readingActivity.isEmpty() &&
                uiState.genreBreakdown.isEmpty() &&
                uiState.pagesPerYear.isEmpty() &&
                uiState.readingPace.isEmpty() &&
                uiState.favoriteAuthors.isEmpty()
            ) {
                EmptyStatsDashboard(
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                StatsDashboardContent(
                    uiState = uiState,
                    modifier = Modifier.fillMaxSize()
                )
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
                imageVector = Icons.Default.BarChart,
                contentDescription = null,
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

@Composable
private fun NoKavitaServerState(
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.BarChart,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Kavita Server Connected",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Connect a Kavita server to see reading statistics.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onNavigateToSetup) {
            Text("Set Up Kavita")
        }
    }
}

@Composable
private fun StatsDashboardContent(
    uiState: StatsDashboardUiState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Reading Activity Chart ────────────────────────
        if (uiState.readingActivity.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Reading Activity")
                    Spacer(modifier = Modifier.height(8.dp))
                    ReadingActivityChart(
                        data = uiState.readingActivity,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }

        // ── Genre Breakdown Pie Chart ─────────────────────
        if (uiState.genreBreakdown.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Genre Breakdown")
                    Spacer(modifier = Modifier.height(8.dp))
                    GenrePieChart(
                        data = uiState.genreBreakdown,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            }
        }

        // ── Pages Per Year Bar Chart ──────────────────────
        if (uiState.pagesPerYear.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Pages Per Year")
                    Spacer(modifier = Modifier.height(8.dp))
                    PagesPerYearChart(
                        data = uiState.pagesPerYear,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }

        // ── Reading Pace Trend ────────────────────────────
        if (uiState.readingPace.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Reading Pace")
                    Spacer(modifier = Modifier.height(8.dp))
                    ReadingPaceChart(
                        data = uiState.readingPace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }

        // ── Favorite Authors ──────────────────────────────
        if (uiState.favoriteAuthors.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Favorite Authors")
                    Spacer(modifier = Modifier.height(8.dp))
                    FavoriteAuthorsList(
                        authors = uiState.favoriteAuthors
                    )
                }
            }
        }

        // Bottom spacer
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EmptyStatsDashboard(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Filled.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Stats Available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Start reading on your Kavita server to see stats here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

// ── Reading Activity Bar Chart ──────────────────────────────

@Composable
private fun ReadingActivityChart(
    data: List<KavitaReadingActivity>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val visibleData = data.takeLast(14)
            val maxPages = visibleData.maxOfOrNull { it.pagesRead }?.coerceAtLeast(1) ?: 1

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                visibleData.forEach { point ->
                    val fraction = point.pagesRead.toFloat() / maxPages
                    val barHeightDp = (fraction * 80f).coerceAtLeast(2f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .width(12.dp)
                                .height(barHeightDp.dp + 4.dp)
                        ) {
                            drawRoundRect(
                                color = if (point.pagesRead > 0) {
                                    primaryColor
                                } else {
                                    surfaceVariantColor.copy(alpha = 0.3f)
                                },
                                topLeft = Offset.Zero,
                                size = Size(size.width, barHeightDp.dp.toPx()),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = point.date.takeLast(2),
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ── Genre Breakdown Pie Chart ───────────────────────────────

@Composable
private fun GenrePieChartColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return listOf(
        scheme.primary, scheme.secondary, scheme.tertiary,
        scheme.error, scheme.primaryContainer, scheme.secondaryContainer,
        scheme.tertiaryContainer, scheme.outline, scheme.surfaceVariant,
        scheme.inversePrimary, scheme.surface, scheme.background
    )
}

@Composable
private fun GenrePieChart(
    data: List<KavitaGenreBreakdown>,
    modifier: Modifier = Modifier
) {
    val totalPages = data.sumOf { it.pagesRead }.coerceAtLeast(1)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    val pieColors = GenrePieChartColors()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pie chart
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
            ) {
                var startAngle = -90f
                data.forEachIndexed { index, genre ->
                    val sweepAngle = (genre.pagesRead.toFloat() / totalPages) * 360f
                    val color = pieColors[index % pieColors.size]
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true,
                        style = Fill
                    )
                    startAngle += sweepAngle
                }
            }

            // Legend
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                data.take(8).forEachIndexed { index, genre ->
                    val percentage = (genre.pagesRead.toFloat() / totalPages * 100).toInt()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(modifier = Modifier.size(10.dp)) {
                            drawCircle(
                                color = pieColors[index % pieColors.size],
                                radius = size.minDimension / 2
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${genre.genre} $percentage%",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ── Pages Per Year Bar Chart ────────────────────────────────

@Composable
private fun PagesPerYearChart(
    data: List<KavitaPagesPerYear>,
    modifier: Modifier = Modifier
) {
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val maxPages = data.maxOfOrNull { it.pagesRead }?.coerceAtLeast(1) ?: 1

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                data.forEach { point ->
                    val fraction = point.pagesRead.toFloat() / maxPages
                    val barHeightDp = (fraction * 80f).coerceAtLeast(2f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${point.pagesRead}",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Canvas(
                            modifier = Modifier
                                .width(20.dp)
                                .height(barHeightDp.dp + 4.dp)
                        ) {
                            drawRoundRect(
                                color = tertiaryColor,
                                topLeft = Offset.Zero,
                                size = Size(size.width, barHeightDp.dp.toPx()),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${point.year}",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ── Reading Pace Trend Line Chart ───────────────────────────

@Composable
private fun ReadingPaceChart(
    data: List<KavitaReadingPace>,
    modifier: Modifier = Modifier
) {
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (data.size < 2) {
                Text(
                    text = "Not enough data for trend",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceColor
                )
            } else {
                val maxValue =
                    data.maxOfOrNull { it.pagesPerDay }?.coerceAtLeast(1f) ?: 1f

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    val chartWidth = size.width - 40f
                    val chartHeight = size.height - 20f
                    val startX = 40f
                    val startY = 0f

                    // Draw grid lines
                    for (i in 0..4) {
                        val y = startY + chartHeight * i / 4f
                        drawLine(
                            color = surfaceVariantColor.copy(alpha = 0.3f),
                            start = Offset(startX, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                        )
                    }

                    // Draw the trend line
                    val path = Path()
                    data.forEachIndexed { index, point ->
                        val x = startX + (chartWidth * index / (data.size - 1).toFloat())
                        val y = startY + chartHeight * (1f - point.pagesPerDay / maxValue)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = secondaryColor,
                        style = Stroke(width = 3f)
                    )

                    // Draw data points
                    data.forEachIndexed { index, point ->
                        val x = startX + (chartWidth * index / (data.size - 1).toFloat())
                        val y = startY + chartHeight * (1f - point.pagesPerDay / maxValue)
                        drawCircle(
                            color = secondaryColor,
                            radius = 5f,
                            center = Offset(x, y)
                        )
                    }
                }

                // Month labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    data.take(12).forEach { point ->
                        Text(
                            text = point.month.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ── Favorite Authors List ───────────────────────────────────

@Composable
private fun FavoriteAuthorsList(
    authors: List<KavitaFavoriteAuthor>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            authors.take(10).forEachIndexed { index, author ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = author.author,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${author.pagesRead} pages" +
                                if (author.seriesCount > 0) {
                                    " \u00b7 ${author.seriesCount} series"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "#${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
