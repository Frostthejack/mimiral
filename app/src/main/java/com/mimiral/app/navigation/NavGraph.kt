package com.mimiral.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mimiral.app.ui.collections.CollectionsScreen
import com.mimiral.app.ui.discover.DiscoverScreen
import com.mimiral.app.ui.discover.KavitaSeriesScreen
import com.mimiral.app.ui.goals.ReadingGoalsScreen
import com.mimiral.app.ui.library.AddBooksScreen
import com.mimiral.app.ui.library.BookMetadataEditScreen
import com.mimiral.app.ui.library.CollectionPickerScreen
import com.mimiral.app.ui.library.LibraryScreen
import com.mimiral.app.ui.nowreading.NowReadingScreen
import com.mimiral.app.ui.reader.ComicReaderScreen
import com.mimiral.app.ui.reader.DjvuReaderScreen
import com.mimiral.app.ui.reader.DocReaderScreen
import com.mimiral.app.ui.reader.EpubReaderScreen
import com.mimiral.app.ui.reader.Fb2ReaderScreen
import com.mimiral.app.ui.reader.MarkdownReaderScreen
import com.mimiral.app.ui.reader.MobiReaderScreen
import com.mimiral.app.ui.reader.PdfReaderScreen
import com.mimiral.app.ui.reader.ReadingModeScreen
import com.mimiral.app.ui.reader.TxtRtfReaderScreen
import com.mimiral.app.ui.readinglists.ReadingListDetailScreen
import com.mimiral.app.ui.readinglists.ReadingListsScreen
import com.mimiral.app.ui.settings.AccessibilitySettingsScreen
import com.mimiral.app.ui.settings.GestureSettingsScreen
import com.mimiral.app.ui.settings.KavitaSetupScreen
import com.mimiral.app.ui.settings.KavitaSetupViewModel
import com.mimiral.app.ui.settings.KavitaDeviceManagementScreen
import com.mimiral.app.ui.settings.LibraryPreferencesScreen
import com.mimiral.app.ui.settings.ReadingPreferencesScreen
import com.mimiral.app.ui.settings.SettingsScreen
import com.mimiral.app.ui.settings.TTSSettingsScreen
import com.mimiral.app.ui.statistics.StatisticsScreen
import kotlinx.coroutines.launch

/**
 * Format-aware navigation: routes to the correct reader based on the book's format.
 * Defaults to PDF reader for unknown formats.
 */
private fun routeForBook(bookId: Int): String {
    return "pdf_reader/$bookId"
}

/**
 * Format-aware navigation route generator.
 * Call this from the library screen with the book's format string.
 */
fun routeForBookFormat(bookId: Int, format: String): String {
    return when (format.uppercase()) {
        "DJVU" -> "djvu_reader/$bookId"
        "EPUB" -> "epub_reader/$bookId"
        "TXT", "RTF" -> "txt_rtf_reader/$bookId"
        "PDF" -> "pdf_reader/$bookId"
        "MOBI", "AZW3" -> "mobi_reader/$bookId"
        "FB2" -> "fb2_reader/$bookId"
        "CBZ", "CBR" -> "comic_reader/$bookId"
        "DOC", "DOCX" -> "doc_reader/$bookId"
        "MD" -> "markdown_reader/$bookId"
        else -> "pdf_reader/$bookId"
    }
}

/**
 * Navigation route for the Reading Mode (reflowable text) view.
 * All formats that support text extraction go to reading_mode.
 * Comic archives (CBZ/CBR) are excluded since they are image-based.
 */
fun routeForBookReadingMode(bookId: Int, format: String): String {
    return when (format.uppercase()) {
        "CBZ", "CBR" -> "comic_reader/$bookId" // Comics don't have text
        else -> "reading_mode/$bookId" // All other formats support text extraction
    }
}

