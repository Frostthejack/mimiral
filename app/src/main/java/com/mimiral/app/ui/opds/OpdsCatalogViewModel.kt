package com.mimiral.app.ui.opds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.OpdsCatalogEntity
import com.mimiral.app.data.repository.OpdsRepository
import com.mimiral.app.data.repository.OpdsValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class OpdsCatalogUiState(
    val catalogs: List<OpdsCatalogEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val validationMessage: String? = null,
    val validationError: String? = null
)

@HiltViewModel
class OpdsCatalogViewModel @Inject constructor(
    private val opdsRepository: OpdsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpdsCatalogUiState())
    val uiState: StateFlow<OpdsCatalogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            opdsRepository.getAllCatalogs()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = e.message ?: "Failed to load catalogs"
                    )
                }
                .collect { catalogs ->
                    _uiState.value = _uiState.value.copy(catalogs = catalogs)
                }
        }
    }

    /**
     * Add a new OPDS catalog. Validates the URL first, then inserts.
     * If validation succeeds and returns a title, uses it as the catalog name
     * (unless a custom name was provided).
     */
    fun addCatalog(
        name: String,
        url: String,
        authType: String = "NONE",
        username: String? = null,
        password: String? = null,
        token: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                validationMessage = null,
                validationError = null,
                errorMessage = null
            )

            val catalog = OpdsCatalogEntity(
                name = name,
                url = url,
                authType = authType,
                username = username,
                password = password,
                token = token
            )

            // Validate the URL by fetching and parsing the feed
            when (val result = opdsRepository.validateCatalogUrl(catalog)) {
                is OpdsValidationResult.Success -> {
                    // Use feed title if no custom name provided
                    val finalName = name.ifBlank { result.title ?: url }
                    val catalogToInsert = catalog.copy(name = finalName)
                    opdsRepository.insertCatalog(catalogToInsert)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        validationMessage = "Catalog added successfully"
                    )
                }
                is OpdsValidationResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        validationError = result.message
                    )
                }
            }
        }
    }

    /**
     * Update an existing catalog. Validates the URL if it changed.
     */
    fun updateCatalog(
        catalog: OpdsCatalogEntity,
        newName: String,
        newUrl: String,
        newAuthType: String,
        newUsername: String?,
        newPassword: String?,
        newToken: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                validationMessage = null,
                validationError = null,
                errorMessage = null
            )

            val updatedCatalog = catalog.copy(
                name = newName,
                url = newUrl,
                authType = newAuthType,
                username = newUsername,
                password = newPassword,
                token = newToken
            )

            // Validate the updated URL
            when (val result = opdsRepository.validateCatalogUrl(updatedCatalog)) {
                is OpdsValidationResult.Success -> {
                    opdsRepository.updateCatalog(updatedCatalog)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        validationMessage = "Catalog updated successfully"
                    )
                }
                is OpdsValidationResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        validationError = result.message
                    )
                }
            }
        }
    }

    /**
     * Delete a catalog.
     */
    fun deleteCatalog(catalog: OpdsCatalogEntity) {
        viewModelScope.launch {
            try {
                opdsRepository.deleteCatalog(catalog)
                _uiState.value = _uiState.value.copy(
                    validationMessage = "Catalog removed"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to delete catalog"
                )
            }
        }
    }

    /**
     * Toggle catalog active status.
     */
    fun toggleCatalogActive(catalog: OpdsCatalogEntity) {
        viewModelScope.launch {
            try {
                opdsRepository.updateCatalog(
                    catalog.copy(isActive = !catalog.isActive)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to update catalog"
                )
            }
        }
    }

    /**
     * Clear validation/error messages.
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            validationMessage = null,
            validationError = null,
            errorMessage = null
        )
    }
}
