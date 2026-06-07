package com.mimiral.app.data.local.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Handles exporting all app settings to a JSON backup file and restoring from it.
 *
 * Backs up:
 * - reader_settings DataStore (theme, volume keys, text rendering)
 * - library_settings DataStore (sort, filter, view mode)
 *
 * Does NOT back up:
 * - Kavita credentials (EncryptedSharedPreferences — security sensitive)
 * - Database content (books, highlights, etc. — handled by separate library export)
 */
class SettingsBackupExporter(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // DataStore references — must match the names used in ReaderSettings and LibrarySettings
    private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "reader_settings"
    )
    private val Context.libraryDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "library_settings"
    )
    private val Context.readingModeDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "reading_mode_settings"
    )

    /**
     * Export all settings to a JSON file in the cache/backups directory.
     * Returns the File on success, null on failure.
     */
    suspend fun exportSettings(): File? = withContext(Dispatchers.IO) {
        try {
            val readerPrefs = context.readerDataStore.data.first()
            val libraryPrefs = context.libraryDataStore.data.first()
            val readingModePrefs = context.readingModeDataStore.data.first()

            val backupJson = JsonObject().apply {
                addProperty("version", BACKUP_VERSION)
                addProperty("app_version", "0.1.0")
                addProperty(
                    "exported_at",
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
                )
                add("reader_settings", prefsToJson(readerPrefs))
                add("library_settings", prefsToJson(libraryPrefs))
                add("reading_mode_settings", prefsToJson(readingModePrefs))
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            val filename = "mimiral_settings_$timestamp.json"

            val backupDir = File(context.cacheDir, "backups")
            if (!backupDir.exists()) backupDir.mkdirs()

            val file = File(backupDir, filename)
            file.writeText(gson.toJson(backupJson))
            file
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Share a backup file via Android SEND intent with FileProvider.
     */
    fun shareBackupFile(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Mimiral Settings Backup")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share settings backup via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Restore settings from a JSON backup file.
     * Returns true on success, false on failure.
     */
    suspend fun restoreSettings(backupFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonText = backupFile.readText()
            restoreSettingsFromJson(jsonText)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Restore settings from a JSON string (used with SAF file picker).
     */
    suspend fun restoreSettingsFromJson(jsonText: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonObject = gson.fromJson(jsonText, JsonObject::class.java)
                ?: return@withContext false

            // Validate version
            val version = jsonObject.get("version")?.asInt ?: 0
            if (version > BACKUP_VERSION) {
                return@withContext false // Cannot restore from newer version
            }

            // Restore reader settings
            jsonObject.getAsJsonObject("reader_settings")?.let { readerJson ->
                context.readerDataStore.edit { prefs ->
                    applyJsonToPrefs(readerJson, prefs)
                }
            }

            // Restore library settings
            jsonObject.getAsJsonObject("library_settings")?.let { libraryJson ->
                context.libraryDataStore.edit { prefs ->
                    applyJsonToPrefs(libraryJson, prefs)
                }
            }

            // Restore reading mode settings
            jsonObject.getAsJsonObject("reading_mode_settings")?.let { readingModeJson ->
                context.readingModeDataStore.edit { prefs ->
                    applyJsonToPrefs(readingModeJson, prefs)
                }
            }

            true
        } catch (e: JsonSyntaxException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Convert a DataStore Preferences snapshot to a JsonObject.
     */
    private fun prefsToJson(prefs: Preferences): JsonObject {
        val json = JsonObject()
        for ((key, value) in prefs.asMap()) {
            when (value) {
                is String -> json.addProperty(key.name, value)
                is Int -> json.addProperty(key.name, value)
                is Long -> json.addProperty(key.name, value)
                is Float -> json.addProperty(key.name, value)
                is Double -> json.addProperty(key.name, value)
                is Boolean -> json.addProperty(key.name, value)
                null -> json.add(key.name, com.google.gson.JsonNull.INSTANCE)
                else -> json.addProperty(key.name, value.toString())
            }
        }
        return json
    }

    /**
     * Apply values from a JsonObject into DataStore preferences.
     * Uses the known key names to construct the correct Preferences.Key types.
     */
    private fun applyJsonToPrefs(
        json: JsonObject,
        prefs: androidx.datastore.preferences.core.MutablePreferences
    ) {
        for ((keyName, element) in json.entrySet()) {
            if (element.isJsonNull) continue
            if (!element.isJsonPrimitive) continue

            val primitive = element.asJsonPrimitive
            try {
                when {
                    primitive.isString -> {
                        prefs[stringPreferencesKey(keyName)] = primitive.asString
                    }
                    primitive.isBoolean -> {
                        prefs[booleanPreferencesKey(keyName)] = primitive.asBoolean
                    }
                    primitive.isNumber -> {
                        // Try int first, then float
                        val num = primitive.asNumber
                        try {
                            prefs[intPreferencesKey(keyName)] = num.toInt()
                        } catch (_: Exception) {
                            prefs[floatPreferencesKey(keyName)] = num.toFloat()
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip keys that can't be mapped
            }
        }
    }

    companion object {
        /** Backup format version — increment when the schema changes. */
        const val BACKUP_VERSION = 2
    }
}
