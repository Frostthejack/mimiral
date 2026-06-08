package com.mimiral.app.data.remote.kavita

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Kavita collection operations.
 *
 * Wraps [KavitaApi] Retrofit calls with structured error handling via [KavitaResult].
 * Provides CRUD operations for collections and series membership management.
 */
@Singleton
class KavitaCollectionRepository @Inject constructor(
    private val kavitaApi: KavitaApi
) {
    companion object {
        private const val TAG = "KavitaCollectionRepo"
    }

    /**
     * Get all collections from the server.
     * GET /api/Collection
     */
    suspend fun getCollections(): KavitaResult<List<KavitaCollection>> {
        return try {
            val response = kavitaApi.getCollections()
            if (response.isSuccessful) {
                val collections = response.body() ?: emptyList()
                Log.d(TAG, "Fetched ${collections.size} collections")
                KavitaResult.Success(collections)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.w(TAG, "getCollections failed: ${response.code()} $errorBody")
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: $errorBody",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCollections error: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
    }

    /**
     * Get series in a collection (paginated).
     * GET /api/Series/series-by-collection
     *
     * @param collectionId The collection ID
     * @param pageNumber Page number (1-based)
     * @param pageSize Items per page
     */
    suspend fun getSeriesByCollection(
        collectionId: Int,
        pageNumber: Int = 1,
        pageSize: Int = 20
    ): KavitaResult<KavitaCollectionSeriesPage> {
        return try {
            val response = kavitaApi.getSeriesByCollection(collectionId, pageNumber, pageSize)
            if (response.isSuccessful) {
                val page = response.body() ?: KavitaCollectionSeriesPage()
                Log.d(TAG, "Fetched ${page.series.size} series for collection $collectionId (page $pageNumber)")
                KavitaResult.Success(page)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.w(TAG, "getSeriesByCollection failed: ${response.code()} $errorBody")
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: $errorBody",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSeriesByCollection error: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
    }

    /**
     * Create a new collection with the given title and series.
     * POST /api/Collection/update-series (tagId=0 creates new)
     *
     * @param title Collection name
     * @param summary Optional description
     * @param seriesIds Series IDs to include
     * @param promoted Whether the collection is promoted
     */
    suspend fun createCollection(
        title: String,
        summary: String? = null,
        seriesIds: List<Int> = emptyList(),
        promoted: Boolean = false
    ): KavitaResult<Unit> {
        return try {
            val request = KavitaCollectionUpdateRequest(
                title = title,
                summary = summary,
                tagId = 0, // 0 = create new
                seriesIds = seriesIds,
                promoted = promoted
            )
            val response = kavitaApi.updateCollectionSeries(request)
            if (response.isSuccessful) {
                Log.d(TAG, "Created collection: $title")
                KavitaResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.w(TAG, "createCollection failed: ${response.code()} $errorBody")
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: $errorBody",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "createCollection error: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
    }

    /**
     * Add series to an existing collection.
     * POST /api/Collection/update-for-series
     *
     * @param collectionId The collection ID
     * @param seriesIds Series IDs to add
     */
    suspend fun addSeriesToCollection(
        collectionId: Int,
        seriesIds: List<Int>
    ): KavitaResult<Unit> {
        return try {
            val request = KavitaCollectionSeriesRequest(
                tagId = collectionId,
                seriesIds = seriesIds
            )
            val response = kavitaApi.updateForSeries(request)
            if (response.isSuccessful) {
                Log.d(TAG, "Added ${seriesIds.size} series to collection $collectionId")
                KavitaResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.w(TAG, "addSeriesToCollection failed: ${response.code()} $errorBody")
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: $errorBody",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "addSeriesToCollection error: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
    }

    /**
     * Update collection metadata (title, summary, promoted).
     * POST /api/Collection/update
     *
     * @param collectionId The collection ID
     * @param title New title
     * @param summary New summary
     * @param promoted Whether promoted
     * @param coverImageLocked Whether cover image is locked
     */
    suspend fun updateCollection(
        collectionId: Int,
        title: String,
        summary: String? = null,
        promoted: Boolean = false,
        coverImageLocked: Boolean = false
    ): KavitaResult<Unit> {
        return try {
            val request = KavitaCollectionEditRequest(
                tagId = collectionId,
                title = title,
                summary = summary,
                promoted = promoted,
                coverImageLocked = coverImageLocked
            )
            val response = kavitaApi.updateCollection(request)
            if (response.isSuccessful) {
                Log.d(TAG, "Updated collection $collectionId")
                KavitaResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.w(TAG, "updateCollection failed: ${response.code()} $errorBody")
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: $errorBody",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateCollection error: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
    }

    /**
     * Delete a collection.
     * DELETE /api/Collection
     *
     * @param collectionId The collection ID to delete
     */
    suspend fun deleteCollection(collectionId: Int): KavitaResult<Unit> {
        return try {
            val response = kavitaApi.deleteCollection(collectionId)
            if (response.isSuccessful) {
                Log.d(TAG, "Deleted collection $collectionId")
                KavitaResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.w(TAG, "deleteCollection failed: ${response.code()} $errorBody")
                KavitaResult.Error(
                    message = "HTTP ${response.code()}: $errorBody",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteCollection error: ${e.message}", e)
            KavitaResult.Error(message = "Network error: ${e.message}", cause = e)
        }
    }
}
