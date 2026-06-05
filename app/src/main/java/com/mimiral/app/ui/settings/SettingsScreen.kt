package com.mimiral.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import com.mimiral.app.ui.theme.MimiralThemeSwitcher
import com.mimiral.app.ui.theme.MimiralThemeType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToKavitaSetup: () -> Unit = {},
    backupRestoreViewModel: BackupRestoreViewModel = viewModel()
) {
    val context = LocalContext.current
    val settingsRepository = remember { ReaderSettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settings.collectAsState(
        initial = com.mimiral.app.data.local.settings.ReaderSettings()
    )
    val backupState by backupRestoreViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentTheme = try {
        MimiralThemeType.valueOf(settings.themeName)
    } catch (_: IllegalArgumentException) {
        MimiralThemeType.DAY
    }

    // SAF file picker for restore
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { backupRestoreViewModel.restoreFromUri(it) }
    }

    // Show snackbar messages for backup/restore results
    LaunchedEffect(backupState) {
        when {
            backupState.backupSuccess -> {
                snackbarHostState.showSnackbar("Settings backed up successfully")
                backupRestoreViewModel.clearMessage()
            }
            backupState.restoreSuccess -> {
                snackbarHostState.showSnackbar("Settings restored successfully")
                backupRestoreViewModel.clearMessage()
            }
            backupState.errorMessage != null -> {
                snackbarHostState.showSnackbar(backupState.errorMessage ?: "Error")
                backupRestoreViewModel.clearMessage()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Appearance Section ────────────────────────────
            Text(
                text = "Appearance",
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
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Reading Theme",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Choose a theme for your reading experience. " +
                            "Changes apply immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    MimiralThemeSwitcher(
                        currentTheme = currentTheme,
                        onThemeSelected = { theme ->
                            scope.launch {
                                settingsRepository.setTheme(theme.name)
                            }
                        }
                    )
                }
            }

            // ── Theme Preview Section ─────────────────────────
            Text(
                text = "Theme Preview",
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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemePreviewItem(
                        "Day",
                        MimiralThemeType.DAY,
                        currentTheme == MimiralThemeType.DAY
                    )
                    ThemePreviewItem(
                        "Sepia",
                        MimiralThemeType.SEPIA,
                        currentTheme == MimiralThemeType.SEPIA
                    )
                    ThemePreviewItem(
                        "Dark",
                        MimiralThemeType.DARK,
                        currentTheme == MimiralThemeType.DARK
                    )
                    ThemePreviewItem(
                        "Night",
                        MimiralThemeType.NIGHT,
                        currentTheme == MimiralThemeType.NIGHT
                    )
                }
            }

            // ── Cloud & Sync Section ──────────────────────────
            Text(
                text = "Cloud & Sync",
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
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToKavitaSetup() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kavita Server",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Connect to a Kavita server for cloud sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Backup & Restore Section ──────────────────────
            Text(
                text = "Backup & Restore",
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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Export your reading preferences, theme, and library settings to a file. " +
                            "You can restore them later or on a new device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Backup button
                        Button(
                            onClick = { backupRestoreViewModel.backupSettings() },
                            modifier = Modifier.weight(1f),
                            enabled = !backupState.isBackingUp && !backupState.isRestoring
                        ) {
                            if (backupState.isBackingUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    Icons.Default.SaveAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Backup")
                        }

                        // Share button (only enabled after a successful backup)
                        OutlinedButton(
                            onClick = { backupRestoreViewModel.shareBackup() },
                            modifier = Modifier.weight(1f),
                            enabled = backupState.lastBackupFile != null &&
                                !backupState.isBackingUp && !backupState.isRestoring
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Share")
                        }
                    }

                    // Restore button
                    OutlinedButton(
                        onClick = {
                            restoreLauncher.launch(arrayOf("application/json"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !backupState.isBackingUp && !backupState.isRestoring
                    ) {
                        if (backupState.isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Restore from file...")
                    }

                    // Last backup info
                    if (backupState.lastBackupFile != null) {
                        Text(
                            text = "Last backup: ${backupState.lastBackupFile}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── About Section ─────────────────────────────────
            HorizontalDivider()
            Text(
                text = "About",
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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Mimiral",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Version 0.1.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewItem(
    name: String,
    themeType: MimiralThemeType,
    isSelected: Boolean
) {
    val bgColor = when (themeType) {
        MimiralThemeType.DAY -> com.mimiral.app.ui.theme.DayBackground
        MimiralThemeType.SEPIA -> com.mimiral.app.ui.theme.SepiaBackground
        MimiralThemeType.DARK -> com.mimiral.app.ui.theme.DarkBackground
        MimiralThemeType.NIGHT -> com.mimiral.app.ui.theme.NightBackground
    }
    val textColor = when (themeType) {
        MimiralThemeType.DAY -> com.mimiral.app.ui.theme.DayOnBackground
        MimiralThemeType.SEPIA -> com.mimiral.app.ui.theme.SepiaOnBackground
        MimiralThemeType.DARK -> com.mimiral.app.ui.theme.DarkOnBackground
        MimiralThemeType.NIGHT -> com.mimiral.app.ui.theme.NightOnBackground
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Color swatch
        Surface(
            modifier = Modifier.size(32.dp),
            shape = MaterialTheme.shapes.small,
            color = bgColor,
            border = ButtonDefaults.outlinedButtonBorder
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aa",
                    color = textColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        if (isSelected) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Active",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
