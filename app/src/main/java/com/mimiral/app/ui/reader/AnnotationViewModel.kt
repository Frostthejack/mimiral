package com.mimiral.app.ui.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.kavita.KavitaAnnotation
import com.mimiral.app.data.remote.kavita.KavitaAnnotationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the annotation panel overlay.
 */
data class AnnotationUiState(
    val annotations: List<KavitaAnnotation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val editingAnnotation: KavitaAnnotation? = null,
    val createXPath: String? = null,
    val createSelectedText: String = "",
    val createPageNumber: Int = 0,
    val createChapterId: Int = 0,
    val createNote: String = "",
    val createColor: String = "#FFFFEB3B",
    val createIsSpoiler: Boolean = false,
    val editNote: String = "",
    val editIsSpoiler: Boolean = false,
    val editColor: String = "#FFFFEB3B"
)

/**
 * ViewModel for managing Kavita annotations (highlights + comments)
 * within the reader.
 *
 * Supports:
 * - Fetching annotations for the current chapter or series
 * - Creating new annotations from text selection
 * - Updating annotation notes, spoiler status, and colors
 * - Deleting annotations
 * - Liking/unliking annotations
 * - Exporting annotations
 */
@HiltViewModel
class AnnotationViewModel @Inject constructor(
    private val annotationRepository: KavitaAnnotationRepository
) : ViewModel() {

    companion object {
        private const val TAG = "AnnotationViewModel"
    }

    private val _uiState = MutableStateFlow(AnnotationUiState())
    val uiState: StateFlow<AnnotationUiState> = _uiState.asStateFlow()

    /**
     * Load all annotations for a chapter.
     */
    fun loadChapterAnnotations(chapterId: Int) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val annotations = annotationRepository.getChapterAnnotations(chapterId)
                _uiState.update {
                    it.copy(
                        annotations = annotations,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chapter annotations: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    /**
     * Load all annotations for a series.
     */
    fun loadSeriesAnnotations(seriesId: Int) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val annotations = annotationRepository.getSeriesAnnotations(seriesId)
                _uiState.update {
                    it.copy(
                        annotations = annotations,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading series annotations: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    // ==================== Create ====================

    /**
     * Show the create annotation dialog after text selection.
     */
    fun showCreateDialog(
        xPath: String? = null,
        selectedText: String,
        pageNumber: Int,
        chapterId: Int
    ) {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                createXPath = xPath,
                createSelectedText = selectedText,
                createPageNumber = pageNumber,
                createChapterId = chapterId,
                createNote = "",
                createColor = "#FFFFEB3B",
                createIsSpoiler = false
            )
        }
    }

    /**
     * Dismiss the create annotation dialog.
     */
    fun dismissCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = false,
                createSelectedText = "",
                createNote = ""
            )
        }
    }

    /**
     * Update the note text while creating an annotation.
     */
    fun updateCreateNote(note: String) {
        _uiState.update { it.copy(createNote = note) }
    }

    /**
     * Update the color while creating an annotation.
     */
    fun updateCreateColor(color: String) {
        _uiState.update { it.copy(createColor = color) }
    }

    /**
     * Toggle spoiler status while creating an annotation.
     */
    fun toggleCreateSpoiler() {
        _uiState.update { it.copy(createIsSpoiler = !it.createIsSpoiler) }
    }

    /**
     * Submit the create annotation request to Kavita.
     */
    fun createAnnotation() {
        val state = _uiState.value
        viewModelScope.launch {
            try {
                val annotation = annotationRepository.createAnnotation(
                    xPath = state.createXPath,
                    selectedText = state.createSelectedText,
                    pageNumber = state.createPageNumber,
                    chapterId = state.createChapterId,
                    color = state.createColor,
                    note = state.createNote.ifBlank { null },
                    isSpoiler = state.createIsSpoiler
                )
                if (annotation != null) {
                    _uiState.update {
                        it.copy(
                            annotations = it.annotations + annotation,
                            showCreateDialog = false,
                            createSelectedText = "",
                            createNote = ""
                        )
                    }
                    Log.d(TAG, "Annotation created: id=${annotation.id}")
                } else {
                    _uiState.update {
                        it.copy(error = "Failed to create annotation on server")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating annotation: ${e.message}", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ==================== Update ====================

    /**
     * Show the edit dialog for an annotation.
     */
    fun showEditDialog(annotation: KavitaAnnotation) {
        _uiState.update {
            it.copy(
                showEditDialog = true,
                editingAnnotation = annotation,
                editNote = annotation.note ?: "",
                editIsSpoiler = annotation.isSpoiler,
                editColor = annotation.color ?: "#FFFFEB3B"
            )
        }
    }

    /**
     * Dismiss the edit dialog.
     */
    fun dismissEditDialog() {
        _uiState.update {
            it.copy(showEditDialog = false, editingAnnotation = null)
        }
    }

    /**
     * Update the note text while editing.
     */
    fun updateEditNote(note: String) {
        _uiState.update { it.copy(editNote = note) }
    }

    /**
     * Update the color while editing.
     */
    fun updateEditColor(color: String) {
        _uiState.update { it.copy(editColor = color) }
    }

    /**
     * Toggle spoiler status while editing.
     */
    fun toggleEditSpoiler() {
        _uiState.update { it.copy(editIsSpoiler = !it.editIsSpoiler) }
    }

    /**
     * Submit the update annotation request.
     */
    fun updateAnnotation() {
        val state = _uiState.value
        val annotationId = state.editingAnnotation?.id ?: return
        viewModelScope.launch {
            try {
                val updated = annotationRepository.updateAnnotation(
                    annotationId = annotationId,
                    note = state.editNote.ifBlank { null },
                    isSpoiler = state.editIsSpoiler,
                    color = state.editColor
                )
                if (updated != null) {
                    _uiState.update { current ->
                        current.copy(
                            annotations = current.annotations.map {
                                if (it.id == annotationId) updated else it
                            },
                            showEditDialog = false,
                            editingAnnotation = null
                        )
                    }
                } else {
                    _uiState.update { it.copy(error = "Failed to update annotation") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating annotation: ${e.message}", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ==================== Delete ====================

    /**
     * Delete an annotation.
     */
    fun deleteAnnotation(annotationId: Int) {
        viewModelScope.launch {
            try {
                val success = annotationRepository.deleteAnnotation(annotationId)
                if (success) {
                    _uiState.update {
                        it.copy(annotations = it.annotations.filter { ann -> ann.id != annotationId })
                    }
                } else {
                    _uiState.update { it.copy(error = "Failed to delete annotation") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting annotation: ${e.message}", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ==================== Like/Unlike ====================

    /**
     * Toggle like on an annotation.
     */
    fun toggleLike(annotation: KavitaAnnotation) {
        viewModelScope.launch {
            try {
                val success = if (annotation.isLiked) {
                    annotationRepository.unlikeAnnotation(annotation.id)
                } else {
                    annotationRepository.likeAnnotation(annotation.id)
                }
                if (success) {
                    _uiState.update { current ->
                        current.copy(
                            annotations = current.annotations.map {
                                if (it.id == annotation.id) {
                                    it.copy(
                                        isLiked = !it.isLiked,
                                        likesCount = if (it.isLiked) it.likesCount - 1 else it.likesCount + 1
                                    )
                                } else it
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling like: ${e.message}", e)
            }
        }
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
