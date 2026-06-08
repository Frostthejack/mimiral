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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mimiral.app.data.local.settings.LibrarySettingsRepository
import com.mimiral.app.data.local.settings.MarginWidth
import com.mimiral.app.data.local.settings.ParagraphSpacing
import com.mimiral.app.data.local.settings.ReadingModeSettingsRepository
import com.mimiral.app.data.local.settings.ReadingModeTheme
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import com.mimiral.app.data.local.settings.ProgressSyncMode
import com.mimiral.app.data.local.settings.SyncInterval
import com.mimiral.app.data.local.settings.SyncSettingsRepository
import com.mimiral.app.data.local.settings.TTSSettingsRepository
import com.mimiral.app.data.local.settings.TtsHighlightColor
import com.mimiral.app.ui.theme.MimiralThemeSwitcher
import com.mimiral.app.ui.theme.MimiralThemeType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToKavitaSetup: () -> Unit = {},
    onNavigateToReadingPreferences: () -> Unit = {},
    onNavigateToTTSSettings: () -> Unit = {},
    onNavigateToAccessibilitySettings: () -> Unit = {},
    onNavigateToLibraryPreferences: () -> Unit = {},
    onNavigateToGestureSettings: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    backupRestoreViewModel: BackupRestoreViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsRepository = remember { ReaderSettingsRepository(context) }
    val syncSettingsRepository = remember { SyncSettingsRepository(context) }
    val scope = rememberCoroutineScope()

    // Repositories
    val readerSettingsRepo = remember { ReaderSettingsRepository(context) }
    val librarySettingsRepo = remember { LibrarySettingsRepository(context) }
    val ttsSettingsRepo = remember { TTSSettingsRepository(context) }
    val syncSettingsRepo = remember { SyncSettingsRepository(context) }
    val readingModeSettingsRepo = remember { ReadingModeSettingsRepository(context) }

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
    val syncSettings by syncSettingsRepository.settings.collectAsState(
        initial = com.mimiral.app.data.local.settings.SyncSettings()
    )
    val readingModeSettings by readingModeSettingsRepo.settings.collectAsState(
        initial = com.mimiral.app.data.local.settings.ReadingModeSettings()
    )
    val backupState by backupRestoreViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentTheme = try {
        MimiralThemeType.valueOf(readerSettings.themeName)
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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "Open navigation menu"
                        )
                    }
                }
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

