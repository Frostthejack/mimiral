package com.mimiral.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mimiral.app.data.local.settings.ReaderSettings
import com.mimiral.app.data.local.settings.ReaderSettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureSettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsRepository = remember { ReaderSettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settings.collectAsState(
        initial = ReaderSettings()
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gesture Customization") }
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
            // ── Tap Zones Section ──────────────────────────────
            Text(
                text = "Tap Zones",
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Tap zone visual preview
                    TapZonePreview(settings = settings)

                    Spacer(modifier = Modifier.height(4.dp))

                    // Tap zone size slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tap Zone Size",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${settings.tapZoneSize}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = settings.tapZoneSize.toFloat(),
                            onValueChange = { value ->
                                scope.launch {
                                    settingsRepository.setTapZoneSize(value.toInt())
                                }
                            },
                            valueRange = 10f..50f,
                            steps = 39,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "10%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "50%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Width of the left/right screen areas " +
                                "that respond to taps for page turning.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Zone Actions Section ───────────────────────────
            Text(
                text = "Zone Actions",
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
                    // Left zone toggle
                    ZoneActionRow(
                        label = "Left Zone",
                        description = "Tap left side to go to previous page",
                        checked = settings.tapZoneLeftEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsRepository.setTapZoneLeftEnabled(enabled)
                            }
                        }
                    )

                    // Right zone toggle
                    ZoneActionRow(
                        label = "Right Zone",
                        description = "Tap right side to go to next page",
                        checked = settings.tapZoneRightEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsRepository.setTapZoneRightEnabled(enabled)
                            }
                        }
                    )

                    // Center zone toggle
                    ZoneActionRow(
                        label = "Center Zone",
                        description = "Tap center to toggle toolbar",
                        checked = settings.tapZoneCenterEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsRepository.setTapZoneCenterEnabled(enabled)
                            }
                        }
                    )
                }
            }

            // ── Invert Tap Zones ───────────────────────────────
            Text(
                text = "Tap Direction",
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Invert Tap Zones",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (settings.tapZonesInverted) {
                                    "Left = Next page, Right = Previous page"
                                } else {
                                    "Left = Previous page, Right = Next page"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.tapZonesInverted,
                            onCheckedChange = { inverted ->
                                scope.launch {
                                    settingsRepository.setTapZonesInverted(inverted)
                                }
                            }
                        )
                    }
                }
            }

            // ── Haptic Feedback ────────────────────────────────
            Text(
                text = "Feedback",
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Vibration,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Haptic Feedback",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Vibrate on page turn",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.hapticFeedbackEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsRepository.setHapticFeedbackEnabled(enabled)
                                }
                            }
                        )
                    }
                }
            }

            // ── Swipe Sensitivity ──────────────────────────────
            Text(
                text = "Swipe",
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Swipe Sensitivity",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${settings.swipeSensitivity}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = settings.swipeSensitivity.toFloat(),
                        onValueChange = { value ->
                            scope.launch {
                                settingsRepository.setSwipeSensitivity(value.toInt())
                            }
                        },
                        valueRange = 10f..100f,
                        steps = 89,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Low",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "High",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "How sensitive swipe gestures are for page turning.",
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

@Composable
private fun TapZonePreview(settings: ReaderSettings) {
    val zoneFraction = settings.tapZoneSize / 100f
    val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    Column {
        Text(
            text = "Preview",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .border(
                    BorderStroke(1.dp, borderColor),
                    RoundedCornerShape(8.dp)
                )
        ) {
            // Left zone
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(zoneFraction)
                    .align(Alignment.CenterStart)
                    .background(
                        if (settings.tapZoneLeftEnabled) activeColor else inactiveColor,
                        RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◀ Prev",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (settings.tapZoneLeftEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            }

            // Right zone
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(zoneFraction)
                    .align(Alignment.CenterEnd)
                    .background(
                        if (settings.tapZoneRightEnabled) activeColor else inactiveColor,
                        RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Next ▶",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (settings.tapZoneRightEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            }

            // Center zone
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = with(androidx.compose.ui.platform.LocalDensity.current) {
                            (zoneFraction * 300).dp
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (settings.tapZoneCenterEnabled) {
                    Text(
                        text = "☰ Menu",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoneActionRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
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
