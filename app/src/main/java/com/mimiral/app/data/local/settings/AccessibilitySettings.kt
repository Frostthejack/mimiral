package com.mimiral.app.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.accessibilityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "accessibility_settings"
)

data class AccessibilitySettings(
    val highContrastEnabled: Boolean = false,
    val fontScaleMultiplier: Float = 1.0f,
    val talkBackOptimized: Boolean = false,
    val boldTextEnabled: Boolean = false
)

class AccessibilitySettingsRepository(private val context: Context) {

    private object Keys {
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast_enabled")
        val FONT_SCALE = floatPreferencesKey("font_scale_multiplier")
        val TALKBACK_OPTIMIZED = booleanPreferencesKey("talkback_optimized")
        val BOLD_TEXT = booleanPreferencesKey("bold_text_enabled")
    }

    val settings: Flow<AccessibilitySettings> = context.accessibilityDataStore.data.map { prefs ->
        AccessibilitySettings(
            highContrastEnabled = prefs[Keys.HIGH_CONTRAST] ?: false,
            fontScaleMultiplier = prefs[Keys.FONT_SCALE] ?: 1.0f,
            talkBackOptimized = prefs[Keys.TALKBACK_OPTIMIZED] ?: false,
            boldTextEnabled = prefs[Keys.BOLD_TEXT] ?: false
        )
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.accessibilityDataStore.edit { prefs ->
            prefs[Keys.HIGH_CONTRAST] = enabled
        }
    }

    suspend fun setFontScale(multiplier: Float) {
        context.accessibilityDataStore.edit { prefs ->
            prefs[Keys.FONT_SCALE] = multiplier
        }
    }

    suspend fun setTalkBackOptimized(enabled: Boolean) {
        context.accessibilityDataStore.edit { prefs ->
            prefs[Keys.TALKBACK_OPTIMIZED] = enabled
        }
    }

    suspend fun setBoldText(enabled: Boolean) {
        context.accessibilityDataStore.edit { prefs ->
            prefs[Keys.BOLD_TEXT] = enabled
        }
    }
}
