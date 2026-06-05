package com.mimiral.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import com.mimiral.app.data.local.settings.SyncInterval
import com.mimiral.app.data.local.settings.SyncSettingsRepository
import com.mimiral.app.ui.theme.MimiralThemeSwitcher
import com.mimiral.app.ui.theme.MimiralThemeType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToKavitaSetup: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsRepository = remember { ReaderSettingsRepository(context) }
    val syncSettingsRepository = remember { SyncSettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settings.collectAsState(
        initial = com.mimiral.app.data.local.settings.ReaderSettings()
    )
    val syncSettings by syncSettingsRepository.settings.collectAsState(
        initial = com.mimiral.app.data.local.settings.SyncSettings()
    )

    val currentTheme = try {
        MimiralThemeType.valueOf(settings.themeName)
    } catch (_: IllegalArgumentException) {
        MimiralThemeType.DAY
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
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

            // ── Sync Preferences Section ──────────────────────
            Text(
                text = "Sync Preferences",
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
                    // Auto-sync toggle
                    SyncPreferenceRow(
                        icon = Icons.Default.CloudSync,
                        title = "Auto-sync",
                        subtitle = "Automatically sync reading progress with Kavita",
                        checked = syncSettings.autoSyncEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                syncSettingsRepository.setAutoSyncEnabled(enabled)
                            }
                        }
                    )

                    // Sync interval selector (only when auto-sync is enabled)
                    AnimatedVisibility(
                        visible = syncSettings.autoSyncEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            SyncIntervalSelector(
                                selectedInterval = syncSettings.syncInterval,
                                onIntervalSelected = { interval ->
                                    scope.launch {
                                        syncSettingsRepository.setSyncInterval(interval)
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Wi-Fi only toggle
                    SyncPreferenceRow(
                        icon = Icons.Default.Wifi,
                        title = "Wi-Fi only",
                        subtitle = "Only sync when connected to Wi-Fi",
                        checked = syncSettings.syncOnWifiOnly,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                syncSettingsRepository.setSyncOnWifiOnly(enabled)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))

                    // Content sync toggles
                    Text(
                        text = "Sync Content",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    SyncPreferenceRow(
                        icon = null,
                        title = "Reading progress",
                        subtitle = "Sync current page and chapter",
                        checked = syncSettings.syncReadingProgress,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                syncSettingsRepository.setSyncReadingProgress(enabled)
                            }
                        }
                    )

                    SyncPreferenceRow(
                        icon = null,
                        title = "Bookmarks",
                        subtitle = "Sync bookmarks with Kavita",
                        checked = syncSettings.syncBookmarks,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                syncSettingsRepository.setSyncBookmarks(enabled)
                            }
                        }
                    )

                    SyncPreferenceRow(
                        icon = Icons.Default.Highlight,
                        title = "Highlights & Notes",
                        subtitle = "Sync highlights and notes",
                        checked = syncSettings.syncHighlights,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                syncSettingsRepository.setSyncHighlights(enabled)
                            }
                        }
                    )
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
private fun SyncPreferenceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SyncIntervalSelector(
    selectedInterval: SyncInterval,
    onIntervalSelected: (SyncInterval) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sync interval",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "How often to auto-sync in the background",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            Surface(
                modifier = Modifier
                    .clickable { expanded = true },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = selectedInterval.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                SyncInterval.entries.forEach { interval ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = interval.displayName,
                                fontWeight = if (interval == selectedInterval) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        },
                        onClick = {
                            onIntervalSelected(interval)
                            expanded = false
                        }
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
