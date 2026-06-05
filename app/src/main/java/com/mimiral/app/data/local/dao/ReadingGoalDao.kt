package com.mimiral.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.mimiral.app.data.local.entity.ReadingGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingGoalDao {

    @Insert
    suspend fun insertGoal(goal: ReadingGoalEntity): Long

    @Update
    suspend fun updateGoal(goal: ReadingGoalEntity)

    @Query("UPDATE reading_goals SET is_active = 0 WHERE id = :goalId")
    suspend fun deactivateGoal(goalId: Int)

    @Query("SELECT * FROM reading_goals WHERE is_active = 1 ORDER BY created_at DESC")
    fun getActiveGoals(): Flow<List<ReadingGoalEntity>>

    @Query(
        "SELECT * FROM reading_goals WHERE is_active = 1 " +
            "AND goal_type = :goalType ORDER BY created_at DESC"
    )
    fun getActiveGoalsByType(goalType: String): Flow<List<ReadingGoalEntity>>

    @Query(
        "SELECT * FROM reading_goals WHERE goal_type = :goalType " +
            "AND target_type = :targetType AND is_active = 1 LIMIT 1"
    )
    suspend fun getActiveGoal(goalType: String, targetType: String): ReadingGoalEntity?

    @Query("SELECT * FROM reading_goals ORDER BY created_at DESC")
    fun getAllGoals(): Flow<List<ReadingGoalEntity>>

    @Query("DELETE FROM reading_goals WHERE id = :goalId")
    suspend fun deleteGoal(goalId: Int)
}
