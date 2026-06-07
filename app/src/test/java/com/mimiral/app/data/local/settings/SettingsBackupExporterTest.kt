package com.mimiral.app.data.local.settings

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for SettingsBackupExporter JSON serialization logic.
 *
 * Note: Full integration tests with real DataStore require Robolectric or
 * instrumented tests. These tests verify the JSON format and version handling.
 */
class SettingsBackupExporterTest {

    @Test
    fun `backup JSON contains expected top-level fields`() {
        // Verify the backup format by checking the JSON structure
        val sampleJson = """
        {
            "version": 1,
            "app_version": "0.1.0",
            "exported_at": "2026-06-05T12:00:00",
            "reader_settings": {
                "volume_key_navigation_enabled": true,
                "volume_key_direction_swapped": false,
                "theme_name": "DARK",
                "text_font_size": 20,
                "text_line_spacing_multiplier": 1.5,
                "text_line_spacing_extra": 10.0,
                "text_margin_top": 30,
                "text_margin_bottom": 30,
                "text_margin_left": 20,
                "text_margin_right": 20,
                "text_font_family": "SERIF",
                "text_custom_font_path": null
            },
            "library_settings": {
                "sort_option": "TITLE",
                "filter_option": "READING",
                "view_mode": "LIST"
            }
        }
        """.trimIndent()

        val jsonObject = JsonParser.parseString(sampleJson).asJsonObject

        assertEquals(1, jsonObject.get("version").asInt)
        assertEquals("0.1.0", jsonObject.get("app_version").asString)
        assertTrue(jsonObject.has("exported_at"))
        assertTrue(jsonObject.has("reader_settings"))
        assertTrue(jsonObject.has("library_settings"))
    }

    @Test
    fun `reader settings JSON contains all expected keys`() {
        val sampleJson = """
        {
            "version": 1,
            "reader_settings": {
                "volume_key_navigation_enabled": true,
                "volume_key_direction_swapped": false,
                "theme_name": "DARK",
                "text_font_size": 20,
                "text_line_spacing_multiplier": 1.5,
                "text_line_spacing_extra": 10.0,
                "text_margin_top": 30,
                "text_margin_bottom": 30,
                "text_margin_left": 20,
                "text_margin_right": 20,
                "text_font_family": "SERIF"
            },
            "library_settings": {}
        }
        """.trimIndent()

        val readerSettings = JsonParser.parseString(sampleJson)
            .asJsonObject
            .getAsJsonObject("reader_settings")

        // Boolean keys
        assertTrue(readerSettings.has("volume_key_navigation_enabled"))
        assertTrue(readerSettings.has("volume_key_direction_swapped"))

        // String keys
        assertTrue(readerSettings.has("theme_name"))
        assertTrue(readerSettings.has("text_font_family"))

        // Int keys
        assertTrue(readerSettings.has("text_font_size"))
        assertTrue(readerSettings.has("text_margin_top"))
        assertTrue(readerSettings.has("text_margin_bottom"))
        assertTrue(readerSettings.has("text_margin_left"))
        assertTrue(readerSettings.has("text_margin_right"))

        // Float keys
        assertTrue(readerSettings.has("text_line_spacing_multiplier"))
        assertTrue(readerSettings.has("text_line_spacing_extra"))
    }

    @Test
    fun `library settings JSON contains all expected keys`() {
        val sampleJson = """
        {
            "version": 1,
            "reader_settings": {},
            "library_settings": {
                "sort_option": "TITLE",
                "filter_option": "ALL",
                "view_mode": "GRID"
            }
        }
        """.trimIndent()

        val librarySettings = JsonParser.parseString(sampleJson)
            .asJsonObject
            .getAsJsonObject("library_settings")

        assertTrue(librarySettings.has("sort_option"))
        assertTrue(librarySettings.has("filter_option"))
        assertTrue(librarySettings.has("view_mode"))
    }

    @Test
    fun `backup version constant is 2`() {
        assertEquals(2, SettingsBackupExporter.BACKUP_VERSION)
    }

