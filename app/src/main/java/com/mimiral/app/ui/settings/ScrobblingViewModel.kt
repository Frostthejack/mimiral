package com.mimiral.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.kavita.KavitaLicenseStatus
import com.mimiral.app.data.remote.kavita.KavitaScrobbleError
import com.mimiral.app.data.remote.kavita.KavitaScrobblingRepository
import com.mimiral.app.data.remote.kavita.KavitaScrobblingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the Kavita scrobbling management screen.
 */
data class ScrobblingUiState(
    // License
    val licenseStatus: KavitaLicenseStatus? = null,
    val isLicenseLoading: Boolean = true,
    val licenseError: String? = null,

    // Settings
    val settings: KavitaScrobblingSettings? = null,
    val isSettingsLoading: Boolean = false,

    // Errors
    val scrobbleErrors: List<KavitaScrobbleError> = emptyList(),
    val isErrorsLoading: Boolean = false,

    // Holds
    val scrobbleHolds: List<Int> = emptyList(),
    val isHoldsLoading: Boolean = false,

    // Token update
    val isUpdatingProvider: Boolean = false,
    val updatingProvider: String? = null,
    val newTokenInput: String = "",
    val tokenUpdateError: String? = null,
    val tokenUpdateSuccess: Boolean = false,

    // Retry
    val retryingErrorId: Int? = null,

    // General
    val errorMessage: String? = null,
    val isLoading: Boolean = false
) {
    /** Whether Kavita+ license is valid and scrobbling endpoints are available. */
    val isKavitaPlus: Boolean get() = licenseStatus?.isValid == true
}

@HiltViewModel
class ScrobblingViewModel @Inject constructor(
    private val scrobblingRepo: KavitaScrobblingRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ScrobblingVM"
    }

    private val _uiState = MutableStateFlow(ScrobblingUiState())
    val uiState: StateFlow<ScrobblingUiState> = _uiState.asStateFlow()

    init {
        checkLicenseAndLoad()
    }

    /**
     * Check the Kavita+ license and, if valid, load all scrobbling data.
     */
    private fun checkLicenseAndLoad() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLicenseLoading = true,
                licenseError = null
            )

            when (val result = scrobblingRepo.validateLicense()) {
                is com.mimiral.app.data.remote.kavita.KavitaResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        licenseStatus = result.data,
                        isLicenseLoading = false
                    )
                    if (result.data.isValid) {
                        loadAll()
                    }
                }
                is com.mimiral.app.data.remote.kavita.KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLicenseLoading = false,
                        licenseError = result.message
                    )
                }
            }
        }
    }

    /**
     * Load all scrobbling data: settings, errors, and holds.
     */
    private fun loadAll() {
        loadSettings()
        loadErrors()
        loadHolds()
    }

    /**
     * Refresh all data (pull-to-refresh).
     */
    fun refresh() {
        checkLicenseAndLoad()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSettingsLoading = true)
            when (val result = scrobblingRepo.getScrobbleSettings()) {
                is com.mimiral.app.data.remote.kavita.KavitaResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        settings = result.data,
                        isSettingsLoading = false
                    )
                }
                is com.mimiral.app.data.remote.kavita.KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSettingsLoading = false,
                        errorMessage = result.message
                    )
                    Log.e(TAG, "Failed to load settings: ${result.message}")
                }
            }
        }
    }

    private fun loadErrors() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isErrorsLoading = true)
            when (val result = scrobblingRepo.getScrobbleErrors()) {
                is com.mimiral.app.data.remote.kavita.KavitaResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        scrobbleErrors = result.data,
                        isErrorsLoading = false
                    )
                }
                is com.mimiral.app.data.remote.kavita.KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isErrorsLoading = false,
                        errorMessage = result.message
                    )
                    Log.e(TAG, "Failed to load errors: ${result.message}")
                }
            }
        }
    }

    private fun loadHolds() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isHoldsLoading = true)
            when (val result = scrobblingRepo.getScrobbleHolds()) {
                is com.mimiral.app.data.remote.kavita.KavitaResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        scrobbleHolds = result.data,
                        isHoldsLoading = false
                    )
                }
                is com.mimiral.app.data.remote.kavita.KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isHoldsLoading = false,
                        errorMessage = result.message
                    )
                    Log.e(TAG, "Failed to load holds: ${result.message}")
                }
            }
        }
    }

    // ── Token Management ──────────────────────────────────────

    fun setNewTokenInput(token: String) {
        _uiState.value = _uiState.value.copy(
            newTokenInput = token,
            tokenUpdateError = null,
            tokenUpdateSuccess = false
        )
    }

    /**
     * Update a scrobble provider token.
     *
     * @param provider Provider name (e.g., "AniList", "Mal", "GoogleBooks")
     */
    fun updateProviderToken(provider: String) {
        val token = _uiState.value.newTokenInput.trim()
        if (token.isBlank()) {
            _uiState.value = _uiState.value.copy(
                tokenUpdateError = "Token cannot be empty"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUpdatingProvider = true,
                updatingProvider = provider,
                tokenUpdateError = null,
                tokenUpdateSuccess = false
            )

            when (val result = scrobblingRepo.updateScrobbleProvider(provider, token)) {
                is com.mimiral.app.data.remote.kavita.KavitaResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdatingProvider = false,
                        updatingProvider = null,
                        newTokenInput = "",
                        tokenUpdateSuccess = true
                    )
                    // Reload settings to reflect the updated token
                    loadSettings()
                }
                is com.mimiral.app.data.remote.kavita.KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdatingProvider = false,
                        updatingProvider = null,
                        tokenUpdateError = result.message
                    )
                    Log.e(TAG, "Failed to update provider token: ${result.message}")
                }
            }
        }
    }

    // ── Scrobble Hold Toggle ──────────────────────────────────

    /**
     * Toggle a scrobble hold on a series.
     * If held, remove the hold. If not held, add the hold.
     */
    fun toggleHold(seriesId: Int) {
        val isCurrentlyHeld = _uiState.value.scrobbleHolds.contains(seriesId)
        viewModelScope.launch {
            val result = if (isCurrentlyHeld) {
                scrobblingRepo.removeScrobbleHold(seriesId)
            } else {
                scrobblingRepo.addScrobbleHold(seriesId)
            }

            when (result) {
                is com.mimiral.app.data.remote.kavita.KavitaResult.Success -> {
                    val currentHolds = _uiState.value.scrobbleHolds.toMutableList()
                    if (isCurrentlyHeld) {
                        currentHolds.remove(seriesId)
                    } else {
                        currentHolds.add(seriesId)
                    }
                    _uiState.value = _uiState.value.copy(scrobbleHolds = currentHolds)
                }
                is com.mimiral.app.data.remote.kavita.KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.message
                    )
                    Log.e(TAG, "Failed to toggle hold: ${result.message}")
                }
            }
        }
    }

    // ── Retry Scrobble ────────────────────────────────────────

    /**
     * Retry a failed scrobble.
     */
    fun retryError(errorId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(retryingErrorId = errorId)

            when (val result = scrobblingRepo.retryScrobble(errorId)) {
                is com.mimiral.app.data.remote.kavita.KavitaResult.Success -> {
                    // Remove the retried error from the list
                    _uiState.value = _uiState.value.copy(
                        retryingErrorId = null,
                        scrobbleErrors = _uiState.value.scrobbleErrors
                            .filter { it.id != errorId }
                    )
                }
                is com.mimiral.app.data.remote.kavita.KavitaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        retryingErrorId = null,
                        errorMessage = result.message
                    )
                    Log.e(TAG, "Failed to retry scrobble: ${result.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
