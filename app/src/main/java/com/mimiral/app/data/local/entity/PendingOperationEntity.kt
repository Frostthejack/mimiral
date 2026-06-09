package com.mimiral.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for pending sync operations queued when offline.
 * Flushed to Kavita on network reconnect.
 */
@Entity(
    tableName = "pending_operations",
    indices = [Index(value = ["created_at"], name = "index_pending_operations_created_at")]
)
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "operation_type") val operationType: String, // PUSH_PROGRESS, FULL_SYNC
    @ColumnInfo(name = "series_id") val seriesId: Int,
    @ColumnInfo(name = "library_id") val libraryId: Int,
    @ColumnInfo(name = "volume_id") val volumeId: Int = 0,
    @ColumnInfo(name = "chapter_id") val chapterId: Int = 0,
    @ColumnInfo(name = "page_num") val pageNum: Int = 0,
    @ColumnInfo(name = "book_scroll_id") val bookScrollId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "attempts") val attempts: Int = 0
)
