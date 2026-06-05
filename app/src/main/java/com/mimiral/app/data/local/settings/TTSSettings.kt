package com.mimiral.app.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ttsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tts_settings"
)

data class TTSSettings(
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceName: String = "",
    val localeTag: String = ""
)

class TTSSettingsRepository(private val context: Context) {

    private object Keys {
        val SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val PITCH = floatPreferencesKey("tts_pitch")
        val VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val LOCALE_TAG = stringPreferencesKey("tts_locale_tag")
    }

    val settings: Flow<TTSSettings> = context.ttsDataStore.data.map { prefs ->
        TTSSettings(
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            pitch = prefs[Keys.PITCH] ?: 1.0f,
            voiceName = prefs[Keys.VOICE_NAME] ?: "",
            localeTag = prefs[Keys.LOCALE_TAG] ?: ""
        )
    }

    suspend fun setSpeechRate(rate: Float) {
        context.ttsDataStore.edit { prefs ->
            prefs[Keys.SPEECH_RATE] = rate.coerceIn(0.1f, 4.0f)
        }
    }

    suspend fun setPitch(pitch: Float) {
        context.ttsDataStore.edit { prefs ->
            prefs[Keys.PITCH] = pitch.coerceIn(0.1f, 4.0f)
        }
    }

    suspend fun setVoiceName(voiceName: String) {
        context.ttsDataStore.edit { prefs ->
            prefs[Keys.VOICE_NAME] = voiceName
        }
    }

    suspend fun setLocaleTag(localeTag: String) {
        context.ttsDataStore.edit { prefs ->
            prefs[Keys.LOCALE_TAG] = localeTag
        }
    }

    suspend fun resetToDefaults() {
        context.ttsDataStore.edit { prefs ->
            prefs[Keys.SPEECH_RATE] = 1.0f
            prefs[Keys.PITCH] = 1.0f
            prefs[Keys.VOICE_NAME] = ""
            prefs[Keys.LOCALE_TAG] = ""
        }
    }
}
