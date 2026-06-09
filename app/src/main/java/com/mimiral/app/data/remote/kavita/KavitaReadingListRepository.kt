package com.mimiral.app.data.remote.kavita

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Repository for Kavita Reading List feature.
 *
 * Provides:
 * - Browse all reading lists
 * - Paginated item listing with read/unread + progress
 * - Next-chapter lookup for ordered reading
 * - Create, update, delete reading lists
 * - Add items by series or by multiple IDs
 * - Reorder item positions
 * - Remove read items (cleanup)
 * - In-memory state flows for UI binding
 */
@Singleton
class KavitaReadingListRepository @Inject constructor(
    private val kavitaApi: KavitaApi
) {
    companion object {
        private const val TAG = "KavitaReadingList"
        private const val DEFAULT_PAGE_SIZE = 20
    }

    // ── UI State ──

    private val _readingLists = MutableStateFlow<List<KavitaReadingList>>(emptyList())
    val readingLists: StateFlow<List<KavitaReadingList>> = _readingLists.asStateFlow()

    private val _currentItems = MutableStateFlow<List<KavitaReadingListItem>>(emptyList())
    val currentItems: StateFlow<List<KavitaReadingListItem>> = _currentItems.asStateFlow()

    private val _itemTotalCount = MutableStateFlow(0)
    val itemTotalCount: StateFlow<Int> = _itemTotalCount.asStateFlow()

    private val _itemCurrentPage = MutableStateFlow(0)
    val itemCurrentPage: StateFlow<Int> = _itemCurrentPage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── Reading List CRUD ──

    /**
     * Load all reading lists.
     */
    suspend fun loadReadingLists() = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val response = kavitaApi.getReadingLists()
            if (response.isSuccessful) {
                val lists = response.body() ?: emptyList()
                _readingLists.value = lists
                Log.d(TAG, "Loaded ${lists.size} reading lists")
            } else {
                val msg = "Failed to load reading lists: HTTP ${response.code()}"
                _errorMessage.value = msg
                Log.e(TAG, msg)
            }
        } catch (e: Exception) {
            val msg = "Error loading reading lists: ${e.message}"
            _errorMessage.value = msg
            Log.e(TAG, msg, e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Create a new reading list.
     *
     * @param name The list name
     * @param summary Optional description
     * @return The created reading list, or null on failure
     */
    suspend fun createReadingList(
        name: String,
        summary: String? = null
    ): KavitaReadingList? = withContext(Dispatchers.IO) {
        try {
            val response = kavitaApi.createReadingList(
                KavitaReadingListCreateRequest(name = name, summary = summary)
            )
            if (response.isSuccessful) {
                val list = response.body()
                if (list != null) {
                    _readingLists.value = _readingLists.value + list
                    Log.d(TAG, "Created reading list: $name")
                }
                list
            } else {
                Log.e(TAG, "Failed to create reading list: HTTP ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating reading list: ${e.message}", e)
            null
        }
    }

    /**
     * Update a reading list's name and/or summary.
     *
     * @param id The reading list ID
     * @param name New name
     * @param summary New summary
     * @param promoted Promoted flag
     * @return true if successful
     */
    suspend fun updateReadingList(
        id: Int,
        name: String,
        summary: String? = null,
        promoted: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = kavitaApi.updateReadingList(
                KavitaReadingListUpdateRequest(
                    id = id,
                    name = name,
                    summary = summary,
                    promoted = promoted
                )
            )
            if (response.isSuccessful) {
                // Update local cache
                _readingLists.value = _readingLists.value.map {
                    if (it.id == id) {
                        it.copy(name = name, summary = summary, promoted = promoted)
                    } else {
                        it
                    }
                }
                Log.d(TAG, "Updated reading list $id")
                true
            } else {
                Log.e(TAG, "Failed to update reading list: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating reading list: ${e.message}", e)
            false
        }
    }

    /**
     * Delete a reading list.
     *
     * @param id The reading list ID
     * @return true if successful
     */
    suspend fun deleteReadingList(id: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = kavitaApi.deleteReadingList(
                KavitaReadingListDeleteRequest(id = id)
            )
            if (response.isSuccessful) {
                _readingLists.value = _readingLists.value.filter { it.id != id }
                Log.d(TAG, "Deleted reading list $id")
                true
            } else {
                Log.e(TAG, "Failed to delete reading list: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting reading list: ${e.message}", e)
            false
        }
    }

    // ── Items ──

    /**
     * Load items for a specific reading list.
     *
     * @param readingListId The reading list ID
     * @param pageNumber 0-based page index
     * @param pageSize Items per page
     */
    suspend fun loadReadingListItems(
        readingListId: Int,
        pageNumber: Int = 0,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ) = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val response = kavitaApi.getReadingListItems(
                readingListId = readingListId,
                pageNumber = pageNumber,
                pageSize = pageSize
            )
            if (response.isSuccessful) {
                val body = response.body()!!
                _currentItems.value = body.items
                _itemTotalCount.value = body.totalCount
                _itemCurrentPage.value = body.pageNumber
                Log.d(
                    TAG,
                    "Loaded ${body.items.size} items for list " +
                        "$readingListId (page $pageNumber, total ${body.totalCount})"
                )
            } else {
                val msg = "Failed to load reading list items: HTTP ${response.code()}"
                _errorMessage.value = msg
                Log.e(TAG, msg)
            }
        } catch (e: Exception) {
            val msg = "Error loading reading list items: ${e.message}"
            _errorMessage.value = msg
            Log.e(TAG, msg, e)
        } finally {
            _isLoading.value = false
        }
    }

    // ── Add Items ──

    /**
     * Add all chapters from a series to a reading list.
     *
     * @param readingListId The reading list
     * @param seriesId The series whose chapters to add
     * @return true if successful
     */
    suspend fun addSeriesToReadingList(
        readingListId: Int,
        seriesId: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = kavitaApi.updateReadingListBySeries(
                KavitaReadingListUpdateBySeriesRequest(
                    readingListId = readingListId,
                    seriesId = seriesId
                )
            )
            if (response.isSuccessful) {
                Log.d(TAG, "Added series $seriesId to reading list $readingListId")
                true
            } else {
                Log.e(TAG, "Failed to add series to reading list: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding series to reading list: ${e.message}", e)
            false
        }
    }

    /**
     * Add multiple items to a reading list.
     *
     * @param readingListId The reading list
     * @param seriesIds Series IDs to add
     * @param chapterIds Chapter IDs to add
     * @param volumeIds Volume IDs to add
     * @return true if successful
     */
    suspend fun addMultipleToReadingList(
        readingListId: Int,
        seriesIds: List<Int> = emptyList(),
        chapterIds: List<Int> = emptyList(),
        volumeIds: List<Int> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = kavitaApi.updateReadingListByMultiple(
                KavitaReadingListUpdateByMultipleRequest(
                    readingListId = readingListId,
                    seriesIds = seriesIds,
                    chapterIds = chapterIds,
                    volumeIds = volumeIds
                )
            )
            if (response.isSuccessful) {
                Log.d(TAG, "Added multiple items to reading list $readingListId")
                true
            } else {
                Log.e(TAG, "Failed to add multiple items: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding multiple items: ${e.message}", e)
            false
        }
    }

    // ── Reorder ──

    /**
     * Reorder an item in a reading list.
     *
     * @param readingListId The reading list
     * @param readingListItemId The item to move
     * @param fromPosition Current position
     * @param toPosition New position
     * @return true if successful
     */
    suspend fun updateItemPosition(
        readingListId: Int,
        readingListItemId: Int,
        fromPosition: Int,
        toPosition: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = kavitaApi.updateReadingListPosition(
                KavitaReadingListUpdatePositionRequest(
                    readingListId = readingListId,
                    readingListItemId = readingListItemId,
                    fromPosition = fromPosition,
                    toPosition = toPosition
                )
            )
            if (response.isSuccessful) {
                Log.d(TAG, "Moved item $readingListItemId from $fromPosition to $toPosition")
                true
            } else {
                Log.e(TAG, "Failed to update item position: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating item position: ${e.message}", e)
            false
        }
    }

    // ── Cleanup ──

    /**
     * Remove read items from a reading list.
     *
     * @param readingListId The reading list to clean
     * @return true if successful
     */
    suspend fun removeReadItems(readingListId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = kavitaApi.removeReadFromReadingList(
                KavitaReadingListRemoveReadRequest(readingListId = readingListId)
            )
            if (response.isSuccessful) {
                Log.d(TAG, "Removed read items from reading list $readingListId")
                true
            } else {
                Log.e(TAG, "Failed to remove read items: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing read items: ${e.message}", e)
            false
        }
    }

    // ── Next Chapter ──

    /**
     * Get the next chapter to read in reading-list order.
     * Use this instead of series next-chapter when reading in list order.
     *
     * @param readingListId The reading list
     * @param seriesId Current series ID
     * @param volumeId Current volume ID
     * @param chapterId Current chapter ID
     * @return Next chapter info, or null on failure
     */
    suspend fun getNextChapter(
        readingListId: Int,
        seriesId: Int,
        volumeId: Int,
        chapterId: Int
    ): KavitaReadingListNextChapter? = withContext(Dispatchers.IO) {
        try {
            val response = kavitaApi.getReadingListNextChapter(
                readingListId = readingListId,
                seriesId = seriesId,
                volumeId = volumeId,
                chapterId = chapterId
            )
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.e(TAG, "Failed to get next chapter: HTTP ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting next chapter: ${e.message}", e)
            null
        }
    }

    // ── Utility ──

    /**
     * Clear any error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Reset items state.
     */
    fun resetItems() {
        _currentItems.value = emptyList()
        _itemTotalCount.value = 0
        _itemCurrentPage.value = 0
    }

    /**
     * Reset all state.
     */
    fun reset() {
        _readingLists.value = emptyList()
        resetItems()
        _errorMessage.value = null
    }
}
