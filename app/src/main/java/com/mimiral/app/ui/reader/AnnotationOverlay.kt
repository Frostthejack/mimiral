package com.mimiral.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mimiral.app.data.remote.kavita.KavitaAnnotation

/**
 * Predefined highlight colors for annotations.
 */
val ANNOTATION_COLORS = listOf(
    "#FFFFEB3B" to "Yellow",
    "#FF4CAF50" to "Green",
    "#FF2196F3" to "Blue",
    "#FFF44336" to "Red",
    "#FFE91E63" to "Pink",
    "#FFFF9800" to "Orange",
    "#FF9C27B0" to "Purple"
)

/**
 * Popup overlay that appears after text selection in the reader,
 * allowing users to create a new Kavita annotation.
 *
 * Shows:
 * - Selected text preview
 * - Color picker (swatches)
 * - Optional note/comment input
 * - Spoiler toggle
 * - Create button
 * - Copy text action
 */
@Composable
fun AnnotationOverlay(
    selectedText: String,
    onDismiss: () -> Unit,
    onCreateAnnotation: (color: String, note: String?, isSpoiler: Boolean) -> Unit,
    onCopyText: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedColor by remember { mutableStateOf("#FFFFEB3B") }
    var noteText by remember { mutableStateOf("") }
    var isSpoiler by remember { mutableStateOf(false) }
    var showNoteField by remember { mutableStateOf(false) }

    Popup(
        alignment = Alignment.TopCenter,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header row: title + close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Create Annotation",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Selected text preview
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "\"$selectedText\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Color picker
                Text(
                    text = "Highlight Color",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ANNOTATION_COLORS) { (hexColor, label) ->
                        val isSelected = selectedColor == hexColor
                        val color = try {
                            Color(android.graphics.Color.parseColor(hexColor))
                        } catch (_: Exception) {
                            Color.Yellow
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        )
                                    } else {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                    }
                                )
                                .clickable { selectedColor = hexColor },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimary)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons row: Add note, Copy, Spoiler toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Add note toggle
                    IconButton(
                        onClick = { showNoteField = !showNoteField },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NoteAdd,
                            contentDescription = if (showNoteField) "Hide note" else "Add note",
                            tint = if (showNoteField) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // Copy selected text
                    IconButton(
                        onClick = onCopyText,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy text",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Spoiler toggle
                    IconButton(
                        onClick = { isSpoiler = !isSpoiler },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpoiler) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (isSpoiler) "Spoiler on" else "Spoiler off",
                            tint = if (isSpoiler) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Note text field (collapsible)
                if (showNoteField) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note / Comment") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Create button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onCreateAnnotation(
                                selectedColor,
                                noteText.ifBlank { null },
                                isSpoiler
                            )
                        },
                        enabled = selectedText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create")
                    }
                }
            }
        }
    }
}

/**
 * Dialog for editing an existing annotation (note, color, spoiler).
 */
@Composable
fun AnnotationEditDialog(
    annotation: KavitaAnnotation,
    onDismiss: () -> Unit,
    onUpdate: (note: String?, isSpoiler: Boolean, color: String) -> Unit,
    onDelete: () -> Unit
) {
    var noteText by remember { mutableStateOf(annotation.note ?: "") }
    var isSpoiler by remember { mutableStateOf(annotation.isSpoiler) }
    var selectedColor by remember { mutableStateOf(annotation.color ?: "#FFFFEB3B") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Annotation") },
        text = {
            Column {
                // Selected text preview (read-only)
                Text(
                    text = "\"${annotation.selectedText}\"",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Color picker
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ANNOTATION_COLORS) { (hexColor, _) ->
                        val isSelected = selectedColor == hexColor
                        val color = try {
                            Color(android.graphics.Color.parseColor(hexColor))
                        } catch (_: Exception) {
                            Color.Yellow
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { selectedColor = hexColor }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Note field
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note / Comment") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Spoiler toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSpoiler,
                        onCheckedChange = { isSpoiler = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mark as spoiler")
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = {
                        onUpdate(
                            noteText.ifBlank { null },
                            isSpoiler,
                            selectedColor
                        )
                    }
                ) {
                    Text("Save")
                }
            }
        }
    )
}
