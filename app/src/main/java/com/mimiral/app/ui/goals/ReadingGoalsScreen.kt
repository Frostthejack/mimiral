package com.mimiral.app.ui.goals

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingGoalsScreen(
    viewModel: ReadingGoalsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Goals") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            GoalsFab(
                onAddDaily = { viewModel.showAddGoalDialog("daily") },
                onAddWeekly = { viewModel.showAddGoalDialog("weekly") },
                onAddYearly = { viewModel.showAddGoalDialog("yearly") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                ReadingGoalsContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Edit/Add Goal Dialog
    if (uiState.showEditDialog) {
        GoalEditDialog(
            goalType = uiState.editingGoalType,
            targetType = uiState.editingTargetType,
            targetValue = uiState.editingTargetValue,
            onValueChange = { viewModel.updateEditingValue(it) },
            onSave = { viewModel.saveGoal() },
            onDismiss = { viewModel.dismissEditDialog() }
        )
    }
}

@Composable
private fun ReadingGoalsContent(
    uiState: ReadingGoalsUiState,
    viewModel: ReadingGoalsViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Daily Goals
        item {
            GoalSectionHeader(
                title = "Daily Goals",
                icon = Icons.Default.Schedule,
                onAdd = { viewModel.showAddGoalDialog("daily") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.dailyGoals.isEmpty()) {
                EmptyGoalState(
                    message = "No daily goals set",
                    subMessage = "Tap + to set a daily reading goal"
                )
            } else {
                uiState.dailyGoals.forEach { goal ->
                    GoalCard(
                        goalWithProgress = goal,
                        onRemove = { viewModel.removeGoal(goal.goal.id) }
                    )
                }
            }
        }

        // Weekly Goals
        item {
            GoalSectionHeader(
                title = "Weekly Goals",
                icon = Icons.Default.MenuBook,
                onAdd = { viewModel.showAddGoalDialog("weekly") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.weeklyGoals.isEmpty()) {
                EmptyGoalState(
                    message = "No weekly goals set",
                    subMessage = "Tap + to set a weekly reading goal"
                )
            } else {
                uiState.weeklyGoals.forEach { goal ->
                    GoalCard(
                        goalWithProgress = goal,
                        onRemove = { viewModel.removeGoal(goal.goal.id) }
                    )
                }
            }
        }

        // Yearly Goals
        item {
            GoalSectionHeader(
                title = "Yearly Goals",
                icon = Icons.Default.EmojiEvents,
                onAdd = { viewModel.showAddGoalDialog("yearly") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.yearlyGoals.isEmpty()) {
                EmptyGoalState(
                    message = "No yearly goals set",
                    subMessage = "Tap + to set a yearly reading goal"
                )
            } else {
                uiState.yearlyGoals.forEach { goal ->
                    GoalCard(
                        goalWithProgress = goal,
                        onRemove = { viewModel.removeGoal(goal.goal.id) }
                    )
                }
            }
        }

        // Bottom spacer
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun GoalSectionHeader(
    title: String,
    icon: ImageVector,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add $title",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun GoalCard(
    goalWithProgress: GoalWithProgress,
    onRemove: () -> Unit
) {
    val goal = goalWithProgress.goal
    val progress = goalWithProgress.progressPercent
    val isComplete = goalWithProgress.isComplete

    val progressColor by animateColorAsState(
        targetValue = when {
            isComplete -> Color(0xFF4CAF50)
            progress >= 0.75f -> Color(0xFF2196F3)
            progress >= 0.5f -> Color(0xFFFFC107)
            else -> MaterialTheme.colorScheme.primary
        },
        label = "progressColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isComplete) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            text = goal.targetType.replaceFirstChar { it.uppercase() } +
                                " Goal",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${goalWithProgress.currentValue} / " +
                                "${goal.targetValue} ${goal.targetType}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove goal",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${(progress * 100).toInt()}% complete",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyGoalState(
    message: String,
    subMessage: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GoalsFab(
    onAddDaily: () -> Unit,
    onAddWeekly: () -> Unit,
    onAddYearly: () -> Unit
) {
    FloatingActionButton(
        onClick = onAddDaily,
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add goal",
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun GoalEditDialog(
    goalType: String,
    targetType: String,
    targetValue: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set ${goalType.replaceFirstChar { it.uppercase() }} Goal",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = "Target type: ${targetType.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = targetValue,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            onValueChange(newValue)
                        }
                    },
                    label = { Text("Target ($targetType)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = targetValue.toIntOrNull()?.let { it > 0 } == true
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
