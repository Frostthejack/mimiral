package com.mimiral.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mimiral.app.data.local.entity.PendingOperationEntity

/**
 * DAO for the pending_operations table — offline sync queue.
 */
@Dao
interface PendingOperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: PendingOperationEntity): Long

    @Query("SELECT * FROM pending_operations ORDER BY created_at ASC")
    suspend fun getAll(): List<PendingOperationEntity>

    @Query("SELECT * FROM pending_operations ORDER BY created_at ASC LIMIT :limit")
    suspend fun getOldest(limit: Int): List<PendingOperationEntity>

    @Query("SELECT COUNT(*) FROM pending_operations")
    suspend fun count(): Int

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM pending_operations")
    suspend fun deleteAll()

    @Query("UPDATE pending_operations SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: Int)

    @Query("DELETE FROM pending_operations WHERE attempts >= :maxAttempts")
    suspend fun deleteFailed(maxAttempts: Int = 5)
}
