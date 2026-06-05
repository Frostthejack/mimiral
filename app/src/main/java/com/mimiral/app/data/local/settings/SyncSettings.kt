package com.mimiral.app.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.syncSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_settings"
)

data class SyncSettings(
    val autoSyncEnabled: Boolean = false,
    val syncIntervalMinutes: Int = 60,
    val syncOnWifiOnly: Boolean = true,
    val lastSyncTimestamp: Long = 0L
)

class SyncSettingsRepository(private val context: Context) {

    private object Keys {
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val SYNC_INTERVAL = stringPreferencesKey("sync_interval_minutes")
        val SYNC_ON_WIFI_ONLY = booleanPreferencesKey("sync_on_wifi_only")
        val LAST_SYNC = stringPreferencesKey("last_sync_timestamp")
    }

    val settings: Flow<SyncSettings> = context.syncSettingsDataStore.data.map { prefs ->
        SyncSettings(
            autoSyncEnabled = prefs[Keys.AUTO_SYNC_ENABLED] ?: false,
            syncIntervalMinutes = prefs[Keys.SYNC_INTERVAL]?.toIntOrNull() ?: 60,
            syncOnWifiOnly = prefs[Keys.SYNC_ON_WIFI_ONLY] ?: true,
            lastSyncTimestamp = prefs[Keys.LAST_SYNC]?.toLongOrNull() ?: 0L
        )
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.AUTO_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setSyncInterval(minutes: Int) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.SYNC_INTERVAL] = minutes.toString()
        }
    }

    suspend fun setSyncOnWifiOnly(enabled: Boolean) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.SYNC_ON_WIFI_ONLY] = enabled
        }
    }

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        context.syncSettingsDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC] = timestamp.toString()
        }
    }
}
