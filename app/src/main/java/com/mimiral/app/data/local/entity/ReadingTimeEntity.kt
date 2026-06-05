package com.mimiral.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Daily aggregated reading time per book.
 * One row per book per day — updated incrementally as the user reads.
 * Used for statistics dashboard (pages 4.7–4.10) and reading streak calculation.
 */
@Entity(
    tableName = "reading_time",
    primaryKeys = ["book_id", "date"],
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["date"])
    ]
)
data class ReadingTimeEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    /** Date as epoch day (System.currentTimeMillis() / 86_400_000). */
    val date: Long,
    /** Total reading time in milliseconds for this book on this date. */
    @ColumnInfo(name = "time_read_ms") val timeReadMs: Long = 0,
    /** Last updated timestamp (epoch millis) — used for session resume. */
    @ColumnInfo(name = "last_session_start") val lastSessionStart: Long = 0
)
