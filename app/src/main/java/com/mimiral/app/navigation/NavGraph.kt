package com.mimiral.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.mimiral.app.ui.freesources.FreeSourcesScreen
import com.mimiral.app.ui.library.AddBooksScreen
import com.mimiral.app.ui.library.LibraryScreen
import com.mimiral.app.ui.reader.DjvuReaderScreen
import com.mimiral.app.ui.reader.EpubReaderScreen
import com.mimiral.app.ui.reader.PdfReaderScreen
import com.mimiral.app.ui.reader.TxtRtfReaderScreen
import com.mimiral.app.ui.settings.SettingsScreen

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
        else -> "pdf_reader/$bookId"
    }
}

@Composable
fun MimiralNavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            val route = currentDestination?.route
            if (route == null || (
                route != Screen.Library.route &&
                    route != Screen.FreeSources.route &&
                    route != Screen.AddBooks.route &&
                    route != Screen.NowReading.route &&
                    route != Screen.Settings.route
                )
            ) {
                // Don't show bottom bar
            } else {
                BottomNavBar(
                    navController = navController,
                    currentDestination = currentDestination
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    onBookClick = { bookId, format ->
                        val route = routeForBookFormat(bookId, format)
                        navController.navigate(route)
                    }
                )
            }
            composable(Screen.AddBooks.route) {
                AddBooksScreen()
            }
            composable(Screen.FreeSources.route) {
                FreeSourcesScreen()
            }
            composable(Screen.NowReading.route) {
                PlaceholderScreen("Now Reading")
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            // Reader routes
            composable(
                route = "pdf_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId") ?: return@composable
                PdfReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "epub_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId") ?: return@composable
                EpubReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "djvu_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId") ?: return@composable
                DjvuReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "txt_rtf_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId") ?: return@composable
                TxtRtfReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = name)
    }
}
