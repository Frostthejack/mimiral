package com.mimiral.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.exportimport.ImportResult
import com.mimiral.app.data.exportimport.LibraryExporter
import com.mimiral.app.data.exportimport.LibraryImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LibraryExportImportUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportSuccess: Boolean = false,
    val importSuccess: Boolean = false,
    val importResult: ImportResult? = null,
    val errorMessage: String? = null,
    val exportedFilePath: String? = null
)

@HiltViewModel
class LibraryExportImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: LibraryExporter,
    private val importer: LibraryImporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryExportImportUiState())
    val uiState: StateFlow<LibraryExportImportUiState> = _uiState.asStateFlow()

    /**
     * Export all library data to a JSON file.
     */
    fun exportLibrary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExporting = true,
                errorMessage = null,
                exportSuccess = false
            )

            try {
                val file = withContext(Dispatchers.IO) {
                    exporter.exportLibrary()
                }

                if (file != null) {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportSuccess = true,
                        exportedFilePath = file.absolutePath
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = "Export failed. Please try again."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    errorMessage = "Export error: ${e.message}"
                )
            }
        }
    }

    /**
     * Share the most recently exported file.
     */
    fun shareExportedFile() {
        val filePath = _uiState.value.exportedFilePath ?: return
        val file = java.io.File(filePath)
        if (file.exists()) {
            exporter.shareExportedFile(file)
        }
    }

    /**
     * Import library data from a JSON file URI (SAF picker result).
     */
    fun importLibraryFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImporting = true,
                errorMessage = null,
                importSuccess = false,
                importResult = null
            )

            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    } ?: throw IllegalStateException("Cannot read selected file")
                }

                val result = withContext(Dispatchers.IO) {
                    importer.importLibrary(json)
                }

                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importSuccess = result.success,
                    importResult = result,
                    errorMessage = if (!result.success && result.errors.isNotEmpty()) {
                        result.errors.first()
                    } else {
                        null
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    errorMessage = "Import error: ${e.message}"
                )
            }
        }
    }

    /**
     * Import library data from a JSON string (for testing).
     */
    fun importLibraryFromJson(json: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImporting = true,
                errorMessage = null,
                importSuccess = false,
                importResult = null
            )

            try {
                val result = withContext(Dispatchers.IO) {
                    importer.importLibrary(json)
                }

                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importSuccess = result.success,
                    importResult = result,
                    errorMessage = if (!result.success && result.errors.isNotEmpty()) {
                        result.errors.first()
                    } else {
                        null
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    errorMessage = "Import error: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearExportSuccess() {
        _uiState.value = _uiState.value.copy(exportSuccess = false)
    }

    fun clearImportSuccess() {
        _uiState.value = _uiState.value.copy(importSuccess = false, importResult = null)
    }
}
