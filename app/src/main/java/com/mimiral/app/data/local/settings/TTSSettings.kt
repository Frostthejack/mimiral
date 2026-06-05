package com.mimiral.app.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.ttsSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tts_settings"
)

data class TTSSettings(
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceName: String = "",
    val languageTag: String = "",
    val isTTSEnabled: Boolean = true,
    val autoPlayOnOpen: Boolean = false,
    val highlightWhileReading: Boolean = true,
    val sleepTimerDefaultMinutes: Int = 30
)

class TTSSettingsRepository(private val context: Context) {

    private object Keys {
        val SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val PITCH = floatPreferencesKey("tts_pitch")
        val VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val LANGUAGE_TAG = stringPreferencesKey("tts_language_tag")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val AUTO_PLAY_ON_OPEN = booleanPreferencesKey("tts_auto_play_on_open")
        val HIGHLIGHT_WHILE_READING = booleanPreferencesKey("tts_highlight_while_reading")
        val SLEEP_TIMER_DEFAULT = stringPreferencesKey("tts_sleep_timer_default")
    }

    val settings: Flow<TTSSettings> = context.ttsSettingsDataStore.data.map { prefs ->
        TTSSettings(
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            pitch = prefs[Keys.PITCH] ?: 1.0f,
            voiceName = prefs[Keys.VOICE_NAME] ?: "",
            languageTag = prefs[Keys.LANGUAGE_TAG] ?: "",
            isTTSEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            autoPlayOnOpen = prefs[Keys.AUTO_PLAY_ON_OPEN] ?: false,
            highlightWhileReading = prefs[Keys.HIGHLIGHT_WHILE_READING] ?: true,
            sleepTimerDefaultMinutes = prefs[Keys.SLEEP_TIMER_DEFAULT]?.toIntOrNull() ?: 30
        )
    }

    suspend fun setSpeechRate(rate: Float) {
        context.ttsSettingsDataStore.edit { prefs ->
            prefs[Keys.SPEECH_RATE] = rate.coerceIn(0.1f, 4.0f)
        }
    }

    suspend fun setPitch(pitch: Float) {
        context.ttsSettingsDataStore.edit { prefs ->
            prefs[Keys.PITCH] = pitch.coerceIn(0.1f, 4.0f)
        }
    }

    suspend fun setVoiceName(name: String) {
        context.ttsSettingsDataStore.edit { prefs ->
            prefs[Keys.VOICE_NAME] = name
        }
    }

    suspend fun setLanguageTag(tag: String) {
        context.ttsSettingsDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE_TAG] = tag
        }
    }

    suspend fun setTTSEnabled(enabled: Boolean) {
        context.ttsSettingsDataStore.edit { prefs ->
            prefs[Keys.TTS_ENABLED] = enabled
        }
    }

    suspend fun setAutoPlayOnOpen(enabled: Boolean) {
        context.ttsSettingsDataStore.edit { prefs ->
            prefs[Keys.AUTO_PLAY_ON_OPEN] = enabled
        }
    }

    suspend fun setHighlightWhileReading(enabled: Boolean) {
        context.ttsSettingsDataStore.edit { prefs ->
            prefs[Keys.HIGHLIGHT_WHILE_READING] = enabled
        }
    }

    suspend fun setSleepTimerDefaultMinutes(minutes: Int) {
        context.ttsSettingsDataStore.edit { prefs ->
            prefs[Keys.SLEEP_TIMER_DEFAULT] = minutes.toString()
        }
    }
}
