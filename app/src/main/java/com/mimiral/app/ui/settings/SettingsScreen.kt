package com.mimiral.app.ui.settings

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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.mimiral.app.data.local.settings.LibrarySettingsRepository
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import com.mimiral.app.data.local.settings.SyncSettingsRepository
import com.mimiral.app.data.local.settings.TTSSettingsRepository
import com.mimiral.app.ui.theme.MimiralThemeSwitcher
import com.mimiral.app.ui.theme.MimiralThemeType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToKavitaSetup: () -> Unit = {},
    onNavigateToReadingPreferences: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Repositories
    val readerSettingsRepo = remember { ReaderSettingsRepository(context) }
    val librarySettingsRepo = remember { LibrarySettingsRepository(context) }
    val ttsSettingsRepo = remember { TTSSettingsRepository(context) }
    val syncSettingsRepo = remember { SyncSettingsRepository(context) }

    // State flows
    val readerSettings by readerSettingsRepo.settings.collectAsState(
        initial = com.mimiral.app.data.local.settings.ReaderSettings()
    )
    val librarySettings by librarySettingsRepo.settings.collectAsState(
        initial = com.mimiral.app.data.local.settings.LibrarySettings()
    )
    val ttsSettings by ttsSettingsRepo.settings.collectAsState(
        initial = com.mimiral.app.data.local.settings.TTSSettings()
    )
    val syncSettings by syncSettingsRepo.settings.collectAsState(
        initial = com.mimiral.app.data.local.settings.SyncSettings()
    )

    val currentTheme = try {
        MimiralThemeType.valueOf(readerSettings.themeName)
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
            // ═══════════════════════════════════════════════════
            // SECTION: Reading Preferences
            // ═══════════════════════════════════════════════════
            SectionHeader(
                title = "Reading Preferences",
                icon = Icons.Default.FormatSize
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Volume key navigation
                    SettingsToggleRow(
                        title = "Volume Key Navigation",
                        description = "Use volume keys to turn pages",
                        icon = Icons.Default.Keyboard,
                        checked = readerSettings.volumeKeyNavigationEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                readerSettingsRepo.setVolumeKeyNavigation(enabled)
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Swap volume key direction
                    SettingsToggleRow(
                        title = "Swap Volume Keys",
                        description = "Reverse the direction of volume key page turns",
                        icon = Icons.Default.Tune,
                        checked = readerSettings.volumeKeyDirectionSwapped,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                readerSettingsRepo.setVolumeKeyDirectionSwapped(enabled)
                            }
                        }
                    )
                }
            }

            // ═══════════════════════════════════════════════════
            // SECTION: Appearance
            // ═══════════════════════════════════════════════════
            SectionHeader(
                title = "Appearance",
                icon = Icons.Default.Palette
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                readerSettingsRepo.setTheme(theme.name)
                            }
                        }
                    )
                }
            }

            // ═══════════════════════════════════════════════════
            // SECTION: TTS Preferences
            // ═══════════════════════════════════════════════════
            SectionHeader(
                title = "Text-to-Speech",
                icon = Icons.Default.RecordVoiceOver
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // TTS enabled toggle
                    SettingsToggleRow(
                        title = "Enable TTS",
                        description = "Allow text-to-speech reading",
                        icon = Icons.Default.RecordVoiceOver,
                        checked = ttsSettings.isTTSEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { ttsSettingsRepo.setTTSEnabled(enabled) }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Auto-play on open
                    SettingsToggleRow(
                        title = "Auto-Play on Open",
                        description = "Start TTS automatically when opening a book",
                        icon = Icons.Default.RecordVoiceOver,
                        checked = ttsSettings.autoPlayOnOpen,
                        onCheckedChange = { enabled ->
                            scope.launch { ttsSettingsRepo.setAutoPlayOnOpen(enabled) }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Highlight while reading
                    SettingsToggleRow(
                        title = "Highlight While Reading",
                        description = "Highlight the current sentence during TTS playback",
                        icon = Icons.Default.Palette,
                        checked = ttsSettings.highlightWhileReading,
                        onCheckedChange = { enabled ->
                            scope.launch { ttsSettingsRepo.setHighlightWhileReading(enabled) }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Speech rate slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Speech Rate",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = String.format("%.1fx", ttsSettings.speechRate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = ttsSettings.speechRate,
                            onValueChange = { rate ->
                                scope.launch { ttsSettingsRepo.setSpeechRate(rate) }
                            },
                            valueRange = 0.5f..3.0f,
                            steps = 24,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0.5x", style = MaterialTheme.typography.labelSmall)
                            Text("3.0x", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Pitch slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Pitch",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = String.format("%.1fx", ttsSettings.pitch),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = ttsSettings.pitch,
                            onValueChange = { pitch ->
                                scope.launch { ttsSettingsRepo.setPitch(pitch) }
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 14,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0.5x", style = MaterialTheme.typography.labelSmall)
                            Text("2.0x", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Sleep timer default
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Default Sleep Timer",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SleepTimerDropdown(
                            selectedMinutes = ttsSettings.sleepTimerDefaultMinutes,
                            onMinutesSelected = { minutes ->
                                scope.launch {
                                    ttsSettingsRepo.setSleepTimerDefaultMinutes(minutes)
                                }
                            }
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════
            // SECTION: Sync Preferences
            // ═══════════════════════════════════════════════════
            SectionHeader(
                title = "Sync Preferences",
                icon = Icons.Default.CloudSync
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Auto-sync toggle
                    SettingsToggleRow(
                        title = "Auto-Sync",
                        description = "Automatically sync with connected servers",
                        icon = Icons.Default.Sync,
                        checked = syncSettings.autoSyncEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { syncSettingsRepo.setAutoSyncEnabled(enabled) }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Sync interval
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Sync Interval",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SyncIntervalDropdown(
                            selectedMinutes = syncSettings.syncIntervalMinutes,
                            onIntervalSelected = { minutes ->
                                scope.launch { syncSettingsRepo.setSyncInterval(minutes) }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // WiFi only toggle
                    SettingsToggleRow(
                        title = "Sync on Wi-Fi Only",
                        description = "Only sync when connected to Wi-Fi",
                        icon = Icons.Default.Wifi,
                        checked = syncSettings.syncOnWifiOnly,
                        onCheckedChange = { enabled ->
                            scope.launch { syncSettingsRepo.setSyncOnWifiOnly(enabled) }
                        }
                    )
                }
            )

            // ═══════════════════════════════════════════════════
            // SECTION: Reading Preferences (Navigation)
            // ═══════════════════════════════════════════════════
            SectionHeader(
                title = "Reading Preferences",
                icon = Icons.Default.Book
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
                            .clickable { onNavigateToReadingPreferences() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reading Preferences",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Default font, theme, and page turn animation",
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

            // ═══════════════════════════════════════════════════
            // SECTION: Library Preferences
            // ═══════════════════════════════════════════════════
            SectionHeader(
                title = "Library Preferences",
                icon = Icons.Default.LibraryBooks
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Default sort option
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.LibraryBooks,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Default Sort",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SortOptionDropdown(
                            selectedSort = librarySettings.sortOption,
                            onSortSelected = { sort ->
                                scope.launch { librarySettingsRepo.setSortOption(sort) }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Default filter option
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Default Filter",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FilterOptionDropdown(
                            selectedFilter = librarySettings.filterOption,
                            onFilterSelected = { filter ->
                                scope.launch { librarySettingsRepo.setFilterOption(filter) }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // View mode
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "View Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ViewModeSelector(
                            selectedMode = librarySettings.viewMode,
                            onModeSelected = { mode ->
                                scope.launch { librarySettingsRepo.setViewMode(mode) }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Supported file formats info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Supported Formats",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "EPUB, PDF, DJVU, TXT, RTF, FB2, MOBI, CBZ, CBR",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════
            // SECTION: Cloud & Sync (Kavita)
            // ═══════════════════════════════════════════════════
            SectionHeader(
                title = "Cloud & Sync",
                icon = Icons.Default.Cloud
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

            // ═══════════════════════════════════════════════════
            // SECTION: About
            // ═══════════════════════════════════════════════════
            HorizontalDivider()
            SectionHeader(
                title = "About",
                icon = Icons.Default.Settings
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

            // Bottom spacer
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
}

// ═══════════════════════════════════════════════════════════
// Reusable Components
// ═══════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerDropdown(
    selectedMinutes: Int,
    onMinutesSelected: (Int) -> Unit
) {
    val options = listOf(0, 5, 10, 15, 30, 45, 60, 90, 120)
    val labels = options.map { minutes ->
        when (minutes) {
            0 -> "Off"
            60 -> "1 hour"
            120 -> "2 hours"
            else -> "$minutes min"
        }
    }
    val selectedIndex = options.indexOf(selectedMinutes).coerceAtLeast(0)
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = labels[selectedIndex],
            onValueChange = {},
            readOnly = true,
            label = { Text("Sleep Timer") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, minutes ->
                DropdownMenuItem(
                    text = { Text(labels[index]) },
                    onClick = {
                        onMinutesSelected(minutes)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncIntervalDropdown(
    selectedMinutes: Int,
    onIntervalSelected: (Int) -> Unit
) {
    val options = listOf(15, 30, 60, 120, 360, 720, 1440)
    val labels = options.map { minutes ->
        when {
            minutes < 60 -> "$minutes minutes"
            minutes == 60 -> "1 hour"
            minutes < 1440 -> "${minutes / 60} hours"
            else -> "24 hours"
        }
    }
    val selectedIndex = options.indexOf(selectedMinutes).coerceAtLeast(0)
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = labels[selectedIndex],
            onValueChange = {},
            readOnly = true,
            label = { Text("Sync Interval") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, minutes ->
                DropdownMenuItem(
                    text = { Text(labels[index]) },
                    onClick = {
                        onIntervalSelected(minutes)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortOptionDropdown(
    selectedSort: com.mimiral.app.data.local.settings.SortOption,
    onSortSelected: (com.mimiral.app.data.local.settings.SortOption) -> Unit
) {
    val options = com.mimiral.app.data.local.settings.SortOption.entries
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedSort.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sort By") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onSortSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterOptionDropdown(
    selectedFilter: com.mimiral.app.data.local.settings.FilterOption,
    onFilterSelected: (com.mimiral.app.data.local.settings.FilterOption) -> Unit
) {
    val options = com.mimiral.app.data.local.settings.FilterOption.entries
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedFilter.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Filter By") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onFilterSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ViewModeSelector(
    selectedMode: com.mimiral.app.data.local.settings.ViewMode,
    onModeSelected: (com.mimiral.app.data.local.settings.ViewMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        com.mimiral.app.data.local.settings.ViewMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            val label = when (mode) {
                com.mimiral.app.data.local.settings.ViewMode.GRID -> "Grid"
                com.mimiral.app.data.local.settings.ViewMode.LIST -> "List"
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onModeSelected(mode) },
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}
