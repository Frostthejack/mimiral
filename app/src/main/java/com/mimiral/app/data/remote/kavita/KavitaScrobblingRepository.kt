package com.mimiral.app.data.remote.kavita

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Kavita scrobbling management.
 *
 * Manages:
 * - Kavita+ license check (required for scrobbling)
 * - Scrobble settings (enable/disable, provider tokens)
 * - Scrobble holds (per-series toggle to pause scrobbling)
 * - Scrobble errors (failed attempts) and retry
 *
 * Scrobbling itself is server-side automatic; Mimiral only manages
 * settings, holds, and tokens.
 */
@Singleton
class KavitaScrobblingRepository @Inject constructor(
    private val kavitaApi: KavitaApi
) {
    companion object {
        private const val TAG = "KavitaScrobbling"
    }

    /**
     * Check if Kavita+ license is valid.
     * Must be called before accessing scrobbling endpoints.
     */
    suspend fun validateLicense(): KavitaResult<KavitaLicenseStatus> {
        return try {
            val response = kavitaApi.validateLicense()
            if (response.isSuccessful) {
                val status = response.body() ?: KavitaLicenseStatus()
                KavitaResult.Success(status)
            } else {
                KavitaResult.Error(
                    message = "License check failed: HTTP ${response.code()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "License check error", e)
            KavitaResult.Error(message = "License check error: ${e.message}", cause = e)
        }
    }

    /**
     * Get current scrobble settings.
     * Requires active Kavita+ license.
     */
    suspend fun getScrobbleSettings(): KavitaResult<KavitaScrobblingSettings> {
        return try {
            val response = kavitaApi.getScrobbleSettings()
            if (response.isSuccessful) {
                val settings = response.body() ?: KavitaScrobblingSettings()
                KavitaResult.Success(settings)
            } else {
                KavitaResult.Error(
                    message = "Failed to get scrobble settings: HTTP ${response.code()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get scrobble settings error", e)
            KavitaResult.Error(message = "Get settings error: ${e.message}", cause = e)
        }
    }

    /**
     * Update a scrobble provider token.
     * Use when an external provider token (AniList, MAL, Google Books) has expired.
     *
     * @param provider Provider name (e.g., "AniList", "Mal", "GoogleBooks")
     * @param token New authentication token for the provider
     */
    suspend fun updateScrobbleProvider(
        provider: String,
        token: String
    ): KavitaResult<Unit> {
        return try {
            val request = KavitaUpdateScrobbleProviderRequest(
                provider = provider,
                token = token
            )
            val response = kavitaApi.updateScrobbleProvider(request)
            if (response.isSuccessful) {
                KavitaResult.Success(Unit)
            } else {
                KavitaResult.Error(
                    message = "Failed to update provider: HTTP ${response.code()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update scrobble provider error", e)
            KavitaResult.Error(message = "Update provider error: ${e.message}", cause = e)
        }
    }

    /**
     * Get all scrobble errors (failed scrobble attempts).
     */
    suspend fun getScrobbleErrors(): KavitaResult<List<KavitaScrobbleError>> {
        return try {
            val response = kavitaApi.getScrobbleErrors()
            if (response.isSuccessful) {
                KavitaResult.Success(response.body() ?: emptyList())
            } else {
                KavitaResult.Error(
                    message = "Failed to get scrobble errors: HTTP ${response.code()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get scrobble errors error", e)
            KavitaResult.Error(message = "Get errors error: ${e.message}", cause = e)
        }
    }

    /**
     * Retry a failed scrobble.
     *
     * @param errorId The scrobble error ID to retry
     */
    suspend fun retryScrobble(errorId: Int): KavitaResult<Unit> {
        return try {
            val response = kavitaApi.retryScrobble(errorId)
            if (response.isSuccessful) {
                KavitaResult.Success(Unit)
            } else {
                KavitaResult.Error(
                    message = "Retry failed: HTTP ${response.code()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Retry scrobble error", e)
            KavitaResult.Error(message = "Retry error: ${e.message}", cause = e)
        }
    }

    /**
     * Add a scrobble hold on a series.
     * Prevents scrobbling for the specified series.
     *
     * @param seriesId The series ID to put on hold
     */
    suspend fun addScrobbleHold(seriesId: Int): KavitaResult<Unit> {
        return try {
            val response = kavitaApi.addScrobbleHold(seriesId)
            if (response.isSuccessful) {
                KavitaResult.Success(Unit)
            } else {
                KavitaResult.Error(
                    message = "Failed to add hold: HTTP ${response.code()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Add scrobble hold error", e)
            KavitaResult.Error(message = "Add hold error: ${e.message}", cause = e)
        }
    }

    /**
     * Remove a scrobble hold from a series.
     * Re-enables scrobbling for the specified series.
     *
     * @param seriesId The series ID to remove hold from
     */
    suspend fun removeScrobbleHold(seriesId: Int): KavitaResult<Unit> {
        return try {
            val response = kavitaApi.removeScrobbleHold(seriesId)
            if (response.isSuccessful) {
                KavitaResult.Success(Unit)
            } else {
                KavitaResult.Error(
                    message = "Failed to remove hold: HTTP ${response.code()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remove scrobble hold error", e)
            KavitaResult.Error(message = "Remove hold error: ${e.message}", cause = e)
        }
    }

    /**
     * Get all series IDs with active scrobble holds.
     */
    suspend fun getScrobbleHolds(): KavitaResult<List<Int>> {
        return try {
            val response = kavitaApi.getScrobbleHolds()
            if (response.isSuccessful) {
                KavitaResult.Success(response.body() ?: emptyList())
            } else {
                KavitaResult.Error(
                    message = "Failed to get holds: HTTP ${response.code()}",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get scrobble holds error", e)
            KavitaResult.Error(message = "Get holds error: ${e.message}", cause = e)
        }
    }
}
