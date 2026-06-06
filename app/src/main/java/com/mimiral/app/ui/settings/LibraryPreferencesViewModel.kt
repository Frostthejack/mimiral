package com.mimiral.app.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.settings.LibrarySettingsRepository
import com.mimiral.app.data.local.settings.SupportedFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state for the Library Preferences screen.
 */
data class LibraryPreferencesUiState(
    val scanDirectories: Set<String> = emptySet(),
    val enabledFormats: Set<String> = SupportedFormat.ALL_EXTENSIONS,
    val isAddingFolder: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel for [LibraryPreferencesScreen].
 *
 * Fixes ANR by:
 * 1. Collecting DataStore flows on [viewModelScope] instead of the composition main thread
 * 2. Processing SAF URI results on [Dispatchers.IO]
 * 3. Taking persistable URI permissions so access survives across Activity restarts
 * 4. Properly converting SAF tree URIs to displayable paths instead of using [Uri.path]
 */
@HiltViewModel
class LibraryPreferencesViewModel @Inject constructor(
    private val settingsRepository: LibrarySettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryPreferencesUiState())
    val uiState: StateFlow<LibraryPreferencesUiState> = _uiState.asStateFlow()

    init {
        // Collect scan directories off main thread
        viewModelScope.launch {
            settingsRepository.scanDirectories.collect { dirs ->
                _uiState.value = _uiState.value.copy(scanDirectories = dirs)
            }
        }

        // Collect enabled formats off main thread
        viewModelScope.launch {
            settingsRepository.enabledFormats.collect { formats ->
                _uiState.value = _uiState.value.copy(enabledFormats = formats)
            }
        }
    }

    /**
     * Process a SAF folder URI returned from [ActivityResultContracts.OpenDocumentTree].
     *
     * This is called from the ActivityResultLauncher callback (main thread),
     * but the heavy work is dispatched to [Dispatchers.IO].
     *
     * @param uri The SAF tree URI from the folder picker
     * @param context Application context for ContentResolver access
     */
    fun addScanDirectoryFromSafUri(uri: Uri, context: Context) {
        val contentResolver = context.contentResolver

        // 1. Take persistable URI permission immediately on main thread
        //    (must be done before the Activity result callback returns)
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers don't support persistable permissions; continue anyway
        }

        _uiState.value = _uiState.value.copy(isAddingFolder = true, errorMessage = null)

        // 2. Convert URI to a usable path on IO dispatcher
        viewModelScope.launch {
            try {
                val displayPath = withContext(Dispatchers.IO) {
                    resolveSafUriToPath(uri, contentResolver)
                }

                if (displayPath != null) {
                    settingsRepository.addScanDirectory(displayPath)
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Could not resolve folder path"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to add folder: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isAddingFolder = false)
            }
        }
    }

    /**
     * Remove a scan directory from settings.
     */
    fun removeScanDirectory(path: String) {
        viewModelScope.launch {
            settingsRepository.removeScanDirectory(path)
        }
    }

    /**
     * Update the set of enabled file formats.
     */
    fun setEnabledFormats(extensions: Set<String>) {
        viewModelScope.launch {
            settingsRepository.setEnabledFormats(extensions)
        }
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    companion object {
        /**
         * Convert a SAF tree URI to a human-readable filesystem path.
         *
         * SAF URIs look like:
         *   content://com.android.externalstorage.documents/tree/primary%3ADownload
         *
         * This method extracts the document ID from the URI and decodes it
         * to produce a path like "/storage/emulated/0/Download" or "primary:Download".
         */
        private fun resolveSafUriToPath(
            uri: Uri,
            contentResolver: ContentResolver
        ): String? {
            // Try to get the document ID from the URI
            val docId = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Exception) {
                return null
            }

            // Decode the document ID which is typically "primary:Download" or
            // "home:Documents" for external storage
            val decodedId = try {
                // The document ID may still be URL-encoded
                if (docId.contains("%")) {
                    java.net.URLDecoder.decode(docId, "UTF-8")
                } else {
                    docId
                }
            } catch (_: Exception) {
                docId
            }

            // Convert "primary:Download" -> "/storage/emulated/0/Download"
            // Convert "home:Documents" -> "/storage/emulated/0/Documents"
            return when {
                decodedId.startsWith("primary:") -> {
                    val relativePath = decodedId.substringAfter("primary:")
                    if (relativePath.isEmpty()) {
                        "/storage/emulated/0"
                    } else {
                        "/storage/emulated/0/$relativePath"
                    }
                }
                decodedId.startsWith("home:") -> {
                    val relativePath = decodedId.substringAfter("home:")
                    if (relativePath.isEmpty()) {
                        "/storage/emulated/0"
                    } else {
                        "/storage/emulated/0/$relativePath"
                    }
                }
                else -> {
                    // For other providers (Google Drive, etc.), use the decoded ID
                    // as a display name since we can't resolve a filesystem path
                    decodedId.ifBlank { null }
                }
            }
        }
    }
}
