package com.mimiral.app.ui.library

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.scanner.FileScanner
import com.mimiral.app.data.local.scanner.ScanState
import com.mimiral.app.data.local.settings.LibrarySettingsRepository
import com.mimiral.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AddBooksUiState(
    val scanState: ScanState = ScanState.Idle,
    val lastImportCount: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class AddBooksViewModel @Inject constructor(
    application: Application,
    private val libraryRepository: LibraryRepository,
    private val fileScanner: FileScanner,
    private val settingsRepository: LibrarySettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AddBooksUiState())
    val uiState: StateFlow<AddBooksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            fileScanner.scanState.collect { state ->
                val importCount = when (state) {
                    is ScanState.Completed -> state.newFiles
                    else -> _uiState.value.lastImportCount
                }
                _uiState.value = _uiState.value.copy(
                    scanState = state,
                    lastImportCount = importCount
                )
            }
        }
    }

    fun scanFolder(uri: Uri) {
        val context = getApplication<Application>()
        val contentResolver = context.contentResolver

        // Take persistable URI permission so access survives app restarts
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers don't support persistable permissions
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(errorMessage = null)

                // Resolve the SAF URI to a filesystem path on IO dispatcher
                val displayPath = withContext(Dispatchers.IO) {
                    resolveSafUriToPath(uri, contentResolver)
                }

                if (displayPath != null) {
                    // Save the directory to settings so it appears in
                    // LibraryPreferencesScreen's scan folder list.
                    settingsRepository.addScanDirectory(displayPath)
                }

                // Scan the folder for books and import them
                val count = libraryRepository.scanAndImport(uri)
                _uiState.value = _uiState.value.copy(lastImportCount = count)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Scan failed"
                )
            }
        }
    }

    fun startFullScan() {
        fileScanner.startScan(
            scanDownloads = true,
            scanDocuments = true
        )
    }

    fun resetState() {
        fileScanner.reset()
        _uiState.value = AddBooksUiState()
    }

    companion object {
        /**
         * Convert a SAF tree URI to a human-readable filesystem path.
         * Mirrors the same logic in LibraryPreferencesViewModel.
         */
        private fun resolveSafUriToPath(
            uri: Uri,
            contentResolver: ContentResolver
        ): String? {
            val docId = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Exception) {
                return null
            }

            val decodedId = try {
                if (docId.contains("%")) {
                    java.net.URLDecoder.decode(docId, "UTF-8")
                } else {
                    docId
                }
            } catch (_: Exception) {
                docId
            }

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
                    decodedId.ifBlank { null }
                }
            }
        }
    }
}