@Composable
fun MimiralNavGraph(navController: NavHostController) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = remember(scope, drawerState) {
        { scope.launch { drawerState.open() } }
    }

    MimiralNavigationDrawer(
        drawerState = drawerState,
        navController = navController
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    onBookClick = { bookId, format ->
                        val route = routeForBookFormat(bookId, format)
                        navController.navigate(route)
                    },
                    onEditBookMetadata = { bookId ->
                        navController.navigate(
                            Screen.EditBookMetadata.createRoute(bookId)
                        )
                    },
                    onNavigateToCollections = { bookIds ->
                        val route = Screen.CollectionPicker.createRoute(bookIds)
                        navController.navigate(route)
                    },
                    onNavigateToAddBooks = {
                        navController.navigate(Screen.AddBooks.route)
                    },
                    onOpenDrawer = openDrawer
                )
            }
            composable(Screen.Collections.route) {
                CollectionsScreen(
                    onBookClick = { bookId, format ->
                        val route = routeForBookFormat(bookId, format)
                        navController.navigate(route)
                    },
                    onOpenDrawer = openDrawer
                )
            }
            composable(Screen.ReadingLists.route) {
                ReadingListsScreen(
                    onBookClick = { bookId, format ->
                        val route = routeForBookFormat(bookId, format)
                        navController.navigate(route)
                    },
                    onOpenDrawer = openDrawer
                )
            }
            composable(
                route = Screen.ReadingListDetail.route,
                arguments = listOf(navArgument("listId") { type = NavType.IntType })
            ) { backStackEntry ->
                val listId = backStackEntry.arguments?.getInt("listId")
                    ?: return@composable
                ReadingListDetailScreen(
                    listId = listId,
                    onBookClick = { bookId, format ->
                        val route = routeForBookFormat(bookId, format)
                        navController.navigate(route)
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AddBooks.route) {
                AddBooksScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.NowReading.route) {
                NowReadingScreen(
                    onBookClick = { bookId, format ->
                        val route = routeForBookFormat(bookId, format)
                        navController.navigate(route)
                    },
                    onNavigateToLibrary = {
                        navController.navigate(Screen.Library.route) {
                            popUpTo(Screen.Library.route) { inclusive = true }
                        }
                    },
                    onOpenDrawer = openDrawer
                )
            }
            composable(Screen.Discover.route) {
                DiscoverScreen(
                    onOpenDrawer = openDrawer,
                    onNavigateToKavitaSeries = { seriesId ->
                        navController.navigate("kavita_series/$seriesId")
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToKavitaSetup = {
                        navController.navigate(Screen.KavitaSetup.route)
                    },
                    onNavigateToKavitaDeviceManagement = {
                        navController.navigate(Screen.KavitaDeviceManagement.route)
                    },
                    onNavigateToReadingPreferences = {
                        navController.navigate(Screen.ReadingPreferences.route)
                    },
                    onNavigateToTTSSettings = {
                        navController.navigate(Screen.TTSSettings.route)
                    },
                    onNavigateToAccessibilitySettings = {
                        navController.navigate(Screen.AccessibilitySettings.route)
                    },
                    onNavigateToLibraryPreferences = {
                        navController.navigate(Screen.LibraryPreferences.route)
                    },
                    onNavigateToGestureSettings = {
                        navController.navigate(Screen.GestureSettings.route)
                    },
                    onOpenDrawer = openDrawer
                )
            }

            composable(Screen.Statistics.route) {
                StatisticsScreen(
                    onNavigateToGoals = {
                        navController.navigate(Screen.ReadingGoals.route)
                    },
                    onOpenDrawer = openDrawer
                )
            }

            composable(Screen.ReadingGoals.route) {
                ReadingGoalsScreen(onOpenDrawer = openDrawer)
            }

            composable(Screen.TTSSettings.route) {
                TTSSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.GestureSettings.route) {
                GestureSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.AccessibilitySettings.route) {
                AccessibilitySettingsScreen()
            }

            composable(Screen.KavitaSetup.route) {
                val kavitaViewModel: KavitaSetupViewModel = hiltViewModel()
                KavitaSetupScreen(
                    viewModel = kavitaViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.KavitaDeviceManagement.route) {
                KavitaDeviceManagementScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ReadingPreferences.route) {
                ReadingPreferencesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.LibraryPreferences.route) {
                LibraryPreferencesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Reader routes
            composable(
                route = "pdf_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId")
                    ?: return@composable
                PdfReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToReadingMode = {
                        navController.navigate("reading_mode/$bookId")
                    }
                )
            }

            composable(
                route = "epub_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId")
                    ?: return@composable
                EpubReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToReadingMode = {
                        navController.navigate("reading_mode/$bookId")
                    }
                )
            }

            composable(
                route = "djvu_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId")
                    ?: return@composable
                DjvuReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "txt_rtf_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId")
                    ?: return@composable
                TxtRtfReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "mobi_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId")
                    ?: return@composable
                MobiReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "fb2_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId")
                    ?: return@composable
                Fb2ReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "comic_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId")
                    ?: return@composable
                ComicReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "doc_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId")
                    ?: return@composable
                DocReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "markdown_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId")
                    ?: return@composable
                MarkdownReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "reading_mode/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId")
                    ?: return@composable
                ReadingModeScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Kavita series/volume browsing
            composable(
                route = Screen.KavitaSeries.route,
                arguments = listOf(navArgument("seriesId") { type = NavType.IntType })
            ) { backStackEntry ->
                val seriesId = backStackEntry.arguments?.getInt("seriesId")
                    ?: return@composable
                KavitaSeriesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToReader = { route -> navController.navigate(route) }
                )
            }
            // Book metadata editing
            composable(
                route = Screen.EditBookMetadata.route,
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId")
                    ?: return@composable
                BookMetadataEditScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Collection picker
            composable(
                route = Screen.CollectionPicker.route,
                arguments = listOf(navArgument("bookIds") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookIdsString = backStackEntry.arguments?.getString("bookIds") ?: ""
                val bookIds = bookIdsString.split(",").mapNotNull { it.toIntOrNull() }
                if (bookIds.isNotEmpty()) {
                    CollectionPickerScreen(
                        bookIds = bookIds,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String, onOpenDrawer: () -> Unit = {}) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = name)
    }
}
