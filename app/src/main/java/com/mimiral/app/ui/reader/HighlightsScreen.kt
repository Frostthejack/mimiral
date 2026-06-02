package com.mimiral.app.ui.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.HighlightEntity
import com.mimiral.app.data.repository.BookRepository
import com.mimiral.app.ui.components.ConfirmDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HighlightsUiState(
    val highlights: List<HighlightEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HighlightsViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HighlightsUiState())
    val uiState: StateFlow<HighlightsUiState> = _uiState.asStateFlow()

    private var currentBookId: Int = -1

    fun loadHighlights(bookId: Int) {
        if (bookId == -1) {
            _uiState.value = HighlightsUiState(isLoading = false, error = "Invalid book ID")
            return
        }
        currentBookId = bookId
        viewModelScope.launch {
            try {
                bookRepository.getHighlightsForBook(bookId).collect { highlights ->
                    _uiState.value = HighlightsUiState(
                        highlights = highlights,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = HighlightsUiState(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun deleteHighlight(highlight: HighlightEntity) {
        viewModelScope.launch {
            try {
                bookRepository.deleteHighlight(highlight)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete highlight: ${e.message}")
            }
        }
    }

    fun updateNote(highlightId: Int, note: String?) {
        viewModelScope.launch {
            try {
                bookRepository.updateHighlightNote(highlightId, note)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to update note: ${e.message}")
            }
        }
    }
}

/**
 * Parses a highlight color string (hex or name) into a [Color].
 * Falls back to yellow for unrecognized values.
 */
fun parseHighlightColor(colorString: String): Color {
    return try {
        when {
            colorString.startsWith("#") -> Color(android.graphics.Color.parseColor(colorString))
            colorString.equals("yellow", ignoreCase = true) -> Color(0xFFFFEB3B)
            colorString.equals("green", ignoreCase = true) -> Color(0xFF4CAF50)
            colorString.equals("blue", ignoreCase = true) -> Color(0xFF2196F3)
            colorString.equals("red", ignoreCase = true) -> Color(0xFFF44336)
            colorString.equals("pink", ignoreCase = true) -> Color(0xFFE91E63)
            colorString.equals("orange", ignoreCase = true) -> Color(0xFFFF9800)
            colorString.equals("purple", ignoreCase = true) -> Color(0xFF9C27B0)
            else -> Color(0xFFFFEB3B)
        }
    } catch (_: Exception) {
        Color(0xFFFFEB3B)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsScreen(
    bookId: Int,
    onNavigateBack: () -> Unit,
    onJumpToHighlight: (HighlightEntity) -> Unit = {},
    viewModel: HighlightsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Load highlights when screen opens
    LaunchedEffect(bookId) {
        viewModel.loadHighlights(bookId)
    }

    var showDeleteConfirm by remember { mutableStateOf<HighlightEntity?>(null) }
    var editingHighlight by remember { mutableStateOf<HighlightEntity?>(null) }
    var editNoteText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Highlight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Highlights",
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (!uiState.isLoading) {
                                Text(
                                    text = "${uiState.highlights.size} highlight${if (uiState.highlights.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.error != null) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.highlights.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Highlight,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No highlights yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Highlight text while reading to see it here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = uiState.highlights,
                        key = { it.id }
                    ) { highlight ->
                        HighlightListItem(
                            highlight = highlight,
                            onJump = { onJumpToHighlight(highlight) },
                            onEditNote = {
                                editingHighlight = highlight
                                editNoteText = highlight.note ?: ""
                            },
                            onDelete = { showDeleteConfirm = highlight }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm != null) {
        ConfirmDialog(
            title = "Delete Highlight",
            message = "This will permanently remove this highlight and its note.",
            confirmText = "Delete",
            cancelText = "Cancel",
            onConfirm = {
                viewModel.deleteHighlight(showDeleteConfirm!!)
                showDeleteConfirm = null
            },
            onDismiss = { showDeleteConfirm = null },
            visible = true,
            isDestructive = true
        )
    }

    // Edit note dialog
    if (editingHighlight != null) {
        AlertDialog(
            onDismissRequest = { editingHighlight = null },
            title = { Text("Edit Note") },
            text = {
                OutlinedTextField(
                    value = editNoteText,
                    onValueChange = { editNoteText = it },
                    label = { Text("Note") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    maxLines = 8
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateNote(
                            editingHighlight!!.id,
                            editNoteText.ifBlank { null }
                        )
                        editingHighlight = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingHighlight = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HighlightListItem(
    highlight: HighlightEntity,
    onJump: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit
) {
    val highlightColor = parseHighlightColor(highlight.color)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onJump)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color swatch
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(highlightColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Highlighted text preview
            Text(
                text = highlight.selectedText ?: "Highlighted text",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Location info
        Text(
            text = "Chapter ${highlight.chapterIndex + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 32.dp)
        )

        // Note (if present)
        if (!highlight.note.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = highlight.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 32.dp)
            )
        }

        // Action buttons
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onEditNote, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit note",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete highlight",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
