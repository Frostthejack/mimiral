package com.mimiral.app.data.repository

import com.mimiral.app.data.local.dao.ReadingGoalDao
import com.mimiral.app.data.local.entity.ReadingGoalEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingGoalsRepository @Inject constructor(
    private val readingGoalDao: ReadingGoalDao
) {

    fun getActiveGoals(): Flow<List<ReadingGoalEntity>> =
        readingGoalDao.getActiveGoals()

    fun getActiveGoalsByType(goalType: String): Flow<List<ReadingGoalEntity>> =
        readingGoalDao.getActiveGoalsByType(goalType)

    fun getAllGoals(): Flow<List<ReadingGoalEntity>> =
        readingGoalDao.getAllGoals()

    suspend fun getActiveGoal(goalType: String, targetType: String): ReadingGoalEntity? =
        readingGoalDao.getActiveGoal(goalType, targetType)

    suspend fun setGoal(goalType: String, targetType: String, targetValue: Int) {
        // Deactivate any existing goal of the same type+target
        val existing = readingGoalDao.getActiveGoal(goalType, targetType)
        if (existing != null) {
            readingGoalDao.deactivateGoal(existing.id)
        }
        readingGoalDao.insertGoal(
            ReadingGoalEntity(
                goalType = goalType,
                targetType = targetType,
                targetValue = targetValue,
                isActive = true
            )
        )
    }

    suspend fun removeGoal(goalId: Int) {
        readingGoalDao.deactivateGoal(goalId)
    }

    suspend fun deleteGoal(goalId: Int) {
        readingGoalDao.deleteGoal(goalId)
    }
}
