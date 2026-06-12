package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.mimiral.app.data.local.dao.ServerDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository for marking chapters, volumes, and series as read or unread in Kavita.
 *
 * Orchestrates:
 * - Marking individual chapters as read
 * - Marking volumes as read or unread
 * - Marking series as read or unread
 * - Bulk marking multiple series as read or unread
 * - Catch-up marking (mark all chapters up to a point as read)
 * - Local progress state refresh after marking operations
 *
 * After each marking operation, the [uiState] is updated to reflect
 * the operation result, allowing the UI to refresh progress indicators.
 */
@Singleton
class KavitaMarkReadRepository @Inject constructor(
    private val client: KavitaMarkReadClient,
    private val serverDao: ServerDao
) {
    companion object {
        private const val TAG = "KavitaMarkRead"
    }

    private val _uiState = MutableStateFlow(KavitaMarkReadUiState())
    val uiState: StateFlow<KavitaMarkReadUiState> = _uiState.asStateFlow()

    // Serializes initClient() + the operation so concurrent calls cannot
    // overwrite each other's configure() state on the shared client.
    private val operationMutex = Mutex()

    /**
     * Initialize the Kavita client from the active server configuration.
     */
    private suspend fun initClient(): Boolean {
        val server = serverDao.getActiveServerByType("KAVITA") ?: run {
            Log.w(TAG, "No active Kavita server configured")
            return false
        }
        client.configure(
            url = server.url,
            key = server.apiKey,
            token = server.jwtToken,
            user = server.username,
            pass = server.password
        )
        return true
    }

    /**
     * Mark a single chapter as read.
     *
     * POST /api/Reader/mark-chapter-read
     *
     * @param chapterId The Kavita chapter ID
     * @return True if operation succeeded, false otherwise
     */
    suspend fun markChapterRead(chapterId: Int): Boolean = operationMutex.withLock {
        if (!initClient()) return@withLock false
        _uiState.value = _uiState.value.copy(isMarking = true, errorMessage = null)

        return when (val result = client.markChapterRead(chapterId)) {
            is KavitaResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    lastOperation = MarkReadOperation.ChapterMarkedRead(chapterId)
                )
                Log.d(TAG, "Marked chapter $chapterId as read")
                true
            }
            is KavitaResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    errorMessage = result.message
                )
                Log.w(TAG, "Failed to mark chapter $chapterId as read: ${result.message}")
                false
            }
        }
    }

    /**
     * Mark a volume as read.
     *
     * POST /api/Reader/mark-volume-read
     *
     * @param volumeId The Kavita volume ID
     * @return True if operation succeeded, false otherwise
     */
    suspend fun markVolumeRead(volumeId: Int): Boolean = operationMutex.withLock {
        if (!initClient()) return@withLock false
        _uiState.value = _uiState.value.copy(isMarking = true, errorMessage = null)

        return when (val result = client.markVolumeRead(volumeId)) {
            is KavitaResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    lastOperation = MarkReadOperation.VolumeMarkedRead(volumeId)
                )
                Log.d(TAG, "Marked volume $volumeId as read")
                true
            }
            is KavitaResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    errorMessage = result.message
                )
                Log.w(TAG, "Failed to mark volume $volumeId as read: ${result.message}")
                false
            }
        }
    }

    /**
     * Mark a volume as unread.
     *
     * POST /api/Reader/mark-volume-unread
     *
     * @param volumeId The Kavita volume ID
     * @return True if operation succeeded, false otherwise
     */
    suspend fun markVolumeUnread(volumeId: Int): Boolean = operationMutex.withLock {
        if (!initClient()) return@withLock false
        _uiState.value = _uiState.value.copy(isMarking = true, errorMessage = null)

        return when (val result = client.markVolumeUnread(volumeId)) {
            is KavitaResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    lastOperation = MarkReadOperation.VolumeMarkedUnread(volumeId)
                )
                Log.d(TAG, "Marked volume $volumeId as unread")
                true
            }
            is KavitaResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    errorMessage = result.message
                )
                Log.w(TAG, "Failed to mark volume $volumeId as unread: ${result.message}")
                false
            }
        }
    }

    /**
     * Mark an entire series as read.
     *
     * POST /api/Series/mark-read
     *
     * @param seriesId The Kavita series ID
     * @return True if operation succeeded, false otherwise
     */
    suspend fun markSeriesRead(seriesId: Int): Boolean = operationMutex.withLock {
        if (!initClient()) return@withLock false
        _uiState.value = _uiState.value.copy(isMarking = true, errorMessage = null)

        return when (val result = client.markSeriesRead(seriesId)) {
            is KavitaResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    lastOperation = MarkReadOperation.SeriesMarkedRead(seriesId)
                )
                Log.d(TAG, "Marked series $seriesId as read")
                true
            }
            is KavitaResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    errorMessage = result.message
                )
                Log.w(TAG, "Failed to mark series $seriesId as read: ${result.message}")
                false
            }
        }
    }

    /**
     * Mark an entire series as unread.
     *
     * POST /api/Series/mark-unread
     *
     * @param seriesId The Kavita series ID
     * @return True if operation succeeded, false otherwise
     */
    suspend fun markSeriesUnread(seriesId: Int): Boolean = operationMutex.withLock {
        if (!initClient()) return@withLock false
        _uiState.value = _uiState.value.copy(isMarking = true, errorMessage = null)

        return when (val result = client.markSeriesUnread(seriesId)) {
            is KavitaResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    lastOperation = MarkReadOperation.SeriesMarkedUnread(seriesId)
                )
                Log.d(TAG, "Marked series $seriesId as unread")
                true
            }
            is KavitaResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    errorMessage = result.message
                )
                Log.w(TAG, "Failed to mark series $seriesId as unread: ${result.message}")
                false
            }
        }
    }

    /**
     * Bulk mark multiple series as read.
     *
     * POST /api/Series/mark-multiple-series-read
     *
     * @param seriesIds List of Kavita series IDs
     * @return True if operation succeeded, false otherwise
     */
    suspend fun markMultipleSeriesRead(seriesIds: List<Int>): Boolean = operationMutex.withLock {
        if (!initClient()) return@withLock false
        _uiState.value = _uiState.value.copy(isMarking = true, errorMessage = null)

        return when (val result = client.markMultipleSeriesRead(seriesIds)) {
            is KavitaResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    lastOperation = MarkReadOperation.BulkSeriesMarkedRead(seriesIds.size)
                )
                Log.d(TAG, "Marked ${seriesIds.size} series as read")
                true
            }
            is KavitaResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    errorMessage = result.message
                )
                Log.w(TAG, "Failed to bulk mark series read: ${result.message}")
                false
            }
        }
    }

    /**
     * Bulk mark multiple series as unread.
     *
     * POST /api/Series/mark-multiple-series-unread
     *
     * @param seriesIds List of Kavita series IDs
     * @return True if operation succeeded, false otherwise
     */
    suspend fun markMultipleSeriesUnread(seriesIds: List<Int>): Boolean = operationMutex.withLock {
        if (!initClient()) return@withLock false
        _uiState.value = _uiState.value.copy(isMarking = true, errorMessage = null)

        return when (val result = client.markMultipleSeriesUnread(seriesIds)) {
            is KavitaResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    lastOperation = MarkReadOperation.BulkSeriesMarkedUnread(seriesIds.size)
                )
                Log.d(TAG, "Marked ${seriesIds.size} series as unread")
                true
            }
            is KavitaResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    errorMessage = result.message
                )
                Log.w(TAG, "Failed to bulk mark series unread: ${result.message}")
                false
            }
        }
    }

    /**
     * Catch-up: mark all chapters up to and including the specified chapter as read.
     *
     * POST /api/Tachiyomi/mark-chapter-until-as-read
     *
     * @param seriesId The Kavita series ID
     * @param chapterId The chapter ID to mark up to (inclusive)
     * @param volumesToInclude Number of volumes to include
     * @return True if operation succeeded, false otherwise
     */
    suspend fun markChapterUntilRead(
        seriesId: Int,
        chapterId: Int,
        volumesToInclude: Int = 0
    ): Boolean = operationMutex.withLock {
        if (!initClient()) return@withLock false
        _uiState.value = _uiState.value.copy(isMarking = true, errorMessage = null)

        return when (
            val result = client.markChapterUntilRead(
                seriesId = seriesId,
                chapterId = chapterId,
                volumesToInclude = volumesToInclude
            )
        ) {
            is KavitaResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    lastOperation = MarkReadOperation.CatchUpMarkedRead(
                        seriesId = seriesId,
                        upToChapterId = chapterId
                    )
                )
                Log.d(TAG, "Catch-up marked series $seriesId up to chapter $chapterId as read")
                true
            }
            is KavitaResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isMarking = false,
                    errorMessage = result.message
                )
                Log.w(
                    TAG,
                    "Failed catch-up mark for series $seriesId: ${result.message}"
                )
                false
            }
        }
    }

    /**
     * Clear the current error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Reset the operation state.
     */
    fun resetState() {
        _uiState.value = KavitaMarkReadUiState()
    }
}
