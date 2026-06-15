package com.mimiral.app.ui.collections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimiral.app.data.local.entity.BookEntity
import com.mimiral.app.data.local.entity.CollectionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onBookClick: (Int, String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    viewModel: CollectionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Hoisted delete/remove confirmation state — only one dialog at a time
    var pendingDeleteCollection by remember { mutableStateOf<CollectionEntity?>(null) }
    var pendingRemoveBook by remember { mutableStateOf<BookEntity?>(null) }

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Collections") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.startEditing(
                        CollectionEntity(name = "", description = null)
                    )
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Collection")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            } else if (uiState.collections.isEmpty()) {
                EmptyCollectionsState(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                CollectionsList(
                    collections = uiState.collections,
                    expandedCollectionId = uiState.expandedCollectionId,
                    booksInExpanded = uiState.booksInExpanded,
                    onExpand = { viewModel.expandCollection(it) },
                    onEdit = { viewModel.startEditing(it) },
                    onDelete = { viewModel.deleteCollection(it) },
                    onBookClick = onBookClick,
                    onRemoveBook = { bookId, collectionId ->
                        viewModel.removeBookFromCollection(bookId, collectionId)
                    },
                    pendingDeleteCollection = pendingDeleteCollection,
                    onPendingDeleteCollectionChange = { pendingDeleteCollection = it },
                    pendingRemoveBook = pendingRemoveBook,
                    onPendingRemoveBookChange = { pendingRemoveBook = it }
                )
            }
        }
    }

    // Create / Edit dialog
    uiState.editingCollection?.let { collection ->
        CollectionEditDialog(
            collection = collection,
            isNew = collection.name.isEmpty() && collection.id == 0,
            onSave = { name, description ->
                if (collection.name.isEmpty() && collection.id == 0) {
                    // New collection
                    viewModel.createCollection(name, description)
                } else {
                    // Edit existing
                    viewModel.updateCollection(
                        collection.copy(name = name, description = description)
                    )
                }
            },
            onDismiss = { viewModel.cancelEditing() }
        )
    }
}

@Composable
private fun EmptyCollectionsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.LibraryBooks,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No collections yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap + to create a bookshelf for organizing your library",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun CollectionsList(
    collections: List<CollectionWithCount>,
    expandedCollectionId: Int?,
    booksInExpanded: List<BookEntity>,
    onExpand: (Int) -> Unit,
    onEdit: (CollectionEntity) -> Unit,
    onDelete: (CollectionEntity) -> Unit,
    onBookClick: (Int, String) -> Unit,
    onRemoveBook: (Int, Int) -> Unit,
    pendingDeleteCollection: CollectionEntity?,
    onPendingDeleteCollectionChange: (CollectionEntity?) -> Unit,
    pendingRemoveBook: BookEntity?,
    onPendingRemoveBookChange: (BookEntity?) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(collections, key = { it.collection.id }) { collectionWithCount ->
            val collectionId = collectionWithCount.collection.id
            val isExpanded = expandedCollectionId == collectionId
            CollectionCard(
                collectionWithCount = collectionWithCount,
                isExpanded = isExpanded,
                booksInCollection = if (isExpanded) {
                    booksInExpanded
                } else {
                    emptyList()
                },
                onExpand = { onExpand(collectionWithCount.collection.id) },
                onEdit = { onEdit(collectionWithCount.collection) },
                onDelete = { onDelete(collectionWithCount.collection) },
                onBookClick = onBookClick,
                onRemoveBook = { bookId ->
                    onRemoveBook(bookId, collectionWithCount.collection.id)
                },
                showDeleteConfirm = pendingDeleteCollection == collectionWithCount.collection,
                onShowDeleteConfirm = {
                    onPendingDeleteCollectionChange(
                        collectionWithCount.collection
                    )
                },
                onDismissDeleteConfirm = { onPendingDeleteCollectionChange(null) },
                pendingRemoveBook = pendingRemoveBook,
                onPendingRemoveBookChange = onPendingRemoveBookChange
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionCard(
    collectionWithCount: CollectionWithCount,
    isExpanded: Boolean,
    booksInCollection: List<BookEntity>,
    onExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBookClick: (Int, String) -> Unit,
    onRemoveBook: (Int) -> Unit,
    showDeleteConfirm: Boolean,
    onShowDeleteConfirm: () -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    pendingRemoveBook: BookEntity?,
    onPendingRemoveBookChange: (BookEntity?) -> Unit
) {
    val collection = collectionWithCount.collection

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onExpand,
                        onLongClick = onEdit
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (collection.description != null) {
                        Text(
                            text = collection.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val count = collectionWithCount.bookCount
                    val label = if (count != 1) "books" else "book"
                    Text(
                        text = "$count $label",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Action buttons
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onShowDeleteConfirm) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                val icon = if (isExpanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                }
                Icon(
                    imageVector = icon,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded book list
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider()
                    if (booksInCollection.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No books in this collection",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        booksInCollection.forEach { book ->
                            BookRow(
                                book = book,
                                onClick = { onBookClick(book.id, book.format) },
                                onRemove = { onRemoveBook(book.id) },
                                showRemoveConfirm = pendingRemoveBook == book,
                                onShowRemoveConfirm = { onPendingRemoveBookChange(book) },
                                onDismissRemoveConfirm = { onPendingRemoveBookChange(null) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirm,
            title = { Text("Delete Collection") },
            text = {
                Text(
                    "Delete \"${collection.name}\"? " +
                        "Books will not be removed from your library."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismissDeleteConfirm()
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteConfirm) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
    book: BookEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    showRemoveConfirm: Boolean,
    onShowRemoveConfirm: () -> Unit,
    onDismissRemoveConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onShowRemoveConfirm
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Book,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (book.author != null) {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onShowRemoveConfirm) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Remove from collection",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = onDismissRemoveConfirm,
            title = { Text("Remove Book") },
            text = { Text("Remove \"${book.title}\" from this collection?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismissRemoveConfirm()
                        onRemove()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRemoveConfirm) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CollectionEditDialog(
    collection: CollectionEntity,
    isNew: Boolean,
    onSave: (name: String, description: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(collection.name) }
    var description by remember { mutableStateOf(collection.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New Collection" else "Edit Collection") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val desc = description.ifBlank { null }
                    onSave(name.trim(), desc)
                },
                enabled = name.isNotBlank()
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
