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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readingModeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reading_mode_settings"
)

enum class ParagraphSpacing(val displayName: String) {
    COMPACT("Compact"),
    COMFORTABLE("Comfortable"),
    SPACIOUS("Spacious")
}

enum class MarginWidth(val displayName: String) {
    NARROW("Narrow"),
    MEDIUM("Medium"),
    WIDE("Wide")
}

enum class ReadingModeTheme(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SEPIA("Sepia"),
    BLACK("Black")
}

enum class TtsHighlightColor(val displayName: String) {
    YELLOW("Yellow"),
    GREEN("Green"),
    BLUE("Blue"),
    CYAN("Cyan")
}

enum class DefaultReaderMode(val displayName: String) {
    ALWAYS_READING_MODE("Always Reading Mode"),
    FORMAT_SPECIFIC("Format-Specific Reader")
}

data class ReadingModeSettings(
    val fontFamily: String = "DEFAULT",
    val fontSize: Int = 18,
    val lineSpacing: Float = 1.2f,
    val paragraphSpacing: ParagraphSpacing = ParagraphSpacing.COMFORTABLE,
    val margins: MarginWidth = MarginWidth.MEDIUM,
    val theme: ReadingModeTheme = ReadingModeTheme.LIGHT,
    val ttsHighlightColor: TtsHighlightColor = TtsHighlightColor.YELLOW,
    val autoScrollTTS: Boolean = false,
    val defaultReaderMode: DefaultReaderMode = DefaultReaderMode.FORMAT_SPECIFIC
)

class ReadingModeSettingsRepository(private val context: Context) {

    private object Keys {
        val FONT_FAMILY = stringPreferencesKey("reading_mode_font_family")
        val FONT_SIZE = intPreferencesKey("reading_mode_font_size")
        val LINE_SPACING = floatPreferencesKey("reading_mode_line_spacing")
        val PARAGRAPH_SPACING = stringPreferencesKey("reading_mode_paragraph_spacing")
        val MARGINS = stringPreferencesKey("reading_mode_margins")
        val THEME = stringPreferencesKey("reading_mode_theme")
        val TTS_HIGHLIGHT_COLOR = stringPreferencesKey("reading_mode_tts_highlight_color")
        val AUTO_SCROLL_TTS = booleanPreferencesKey("reading_mode_auto_scroll_tts")
        val DEFAULT_READER_MODE = stringPreferencesKey("reading_mode_default_reader_mode")
    }

    val settings: Flow<ReadingModeSettings> = context.readingModeDataStore.data.map { prefs ->
        ReadingModeSettings(
            fontFamily = prefs[Keys.FONT_FAMILY] ?: "DEFAULT",
            fontSize = prefs[Keys.FONT_SIZE] ?: 18,
            lineSpacing = prefs[Keys.LINE_SPACING] ?: 1.2f,
            paragraphSpacing = try {
                ParagraphSpacing.valueOf(prefs[Keys.PARAGRAPH_SPACING] ?: "COMFORTABLE")
            } catch (_: IllegalArgumentException) {
                ParagraphSpacing.COMFORTABLE
            },
            margins = try {
                MarginWidth.valueOf(prefs[Keys.MARGINS] ?: "MEDIUM")
            } catch (_: IllegalArgumentException) {
                MarginWidth.MEDIUM
            },
            theme = try {
                ReadingModeTheme.valueOf(prefs[Keys.THEME] ?: "LIGHT")
            } catch (_: IllegalArgumentException) {
                ReadingModeTheme.LIGHT
            },
            ttsHighlightColor = try {
                TtsHighlightColor.valueOf(prefs[Keys.TTS_HIGHLIGHT_COLOR] ?: "YELLOW")
            } catch (_: IllegalArgumentException) {
                TtsHighlightColor.YELLOW
            },
            autoScrollTTS = prefs[Keys.AUTO_SCROLL_TTS] ?: false,
            defaultReaderMode = try {
                DefaultReaderMode.valueOf(prefs[Keys.DEFAULT_READER_MODE] ?: "FORMAT_SPECIFIC")
            } catch (_: IllegalArgumentException) {
                DefaultReaderMode.FORMAT_SPECIFIC
            }
        )
    }

    suspend fun setFontFamily(family: String) {
        context.readingModeDataStore.edit { prefs ->
            prefs[Keys.FONT_FAMILY] = family
        }
    }

    suspend fun setFontSize(size: Int) {
        context.readingModeDataStore.edit { prefs ->
            prefs[Keys.FONT_SIZE] = size.coerceIn(12, 32)
        }
    }

    suspend fun setLineSpacing(spacing: Float) {
        context.readingModeDataStore.edit { prefs ->
            prefs[Keys.LINE_SPACING] = spacing.coerceIn(1.0f, 2.0f)
        }
    }

    suspend fun setParagraphSpacing(spacing: ParagraphSpacing) {
        context.readingModeDataStore.edit { prefs ->
            prefs[Keys.PARAGRAPH_SPACING] = spacing.name
        }
    }

    suspend fun setMargins(margins: MarginWidth) {
        context.readingModeDataStore.edit { prefs ->
            prefs[Keys.MARGINS] = margins.name
        }
    }

    suspend fun setTheme(theme: ReadingModeTheme) {
        context.readingModeDataStore.edit { prefs ->
            prefs[Keys.THEME] = theme.name
        }
    }

    suspend fun setTtsHighlightColor(color: TtsHighlightColor) {
        context.readingModeDataStore.edit { prefs ->
            prefs[Keys.TTS_HIGHLIGHT_COLOR] = color.name
        }
    }

    suspend fun setAutoScrollTTS(enabled: Boolean) {
        context.readingModeDataStore.edit { prefs ->
            prefs[Keys.AUTO_SCROLL_TTS] = enabled
        }
    }

    suspend fun setDefaultReaderMode(mode: DefaultReaderMode) {
        context.readingModeDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_READER_MODE] = mode.name
        }
    }
}
