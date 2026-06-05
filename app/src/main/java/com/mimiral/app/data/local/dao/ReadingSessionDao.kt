package com.mimiral.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mimiral.app.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSessionEntity): Long

    @Query("SELECT * FROM reading_sessions WHERE book_id = :bookId ORDER BY start_time DESC")
    fun getSessionsForBook(bookId: Int): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY start_time DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 50): Flow<List<ReadingSessionEntity>>

    /**
     * Get total pages read today (since midnight local time).
     */
    @Query(
        "SELECT COALESCE(SUM(pages_read), 0) FROM reading_sessions " +
            "WHERE start_time >= :dayStart"
    )
    suspend fun getPagesReadSince(dayStart: Long): Int

    /**
     * Get total reading duration (seconds) since the given timestamp.
     */
    @Query(
        "SELECT COALESCE(SUM(duration_seconds), 0) FROM reading_sessions " +
            "WHERE start_time >= :since"
    )
    suspend fun getTotalDurationSince(since: Long): Long

    /**
     * Get session count since the given timestamp.
     */
    @Query(
        "SELECT COUNT(*) FROM reading_sessions " +
            "WHERE start_time >= :since"
    )
    suspend fun getSessionCountSince(since: Long): Int

    /**
     * Get per-day page totals for a date range.
     * Returns list of (dateMillis, totalPages) ordered by date.
     */
    @Query(
        "SELECT start_time, SUM(pages_read) as total_pages " +
            "FROM reading_sessions " +
            "WHERE start_time >= :rangeStart " +
            "GROUP BY (start_time / 86400000) " +
            "ORDER BY start_time ASC"
    )
    suspend fun getDailyPagesInRange(rangeStart: Long): List<DailyPages>

    /**
     * Get all sessions in a date range (for week/month detail).
     */
    @Query(
        "SELECT * FROM reading_sessions " +
            "WHERE start_time >= :rangeStart " +
            "ORDER BY start_time DESC"
    )
    suspend fun getSessionsInRange(rangeStart: Long): List<ReadingSessionEntity>
}

/**
 * Aggregated daily page count.
 */
data class DailyPages(
    val start_time: Long,
    val total_pages: Int
)
