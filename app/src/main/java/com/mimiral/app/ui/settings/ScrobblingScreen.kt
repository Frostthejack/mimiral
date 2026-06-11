package com.mimiral.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrobblingScreen(
    viewModel: ScrobblingViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.tokenUpdateSuccess) {
        if (uiState.tokenUpdateSuccess) {
            snackbarHostState.showSnackbar("Token updated successfully")
            viewModel.clearTokenSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scrobbling") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // License loading
            if (uiState.isLicenseLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Checking Kavita+ license...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (!uiState.isKavitaPlus) {
                // No valid license
                NoLicenseCard(
                    licenseError = uiState.licenseError,
                    licenseStatus = uiState.licenseStatus
                )
            } else {
                // Valid license — show scrobbling management
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 16.dp,
                        bottom = 32.dp
                    )
                ) {
                    // ── License Banner ──
                    item {
                        LicenseBanner(licenseStatus = uiState.licenseStatus)
                    }

                    // ── Scrobble Settings ──
                    item {
                        ScrobbleSettingsSection(
                            settings = uiState.settings,
                            isLoading = uiState.isSettingsLoading
                        )
                    }

                    // ── Provider Token Management ──
                    item {
                        TokenManagementSection(
                            settings = uiState.settings,
                            newTokenInput = uiState.newTokenInput,
                            isUpdatingProvider = uiState.isUpdatingProvider,
                            updatingProvider = uiState.updatingProvider,
                            tokenUpdateError = uiState.tokenUpdateError,
                            tokenUpdateSuccess = uiState.tokenUpdateSuccess,
                            onTokenInputChanged = { viewModel.setNewTokenInput(it) },
                            onUpdateProvider = { viewModel.updateProviderToken(it) }
                        )
                    }

                    // ── Scrobble Holds ──
                    item {
                        HoldsSection(
                            holds = uiState.scrobbleHolds,
                            isLoading = uiState.isHoldsLoading,
                            onToggleHold = { viewModel.toggleHold(it) }
                        )
                    }

                    // ── Scrobble Errors ──
                    item {
                        Text(
                            text = "Scrobble Errors",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (uiState.isErrorsLoading) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Loading errors...")
                            }
                        }
                    } else if (uiState.scrobbleErrors.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "No scrobble errors — all good!",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            uiState.scrobbleErrors,
                            key = { it.id }
                        ) { error ->
                            ScrobbleErrorCard(
                                error = error,
                                isRetrying = uiState.retryingErrorId == error.id,
                                onRetry = { viewModel.retryError(error.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoLicenseCard(
    licenseError: String?,
    licenseStatus: com.mimiral.app.data.remote.kavita.KavitaLicenseStatus?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Kavita+ License Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = licenseError
                        ?: "Scrobbling requires an active Kavita+ license. " +
                            "Please purchase or renew your license on the Kavita server.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (licenseStatus != null) {
                    Text(
                        text = "License type: ${licenseStatus.licenseType ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseBanner(
    licenseStatus: com.mimiral.app.data.remote.kavita.KavitaLicenseStatus?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Kavita+ Active",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    buildString {
                        append("License: ${licenseStatus?.licenseType ?: "Valid"}")
                        if (licenseStatus?.expirationDate != null) {
                            append(" · Expires: ${licenseStatus.expirationDate}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ScrobbleSettingsSection(
    settings: com.mimiral.app.data.remote.kavita.KavitaScrobblingSettings?,
    isLoading: Boolean
) {
    Text(
        text = "Scrobble Settings",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading settings...")
            }
        } else if (settings == null) {
            Text(
                "Unable to load settings",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Global scrobble toggle (read-only display)
                SettingsRow(
                    label = "Scrobbling Enabled",
                    description = if (settings.isScrobblingEnabled) {
                        "Server is scrobbling reading activity"
                    } else {
                        "Scrobbling is disabled on the server"
                    },
                    checked = settings.isScrobblingEnabled
                )

                HorizontalDivider()

                // Provider status chips
                Text(
                    "Active Providers",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProviderChip("AniList", settings.isAniListEnabled)
                    ProviderChip("MAL", settings.isMalEnabled)
                    ProviderChip("Google Books", settings.isGoogleBooksEnabled)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    description: String,
    checked: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null, // Read-only; server-side setting
            enabled = false
        )
    }
}

@Composable
private fun ProviderChip(name: String, enabled: Boolean) {
    val colors = if (enabled) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
    Card(colors = colors) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                if (enabled) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun TokenManagementSection(
    settings: com.mimiral.app.data.remote.kavita.KavitaScrobblingSettings?,
    newTokenInput: String,
    isUpdatingProvider: Boolean,
    updatingProvider: String?,
    tokenUpdateError: String?,
    tokenUpdateSuccess: Boolean,
    onTokenInputChanged: (String) -> Unit,
    onUpdateProvider: (String) -> Unit
) {
    Text(
        text = "Provider Token Management",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "If a provider token has expired, enter a new token below to re-authenticate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Token input
            OutlinedTextField(
                value = newTokenInput,
                onValueChange = onTokenInputChanged,
                label = { Text("New Token") },
                leadingIcon = {
                    Icon(Icons.Default.Key, contentDescription = null)
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Provider update buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProviderTokenButton(
                    label = "AniList",
                    isUpdating = isUpdatingProvider && updatingProvider == "AniList",
                    enabled = !isUpdatingProvider && newTokenInput.isNotBlank(),
                    onClick = { onUpdateProvider("AniList") },
                    modifier = Modifier.weight(1f)
                )
                ProviderTokenButton(
                    label = "MAL",
                    isUpdating = isUpdatingProvider && updatingProvider == "Mal",
                    enabled = !isUpdatingProvider && newTokenInput.isNotBlank(),
                    onClick = { onUpdateProvider("Mal") },
                    modifier = Modifier.weight(1f)
                )
                ProviderTokenButton(
                    label = "Google",
                    isUpdating = isUpdatingProvider && updatingProvider == "GoogleBooks",
                    enabled = !isUpdatingProvider && newTokenInput.isNotBlank(),
                    onClick = { onUpdateProvider("GoogleBooks") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Error/success messages
            AnimatedVisibility(visible = tokenUpdateError != null) {
                Text(
                    tokenUpdateError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            AnimatedVisibility(visible = tokenUpdateSuccess) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Token updated successfully",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderTokenButton(
    label: String,
    isUpdating: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        if (isUpdating) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HoldsSection(
    holds: List<Int>,
    isLoading: Boolean,
    onToggleHold: (Int) -> Unit
) {
    Text(
        text = "Scrobble Holds",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading holds...")
            }
        } else if (holds.isEmpty()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "No scrobble holds — all series are scrobbling",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "${holds.size} series on hold (scrobbling paused)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                holds.forEach { seriesId ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Series #$seriesId",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { onToggleHold(seriesId) }
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrobbleErrorCard(
    error: com.mimiral.app.data.remote.kavita.KavitaScrobbleError,
    isRetrying: Boolean,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    error.seriesName ?: "Series #${error.seriesId}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    error.scrobbleProvider ?: "Unknown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }

            Text(
                error.errorMessage ?: "Unknown error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )

            if (error.createdDate != null) {
                Text(
                    "Failed: ${error.createdDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)
                )
            }

            FilledTonalButton(
                onClick = onRetry,
                enabled = !isRetrying
            ) {
                if (isRetrying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retrying...", style = MaterialTheme.typography.labelSmall)
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
