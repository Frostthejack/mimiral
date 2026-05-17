package com.mimiral.app.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mimiral.app.ui.reader.ReaderFontFamily
import com.mimiral.app.ui.reader.TextSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

data class ReaderSettings(
    val volumeKeyNavigationEnabled: Boolean = true,
    val volumeKeyDirectionSwapped: Boolean = false,
    val themeName: String = "DAY"
)

class ReaderSettingsRepository(private val context: Context) {

    private object Keys {
        val VOLUME_KEY_NAVIGATION = booleanPreferencesKey("volume_key_navigation_enabled")
        val VOLUME_KEY_SWAPPED = booleanPreferencesKey("volume_key_direction_swapped")
        val THEME = stringPreferencesKey("theme_name")

        // Text rendering settings
        val FONT_SIZE = intPreferencesKey("text_font_size")
        val LINE_SPACING_MULTIPLIER = floatPreferencesKey("text_line_spacing_multiplier")
        val LINE_SPACING_EXTRA = floatPreferencesKey("text_line_spacing_extra")
        val MARGIN_TOP = intPreferencesKey("text_margin_top")
        val MARGIN_BOTTOM = intPreferencesKey("text_margin_bottom")
        val MARGIN_LEFT = intPreferencesKey("text_margin_left")
        val MARGIN_RIGHT = intPreferencesKey("text_margin_right")
        val FONT_FAMILY = stringPreferencesKey("text_font_family")
        val CUSTOM_FONT_PATH = stringPreferencesKey("text_custom_font_path")
    }

    val settings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            volumeKeyNavigationEnabled = prefs[Keys.VOLUME_KEY_NAVIGATION] ?: true,
            volumeKeyDirectionSwapped = prefs[Keys.VOLUME_KEY_SWAPPED] ?: false,
            themeName = prefs[Keys.THEME] ?: "DAY"
        )
    }

    val textSettings: Flow<TextSettings> = context.dataStore.data.map { prefs ->
        TextSettings(
            fontSize = prefs[Keys.FONT_SIZE] ?: 18,
            lineSpacingMultiplier = prefs[Keys.LINE_SPACING_MULTIPLIER] ?: 1.2f,
            lineSpacingExtra = prefs[Keys.LINE_SPACING_EXTRA] ?: 8f,
            marginTop = prefs[Keys.MARGIN_TOP] ?: 24,
            marginBottom = prefs[Keys.MARGIN_BOTTOM] ?: 24,
            marginLeft = prefs[Keys.MARGIN_LEFT] ?: 24,
            marginRight = prefs[Keys.MARGIN_RIGHT] ?: 24,
            selectedFontFamily = try {
                ReaderFontFamily.valueOf(prefs[Keys.FONT_FAMILY] ?: "DEFAULT")
            } catch (e: IllegalArgumentException) {
                ReaderFontFamily.DEFAULT
            },
            customFontPath = prefs[Keys.CUSTOM_FONT_PATH]
        )
    }

    suspend fun setVolumeKeyNavigation(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VOLUME_KEY_NAVIGATION] = enabled
        }
    }

    suspend fun setVolumeKeyDirectionSwapped(swapped: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VOLUME_KEY_SWAPPED] = swapped
        }
    }

    suspend fun setTextSettings(textSettings: TextSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_SIZE] = textSettings.fontSize
            prefs[Keys.LINE_SPACING_MULTIPLIER] = textSettings.lineSpacingMultiplier
            prefs[Keys.LINE_SPACING_EXTRA] = textSettings.lineSpacingExtra
            prefs[Keys.MARGIN_TOP] = textSettings.marginTop
            prefs[Keys.MARGIN_BOTTOM] = textSettings.marginBottom
            prefs[Keys.MARGIN_LEFT] = textSettings.marginLeft
            prefs[Keys.MARGIN_RIGHT] = textSettings.marginRight
            prefs[Keys.FONT_FAMILY] = textSettings.selectedFontFamily.name
            if (textSettings.customFontPath != null) {
                prefs[Keys.CUSTOM_FONT_PATH] = textSettings.customFontPath
            } else {
                prefs.remove(Keys.CUSTOM_FONT_PATH)
            }
        }
    }

    suspend fun setFontSize(size: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_SIZE] = size
        }
    }

    suspend fun setLineSpacing(multiplier: Float, extra: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LINE_SPACING_MULTIPLIER] = multiplier
            prefs[Keys.LINE_SPACING_EXTRA] = extra
        }
    }

    suspend fun setMargins(top: Int, bottom: Int, left: Int, right: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MARGIN_TOP] = top
            prefs[Keys.MARGIN_BOTTOM] = bottom
            prefs[Keys.MARGIN_LEFT] = left
            prefs[Keys.MARGIN_RIGHT] = right
        }
    }

    suspend fun setFontFamily(family: ReaderFontFamily) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_FAMILY] = family.name
        }
    }

    suspend fun setCustomFontPath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path != null) {
                prefs[Keys.CUSTOM_FONT_PATH] = path
            } else {
                prefs.remove(Keys.CUSTOM_FONT_PATH)
            }
        }
    }

    suspend fun setTheme(themeName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME] = themeName
        }
    }
}
