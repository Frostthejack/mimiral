package com.mimiral.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks individual reading sessions for statistics.
 * Each session represents one continuous reading period for a book.
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
    indices = [Index(value = ["book_id"]), Index(value = ["start_time"])]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long = 0,
    @ColumnInfo(name = "pages_read") val pagesRead: Int = 0,
    @ColumnInfo(name = "date") val date: String // YYYY-MM-DD format for easy grouping
)
