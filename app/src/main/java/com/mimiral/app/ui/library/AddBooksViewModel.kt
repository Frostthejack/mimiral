package com.mimiral.app.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.scanner.FileScanner
import com.mimiral.app.data.local.scanner.ScanState
import com.mimiral.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddBooksUiState(
    val scanState: ScanState = ScanState.Idle,
    val lastImportCount: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class AddBooksViewModel @Inject constructor(
    application: Application,
    private val libraryRepository: LibraryRepository,
    private val fileScanner: FileScanner
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
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(errorMessage = null)
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
}
