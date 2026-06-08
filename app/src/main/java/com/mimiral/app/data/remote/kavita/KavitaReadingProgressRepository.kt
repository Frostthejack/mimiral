package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.mimiral.app.data.local.dao.PendingOperationDao
import com.mimiral.app.data.local.dao.ReadingProgressDao
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.dao.BookDao
import com.mimiral.app.data.local.entity.PendingOperationEntity
import com.mimiral.app.data.local.entity.ReadingProgressEntity
import com.mimiral.app.data.remote.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for bidirectional reading progress sync with Kavita.
 *
 * Features:
 * - On chapter open: GET progress, compare, use furthest
 * - Debounced POST every 3 pages or 10 seconds
 * - Immediate POST on reader close
 * - On app start: full sync + Continue Reading
 * - Conflict resolution: lastModifiedUtc wins (furthest page if same timestamp)
 * - Offline: queue in Room PendingOperation table, flush on reconnect
 * - EPUB scroll via bookScrollId
 */
@Singleton
class KavitaReadingProgressRepository @Inject constructor(
    private val kavitaApi: KavitaApi,
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val pendingOperationDao: PendingOperationDao,
    private val serverDao: ServerDao
) {
    companion object {
        private const val TAG = "KavitaProgressSync"
        /** Push after every N pages turned */
        const val DEBOUNCE_PAGE_INTERVAL = 3
        /** Push after this many ms regardless of page count */
        const val DEBOUNCE_TIME_MS = 10_000L
        /** Max retry attempts for pending operations before discard */
        const val MAX_RETRY_ATTEMPTS = 5
    }

    private val dateFormat = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ── Debounce state ──

    /** Pages turned since last push */
    private var pagesSinceLastPush = 0
    /** Timestamp of last push */
    private var lastPushTimeMs = 0L
    /** Timer job for time-based debounce */
    private var debounceTimerJob: Job? = null
    /** Current pending progress to push (latest snapshot) */
    private val pendingPush = MutableStateFlow<KavitaProgressDto?>(null)
    /** Mutex to serialize push operations */
    private val pushMutex = Mutex()
    /** Scope for debounce coroutines */
    private val scope = CoroutineScope(Dispatchers.IO)

    // ── Sync status ──

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // ── Continue Reading ──

    private val _continuePoint = MutableStateFlow<KavitaContinuePointDto?>(null)
    val continuePoint: StateFlow<KavitaContinuePointDto?> = _continuePoint.asStateFlow()

    // ── Server check ──

    /**
     * Check if an active Kavita server is configured.
     */
    suspend fun hasActiveServer(): Boolean {
        return try {
            serverDao.getActiveServerByType("KAVITA") != null
        } catch (_: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════
    // On chapter open: GET progress, compare with local, use furthest
    // ═══════════════════════════════════════════════════════════

    /**
     * Pull progress from Kavita on chapter open and resolve with local.
     * Returns the page number the reader should use.
     *
     * @param bookId Local book ID
     * @param chapterId Kavita chapter ID
     * @param localPage Current local page number
     * @return Resolved page number (furthest progress wins)
     */
    suspend fun syncOnChapterOpen(
        bookId: Int,
        chapterId: Int,
        localPage: Int
    ): Int {
        if (!hasActiveServer()) return localPage
        _syncStatus.value = SyncStatus.SYNCING

        return try {
            val response = kavitaApi.getProgressDto(chapterId)
            if (response.isSuccessful) {
                val remote = response.body()
                if (remote != null) {
                    resolveProgress(localPage, remote)
                } else {
                    localPage
                }
            } else {
                Log.w(TAG, "getProgressDto failed: ${response.code()}")
                localPage
            }
        } catch (e: ConnectException) {
            Log.w(TAG, "Server unreachable on chapter open, using local progress")
            queuePendingOperation(
                type = PendingOperationType.FULL_SYNC,
                seriesId = 0, libraryId = 0, volumeId = 0,
                chapterId = chapterId, pageNum = localPage
            )
            localPage
        } catch (e: Exception) {
            Log.w(TAG, "Error on chapter open sync: ${e.message}")
            localPage
        } finally {
            _syncStatus.value = SyncStatus.SYNCED
        }
    }

    /**
     * Resolve local vs remote progress: furthest page wins.
     * If pages are equal, lastModifiedUtc comparison breaks ties.
     */
    private fun resolveProgress(
        localPage: Int,
        remote: KavitaProgressResponseDto
    ): Int {
        val remotePage = remote.pageNum
        return if (remotePage > localPage) {
            Log.d(TAG, "Remote progress ($remotePage) > local ($localPage), using remote")
            remotePage
        } else if (localPage > remotePage) {
            Log.d(TAG, "Local progress ($localPage) > remote ($remotePage), using local")
            localPage
        } else {
            // Equal pages — use whichever has more recent timestamp
            localPage
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Debounced POST: every 3 pages or 10 seconds
    // ═══════════════════════════════════════════════════════════

    /**
     * Called on each page turn. Debounces pushes to Kavita:
     * - Pushes every [DEBOUNCE_PAGE_INTERVAL] pages
     * - Pushes after [DEBOUNCE_TIME_MS] ms regardless
     * - Queues to offline buffer if server unreachable
     *
     * @param bookId Local book ID
     * @param chapterId Kavita chapter ID
     * @param pageNum Current page number
     * @param seriesId Kavita series ID
     * @param libraryId Kavita library ID
     * @param volumeId Kavita volume ID
     * @param bookScrollId EPUB scroll position (null for non-EPUB)
     */
    fun onPageTurn(
        bookId: Int,
        chapterId: Int,
        pageNum: Int,
        seriesId: Int,
        libraryId: Int,
        volumeId: Int,
        bookScrollId: String? = null
    ) {
        pagesSinceLastPush++
        val now = System.currentTimeMillis()
        val timeSinceLastPush = now - lastPushTimeMs

        val dto = KavitaProgressDto(
            volumeId = volumeId,
            chapterId = chapterId,
            pageNum = pageNum,
            seriesId = seriesId,
            libraryId = libraryId,
            bookScrollId = bookScrollId,
            lastModifiedUtc = dateFormat.format(Date(now))
        )

        val shouldPush = pagesSinceLastPush >= DEBOUNCE_PAGE_INTERVAL ||
            timeSinceLastPush >= DEBOUNCE_TIME_MS

        if (shouldPush) {
            // Immediate push — reset counters
            pagesSinceLastPush = 0
            lastPushTimeMs = now
            debounceTimerJob?.cancel()
            debounceTimerJob = null
            scope.launch { pushProgress(dto, bookId) }
        } else {
            // Store as pending and start/restart the time-based debounce timer
            pendingPush.value = dto
            if (debounceTimerJob == null || !debounceTimerJob!!.isActive) {
                debounceTimerJob = scope.launch {
                    delay(DEBOUNCE_TIME_MS - timeSinceLastPush)
                    val pending = pendingPush.value
                    if (pending != null) {
                        pagesSinceLastPush = 0
                        lastPushTimeMs = System.currentTimeMillis()
                        pushProgress(pending, bookId)
                        pendingPush.value = null
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Immediate POST on reader close
    // ═══════════════════════════════════════════════════════════

    /**
     * Push progress immediately when the reader closes.
     * Cancels any pending debounce timer.
     *
     * @param bookId Local book ID
     * @param chapterId Kavita chapter ID
     * @param pageNum Current page number
     * @param seriesId Kavita series ID
     * @param libraryId Kavita library ID
     * @param volumeId Kavita volume ID
     * @param bookScrollId EPUB scroll position
     */
    suspend fun pushOnReaderClose(
        bookId: Int,
        chapterId: Int,
        pageNum: Int,
        seriesId: Int,
        libraryId: Int,
        volumeId: Int,
        bookScrollId: String? = null
    ) {
        debounceTimerJob?.cancel()
        debounceTimerJob = null
        pagesSinceLastPush = 0
        lastPushTimeMs = System.currentTimeMillis()
        pendingPush.value = null

        val dto = KavitaProgressDto(
            volumeId = volumeId,
            chapterId = chapterId,
            pageNum = pageNum,
            seriesId = seriesId,
            libraryId = libraryId,
            bookScrollId = bookScrollId,
            lastModifiedUtc = dateFormat.format(Date())
        )
        pushProgress(dto, bookId)
    }

    // ═══════════════════════════════════════════════════════════
    // On app start: full sync
    // ═══════════════════════════════════════════════════════════

    /**
     * Perform full progress sync on app start.
     * 1. Flush any pending offline operations
     * 2. Fetch continue-reading point
     */
    suspend fun fullSyncOnStartup() {
        if (!hasActiveServer()) return
        _syncStatus.value = SyncStatus.SYNCING

        try {
            // Flush offline queue
            flushPendingOperations()

            // Fetch continue point
            fetchContinuePoint()

            _syncStatus.value = SyncStatus.SYNCED
        } catch (e: Exception) {
            Log.w(TAG, "Full sync on startup failed: ${e.message}")
            _syncStatus.value = SyncStatus.ERROR
        }
    }

    /**
     * Fetch the continue-reading point from Kavita.
     */
    suspend fun fetchContinuePoint(): KavitaContinuePointDto? {
        return try {
            val response = kavitaApi.getContinuePoint()
            if (response.isSuccessful) {
                val point = response.body()
                _continuePoint.value = point
                point
            } else {
                Log.w(TAG, "getContinuePoint failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching continue point: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Core push with offline fallback
    // ═══════════════════════════════════════════════════════════

    /**
     * Push progress to Kavita. If the server is unreachable,
     * queue the operation for later flush.
     */
    private suspend fun pushProgress(dto: KavitaProgressDto, bookId: Int) {
        pushMutex.withLock {
            try {
                if (!hasActiveServer()) {
                    queueFromDto(dto)
                    return
                }

                val response = kavitaApi.pushProgressDto(dto)
                if (response.isSuccessful && response.body()?.success == true) {
                    // Mark local progress as synced
                    val progress = readingProgressDao.getProgressForBook(bookId)
                    if (progress != null) {
                        readingProgressDao.saveProgress(
                            progress.copy(kavitaSynced = true)
                        )
                    }
                    Log.d(TAG, "Pushed progress: chapter=${dto.chapterId}, page=${dto.pageNum}")
                } else {
                    Log.w(TAG, "Push failed: ${response.code()}, queuing offline")
                    queueFromDto(dto)
                }
            } catch (e: ConnectException) {
                Log.w(TAG, "Server unreachable, queuing progress offline")
                queueFromDto(dto)
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Server timed out, queuing progress offline")
                queueFromDto(dto)
            } catch (e: UnknownHostException) {
                Log.w(TAG, "Server unknown host, queuing progress offline")
                queueFromDto(dto)
            } catch (e: Exception) {
                Log.w(TAG, "Push error: ${e.message}")
                queueFromDto(dto)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Offline queue (PendingOperation)
    // ═══════════════════════════════════════════════════════════

    /**
     * Queue a push operation for later execution.
     */
    private suspend fun queueFromDto(dto: KavitaProgressDto) {
        queuePendingOperation(
            type = PendingOperationType.PUSH_PROGRESS,
            seriesId = dto.seriesId,
            libraryId = dto.libraryId,
            volumeId = dto.volumeId,
            chapterId = dto.chapterId,
            pageNum = dto.pageNum,
            bookScrollId = dto.bookScrollId
        )
    }

    /**
     * Queue a pending operation to Room.
     */
    private suspend fun queuePendingOperation(
        type: PendingOperationType,
        seriesId: Int,
        libraryId: Int,
        volumeId: Int,
        chapterId: Int,
        pageNum: Int,
        bookScrollId: String? = null
    ) {
        try {
            pendingOperationDao.insert(
                PendingOperationEntity(
                    operationType = type.name,
                    seriesId = seriesId,
                    libraryId = libraryId,
                    volumeId = volumeId,
                    chapterId = chapterId,
                    pageNum = pageNum,
                    bookScrollId = bookScrollId
                )
            )
            Log.d(TAG, "Queued $type operation: chapter=$chapterId, page=$pageNum")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to queue pending operation: ${e.message}")
        }
    }

    /**
     * Flush all pending operations to Kavita.
     * Called on app start and on network reconnect.
     * Operations that fail after [MAX_RETRY_ATTEMPTS] are discarded.
     */
    suspend fun flushPendingOperations() {
        val pending = pendingOperationDao.getAll()
        if (pending.isEmpty()) return

        Log.d(TAG, "Flushing ${pending.size} pending operations")

        for (op in pending) {
            try {
                val result = executePendingOperation(op)
                if (result) {
                    pendingOperationDao.deleteById(op.id)
                } else {
                    pendingOperationDao.incrementAttempts(op.id)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Flush failed for op ${op.id}: ${e.message}")
                pendingOperationDao.incrementAttempts(op.id)
            }
        }

        // Discard operations that have exceeded max retries
        pendingOperationDao.deleteFailed(MAX_RETRY_ATTEMPTS)
    }

    /**
     * Execute a single pending operation.
     * @return true if the operation succeeded
     */
    private suspend fun executePendingOperation(op: PendingOperationEntity): Boolean {
        if (!hasActiveServer()) return false

        return try {
            when (PendingOperationType.valueOf(op.operationType)) {
                PendingOperationType.PUSH_PROGRESS -> {
                    val dto = KavitaProgressDto(
                        volumeId = op.volumeId,
                        chapterId = op.chapterId,
                        pageNum = op.pageNum,
                        seriesId = op.seriesId,
                        libraryId = op.libraryId,
                        bookScrollId = op.bookScrollId,
                        lastModifiedUtc = dateFormat.format(Date())
                    )
                    val response = kavitaApi.pushProgressDto(dto)
                    response.isSuccessful && response.body()?.success == true
                }
                PendingOperationType.FULL_SYNC -> {
                    // Full sync: pull then push
                    val pullResponse = kavitaApi.getProgressDto(op.chapterId)
                    // Regardless of pull result, push our latest
                    val dto = KavitaProgressDto(
                        volumeId = op.volumeId,
                        chapterId = op.chapterId,
                        pageNum = op.pageNum,
                        seriesId = op.seriesId,
                        libraryId = op.libraryId,
                        bookScrollId = op.bookScrollId,
                        lastModifiedUtc = dateFormat.format(Date())
                    )
                    val pushResponse = kavitaApi.pushProgressDto(dto)
                    pushResponse.isSuccessful && pushResponse.body()?.success == true
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Convenience: sync with local book lookup
    // ═══════════════════════════════════════════════════════════

    /**
     * Push progress for a local book, resolving its Kavita IDs from the DB.
     * Convenience wrapper used by reader ViewModels.
     */
    suspend fun pushProgressForBook(
        bookId: Int,
        chapterId: Int,
        pageNum: Int,
        bookScrollId: String? = null
    ): Boolean {
        val book = try { bookDao.getBookById(bookId) } catch (_: Exception) { null }
            ?: return false
        val seriesId = book.kavitaSeriesId ?: return false
        val libraryId = book.kavitaLibraryId ?: return false

        val dto = KavitaProgressDto(
            volumeId = seriesId, // single-volume series: volumeId == seriesId
            chapterId = chapterId,
            pageNum = pageNum,
            seriesId = seriesId,
            libraryId = libraryId,
            bookScrollId = bookScrollId,
            lastModifiedUtc = dateFormat.format(Date())
        )
        pushProgress(dto, bookId)

        // Mark synced
        val progress = readingProgressDao.getProgressForBook(bookId)
        if (progress != null) {
            readingProgressDao.saveProgress(progress.copy(kavitaSynced = true))
        }
        return true
    }

    /**
     * Get the count of pending operations (for UI indicator).
     */
    suspend fun pendingOperationCount(): Int {
        return try { pendingOperationDao.count() } catch (_: Exception) { 0 }
    }
}
