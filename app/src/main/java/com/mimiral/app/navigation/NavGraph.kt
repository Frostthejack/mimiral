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
import com.mimiral.app.ui.library.LibraryScreen
import com.mimiral.app.ui.reader.EpubReaderScreen
import com.mimiral.app.ui.reader.PdfReaderScreen
import com.mimiral.app.ui.settings.SettingsScreen

@Composable
fun MimiralNavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            // Hide bottom bar on reader screens
            val route = currentDestination?.route
            if (route == null || (route != Screen.Library.route
                        && route != Screen.Discover.route
                        && route != Screen.NowReading.route
                        && route != Screen.Settings.route)
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
                    onBookClick = { bookId ->
                        navController.navigate("pdf_reader/$bookId")
                    }
                )
            }
            composable(Screen.Discover.route) {
                PlaceholderScreen("Discover")
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
