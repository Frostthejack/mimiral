package com.mimiral.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mimiral.app.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {

    @Insert
    suspend fun insertSession(session: ReadingSessionEntity): Long

    @Query("SELECT * FROM reading_sessions ORDER BY start_time DESC")
    fun getAllSessions(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY start_time DESC")
    suspend fun getAllSessionsList(): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_sessions WHERE book_id = :bookId ORDER BY start_time DESC")
    fun getSessionsForBook(bookId: Int): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE date = :date ORDER BY start_time DESC")
    fun getSessionsForDate(date: String): Flow<List<ReadingSessionEntity>>

    @Query(
        "SELECT * FROM reading_sessions " +
            "WHERE date BETWEEN :startDate AND :endDate " +
            "ORDER BY start_time DESC"
    )
    fun getSessionsBetweenDates(
        startDate: String,
        endDate: String
    ): Flow<List<ReadingSessionEntity>>

    @Query("SELECT COUNT(*) FROM reading_sessions WHERE date = :date")
    suspend fun getSessionCountForDate(date: String): Int

    @Query(
        "SELECT SUM(duration_seconds) FROM reading_sessions " +
            "WHERE date BETWEEN :startDate AND :endDate"
    )
    suspend fun getTotalReadingTimeBetween(
        startDate: String,
        endDate: String
    ): Long?

    @Query(
        "SELECT SUM(pages_read) FROM reading_sessions " +
            "WHERE date BETWEEN :startDate AND :endDate"
    )
    suspend fun getTotalPagesReadBetween(
        startDate: String,
        endDate: String
    ): Int?

    @Query("SELECT date FROM reading_sessions GROUP BY date ORDER BY date DESC")
    fun getAllReadingDates(): Flow<List<String>>

    @Query("SELECT COUNT(DISTINCT date) FROM reading_sessions")
    suspend fun getTotalReadingDays(): Int

    @Query("SELECT DISTINCT date FROM reading_sessions ORDER BY date DESC")
    suspend fun getDistinctDatesDesc(): List<String>
}
