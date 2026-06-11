package com.mimiral.app.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A single item on the Discover hub screen.
 */
private data class HubItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Discover Hub — central place to access all discovery features.
 * Provides entry points to Kavita browsing, feeds, bookmarks,
 * reading lists, collections, and free sources.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverHubScreen(
    onOpenDrawer: () -> Unit = {},
    onNavigateToDiscover: () -> Unit = {},
    onNavigateToKavitaFeeds: () -> Unit = {},
    onNavigateToKavitaBookmarks: () -> Unit = {},
    onNavigateToWantToRead: () -> Unit = {},
    onNavigateToKavitaCollections: () -> Unit = {},
    onNavigateToFreeSources: () -> Unit = {}
) {
    val kavitaItems = listOf(
        HubItem(
            title = "Kavita",
            subtitle = "Browse your Kavita libraries",
            icon = Icons.Filled.Explore,
            onClick = onNavigateToDiscover
        ),
        HubItem(
            title = "Kavita Feeds",
            subtitle = "Collections, reading lists, on deck",
            icon = Icons.Filled.AutoStories,
            onClick = onNavigateToKavitaFeeds
        ),
        HubItem(
            title = "Bookmarks",
            subtitle = "Your Kavita bookmarks",
            icon = Icons.Filled.Bookmarks,
            onClick = onNavigateToKavitaBookmarks
        ),
        HubItem(
            title = "Want To Read",
            subtitle = "Your reading list",
            icon = Icons.Outlined.BookmarkAdd,
            onClick = onNavigateToWantToRead
        ),
        HubItem(
            title = "Kavita Collections",
            subtitle = "Browse Kavita collections",
            icon = Icons.Filled.CollectionsBookmark,
            onClick = onNavigateToKavitaCollections
        )
    )

    val otherItems = listOf(
        HubItem(
            title = "Free Sources",
            subtitle = "Open source book repositories",
            icon = Icons.Filled.Public,
            onClick = onNavigateToFreeSources
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discover") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Kavita section
            item {
                Text(
                    text = "Kavita",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            items(kavitaItems) { hubItem ->
                HubItemCard(item = hubItem)
            }

            // Sources section
            item {
                Text(
                    text = "Sources",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            items(otherItems) { hubItem ->
                HubItemCard(item = hubItem)
            }

            // Bottom spacer
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HubItemCard(item: HubItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
