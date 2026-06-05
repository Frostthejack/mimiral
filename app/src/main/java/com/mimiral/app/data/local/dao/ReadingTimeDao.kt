package com.mimiral.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mimiral.app.data.local.entity.ReadingTimeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingTimeDao {

    /** Get reading time for a specific book on a specific date. */
    @Query("SELECT * FROM reading_time WHERE book_id = :bookId AND date = :date LIMIT 1")
    suspend fun getReadingTime(bookId: Int, date: Long): ReadingTimeEntity?

    /** Get all reading time entries for a book, ordered by date descending. */
    @Query("SELECT * FROM reading_time WHERE book_id = :bookId ORDER BY date DESC")
    fun getReadingTimeForBook(bookId: Int): Flow<List<ReadingTimeEntity>>

    /** Get reading time entries for a book within a date range (epoch days, inclusive). */
    @Query(
        "SELECT * FROM reading_time WHERE book_id = :bookId " +
            "AND date >= :startDate AND date <= :endDate ORDER BY date ASC"
    )
    fun getReadingTimeForBookInRange(
        bookId: Int,
        startDate: Long,
        endDate: Long
    ): Flow<List<ReadingTimeEntity>>

    /** Get total reading time across all books for a given date. */
    @Query("SELECT COALESCE(SUM(time_read_ms), 0) FROM reading_time WHERE date = :date")
    suspend fun getTotalTimeForDate(date: Long): Long

    /** Get total reading time for a book across all dates. */
    @Query("SELECT COALESCE(SUM(time_read_ms), 0) FROM reading_time WHERE book_id = :bookId")
    suspend fun getTotalTimeForBook(bookId: Int): Long

    /** Get total reading time across all books and all dates. */
    @Query("SELECT COALESCE(SUM(time_read_ms), 0) FROM reading_time")
    suspend fun getTotalReadingTime(): Long

    /** Get distinct dates (epoch days) on which any reading occurred, descending. */
    @Query("SELECT DISTINCT date FROM reading_time ORDER BY date DESC")
    fun getReadingDates(): Flow<List<Long>>

    /** Get distinct dates for a specific book, descending. */
    @Query("SELECT DISTINCT date FROM reading_time WHERE book_id = :bookId ORDER BY date DESC")
    fun getReadingDatesForBook(bookId: Int): Flow<List<Long>>

    /** Get the most recent reading date (epoch day), or null if no reading recorded. */
    @Query("SELECT MAX(date) FROM reading_time")
    suspend fun getLastReadingDate(): Long?

    /**
     * Upsert a reading time entry.
     * If a row for (book_id, date) already exists, replaces it.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReadingTime(entity: ReadingTimeEntity)

    /** Delete all reading time entries for a book (e.g., when book is deleted). */
    @Query("DELETE FROM reading_time WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: Int)
}