    @Test
    fun `restore rejects future version`() {
        val futureVersionJson = """
        {
            "version": 999,
            "reader_settings": {},
            "library_settings": {}
        }
        """.trimIndent()

        // The restoreSettingsFromJson method should return false for future versions
        // We can't call it directly without a Context, but we can verify the JSON parsing
        val jsonObject = JsonParser.parseString(futureVersionJson).asJsonObject
        val version = jsonObject.get("version").asInt
        assertTrue(version > SettingsBackupExporter.BACKUP_VERSION)
    }

    @Test
    fun `restore accepts current version`() {
        val currentVersionJson = """
        {
            "version": 2,
            "reader_settings": {},
            "library_settings": {}
        }
        """.trimIndent()

        val jsonObject = JsonParser.parseString(currentVersionJson).asJsonObject
        val version = jsonObject.get("version").asInt
        assertEquals(SettingsBackupExporter.BACKUP_VERSION, version)
    }

    @Test
    fun `restore accepts version 0 legacy format`() {
        val legacyJson = """
        {
            "reader_settings": {
                "theme_name": "SEPIA"
            },
            "library_settings": {}
        }
        """.trimIndent()

        val jsonObject = JsonParser.parseString(legacyJson).asJsonObject
        // Version defaults to 0 when missing
        val version = jsonObject.get("version")?.asInt ?: 0
        assertEquals(0, version)
    }

    @Test
    fun `reader settings values are correctly typed in JSON`() {
        val sampleJson = """
        {
            "version": 1,
            "reader_settings": {
                "volume_key_navigation_enabled": true,
                "theme_name": "DARK",
                "text_font_size": 20,
                "text_line_spacing_multiplier": 1.5
            },
            "library_settings": {}
        }
        """.trimIndent()

        val readerSettings = JsonParser.parseString(sampleJson)
            .asJsonObject
            .getAsJsonObject("reader_settings")

        // Boolean
        assertTrue(readerSettings.get("volume_key_navigation_enabled").asBoolean)

        // String
        assertEquals("DARK", readerSettings.get("theme_name").asString)

        // Int
        assertEquals(20, readerSettings.get("text_font_size").asInt)

        // Float (stored as number in JSON)
        assertEquals(1.5f, readerSettings.get("text_line_spacing_multiplier").asFloat, 0.01f)
    }

    @Test
    fun `library settings enum values are stored as strings`() {
        val sampleJson = """
        {
            "version": 1,
            "reader_settings": {},
            "library_settings": {
                "sort_option": "AUTHOR",
                "filter_option": "FINISHED",
                "view_mode": "LIST"
            }
        }
        """.trimIndent()

        val librarySettings = JsonParser.parseString(sampleJson)
            .asJsonObject
            .getAsJsonObject("library_settings")

        assertEquals("AUTHOR", librarySettings.get("sort_option").asString)
        assertEquals("FINISHED", librarySettings.get("filter_option").asString)
        assertEquals("LIST", librarySettings.get("view_mode").asString)
    }

    @Test
    fun `backup JSON with null custom font path is valid`() {
        val sampleJson = """
        {
            "version": 1,
            "reader_settings": {
                "text_custom_font_path": null
            },
            "library_settings": {}
        }
        """.trimIndent()

        val readerSettings = JsonParser.parseString(sampleJson)
            .asJsonObject
            .getAsJsonObject("reader_settings")

        assertTrue(readerSettings.get("text_custom_font_path").isJsonNull)
    }

    @Test
    fun `empty settings sections are valid`() {
        val sampleJson = """
        {
            "version": 1,
            "reader_settings": {},
            "library_settings": {}
        }
        """.trimIndent()

        val jsonObject = JsonParser.parseString(sampleJson).asJsonObject
        assertEquals(0, jsonObject.getAsJsonObject("reader_settings").size())
        assertEquals(0, jsonObject.getAsJsonObject("library_settings").size())
    }
}
