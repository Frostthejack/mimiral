package com.mimiral.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.settings.SettingsBackupExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Backup/Restore section in SettingsScreen.
 */
data class BackupRestoreUiState(
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val backupSuccess: Boolean = false,
    val restoreSuccess: Boolean = false,
    val errorMessage: String? = null,
    val lastBackupFile: String? = null
)

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val backupExporter = SettingsBackupExporter(application)

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    /**
     * Export all settings to a JSON backup file.
     */
    fun backupSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBackingUp = true,
                errorMessage = null,
                backupSuccess = false
            )

            try {
                val file = backupExporter.exportSettings()
                if (file != null) {
                    _uiState.value = _uiState.value.copy(
                        isBackingUp = false,
                        backupSuccess = true,
                        lastBackupFile = file.name
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isBackingUp = false,
                        errorMessage = "Failed to create backup file"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBackingUp = false,
                    errorMessage = "Backup failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Share the most recent backup file.
     */
    fun shareBackup() {
        val fileName = _uiState.value.lastBackupFile ?: return
        val backupDir = java.io.File(getApplication<Application>().cacheDir, "backups")
        val file = java.io.File(backupDir, fileName)
        if (file.exists()) {
            backupExporter.shareBackupFile(file)
        }
    }

    /**
     * Restore settings from a JSON file URI (from SAF file picker).
     */
    fun restoreFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRestoring = true,
                errorMessage = null,
                restoreSuccess = false
            )

            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                val jsonText = inputStream?.bufferedReader()?.readText()
                inputStream?.close()

                if (jsonText != null) {
                    val success = backupExporter.restoreSettingsFromJson(jsonText)
                    if (success) {
                        _uiState.value = _uiState.value.copy(
                            isRestoring = false,
                            restoreSuccess = true
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isRestoring = false,
                            errorMessage = "Invalid or incompatible backup file"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isRestoring = false,
                        errorMessage = "Could not read the selected file"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    errorMessage = "Restore failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Clear any error or success message.
     */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            backupSuccess = false,
            restoreSuccess = false
        )
    }
}
