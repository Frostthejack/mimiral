package com.mimiral.app.ui.opds

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.mimiral.app.data.local.entity.OpdsCatalogEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsCatalogScreen(
    viewModel: OpdsCatalogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var catalogToEdit by remember { mutableStateOf<OpdsCatalogEntity?>(null) }
    var catalogToDelete by remember { mutableStateOf<OpdsCatalogEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OPDS Catalogs") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add catalog")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.catalogs.isEmpty() && !uiState.isLoading) {
                EmptyCatalogState(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(uiState.catalogs, key = { it.id }) { catalog ->
                        CatalogCard(
                            catalog = catalog,
                            onToggleActive = { viewModel.toggleCatalogActive(catalog) },
                            onEdit = { catalogToEdit = catalog },
                            onDelete = { catalogToDelete = catalog }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    if (showAddDialog) {
        CatalogDialog(
            catalog = null,
            onDismiss = {
                showAddDialog = false
                viewModel.clearMessages()
            },
            onConfirm = { name, url, authType, username, password, token ->
                viewModel.addCatalog(name, url, authType, username, password, token)
            },
            isLoading = uiState.isLoading,
            validationMessage = uiState.validationMessage,
            validationError = uiState.validationError
        )
    }

    catalogToEdit?.let { catalog ->
        CatalogDialog(
            catalog = catalog,
            onDismiss = {
                catalogToEdit = null
                viewModel.clearMessages()
            },
            onConfirm = { name, url, authType, username, password, token ->
                viewModel.updateCatalog(
                    catalog,
                    name,
                    url,
                    authType,
                    username,
                    password,
                    token
                )
            },
            isLoading = uiState.isLoading,
            validationMessage = uiState.validationMessage,
            validationError = uiState.validationError
        )
    }

    catalogToDelete?.let { catalog ->
        AlertDialog(
            onDismissRequest = { catalogToDelete = null },
            title = { Text("Remove Catalog") },
            text = { Text("Remove \"${catalog.name}\" from your catalogs?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCatalog(catalog)
                    catalogToDelete = null
                }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { catalogToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyCatalogState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = "No OPDS Catalogs",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Add an OPDS catalog to browse and download " +
                "books from remote libraries.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun CatalogCard(
    catalog: OpdsCatalogEntity,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (catalog.isActive) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = catalog.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = catalog.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (catalog.authType != "NONE") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = when (catalog.authType) {
                                    "BASIC" -> "HTTP Basic Auth"
                                    "TOKEN" -> "URL Token"
                                    else -> catalog.authType
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Switch(
                    checked = catalog.isActive,
                    onCheckedChange = { onToggleActive() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogDialog(
    catalog: OpdsCatalogEntity?,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        url: String,
        authType: String,
        username: String?,
        password: String?,
        token: String?
    ) -> Unit,
    isLoading: Boolean,
    validationMessage: String?,
    validationError: String?
) {
    val isEditing = catalog != null
    var name by remember { mutableStateOf(catalog?.name ?: "") }
    var url by remember { mutableStateOf(catalog?.url ?: "") }
    var authType by remember { mutableStateOf(catalog?.authType ?: "NONE") }
    var username by remember { mutableStateOf(catalog?.username ?: "") }
    var password by remember { mutableStateOf(catalog?.password ?: "") }
    var token by remember { mutableStateOf(catalog?.token ?: "") }
    var authDropdownExpanded by remember { mutableStateOf(false) }

    val authOptions = listOf("NONE", "BASIC", "TOKEN")
    val authLabels = mapOf(
        "NONE" to "No Authentication",
        "BASIC" to "HTTP Basic Auth",
        "TOKEN" to "URL Token"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "Edit Catalog" else "Add OPDS Catalog")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    placeholder = { Text("Auto-detected from feed") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Catalog URL") },
                    placeholder = { Text("https://example.com/opds") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = authDropdownExpanded,
                    onExpandedChange = { authDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = authLabels[authType] ?: authType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Authentication") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = authDropdownExpanded
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = authDropdownExpanded,
                        onDismissRequest = { authDropdownExpanded = false }
                    ) {
                        authOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(authLabels[option] ?: option) },
                                onClick = {
                                    authType = option
                                    authDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (authType == "BASIC") {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (authType == "TOKEN") {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Validating catalog...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                validationMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                validationError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name,
                        url,
                        authType,
                        username.ifBlank { null },
                        password.ifBlank { null },
                        token.ifBlank { null }
                    )
                },
                enabled = url.isNotBlank() && !isLoading
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