// ── Reading Section ───────────────────────────────
            Text(
                text = "Reading",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // ═══════════════════════════════════════════════════
            // SECTION: Reading Mode
            // ═══════════════════════════════════════════════════
            SectionHeader(
                title = "Reading Mode",
                icon = Icons.Default.Book
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Default Reader Mode
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Default Reader Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ReadingModeEnumSelector(
                            options = com.mimiral.app.data.local.settings.DefaultReaderMode.entries,
                            selected = readingModeSettings.defaultReaderMode,
                            onSelect = { scope.launch { readingModeSettingsRepo.setDefaultReaderMode(it) } },
                            labelFor = { it.displayName }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Font family selector
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Font Family",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ReadingModeEnumSelector(
                            options = com.mimiral.app.ui.reader.ReaderFontFamily.entries,
                            selected = com.mimiral.app.ui.reader.ReaderFontFamily.entries.find {
                                it.name == readingModeSettings.fontFamily
                            } ?: com.mimiral.app.ui.reader.ReaderFontFamily.DEFAULT,
                            onSelect = { scope.launch { readingModeSettingsRepo.setFontFamily(it.name) } },
                            labelFor = { it.displayName }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Font size slider (12–32sp)
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
                                    Icons.Default.FormatSize,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Font Size",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "${readingModeSettings.fontSize}sp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = readingModeSettings.fontSize.toFloat(),
                            onValueChange = { size ->
                                scope.launch { readingModeSettingsRepo.setFontSize(size.toInt()) }
                            },
                            valueRange = 12f..32f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("12sp", style = MaterialTheme.typography.labelSmall)
                            Text("32sp", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Line spacing slider (1.0–2.0x)
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
                                    text = "Line Spacing",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = String.format("%.1fx", readingModeSettings.lineSpacing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = readingModeSettings.lineSpacing,
                            onValueChange = { spacing ->
                                scope.launch { readingModeSettingsRepo.setLineSpacing(spacing) }
                            },
                            valueRange = 1.0f..2.0f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1.0x", style = MaterialTheme.typography.labelSmall)
                            Text("2.0x", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Paragraph spacing
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Paragraph Spacing",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ReadingModeEnumSelector(
                            options = ParagraphSpacing.entries,
                            selected = readingModeSettings.paragraphSpacing,
                            onSelect = { scope.launch { readingModeSettingsRepo.setParagraphSpacing(it) } },
                            labelFor = { it.displayName }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Margins
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Margins",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ReadingModeEnumSelector(
                            options = MarginWidth.entries,
                            selected = readingModeSettings.margins,
                            onSelect = { scope.launch { readingModeSettingsRepo.setMargins(it) } },
                            labelFor = { it.displayName }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Reading Mode Theme
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Theme",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ReadingModeEnumSelector(
                            options = ReadingModeTheme.entries,
                            selected = readingModeSettings.theme,
                            onSelect = { scope.launch { readingModeSettingsRepo.setTheme(it) } },
                            labelFor = { it.displayName }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // TTS Highlight Color
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Highlight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "TTS Highlight Color",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ReadingModeEnumSelector(
                            options = TtsHighlightColor.entries,
                            selected = readingModeSettings.ttsHighlightColor,
                            onSelect = { scope.launch { readingModeSettingsRepo.setTtsHighlightColor(it) } },
                            labelFor = { it.displayName }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Auto-scroll TTS
                    SettingsToggleRow(
                        title = "Auto-Scroll During TTS",
                        description = "Automatically scroll to follow TTS playback",
                        icon = Icons.Default.Speed,
                        checked = readingModeSettings.autoScrollTTS,
                        onCheckedChange = { enabled ->
                            scope.launch { readingModeSettingsRepo.setAutoScrollTTS(enabled) }
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

            // TTS navigation card - opens full TTS settings screen
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
                            .clickable { onNavigateToTTSSettings() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TTS Preferences",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Voice, speed, and pitch settings",
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

            // Gesture navigation card
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
                            .clickable { onNavigateToGestureSettings() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gesture Customization",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Tap zones, swipe sensitivity, haptic feedback",
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
                            selectedMinutes = syncSettings.syncInterval.minutes,
                            onIntervalSelected = { minutes ->
                                scope.launch {
                                    syncSettingsRepo.setSyncInterval(
                                        SyncInterval.fromMinutes(minutes)
                                    )
                                }
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
            }

            // ═══════════════════════════════════════════════════
            // SECTION: Reading Preferences (Navigation)
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
                            Icons.Default.FormatSize,
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

            // ── Library Section ───────────────────────────────
            // ── Theme Preview Section ─────────────────────────
            Text(
                text = "Library",
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
                            .clickable { onNavigateToLibraryPreferences() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scan Folders & Formats",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Configure which folders to scan " +
                                    "and file formats to include",
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
            // SECTION: Sync Preferences (Enhanced)
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
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Auto-sync toggle
                    SettingsToggleRow(
                        title = "Auto-Sync",
                        description = "Automatically sync with connected servers",
                        icon = Icons.Default.Sync,
                        checked = syncSettings.autoSyncEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { syncSettingsRepository.setAutoSyncEnabled(enabled) }
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
                        SyncIntervalSelector(
                            selectedInterval = syncSettings.syncInterval,
                            onIntervalSelected = { interval ->
                                scope.launch { syncSettingsRepository.setSyncInterval(interval) }
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
                            scope.launch { syncSettingsRepository.setSyncOnWifiOnly(enabled) }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Progress sync mode selector
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Progress Sync Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (syncSettings.progressSyncMode) {
                                ProgressSyncMode.NATIVE -> "JWT auth — full fidelity (recommended)"
                                ProgressSyncMode.PANELS -> "API key fallback — no JWT required"
                                ProgressSyncMode.KOREADER -> "KOReader interop for e-ink devices"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ReadingModeEnumSelector(
                            options = ProgressSyncMode.entries,
                            selected = syncSettings.progressSyncMode,
                            onSelect = { mode ->
                                scope.launch { syncSettingsRepository.setProgressSyncMode(mode) }
                            },
                            labelFor = { it.displayName }
                        )
                    }

                    // KOReader device ID (only visible when mode is KOREADER)
                    if (syncSettings.progressSyncMode == ProgressSyncMode.KOREADER) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "KOReader Device ID",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Identifies this device to Kavita and KOReader sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            var deviceIdText by remember(
                                syncSettings.koreaderDeviceId
                            ) {
                                mutableStateOf(syncSettings.koreaderDeviceId)
                            }
                            OutlinedTextField(
                                value = deviceIdText,
                                onValueChange = { newText ->
                                    deviceIdText = newText
                                },
                                label = { Text("Device ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    if (deviceIdText != syncSettings.koreaderDeviceId) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    syncSettingsRepository
                                                        .setKoreaderDeviceId(deviceIdText)
                                                }
                                            }
                                        ) {
                                            Text("Save")
                                        }
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Content sync toggles
                    Text(
                        text = "Sync Content",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    SettingsToggleRow(
                        title = "Reading Progress",
                        description = "Sync current page and chapter",
                        icon = Icons.Default.Sync,
                        checked = syncSettings.syncReadingProgress,
                        onCheckedChange = { enabled ->
                            scope.launch { syncSettingsRepository.setSyncReadingProgress(enabled) }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingsToggleRow(
                        title = "Bookmarks",
                        description = "Sync bookmarks with Kavita",
                        icon = Icons.Default.Book,
                        checked = syncSettings.syncBookmarks,
                        onCheckedChange = { enabled ->
                            scope.launch { syncSettingsRepository.setSyncBookmarks(enabled) }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingsToggleRow(
                        title = "Highlights & Notes",
                        description = "Sync highlights and notes",
                        icon = Icons.Default.Highlight,
                        checked = syncSettings.syncHighlights,
                        onCheckedChange = { enabled ->
                            scope.launch { syncSettingsRepository.setSyncHighlights(enabled) }
                        }
                    )
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
                        text = "Export your reading preferences, theme, " +
                            "and library settings to a file. " +
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

            // ═══════════════════════════════════════════════════
            // SECTION: Accessibility
            // ═══════════════════════════════════════════════════
            SectionHeader(
                title = "Accessibility",
                icon = Icons.Default.Accessibility
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
                            .clickable { onNavigateToAccessibilitySettings() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Accessibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Accessibility Settings",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "High contrast, font size, TalkBack, and more",
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

// ═══════════════════════════════════════════════════════════
// Reusable Components
// ═══════════════════════════════════════════════════════════

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

@Composable
private fun <T> ReadingModeEnumSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelFor: (T) -> String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(option) },
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelFor(option),
                        style = MaterialTheme.typography.bodySmall,
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
