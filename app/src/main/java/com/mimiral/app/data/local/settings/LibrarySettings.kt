package com.mimiral.app.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "library_settings"
)

/**
 * All supported ebook file formats for library scanning.
 */
enum class SupportedFormat(val extension: String, val displayName: String) {
    EPUB("epub", "EPUB"),
    PDF("pdf", "PDF"),
    MOBI("mobi", "MOBI"),
    AZW3("azw3", "AZW3"),
    FB2("fb2", "FB2"),
    DJVU("djvu", "DJVU"),
    CBZ("cbz", "CBZ (Comic)"),
    CBR("cbr", "CBR (Comic)"),
    TXT("txt", "Plain Text"),
    RTF("rtf", "RTF"),
    DOC("doc", "DOC"),
    DOCX("docx", "DOCX"),
    MD("md", "Markdown");

    companion object {
        val ALL_EXTENSIONS: Set<String> = entries.map { it.extension }.toSet()

        fun fromExtension(ext: String): SupportedFormat? =
            entries.find { it.extension == ext.lowercase() }
    }
}

class LibrarySettingsRepository(private val context: Context) {

    private object Keys {
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val FILTER_OPTION = stringPreferencesKey("filter_option")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SCAN_DIRECTORIES = stringSetPreferencesKey("scan_directories")
        val ENABLED_FORMATS = stringSetPreferencesKey("enabled_formats")
    }

    val settings: Flow<LibrarySettings> = context.libraryDataStore.data.map { prefs ->
        val sortOption = try {
            SortOption.valueOf(prefs[Keys.SORT_OPTION] ?: SortOption.RECENT.name)
        } catch (_: IllegalArgumentException) {
            SortOption.RECENT
        }
        val filterOption = try {
            FilterOption.valueOf(prefs[Keys.FILTER_OPTION] ?: FilterOption.ALL.name)
        } catch (_: IllegalArgumentException) {
            FilterOption.ALL
        }
        val viewMode = try {
            ViewMode.valueOf(prefs[Keys.VIEW_MODE] ?: ViewMode.GRID.name)
        } catch (_: IllegalArgumentException) {
            ViewMode.GRID
        }
        val scanDirs = prefs[Keys.SCAN_DIRECTORIES] ?: emptySet()
        val enabledFormats = prefs[Keys.ENABLED_FORMATS]?.let { saved ->
            if (saved.isEmpty()) SupportedFormat.ALL_EXTENSIONS else saved
        } ?: SupportedFormat.ALL_EXTENSIONS

        LibrarySettings(
            sortOption = sortOption,
            filterOption = filterOption,
            viewMode = viewMode,
            scanDirectories = scanDirs,
            enabledFormats = enabledFormats
        )
    }

    val scanDirectories: Flow<Set<String>> = context.libraryDataStore.data.map { prefs ->
        prefs[Keys.SCAN_DIRECTORIES] ?: emptySet()
    }

    val enabledFormats: Flow<Set<String>> = context.libraryDataStore.data.map { prefs ->
        val saved = prefs[Keys.ENABLED_FORMATS]
        if (saved.isNullOrEmpty()) SupportedFormat.ALL_EXTENSIONS else saved
    }

    suspend fun setSortOption(option: SortOption) {
        context.libraryDataStore.edit { prefs ->
            prefs[Keys.SORT_OPTION] = option.name
        }
    }

    suspend fun setFilterOption(option: FilterOption) {
        context.libraryDataStore.edit { prefs ->
            prefs[Keys.FILTER_OPTION] = option.name
        }
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.libraryDataStore.edit { prefs ->
            prefs[Keys.VIEW_MODE] = mode.name
        }
    }

    suspend fun addScanDirectory(path: String) {
        context.libraryDataStore.edit { prefs ->
            val current = prefs[Keys.SCAN_DIRECTORIES] ?: emptySet()
            prefs[Keys.SCAN_DIRECTORIES] = current + path
        }
    }

    suspend fun removeScanDirectory(path: String) {
        context.libraryDataStore.edit { prefs ->
            val current = prefs[Keys.SCAN_DIRECTORIES] ?: emptySet()
            prefs[Keys.SCAN_DIRECTORIES] = current - path
        }
    }

    suspend fun setEnabledFormats(extensions: Set<String>) {
        context.libraryDataStore.edit { prefs ->
            prefs[Keys.ENABLED_FORMATS] = extensions
        }
    }
}

data class LibrarySettings(
    val sortOption: SortOption = SortOption.RECENT,
    val filterOption: FilterOption = FilterOption.ALL,
    val viewMode: ViewMode = ViewMode.GRID,
    val scanDirectories: Set<String> = emptySet(),
    val enabledFormats: Set<String> = SupportedFormat.ALL_EXTENSIONS
)

enum class ViewMode {
    GRID,
    LIST
}

enum class SortOption(val displayName: String) {
    RECENT("Recent"),
    TITLE("Title"),
    AUTHOR("Author"),
    SERIES("Series"),
    PROGRESS("Progress"),
    RATING("Rating"),
    DATE_ADDED("Date Added")
}

enum class FilterOption(val displayName: String) {
    ALL("All"),
    READING("Reading"),
    UNREAD("Unread"),
    FINISHED("Finished"),
    DOWNLOADED("Downloaded")
}
