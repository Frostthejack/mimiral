package com.mimiral.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores user-defined reading goals.
 * Supports daily, weekly, and yearly goals with different target types.
 */
@Entity(tableName = "reading_goals")
data class ReadingGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "goal_type") val goalType: String, // "daily", "weekly", "yearly"
    @ColumnInfo(name = "target_type") val targetType: String, // "pages", "minutes", "books"
    @ColumnInfo(name = "target_value") val targetValue: Int,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
