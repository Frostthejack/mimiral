package com.mimiral.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimiral.app.data.local.entity.ReadingGoalEntity
import com.mimiral.app.data.repository.ReadingGoalsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalWithProgress(
    val goal: ReadingGoalEntity,
    val currentValue: Int = 0
) {
    val progressPercent: Float
        get() = if (goal.targetValue > 0) {
            (currentValue.toFloat() / goal.targetValue).coerceIn(0f, 1f)
        } else 0f

    val isComplete: Boolean
        get() = currentValue >= goal.targetValue
}

data class ReadingGoalsUiState(
    val isLoading: Boolean = true,
    val dailyGoals: List<GoalWithProgress> = emptyList(),
    val weeklyGoals: List<GoalWithProgress> = emptyList(),
    val yearlyGoals: List<GoalWithProgress> = emptyList(),
    val todayPages: Int = 0,
    val todayMinutes: Long = 0,
    val todayBooks: Int = 0,
    val weekPages: Int = 0,
    val weekMinutes: Long = 0,
    val weekBooks: Int = 0,
    val yearPages: Int = 0,
    val yearMinutes: Long = 0,
    val yearBooks: Int = 0,
    val showEditDialog: Boolean = false,
    val editingGoalType: String = "",
    val editingTargetType: String = "",
    val editingTargetValue: String = "",
    val error: String? = null
)

@HiltViewModel
class ReadingGoalsViewModel @Inject constructor(
    private val readingGoalsRepository: ReadingGoalsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadingGoalsUiState())
    val uiState: StateFlow<ReadingGoalsUiState> = _uiState.asStateFlow()

    init {
        loadGoals()
    }

    private fun loadGoals() {
        viewModelScope.launch {
            combine(
                readingGoalsRepository.getActiveGoalsByType("daily"),
                readingGoalsRepository.getActiveGoalsByType("weekly"),
                readingGoalsRepository.getActiveGoalsByType("yearly")
            ) { dailyGoals, weeklyGoals, yearlyGoals ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dailyGoals = wrapWithProgress(dailyGoals),
                    weeklyGoals = wrapWithProgress(weeklyGoals),
                    yearlyGoals = wrapWithProgress(yearlyGoals)
                )
            }.collect { }
        }
    }

    private fun wrapWithProgress(goals: List<ReadingGoalEntity>): List<GoalWithProgress> {
        return goals.map { goal ->
            // Current values are read from statistics; for now initialize to 0
            // The StatisticsModule will update these via the reading sessions
            GoalWithProgress(goal = goal, currentValue = 0)
        }
    }

    fun showAddGoalDialog(goalType: String, targetType: String = "pages") {
        _uiState.value = _uiState.value.copy(
            showEditDialog = true,
            editingGoalType = goalType,
            editingTargetType = targetType,
            editingTargetValue = ""
        )
    }

    fun dismissEditDialog() {
        _uiState.value = _uiState.value.copy(
            showEditDialog = false,
            editingGoalType = "",
            editingTargetType = "",
            editingTargetValue = ""
        )
    }

    fun updateEditingValue(value: String) {
        _uiState.value = _uiState.value.copy(editingTargetValue = value)
    }

    fun saveGoal() {
        val state = _uiState.value
        val targetValue = state.editingTargetValue.toIntOrNull() ?: return
        if (targetValue <= 0) return

        viewModelScope.launch {
            try {
                readingGoalsRepository.setGoal(
                    goalType = state.editingGoalType,
                    targetType = state.editingTargetType,
                    targetValue = targetValue
                )
                dismissEditDialog()
                loadGoals()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to save goal: ${e.message}"
                )
            }
        }
    }

    fun removeGoal(goalId: Int) {
        viewModelScope.launch {
            try {
                readingGoalsRepository.removeGoal(goalId)
                loadGoals()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to remove goal: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
