package com.mimiral.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mimiral.app.data.local.settings.AccessibilitySettings
import com.mimiral.app.data.local.settings.AccessibilitySettingsRepository
import com.mimiral.app.ui.theme.MimiralThemeSwitcher
import com.mimiral.app.ui.theme.MimiralThemeType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AccessibilitySettingsRepository(context) }
    val settings by repo.settings.collectAsState(initial = AccessibilitySettings())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Accessibility",
                        modifier = Modifier.semantics {
                            contentDescription = "Accessibility settings"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
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
            // SECTION: Display
            // ═══════════════════════════════════════════════════
            AccessibilitySectionHeader(
                title = "Display",
                icon = Icons.Default.Contrast
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // High contrast mode
                    AccessibilityToggleRow(
                        title = "High Contrast",
                        description = "Increase contrast between text and " +
                            "background for better readability",
                        icon = Icons.Default.Contrast,
                        checked = settings.highContrastEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { repo.setHighContrast(enabled) }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Font scale
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.TextFields,
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Adjust text size across the app for better readability",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FontScaleSlider(
                            scale = settings.fontScaleMultiplier,
                            onScaleChange = { scale ->
                                scope.launch { repo.setFontScale(scale) }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Bold text
                    AccessibilityToggleRow(
                        title = "Bold Text",
                        description = "Use bold font weight throughout the app",
                        icon = Icons.Default.FormatBold,
                        checked = settings.boldTextEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { repo.setBoldText(enabled) }
                        }
                    )
                }
            }

            // ═══════════════════════════════════════════════════
            // SECTION: Screen Reader
            // ═══════════════════════════════════════════════════
            AccessibilitySectionHeader(
                title = "Screen Reader",
                icon = Icons.Default.Hearing
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // TalkBack optimization
                    AccessibilityToggleRow(
                        title = "TalkBack Optimization",
                        description = "Enhance navigation and descriptions for screen readers",
                        icon = Icons.Default.Hearing,
                        checked = settings.talkBackOptimized,
                        onCheckedChange = { enabled ->
                            scope.launch { repo.setTalkBackOptimized(enabled) }
                        }
                    )
                }
            }

            // ═══════════════════════════════════════════════════
            // SECTION: Theme Override
            // ═══════════════════════════════════════════════════
            AccessibilitySectionHeader(
                title = "Theme Override",
                icon = Icons.Default.FormatSize
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Choose a theme. High contrast themes " +
                            "are recommended for users with low vision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val readerSettingsRepo = remember {
                        com.mimiral.app.data.local.settings.ReaderSettingsRepository(context)
                    }
                    val readerSettings by readerSettingsRepo.settings.collectAsState(
                        initial = com.mimiral.app.data.local.settings.ReaderSettings()
                    )
                    val currentTheme = remember(readerSettings.themeName) {
                        try {
                            MimiralThemeType.valueOf(readerSettings.themeName)
                        } catch (_: IllegalArgumentException) {
                            MimiralThemeType.DAY
                        }
                    }
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

            // Bottom spacer
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AccessibilitySectionHeader(
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
private fun AccessibilityToggleRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics {
                contentDescription = "$title, ${if (checked) "enabled" else "disabled"}"
            },
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
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics {
                contentDescription = "$title toggle"
            }
        )
    }
}

@Composable
private fun FontScaleSlider(
    scale: Float,
    onScaleChange: (Float) -> Unit
) {
    val scalePercent = (scale * 100).toInt()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "A",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.semantics { contentDescription = "Small text example" }
        )
        Slider(
            value = scale,
            onValueChange = onScaleChange,
            valueRange = 0.75f..2.0f,
            steps = 4,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = "Font size slider, currently $scalePercent percent"
                }
        )
        Text(
            text = "A",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { contentDescription = "Large text example" }
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("75%", style = MaterialTheme.typography.labelSmall)
        Text(
            "$scalePercent%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text("200%", style = MaterialTheme.typography.labelSmall)
    }
}
