package com.mimiral.app.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "library_settings"
)

class LibrarySettingsRepository(private val context: Context) {

    private object Keys {
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val FILTER_OPTION = stringPreferencesKey("filter_option")
        val VIEW_MODE = stringPreferencesKey("view_mode")
    }

    val settings: Flow<LibrarySettings> = context.libraryDataStore.data.map { prefs ->
        LibrarySettings(
            sortOption = try {
                SortOption.valueOf(prefs[Keys.SORT_OPTION] ?: SortOption.RECENT.name)
            } catch (_: IllegalArgumentException) {
                SortOption.RECENT
            },
            filterOption = try {
                FilterOption.valueOf(prefs[Keys.FILTER_OPTION] ?: FilterOption.ALL.name)
            } catch (_: IllegalArgumentException) {
                FilterOption.ALL
            },
            viewMode = try {
                ViewMode.valueOf(prefs[Keys.VIEW_MODE] ?: ViewMode.GRID.name)
            } catch (_: IllegalArgumentException) {
                ViewMode.GRID
            }
        )
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
}

data class LibrarySettings(
    val sortOption: SortOption = SortOption.RECENT,
    val filterOption: FilterOption = FilterOption.ALL,
    val viewMode: ViewMode = ViewMode.GRID
)

enum class ViewMode {
    GRID,
    LIST
}

enum class SortOption(val displayName: String) {
    RECENT("Recent"),
    TITLE("Title"),
    AUTHOR("Author"),
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
