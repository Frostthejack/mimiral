package com.mimiral.app.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A discoverable content source shown in the hub.
 */
data class DiscoverSource(
    val title: String,
    val description: String,
    val icon: ImageVector
)

/**
 * The Discover hub screen — entry point for browsing content from multiple sources.
 *
 * Available sources:
 * - Kavita Library: Browse series from connected Kavita servers via REST API
 * - OPDS Servers: Browse and download books from OPDS catalogs
 * - Free Sources: Browse free books from Project Gutenberg, Standard Ebooks, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onNavigateBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onNavigateToKavitaFeeds: () -> Unit = {},
    onNavigateToOpdsCatalogBrowser: () -> Unit = {},
    onNavigateToFreeSources: () -> Unit = {}
) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Find Your Next Read",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Browse books and series from multiple sources.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(discoverSources) { source ->
                DiscoverSourceCard(
                    source = source,
                    onClick = {
                        when (source.title) {
                            "Kavita Library" -> onNavigateToKavitaFeeds()
                            "OPDS Servers" -> onNavigateToOpdsCatalogBrowser()
                            "Free Sources" -> onNavigateToFreeSources()
                        }
                    }
                )
            }
        }
    }
}

/**
 * Card for a single discover source.
 */
@Composable
private fun DiscoverSourceCard(
    source: DiscoverSource,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Icon(
                imageVector = source.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = source.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = source.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The list of available discover sources.
 */
private val discoverSources = listOf(
    DiscoverSource(
        title = "Kavita Library",
        description = "Browse series, collections, and reading lists from your Kavita server.",
        icon = Icons.Filled.LibraryBooks
    ),
    DiscoverSource(
        title = "OPDS Servers",
        description = "Connect to OPDS catalogs like Calibre, Komga, or Standard Ebooks.",
        icon = Icons.Filled.Cloud
    ),
    DiscoverSource(
        title = "Free Sources",
        description = "Download free books from Project Gutenberg and Standard Ebooks.",
        icon = Icons.Filled.Public
    )
)
