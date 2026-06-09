package com.mimiral.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.kavita.KavitaAuthService
import com.mimiral.app.data.remote.kavita.KavitaOpdsFeedCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the Kavita OPDS Feed Categories screen.
 */
data class KavitaOpdsFeedUiState(
    val opdsBaseUrl: String? = null,
    val isAuthed: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel that resolves the Kavita OPDS base URL from auth state
 * and provides it for constructing category-specific feed URLs.
 */
@HiltViewModel
class KavitaOpdsFeedViewModel @Inject constructor(
    private val authService: KavitaAuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow(KavitaOpdsFeedUiState())
    val uiState: StateFlow<KavitaOpdsFeedUiState> = _uiState.asStateFlow()

    init {
        resolveOpdsUrl()
    }

    /**
     * Resolve the OPDS base URL from auth state.
     * If not yet derived, trigger derivation.
     */
    private fun resolveOpdsUrl() {
        viewModelScope.launch {
            val state = authService.authState
            val existingUrl = state.opdsUrl

            if (!existingUrl.isNullOrBlank()) {
                _uiState.value = KavitaOpdsFeedUiState(
                    opdsBaseUrl = existingUrl,
                    isAuthed = true,
                    isLoading = false
                )
            } else if (state.isAuthenticated) {
                // Try to derive OPDS URL
                val result = authService.deriveOpdsUrl()
                if (result.isSuccess) {
                    _uiState.value = KavitaOpdsFeedUiState(
                        opdsBaseUrl = result.getOrDefault(""),
                        isAuthed = true,
                        isLoading = false
                    )
                } else {
                    _uiState.value = KavitaOpdsFeedUiState(
                        isAuthed = true,
                        isLoading = false,
                        error = "Could not resolve OPDS URL. Check your Kavita server settings."
                    )
                }
            } else {
                _uiState.value = KavitaOpdsFeedUiState(
                    isLoading = false,
                    error = "Not connected to Kavita. Configure your server in Settings."
                )
            }
        }
    }

    /**
     * Build the full OPDS feed URL for a given category.
     * Returns null if the OPDS base URL is not yet resolved.
     */
    fun buildFeedUrl(category: KavitaOpdsFeedCategory): String? {
        val baseUrl = _uiState.value.opdsBaseUrl ?: return null
        return category.buildFeedUrl(baseUrl)
    }

    /**
     * Retry resolving the OPDS URL (e.g. after user configures server).
     */
    fun retry() {
        _uiState.value = KavitaOpdsFeedUiState(isLoading = true)
        resolveOpdsUrl()
    }
}
