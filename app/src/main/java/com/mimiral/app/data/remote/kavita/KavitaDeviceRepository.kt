package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.mimiral.app.data.local.dao.ServerDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Repository for Kavita send-to-device operations.
 *
 * Manages:
 * - Device registration (create, update, delete)
 * - Device listing
 * - Sending chapters and series to registered devices
 *
 * Requires server email configuration on the Kavita server side.
 */
@Singleton
class KavitaDeviceRepository @Inject constructor(
    private val kavitaClient: KavitaClient,
    private val serverDao: ServerDao
) {
    companion object {
        private const val TAG = "KavitaDeviceRepo"
    }

    private val _devices = MutableStateFlow<List<KavitaDevice>>(emptyList())
    val devices: StateFlow<List<KavitaDevice>> = _devices.asStateFlow()

    private val _sendState = MutableStateFlow<DeviceSendState>(DeviceSendState.Idle)
    val sendState: StateFlow<DeviceSendState> = _sendState.asStateFlow()

    /**
     * Create a KavitaClient from the active server configuration.
     */
    private suspend fun createClientFromDb(): KavitaClient? {
        val activeServer = withContext(Dispatchers.IO) {
            serverDao.getActiveServerByType("KAVITA")
        } ?: return null
        if (activeServer.url.isBlank()) return null
        return KavitaClient.create(
            baseUrl = activeServer.url,
            apiKey = activeServer.apiKey
        )
    }

    /**
     * Fetch all registered devices and update the devices state flow.
     */
    suspend fun refreshDevices(): KavitaResult<List<KavitaDevice>> {
        val client = createClientFromDb() ?: return KavitaResult.Error(
            message = "No Kavita server configured. Please add a Kavita server in Settings."
        )
        val result = client.getDevices()
        when (result) {
            is KavitaResult.Success -> _devices.value = result.data
            is KavitaResult.Error -> Log.e(TAG, "Failed to fetch devices: ${result.message}")
        }
        return result
    }

    /**
     * Register a new device.
     *
     * @param name Device display name
     * @param emailAddress Device email (e.g. Kindle email)
     * @param deviceType Device type (0=Kindle, 1=Kobo, 2=Other)
     */
    suspend fun createDevice(
        name: String,
        emailAddress: String,
        deviceType: Int = 0
    ): KavitaResult<KavitaDevice> {
        val client = createClientFromDb() ?: return KavitaResult.Error(
            message = "No Kavita server configured."
        )
        val request = KavitaDeviceCreateRequest(
            name = name,
            emailAddress = emailAddress,
            deviceType = deviceType
        )
        val result = client.createDevice(request)
        if (result is KavitaResult.Success) {
            // Refresh the device list after creation
            refreshDevices()
        }
        return result
    }

    /**
     * Update an existing device.
     */
    suspend fun updateDevice(
        id: Int,
        name: String,
        emailAddress: String,
        deviceType: Int = 0
    ): KavitaResult<KavitaDevice> {
        val client = createClientFromDb() ?: return KavitaResult.Error(
            message = "No Kavita server configured."
        )
        val request = KavitaDeviceUpdateRequest(
            id = id,
            name = name,
            emailAddress = emailAddress,
            deviceType = deviceType
        )
        val result = client.updateDevice(request)
        if (result is KavitaResult.Success) {
            refreshDevices()
        }
        return result
    }

    /**
     * Delete a device by ID.
     */
    suspend fun deleteDevice(deviceId: Int): KavitaResult<Unit> {
        val client = createClientFromDb() ?: return KavitaResult.Error(
            message = "No Kavita server configured."
        )
        val result = client.deleteDevice(deviceId)
        if (result is KavitaResult.Success) {
            refreshDevices()
        }
        return result
    }

    /**
     * Send a chapter to a device.
     *
     * @param chapterId The Kavita chapter ID
     * @param deviceId The target device ID
     */
    suspend fun sendChapterToDevice(
        chapterId: Int,
        deviceId: Int
    ): KavitaResult<Unit> {
        _sendState.value = DeviceSendState.Sending("chapter")
        val client = createClientFromDb() ?: run {
            _sendState.value = DeviceSendState.Error("No Kavita server configured.")
            return KavitaResult.Error(message = "No Kavita server configured.")
        }
        val result = client.sendToDevice(KavitaSendToRequest(chapterId, deviceId))
        _sendState.value = when (result) {
            is KavitaResult.Success -> DeviceSendState.Sent
            is KavitaResult.Error -> DeviceSendState.Error(result.message)
        }
        return result
    }

    /**
     * Send an entire series to a device.
     *
     * @param seriesId The Kavita series ID
     * @param deviceId The target device ID
     */
    suspend fun sendSeriesToDevice(
        seriesId: Int,
        deviceId: Int
    ): KavitaResult<Unit> {
        _sendState.value = DeviceSendState.Sending("series")
        val client = createClientFromDb() ?: run {
            _sendState.value = DeviceSendState.Error("No Kavita server configured.")
            return KavitaResult.Error(message = "No Kavita server configured.")
        }
        val result = client.sendSeriesToDevice(KavitaSendSeriesToRequest(seriesId, deviceId))
        _sendState.value = when (result) {
            is KavitaResult.Success -> DeviceSendState.Sent
            is KavitaResult.Error -> DeviceSendState.Error(result.message)
        }
        return result
    }

    /**
     * Reset send state to idle.
     */
    fun resetSendState() {
        _sendState.value = DeviceSendState.Idle
    }
}

/**
 * State of a send-to-device operation.
 */
sealed class DeviceSendState {
    object Idle : DeviceSendState()
    data class Sending(val targetType: String) : DeviceSendState()
    object Sent : DeviceSendState()
    data class Error(val message: String) : DeviceSendState()
}
