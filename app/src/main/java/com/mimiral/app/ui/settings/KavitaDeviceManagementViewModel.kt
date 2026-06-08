package com.mimiral.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.remote.kavita.KavitaDevice
import com.mimiral.app.data.remote.kavita.KavitaDeviceRepository
import com.mimiral.app.data.remote.kavita.KavitaResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KavitaDeviceManagementUiState(
    val devices: List<KavitaDevice> = emptyList(),
    val isLoading: Boolean = false,
    val isAdding: Boolean = false,
    val newDeviceName: String = "",
    val newDeviceEmail: String = "",
    val newDeviceType: Int = 0,
    val error: String? = null
)

@HiltViewModel
class KavitaDeviceManagementViewModel @Inject constructor(
    private val deviceRepository: KavitaDeviceRepository
) : ViewModel() {

    companion object {
        private const val TAG = "KavitaDeviceMgmtVM"
    }

    private val _uiState = MutableStateFlow(KavitaDeviceManagementUiState())
    val uiState: StateFlow<KavitaDeviceManagementUiState> = _uiState.asStateFlow()

    init {
        loadDevices()
    }

    private fun loadDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = deviceRepository.refreshDevices()) {
                is KavitaResult.Success -> {
                    _uiState.update {
                        it.copy(
                            devices = result.data,
                            isLoading = false
                        )
                    }
                }
                is KavitaResult.Error -> {
                    Log.e(TAG, "Failed to load devices: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(newDeviceName = name) }
    }

    fun updateEmail(email: String) {
        _uiState.update { it.copy(newDeviceEmail = email) }
    }

    fun updateDeviceType(type: Int) {
        _uiState.update { it.copy(newDeviceType = type) }
    }

    suspend fun addDevice(): KavitaResult<KavitaDevice> {
        val state = _uiState.value
        _uiState.update { it.copy(isAdding = true) }
        val result = deviceRepository.createDevice(
            name = state.newDeviceName,
            emailAddress = state.newDeviceEmail,
            deviceType = state.newDeviceType
        )
        when (result) {
            is KavitaResult.Success -> {
                _uiState.update {
                    it.copy(
                        isAdding = false,
                        newDeviceName = "",
                        newDeviceEmail = "",
                        newDeviceType = 0
                    )
                }
                loadDevices()
            }
            is KavitaResult.Error -> {
                _uiState.update { it.copy(isAdding = false, error = result.message) }
            }
        }
        return result
    }

    fun deleteDevice(deviceId: Int) {
        viewModelScope.launch {
            when (val result = deviceRepository.deleteDevice(deviceId)) {
                is KavitaResult.Success -> loadDevices()
                is KavitaResult.Error ->
                    _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun updateDevice(id: Int, name: String, email: String, type: Int) {
        viewModelScope.launch {
            when (val result = deviceRepository.updateDevice(id, name, email, type)) {
                is KavitaResult.Success -> loadDevices()
                is KavitaResult.Error ->
                    _uiState.update { it.copy(error = result.message) }
            }
        }
    }
}
