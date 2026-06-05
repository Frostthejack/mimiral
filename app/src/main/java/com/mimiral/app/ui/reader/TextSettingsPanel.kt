package com.mimiral.app.ui.reader

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Available font families for the reader.
 */
enum class ReaderFontFamily(val displayName: String, val fontFamily: FontFamily) {
    DEFAULT("Default", FontFamily.Default),
    SERIF("Serif", FontFamily.Serif),
    SANS_SERIF("Sans Serif", FontFamily.SansSerif),
    MONOSPACE("Monospace", FontFamily.Monospace);

    companion object {
        fun fromFontFamily(ff: FontFamily): ReaderFontFamily {
            return entries.find { it.fontFamily == ff } ?: DEFAULT
        }
    }
}

/**
 * Data class holding all customizable text settings.
 */
data class TextSettings(
    val fontSize: Int = 18,
    val lineSpacingMultiplier: Float = 1.2f,
    val lineSpacingExtra: Float = 8f,
    val marginTop: Int = 24,
    val marginBottom: Int = 24,
    val marginLeft: Int = 24,
    val marginRight: Int = 24,
    val selectedFontFamily: ReaderFontFamily = ReaderFontFamily.DEFAULT,
    val customFontPath: String? = null,
    val customTypeface: Typeface? = null
) {
    fun toRenderConfig(): TextRenderConfig = TextRenderConfig(
        fontSize = fontSize,
        lineSpacingExtra = lineSpacingExtra,
        lineSpacingMultiplier = lineSpacingMultiplier,
        marginTop = marginTop.dp,
        marginBottom = marginBottom.dp,
        marginLeft = marginLeft.dp,
        marginRight = marginRight.dp,
        fontFamily = selectedFontFamily.fontFamily,
        customTypeface = customTypeface
    )
}

/**
 * TextSettingsPanel provides a full settings UI for text rendering customization.
 * Includes font family selector, font size slider, line spacing controls, margin sliders,
 * and custom font loading.
 */
@Composable
fun TextSettingsPanel(
    settings: TextSettings,
    onSettingsChanged: (TextSettings) -> Unit,
    onDismiss: () -> Unit,
    paginationEngine: PaginationEngine? = null
) {
    var localSettings by remember(settings) { mutableStateOf(settings) }
    var showFontPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Text Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Font Family Selector
                    item {
                        FontFamilySection(
                            selectedFamily = localSettings.selectedFontFamily,
                            customFontPath = localSettings.customFontPath,
                            onFamilySelected = { family ->
                                localSettings = localSettings.copy(
                                    selectedFontFamily = family,
                                    customFontPath = null,
                                    customTypeface = null
                                )
                                onSettingsChanged(localSettings)
                            },
                            onCustomFontRequested = { showFontPicker = true }
                        )
                    }

                    // Font Size Slider
                    item {
                        FontSizeSection(
                            fontSize = localSettings.fontSize,
                            onFontSizeChanged = { size ->
                                localSettings = localSettings.copy(fontSize = size)
                                onSettingsChanged(localSettings)
                            }
                        )
                    }

                    // Line Spacing
                    item {
                        LineSpacingSection(
                            lineSpacingMultiplier = localSettings.lineSpacingMultiplier,
                            lineSpacingExtra = localSettings.lineSpacingExtra,
                            onLineSpacingMultiplierChanged = { mult ->
                                localSettings = localSettings.copy(lineSpacingMultiplier = mult)
                                onSettingsChanged(localSettings)
                            },
                            onLineSpacingExtraChanged = { extra ->
                                localSettings = localSettings.copy(lineSpacingExtra = extra)
                                onSettingsChanged(localSettings)
                            }
                        )
                    }

                    // Margins
                    item {
                        MarginsSection(
                            marginTop = localSettings.marginTop,
                            marginBottom = localSettings.marginBottom,
                            marginLeft = localSettings.marginLeft,
                            marginRight = localSettings.marginRight,
                            onMarginTopChanged = { v ->
                                localSettings = localSettings.copy(marginTop = v)
                                onSettingsChanged(localSettings)
                            },
                            onMarginBottomChanged = { v ->
                                localSettings = localSettings.copy(marginBottom = v)
                                onSettingsChanged(localSettings)
                            },
                            onMarginLeftChanged = { v ->
                                localSettings = localSettings.copy(marginLeft = v)
                                onSettingsChanged(localSettings)
                            },
                            onMarginRightChanged = { v ->
                                localSettings = localSettings.copy(marginRight = v)
                                onSettingsChanged(localSettings)
                            }
                        )
                    }

                    // Preview
                    item {
                        PreviewSection(settings = localSettings)
                    }

                    // Bottom spacer
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Custom font picker dialog
    if (showFontPicker) {
        CustomFontPickerDialog(
            paginationEngine = paginationEngine,
            onFontSelected = { typeface, path ->
                localSettings = localSettings.copy(
                    customTypeface = typeface,
                    customFontPath = path,
                    selectedFontFamily = ReaderFontFamily.DEFAULT
                )
                onSettingsChanged(localSettings)
                showFontPicker = false
            },
            onDismiss = { showFontPicker = false }
        )
    }
}

