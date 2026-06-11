package com.mimiral.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch

/**
 * A single item in the navigation drawer.
 */
data class DrawerNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

/**
 * A section header in the navigation drawer for grouping related items.
 */
data class DrawerSection(
    val title: String,
    val items: List<DrawerNavItem>
)

/**
 * All drawer items, grouped into sections.
 */
val drawerSections = listOf(
    DrawerSection(
        title = "Library",
        items = listOf(
            DrawerNavItem(Screen.Library, "Library", Icons.Filled.MenuBook),
            DrawerNavItem(Screen.Collections, "Collections", Icons.Filled.LibraryBooks),
            DrawerNavItem(Screen.ReadingLists, "Lists", Icons.Filled.Bookmarks)
        )
    ),
    DrawerSection(
        title = "Discover",
        items = listOf(
            DrawerNavItem(Screen.Discover, "Discover", Icons.Filled.Explore)
        )
    ),
    DrawerSection(
        title = "Kavita",
        items = listOf(
            DrawerNavItem(Screen.KavitaOpdsFeeds, "Library", Icons.Filled.AutoStories),
            DrawerNavItem(Screen.KavitaBookmarks, "Bookmarks", Icons.Filled.Bookmarks),
            DrawerNavItem(Screen.KavitaCollections, "Collections", Icons.Filled.CollectionsBookmark),
            DrawerNavItem(Screen.KavitaReadingList, "Reading Lists", Icons.Filled.LibraryBooks),
            DrawerNavItem(Screen.WantToRead, "Want To Read", Icons.Outlined.BookmarkAdd),
            DrawerNavItem(Screen.KavitaStats, "Stats", Icons.Filled.BarChart)
        )
    ),
    DrawerSection(
        title = "Reading",
        items = listOf(
            DrawerNavItem(Screen.NowReading, "Now Reading", Icons.Filled.PlayArrow),
            DrawerNavItem(Screen.Statistics, "Statistics", Icons.Filled.BarChart),
            DrawerNavItem(Screen.ReadingGoals, "Goals", Icons.Filled.EmojiEvents)
        )
    ),
    DrawerSection(
        title = "Settings",
        items = listOf(
            DrawerNavItem(Screen.Settings, "Settings", Icons.Filled.Settings)
        )
    )
)

/** Flat list of all drawer items (for convenience). */
val allDrawerItems: List<DrawerNavItem> = drawerSections.flatMap { it.items }

/** Set of routes that should show the drawer (top-level destinations). */
val drawerRoutes: Set<String> = allDrawerItems.map { it.screen.route }.toSet()

/**
 * The content inside the drawer sheet — extracted so it can be used
 * by both the modal drawer and the permanent drawer (future tablet support).
 */
@Composable
fun DrawerSheetContent(
    navController: NavController,
    onNavigate: () -> Unit = {}
) {
    // Read current destination once per recomposition, shared by all items
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    Column(
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        drawerSections.forEachIndexed { sectionIndex, section ->
            // Section header
            Text(
                text = section.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = 16.dp,
                    top = if (sectionIndex == 0) 8.dp else 16.dp,
                    bottom = 4.dp
                )
            )

            // Items in this section
            section.items.forEach { item ->
                val selected = currentDestination?.hierarchy?.any {
                    it.route == item.screen.route
                } == true

                NavigationDrawerItem(
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                    selected = selected,
                    onClick = {
                        // Skip navigation if already on this destination
                        if (currentDestination?.route != item.screen.route) {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                        onNavigate()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            // Divider between sections (not after last)
            if (sectionIndex < drawerSections.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 4.dp
                    )
                )
            }
        }
    }
}

/**
 * The modal navigation drawer that wraps the main content.
 * Opens via hamburger icon or swipe from left edge.
 * Automatically closes when an item is selected.
 */
@Composable
fun MimiralNavigationDrawer(
    drawerState: DrawerState,
    navController: NavController,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .padding(end = 56.dp)
                    .clipToBounds()
            ) {
                DrawerSheetContent(
                    navController = navController,
                    onNavigate = {
                        // Close drawer after navigation
                        scope.launch { drawerState.close() }
                    }
                )
            }
        },
        content = content
    )
}
