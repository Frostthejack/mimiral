package com.mimiral.app.data.remote.kavita

import android.util.Log
import com.mimiral.app.data.local.dao.ServerDao
import com.mimiral.app.data.local.entity.ServerEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Kavita server operations.
 *
 * Combines the Kavita API client with server configuration from the local database.
 */
@Singleton
class KavitaRepository @Inject constructor(
    private val serverDao: ServerDao
) {
    companion object {
        private const val TAG = "KavitaRepository"
        private const val SERVER_TYPE_KAVITA = "KAVITA"
    }

    private var cachedServer: ServerEntity? = null

    /**
     * Get the active Kavita server configuration.
     */
    suspend fun getActiveServer(): ServerEntity? {
        if (cachedServer == null) {
            cachedServer = try {
                serverDao.getActiveServerByType(SERVER_TYPE_KAVITA)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting active server: ${e.message}", e)
                null
            }
        }
        return cachedServer
    }

    /**
     * Create an authenticated Kavita API client from the active server config.
     */
    suspend fun createClient(): KavitaApiClient? {
        val server = getActiveServer() ?: run {
            Log.w(TAG, "No active Kavita server configured")
            return null
        }
        return KavitaApiClient(
            baseUrl = server.url,
            apiKey = server.apiKey,
            username = server.username,
            password = server.password
        )
    }

    /**
     * List all libraries from the active Kavita server.
     */
    suspend fun getLibraries(): KavitaResult<List<KavitaLibrary>> {
        val client = createClient()
            ?: return KavitaResult.Error("No Kavita server configured")
        return client.getLibraries()
    }

    /**
     * Get all series in a library.
     */
    suspend fun getSeriesForLibrary(
        libraryId: Int
    ): KavitaResult<List<KavitaSeries>> {
        val client = createClient()
            ?: return KavitaResult.Error("No Kavita server configured")
        return client.getSeriesForLibrary(libraryId)
    }

    /**
     * Get detailed series metadata including volumes.
     */
    suspend fun getSeriesMetadata(
        seriesId: Int
    ): KavitaResult<KavitaSeries> {
        val client = createClient()
            ?: return KavitaResult.Error("No Kavita server configured")
        return client.getSeriesMetadata(seriesId)
    }

    /**
     * Download a cover image.
     */
    suspend fun getCoverImage(
        coverImageId: String
    ): KavitaResult<ByteArray> {
        val client = createClient()
            ?: return KavitaResult.Error("No Kavita server configured")
        return client.getCoverImage(coverImageId)
    }

    /**
     * Resolve a cover image URL for use with Coil.
     * Non-suspend version that uses cached server config.
     */
    fun resolveCoverImageUrl(coverImage: String?): String? {
        if (coverImage.isNullOrBlank()) return null
        if (coverImage.startsWith("http://") || coverImage.startsWith("https://")) {
            return coverImage
        }
        val server = cachedServer ?: return null
        val base = server.url.trimEnd('/')
        return "$base/api/Image/cover?coverImage=$coverImage"
    }

    /**
     * Test connection to the active Kavita server.
     */
    suspend fun testConnection(): KavitaResult<Boolean> {
        val client = createClient()
            ?: return KavitaResult.Error("No Kavita server configured")
        return client.testConnection()
    }

    /**
     * Clear cached server config (e.g., when server settings change).
     */
    fun clearCache() {
        cachedServer = null
    }
}
