package com.mimiral.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A selectable chip component for sort and filter options.
 * Wraps Material 3 FilterChip with consistent Mimiral styling.
 *
 * @param label The text label displayed in the chip.
 * @param selected Whether the chip is currently selected.
 * @param onClick Called when the chip is tapped.
 * @param modifier Optional modifier.
 * @param leadingIcon Optional leading icon composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MimiralFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        },
        leadingIcon = leadingIcon,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.5.dp,
            enabled = true,
            selected = selected
        )
    )
}
