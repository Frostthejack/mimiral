package com.mimiral.app.ui.opds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Screen for browsing a Kavita OPDS feed directly (no catalog DB).
 * Receives feedUrl + feedTitle as navigation arguments.
 * Uses shared OpdsFeedBrowse.kt for the feed browsing UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KavitaOpdsBrowseScreen(
    viewModel: KavitaOpdsBrowseViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.feedTitle,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.breadcrumbs.size > 1) {
                            Text(
                                text = uiState.breadcrumbs.dropLast(1)
                                    .joinToString(" > ") { it.title },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.navigateBack()) {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val url = uiState.breadcrumbs.lastOrNull()?.feedUrl
                        if (url != null) {
                            viewModel.browseFeed(url, uiState.feedTitle)
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    OpdsLoadingView()
                }
                uiState.currentFeed != null -> {
                    OpdsFeedBrowseView(
                        feed = uiState.currentFeed!!,
                        isDownloading = uiState.isDownloading,
                        isLoadingNextPage = uiState.isLoadingNextPage,
                        hasNextPage = uiState.hasNextPage,
                        onEntryClick = { viewModel.navigateToEntry(it) },
                        onDownloadClick = { viewModel.downloadEntry(it) },
                        onLoadNextPage = { viewModel.loadNextPage() }
                    )
                }
                uiState.errorMessage != null -> {
                    OpdsErrorView(message = uiState.errorMessage!!)
                }
            }
        }
    }
}
