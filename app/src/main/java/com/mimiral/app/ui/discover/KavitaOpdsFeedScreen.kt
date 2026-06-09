package com.mimiral.app.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.remote.kavita.KavitaOpdsFeedCategory

/**
 * Screen showing all Kavita OPDS feed categories as a grid.
 * Tapping a category navigates to the OPDS catalog browser
 * at the appropriate feed URL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KavitaOpdsFeedScreen(
    viewModel: KavitaOpdsFeedViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onNavigateToOpdsBrowse: (feedUrl: String, feedTitle: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kavita Feeds") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "Open navigation menu"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    ErrorView(
                        message = uiState.error!!,
                        onRetry = { viewModel.retry() }
                    )
                }
                else -> {
                    FeedCategoryGrid(
                        onCategoryClick = { category ->
                            val feedUrl = viewModel.buildFeedUrl(category)
                            if (feedUrl != null) {
                                onNavigateToOpdsBrowse(feedUrl, category.label)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedCategoryGrid(
    onCategoryClick: (KavitaOpdsFeedCategory) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(KavitaOpdsFeedCategory.entries.toList()) { category ->
            FeedCategoryCard(
                category = category,
                onClick = { onCategoryClick(category) }
            )
        }
    }
}

@Composable
private fun FeedCategoryCard(
    category: KavitaOpdsFeedCategory,
    onClick: () -> Unit
) {
    val icon = category.icon
    val description = category.description

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = category.label,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = category.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ErrorView(
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
            imageVector = Icons.Filled.Explore,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

/** Icon for each feed category. */
private val KavitaOpdsFeedCategory.icon: ImageVector
    get() = when (this) {
        KavitaOpdsFeedCategory.COLLECTIONS -> Icons.Filled.CollectionsBookmark
        KavitaOpdsFeedCategory.READING_LISTS -> Icons.Filled.Bookmarks
        KavitaOpdsFeedCategory.WANT_TO_READ -> Icons.Filled.Star
        KavitaOpdsFeedCategory.ON_DECK -> Icons.Filled.PlayArrow
        KavitaOpdsFeedCategory.RECENTLY_ADDED -> Icons.Filled.History
        KavitaOpdsFeedCategory.RECENTLY_UPDATED -> Icons.Filled.Update
        KavitaOpdsFeedCategory.SMART_FILTERS -> Icons.Filled.Search
    }

/** Short description for each feed category. */
private val KavitaOpdsFeedCategory.description: String?
    get() = when (this) {
        KavitaOpdsFeedCategory.COLLECTIONS -> "Curated groups of series"
        KavitaOpdsFeedCategory.READING_LISTS -> "Ordered reading sequences"
        KavitaOpdsFeedCategory.WANT_TO_READ -> "Series you plan to read"
        KavitaOpdsFeedCategory.ON_DECK -> "Continue where you left off"
        KavitaOpdsFeedCategory.RECENTLY_ADDED -> "Newly added to your library"
        KavitaOpdsFeedCategory.RECENTLY_UPDATED -> "Recently updated series"
        KavitaOpdsFeedCategory.SMART_FILTERS -> "Saved search filters"
    }