@Composable
private fun FontFamilySection(
    selectedFamily: ReaderFontFamily,
    customFontPath: String?,
    onFamilySelected: (ReaderFontFamily) -> Unit,
    onCustomFontRequested: () -> Unit
) {
    Column {
        Text(
            text = "Font Family",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        ReaderFontFamily.entries.forEach { family ->
            val isSelected = selectedFamily == family && customFontPath == null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFamilySelected(family) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = family.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = family.fontFamily
                    )
                )
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Custom font option
        if (customFontPath != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Custom Font",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = customFontPath.substringAfterLast("/"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Load custom font button
        TextButton(
            onClick = onCustomFontRequested,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = "Load custom font file",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Load Custom Font (TTF/OTF)")
        }
    }
}

@Composable
private fun FontSizeSection(
    fontSize: Int,
    onFontSizeChanged: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Font Size",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${fontSize}sp",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = fontSize.toFloat(),
            onValueChange = { onFontSizeChanged(it.toInt()) },
            valueRange = 10f..32f,
            steps = 21, // 1sp increments from 10 to 32
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("10", style = MaterialTheme.typography.bodySmall)
            Text("32", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LineSpacingSection(
    lineSpacingMultiplier: Float,
    lineSpacingExtra: Float,
    onLineSpacingMultiplierChanged: (Float) -> Unit,
    onLineSpacingExtraChanged: (Float) -> Unit
) {
    Column {
        Text(
            text = "Line Spacing",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Multiplier
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Multiplier",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = String.format("%.1fx", lineSpacingMultiplier),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = lineSpacingMultiplier,
            onValueChange = onLineSpacingMultiplierChanged,
            valueRange = 0.8f..2.5f,
            steps = 16,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Extra spacing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Extra Spacing",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = String.format("%.0fsp", lineSpacingExtra),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = lineSpacingExtra,
            onValueChange = onLineSpacingExtraChanged,
            valueRange = 0f..24f,
            steps = 23,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun MarginsSection(
    marginTop: Int,
    marginBottom: Int,
    marginLeft: Int,
    marginRight: Int,
    onMarginTopChanged: (Int) -> Unit,
    onMarginBottomChanged: (Int) -> Unit,
    onMarginLeftChanged: (Int) -> Unit,
    onMarginRightChanged: (Int) -> Unit
) {
    Column {
        Text(
            text = "Margins",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        MarginSlider(
            label = "Top",
            value = marginTop,
            onValueChange = onMarginTopChanged
        )
        MarginSlider(
            label = "Bottom",
            value = marginBottom,
            onValueChange = onMarginBottomChanged
        )
        MarginSlider(
            label = "Left",
            value = marginLeft,
            onValueChange = onMarginLeftChanged
        )
        MarginSlider(
            label = "Right",
            value = marginRight,
            onValueChange = onMarginRightChanged
        )
    }
}

@Composable
private fun MarginSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(50.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..64f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = "${value}dp",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )
    }
}

@Composable
private fun PreviewSection(settings: TextSettings) {
    Column {
        Text(
            text = "Preview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(8.dp)
                )
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                )
                .padding(
                    start = settings.marginLeft.dp.coerceAtMost(16.dp),
                    end = settings.marginRight.dp.coerceAtMost(16.dp),
                    top = settings.marginTop.dp.coerceAtMost(8.dp),
                    bottom = settings.marginBottom.dp.coerceAtMost(8.dp)
                )
        ) {
            Text(
                text = "The quick brown fox jumps over the lazy dog. " +
                    "This is a preview of how your text will appear.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (settings.fontSize <= 20) settings.fontSize.sp else 20.sp,
                    lineHeight = TextUnit(
                        (settings.fontSize * settings.lineSpacingMultiplier).toFloat(),
                        TextUnitType.Sp
                    )
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Dialog for picking custom TTF/OTF fonts from the file system.
 * In a real app, this would use ActivityResultContracts.OpenDocument.
 * For now, shows a placeholder with instructions.
 */
@Composable
private fun CustomFontPickerDialog(
    paginationEngine: PaginationEngine?,
    onFontSelected: (Typeface?, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Load Custom Font",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Place your TTF or OTF font files in the app's " +
                        "fonts directory, then select one below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Scan for fonts in assets/fonts/
                val fontFiles = remember {
                    try {
                        context.assets.list("fonts")?.filter {
                            it.endsWith(".ttf", ignoreCase = true) ||
                                it.endsWith(".otf", ignoreCase = true)
                        } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                if (fontFiles.isEmpty()) {
                    Text(
                        text = "No font files found in assets/fonts/",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add .ttf or .otf files to " +
                            "app/src/main/assets/fonts/ to use custom fonts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    fontFiles.forEach { fontFile ->
                        val path = "fonts/$fontFile"
                        TextButton(
                            onClick = {
                                val typeface = paginationEngine?.loadCustomFont(path)
                                if (typeface != null) {
                                    onFontSelected(typeface, fontFile)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = fontFile,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}
