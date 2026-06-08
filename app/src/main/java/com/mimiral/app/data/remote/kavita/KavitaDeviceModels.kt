package com.mimiral.app.data.remote.kavita

/**
 * A Kavita send-to device (e.g. Kindle email, Kobo, etc.).
 *
 * Returned by GET /api/Device/client/devices and used for
 * sending chapters/series to email devices.
 */
data class KavitaDevice(
    val id: Int,
    val name: String,
    val emailAddress: String,
    val deviceType: Int = 0
) {
    companion object {
        const val TYPE_KINDLE = 0
        const val TYPE_KOBO = 1
        const val TYPE_OTHER = 2
    }

    val typeLabel: String
        get() = when (deviceType) {
            TYPE_KINDLE -> "Kindle"
            TYPE_KOBO -> "Kobo"
            else -> "Other"
        }
}

/**
 * Request body for creating a new device.
 * POST /api/Device/create
 */
data class KavitaDeviceCreateRequest(
    val name: String,
    val emailAddress: String,
    val deviceType: Int = 0
)

/**
 * Request body for updating an existing device.
 * POST /api/Device/update
 */
data class KavitaDeviceUpdateRequest(
    val id: Int,
    val name: String,
    val emailAddress: String,
    val deviceType: Int = 0
)

/**
 * Request body for sending a chapter to a device.
 * POST /api/Device/send-to
 */
data class KavitaSendToRequest(
    val chapterId: Int,
    val deviceId: Int
)

/**
 * Request body for sending an entire series to a device.
 * POST /api/Device/send-series-to
 */
data class KavitaSendSeriesToRequest(
    val seriesId: Int,
    val deviceId: Int
)

/**
 * Result of a send-to-device operation.
 */
data class KavitaSendResult(
    val success: Boolean,
    val message: String? = null,
    val errors: List<String>? = null
)
