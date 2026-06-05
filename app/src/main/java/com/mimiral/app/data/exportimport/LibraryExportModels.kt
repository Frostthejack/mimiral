package com.mimiral.app.data.exportimport

import com.google.gson.annotations.SerializedName

/**
 * Top-level container for a full library export.
 * Contains all data needed to restore a library on another device.
 */
data class LibraryExport(
    @SerializedName("version") val version: Int = CURRENT_VERSION,
    @SerializedName("exported_at") val exportedAt: Long = System.currentTimeMillis(),
    @SerializedName("app_version") val appVersion: String = "0.1.0",
    @SerializedName("books") val books: List<ExportBook> = emptyList(),
    @SerializedName("reading_progress") val readingProgress: List<ExportReadingProgress> = emptyList(),
    @SerializedName("bookmarks") val bookmarks: List<ExportBookmark> = emptyList(),
    @SerializedName("highlights") val highlights: List<ExportHighlight> = emptyList(),
    @SerializedName("collections") val collections: List<ExportCollection> = emptyList(),
    @SerializedName("book_collections") val bookCollections: List<ExportBookCollectionCrossRef> = emptyList(),
    @SerializedName("tags") val tags: List<ExportTag> = emptyList(),
    @SerializedName("book_tags") val bookTags: List<ExportBookTagCrossRef> = emptyList(),
    @SerializedName("reading_sessions") val readingSessions: List<ExportReadingSession> = emptyList(),
    @SerializedName("pdf_settings") val pdfSettings: List<ExportPdfSettings> = emptyList(),
    @SerializedName("servers") val servers: List<ExportServer> = emptyList(),
    @SerializedName("opds_catalogs") val opdsCatalogs: List<ExportOpdsCatalog> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

data class ExportBook(
    @SerializedName("id") val id: Int,
    @SerializedName("file_path") val filePath: String,
    @SerializedName("title") val title: String,
    @SerializedName("author") val author: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("cover_path") val coverPath: String? = null,
    @SerializedName("format") val format: String,
    @SerializedName("file_size") val fileSize: Long = 0,
    @SerializedName("date_added") val dateAdded: Long = 0,
    @SerializedName("date_modified") val dateModified: Long? = null,
    @SerializedName("is_downloaded") val isDownloaded: Boolean = true,
    @SerializedName("source") val source: String = "LOCAL",
    @SerializedName("source_id") val sourceId: String? = null,
    @SerializedName("kavita_series_id") val kavitaSeriesId: Int? = null,
    @SerializedName("kavita_library_id") val kavitaLibraryId: Int? = null
)

data class ExportReadingProgress(
    @SerializedName("id") val id: Int,
    @SerializedName("book_id") val bookId: Int,
    @SerializedName("chapter_index") val chapterIndex: Int = 0,
    @SerializedName("character_offset") val characterOffset: Long = 0,
    @SerializedName("total_characters") val totalCharacters: Long = 0,
    @SerializedName("page_number") val pageNumber: Int = 0,
    @SerializedName("total_pages") val totalPages: Int = 0,
    @SerializedName("progress_percent") val progressPercent: Float = 0f,
    @SerializedName("last_read_position") val lastReadPosition: String? = null,
    @SerializedName("last_read_time") val lastReadTime: Long = 0,
    @SerializedName("total_time_read") val totalTimeRead: Long = 0,
    @SerializedName("kavita_synced") val kavitaSynced: Boolean = false
)

data class ExportBookmark(
    @SerializedName("id") val id: Int,
    @SerializedName("book_id") val bookId: Int,
    @SerializedName("chapter_index") val chapterIndex: Int = 0,
    @SerializedName("page_number") val pageNumber: Int = 0,
    @SerializedName("position") val position: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("note") val note: String? = null,
    @SerializedName("created_time") val createdTime: Long = 0,
    @SerializedName("kavita_synced") val kavitaSynced: Boolean = false,
    @SerializedName("kavita_chapter_id") val kavitaChapterId: Int? = null,
    @SerializedName("modified_time") val modifiedTime: Long = 0
)

data class ExportHighlight(
    @SerializedName("id") val id: Int,
    @SerializedName("book_id") val bookId: Int,
    @SerializedName("chapter_index") val chapterIndex: Int = 0,
    @SerializedName("start_position") val startPosition: String? = null,
    @SerializedName("end_position") val endPosition: String? = null,
    @SerializedName("selected_text") val selectedText: String? = null,
    @SerializedName("color") val color: String,
    @SerializedName("note") val note: String? = null,
    @SerializedName("created_time") val createdTime: Long = 0
)

data class ExportCollection(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("created_time") val createdTime: Long = 0,
    @SerializedName("sort_order") val sortOrder: Int = 0
)

data class ExportBookCollectionCrossRef(
    @SerializedName("book_id") val bookId: Int,
    @SerializedName("collection_id") val collectionId: Int
)

data class ExportTag(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)

data class ExportBookTagCrossRef(
    @SerializedName("book_id") val bookId: Int,
    @SerializedName("tag_id") val tagId: Int
)

data class ExportReadingSession(
    @SerializedName("id") val id: Int,
    @SerializedName("book_id") val bookId: Int,
    @SerializedName("start_time") val startTime: Long,
    @SerializedName("end_time") val endTime: Long,
    @SerializedName("duration_seconds") val durationSeconds: Long = 0,
    @SerializedName("pages_read") val pagesRead: Int = 0,
    @SerializedName("date") val date: String
)

data class ExportPdfSettings(
    @SerializedName("book_id") val bookId: Int,
    @SerializedName("crop_left") val cropLeft: Int = 0,
    @SerializedName("crop_top") val cropTop: Int = 0,
    @SerializedName("crop_right") val cropRight: Int = 0,
    @SerializedName("crop_bottom") val cropBottom: Int = 0,
    @SerializedName("auto_detected") val autoDetected: Boolean = false
)

data class ExportServer(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("url") val url: String,
    @SerializedName("username") val username: String? = null,
    @SerializedName("api_key") val apiKey: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("last_sync_time") val lastSyncTime: Long? = null
)

data class ExportOpdsCatalog(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("username") val username: String? = null,
    @SerializedName("auth_type") val authType: String = "NONE",
    @SerializedName("is_active") val isActive: Boolean = true
)

/**
 * Summary of an import operation result.
 */
data class ImportResult(
    @SerializedName("success") val success: Boolean,
    @SerializedName("books_imported") val booksImported: Int = 0,
    @SerializedName("books_skipped") val booksSkipped: Int = 0,
    @SerializedName("progress_imported") val progressImported: Int = 0,
    @SerializedName("bookmarks_imported") val bookmarksImported: Int = 0,
    @SerializedName("highlights_imported") val highlightsImported: Int = 0,
    @SerializedName("collections_imported") val collectionsImported: Int = 0,
    @SerializedName("tags_imported") val tagsImported: Int = 0,
    @SerializedName("reading_sessions_imported") val readingSessionsImported: Int = 0,
    @SerializedName("pdf_settings_imported") val pdfSettingsImported: Int = 0,
    @SerializedName("servers_imported") val serversImported: Int = 0,
    @SerializedName("opds_catalogs_imported") val opdsCatalogsImported: Int = 0,
    @SerializedName("errors") val errors: List<String> = emptyList()
)
