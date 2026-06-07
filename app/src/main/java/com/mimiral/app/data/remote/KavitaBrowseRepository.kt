package com.mimiral.app.data.remote

import com.mimiral.app.di.KavitaApiClient
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of a browse operation.
 */
sealed class BrowseResult<out T> {
    data class Success<T>(val data: T) : BrowseResult<T>()
    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : BrowseResult<Nothing>()
    data object Loading : BrowseResult<Nothing>()
}

/**
 * Repository for Kavita series/volume browsing operations.
 * Wraps the KavitaSyncApi with error handling and result mapping.
 * All network calls run on Dispatchers.IO to avoid blocking the main thread.
 */
@Singleton
class KavitaBrowseRepository @Inject constructor(
    @KavitaApiClient private val kavitaApi: KavitaSyncApi
) {

    /**
     * Get series detail including volumes, chapters, and specials.
     */
    suspend fun getSeriesDetail(seriesId: Int): BrowseResult<SeriesDetailDto> =
        withContext(Dispatchers.IO) {
            try {
                val response = kavitaApi.getSeriesDetail(seriesId)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        android.util.Log.d(
                            "KavitaBrowse",
                            "series-detail: vols=${body.volumes.size}, " +
                                "chaps=${body.chapters.size}, specials=${body.specials.size}"
                        )
                        BrowseResult.Success(body)
                    } else {
                        android.util.Log.e(
                            "KavitaBrowse",
                            "Empty body series-detail"
                        )
                        BrowseResult.Error("Empty response body")
                    }
                } else {
                    android.util.Log.e(
                        "KavitaBrowse",
                        "series-detail fail: ${response.code()} ${response.message()}"
                    )
                    BrowseResult.Error(
                        "Failed to load series: ${response.code()} ${response.message()}"
                    )
                }
            } catch (e: ConnectException) {
                BrowseResult.Error("Cannot connect to Kavita server", e)
            } catch (e: SocketTimeoutException) {
                BrowseResult.Error("Kavita server timed out", e)
            } catch (e: UnknownHostException) {
                BrowseResult.Error("Kavita server unreachable", e)
            } catch (e: Exception) {
                BrowseResult.Error("Error loading series: ${e.message}", e)
            }
        }

    /**
     * Get all volumes for a series.
     */
    suspend fun getVolumes(seriesId: Int): BrowseResult<List<VolumeDto>> =
        withContext(Dispatchers.IO) {
            try {
                val response = kavitaApi.getVolumes(seriesId)
                if (response.isSuccessful) {
                    BrowseResult.Success(response.body() ?: emptyList())
                } else {
                    BrowseResult.Error(
                        "Failed to load volumes: ${response.code()} ${response.message()}"
                    )
                }
            } catch (e: ConnectException) {
                BrowseResult.Error("Cannot connect to Kavita server", e)
            } catch (e: SocketTimeoutException) {
                BrowseResult.Error("Kavita server timed out", e)
            } catch (e: UnknownHostException) {
                BrowseResult.Error("Kavita server unreachable", e)
            } catch (e: Exception) {
                BrowseResult.Error("Error loading volumes: ${e.message}", e)
            }
        }

    /**
     * Get a single volume by ID.
     */
    suspend fun getVolume(volumeId: Int): BrowseResult<VolumeDto> =
        withContext(Dispatchers.IO) {
            try {
                val response = kavitaApi.getVolume(volumeId)
                if (response.isSuccessful) {
                    response.body()?.let {
                        BrowseResult.Success(it)
                    } ?: BrowseResult.Error("Empty response body")
                } else {
                    BrowseResult.Error(
                        "Failed to load volume: ${response.code()} ${response.message()}"
                    )
                }
            } catch (e: ConnectException) {
                BrowseResult.Error("Cannot connect to Kavita server", e)
            } catch (e: SocketTimeoutException) {
                BrowseResult.Error("Kavita server timed out", e)
            } catch (e: UnknownHostException) {
                BrowseResult.Error("Kavita server unreachable", e)
            } catch (e: Exception) {
                BrowseResult.Error("Error loading volume: ${e.message}", e)
            }
        }

    /**
     * Get basic series info by ID.
     */
    suspend fun getSeries(seriesId: Int): BrowseResult<SeriesDto> =
        withContext(Dispatchers.IO) {
            try {
                val response = kavitaApi.getSeries(seriesId)
                if (response.isSuccessful) {
                    response.body()?.let {
                        BrowseResult.Success(it)
                    } ?: BrowseResult.Error("Empty response body")
                } else {
                    BrowseResult.Error(
                        "Failed to load series info: ${response.code()} ${response.message()}"
                    )
                }
            } catch (e: ConnectException) {
                BrowseResult.Error("Cannot connect to Kavita server", e)
            } catch (e: SocketTimeoutException) {
                BrowseResult.Error("Kavita server timed out", e)
            } catch (e: UnknownHostException) {
                BrowseResult.Error("Kavita server unreachable", e)
            } catch (e: Exception) {
                BrowseResult.Error("Error loading series info: ${e.message}", e)
            }
        }
}
