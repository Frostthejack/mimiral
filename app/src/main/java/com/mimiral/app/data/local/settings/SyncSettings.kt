package com.mimiral.app.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.syncSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_settings"
)

/**
 * Sync interval options for automatic background sync.
 */
enum class SyncInterval(val displayName: String, val minutes: Int) {
    MINUTES_15("15 minutes", 15),
    MINUTES_30("30 minutes", 30),
    HOURS_1("1 hour", 60),
    HOURS_2("2 hours", 120),
    MANUAL("Manual only", 0);

    companion object {
        fun fromMinutes(minutes: Int): SyncInterval {
            return entries.find { it.minutes == minutes } ?: MINUTES_30
        }
    }
}

/**
 * Progress sync protocol mode.
 *
 * - NATIVE: Uses POST /api/Reader/progress with JWT auth (default, full fidelity).
 * - PANELS: Uses POST /api/Panels/save-progress with API key (fallback when no JWT).
 * - KOREADER: Uses PUT /api/Koreader/{apiKey}/syncs/progress (KOReader interop, niche).
 */
enum class ProgressSyncMode(val displayName: String, val description: String) {
    NATIVE("Native", "Reader/progress with JWT (recommended)"),
    PANELS("Panels", "Panels/progress with API key fallback"),
    KOREADER("KOReader", "KOReader sync for e-ink devices");

    companion object {
        fun fromName(name: String): ProgressSyncMode {
            return try {
                valueOf(name)
            } catch (_: IllegalArgumentException) {
                NATIVE
            }
        }
    }
}

/**
 * Data class holding all sync-related preferences.
 */
data class SyncSettings(
    val autoSyncEnabled: Boolean = false,
    val syncInterval: SyncInterval = SyncInterval.MINUTES_30,
    val syncOnWifiOnly: Boolean = true,
    val lastSyncTimestamp: Long = 0L,
    val syncBookmarks: Boolean = true,
    val syncReadingProgress: Boolean = true,
    val syncHighlights: Boolean = true,
    val progressSyncMode: ProgressSyncMode = ProgressSyncMode.NATIVE,
    val koreaderDeviceId: String = "mimiral"
)

/**
 * Repository for sync preferences using DataStore.
 */
class SyncSettingsRepository(private val context: Context) {

    private object Keys {
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval_minutes")
        val SYNC_ON_WIFI_ONLY = booleanPreferencesKey("sync_on_wifi_only")
        val LAST_SYNC = stringPreferencesKey("last_sync_timestamp")
        val SYNC_BOOKMARKS = booleanPreferencesKey("sync_bookmarks")
        val SYNC_READING_PROGRESS = booleanPreferencesKey("sync_reading_progress")
        val SYNC_HIGHLIGHTS = booleanPreferencesKey("sync_highlights")
        val PROGRESS_SYNC_MODE = stringPreferencesKey("progress_sync_mode")
        val KOREADER_DEVICE_ID = stringPreferencesKey("koreader_device_id")
    }

    val settings: Flow<SyncSettings> = context.syncSettingsDataStore.data.map { prefs ->
        SyncSettings(
            autoSyncEnabled = prefs[Keys.AUTO_SYNC_ENABLED] ?: false,
            syncInterval = SyncInterval.fromMinutes(
                prefs[Keys.SYNC_INTERVAL] ?: SyncInterval.MINUTES_30.minutes
            ),
            syncOnWifiOnly = prefs[Keys.SYNC_ON_WIFI_ONLY] ?: true,
            lastSyncTimestamp = prefs[Keys.LAST_SYNC]?.toLongOrNull() ?: 0L,
            syncBookmarks = prefs[Keys.SYNC_BOOKMARKS] ?: true,
            syncReadingProgress = prefs[Keys.SYNC_READING_PROGRESS] ?: true,
            syncHighlights = prefs[Keys.SYNC_HIGHLIGHTS] ?: true,
            progressSyncMode = ProgressSyncMode.fromName(
                prefs[Keys.PROGRESS_SYNC_MODE] ?: ProgressSyncMode.NATIVE.name
            ),
            koreaderDeviceId = prefs[Keys.KOREADER_DEVICE_ID] ?: "mimiral"
        )
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.AUTO_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setSyncInterval(interval: SyncInterval) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.SYNC_INTERVAL] = interval.minutes
        }
    }

    suspend fun setSyncOnWifiOnly(wifiOnly: Boolean) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.SYNC_ON_WIFI_ONLY] = wifiOnly
        }
    }

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC] = timestamp.toString()
        }
    }

    suspend fun setSyncBookmarks(enabled: Boolean) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.SYNC_BOOKMARKS] = enabled
        }
    }

    suspend fun setSyncReadingProgress(enabled: Boolean) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.SYNC_READING_PROGRESS] = enabled
        }
    }

    suspend fun setSyncHighlights(enabled: Boolean) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.SYNC_HIGHLIGHTS] = enabled
        }
    }

    suspend fun setProgressSyncMode(mode: ProgressSyncMode) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.PROGRESS_SYNC_MODE] = mode.name
        }
    }

    suspend fun setKoreaderDeviceId(deviceId: String) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.KOREADER_DEVICE_ID] = deviceId
        }
    }
}
