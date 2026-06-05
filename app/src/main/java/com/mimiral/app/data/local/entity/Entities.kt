package com.mimiral.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "file_path") val filePath: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "cover_path") val coverPath: String? = null,
    val format: String, // EPUB, PDF, CBZ, etc.
    @ColumnInfo(name = "file_size") val fileSize: Long = 0,
    @ColumnInfo(name = "date_added") val dateAdded: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "date_modified") val dateModified: Long? = null,
    @ColumnInfo(name = "is_downloaded") val isDownloaded: Boolean = true,
    val source: String = "LOCAL", // LOCAL, KAVITA, OPDS
    @ColumnInfo(name = "source_id") val sourceId: String? = null,
    @ColumnInfo(name = "kavita_series_id") val kavitaSeriesId: Int? = null,
    @ColumnInfo(name = "kavita_library_id") val kavitaLibraryId: Int? = null
)

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int = 0,
    @ColumnInfo(name = "character_offset") val characterOffset: Long = 0,
    @ColumnInfo(name = "total_characters") val totalCharacters: Long = 0,
    @ColumnInfo(name = "page_number") val pageNumber: Int = 0,
    @ColumnInfo(name = "total_pages") val totalPages: Int = 0,
    @ColumnInfo(name = "progress_percent") val progressPercent: Float = 0f,
    @ColumnInfo(name = "last_read_position") val lastReadPosition: String? = null,
    @ColumnInfo(name = "last_read_time") val lastReadTime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "total_time_read") val totalTimeRead: Long = 0,
    @ColumnInfo(name = "kavita_synced") val kavitaSynced: Boolean = false
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int = 0,
    @ColumnInfo(name = "page_number") val pageNumber: Int = 0,
    val position: String? = null,
    val title: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "created_time") val createdTime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "kavita_synced") val kavitaSynced: Boolean = false,
    @ColumnInfo(name = "kavita_chapter_id") val kavitaChapterId: Int? = null,
    @ColumnInfo(name = "modified_time") val modifiedTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int = 0,
    @ColumnInfo(name = "start_position") val startPosition: String? = null,
    @ColumnInfo(name = "end_position") val endPosition: String? = null,
    @ColumnInfo(name = "selected_text") val selectedText: String? = null,
    val color: String,
    val note: String? = null,
    @ColumnInfo(name = "created_time") val createdTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "created_time") val createdTime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0
)

@Entity(
    tableName = "book_collections",
    primaryKeys = ["book_id", "collection_id"]
)
data class BookCollectionCrossRef(
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "collection_id") val collectionId: Int
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(
    tableName = "book_tags",
    primaryKeys = ["book_id", "tag_id"]
)
data class BookTagCrossRef(
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "tag_id") val tagId: Int
)

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // KAVITA, CALIBRE, KOMGA
    val url: String,
    val username: String? = null,
    val password: String? = null,
    @ColumnInfo(name = "api_key") val apiKey: String? = null,
    @ColumnInfo(name = "jwt_token") val jwtToken: String? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long? = null
)

@Entity(tableName = "opds_catalogs")
data class OpdsCatalogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
    @ColumnInfo(name = "auth_type") val authType: String = "NONE",
    @ColumnInfo(name = "is_active") val isActive: Boolean = true
)

/**
 * Per-book PDF settings including margin crop values.
 */
@Entity(tableName = "pdf_settings")
data class PdfSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val bookId: Int,
    @ColumnInfo(name = "crop_left") val cropLeft: Int = 0,
    @ColumnInfo(name = "crop_top") val cropTop: Int = 0,
    @ColumnInfo(name = "crop_right") val cropRight: Int = 0,
    @ColumnInfo(name = "crop_bottom") val cropBottom: Int = 0,
    @ColumnInfo(name = "auto_detected") val autoDetected: Boolean = false
)

/**
 * Chapter content entity — stores extracted chapter text for full-text search.
 * Each row represents one chapter of one book.
 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["book_id"])]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    val title: String? = null,
    val content: String = ""
)

/**
 * FTS5 external content virtual table for chapter search.
 * Uses Room's @Fts4 with external content pointing to the chapters table.
 */
@Entity(tableName = "chapters_fts")
@Fts4(contentEntity = ChapterEntity::class)
data class ChapterFtsEntity(
    @PrimaryKey val rowid: Int,
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    val title: String? = null,
    val content: String = ""
)

/**
 * Tracks individual reading sessions for streak calculation and statistics.
 * Each row represents one continuous reading session for a book on a given date.
 */
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["book_id"]), Index(value = ["session_date"])]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    /** Date of the reading session as epoch day (days since 1970-01-01). */
    @ColumnInfo(name = "session_date") val sessionDate: Long,
    /** Reading duration in milliseconds for this session. */
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    /** Pages read during this session. */
    @ColumnInfo(name = "pages_read") val pagesRead: Int = 0,
    /** Starting page number for this session. */
    @ColumnInfo(name = "start_page") val startPage: Int = 0,
    /** Ending page number for this session. */
    @ColumnInfo(name = "end_page") val endPage: Int = 0,
    /** Timestamp when the session was recorded. */
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
