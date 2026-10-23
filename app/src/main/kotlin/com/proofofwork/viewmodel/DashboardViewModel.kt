package com.proofofwork.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proofofwork.ProofOfWorkApp
import com.proofofwork.core.data.AppUserPreferences
import com.proofofwork.core.data.database.TaskEntity
import com.proofofwork.core.model.GitHubStatus
import com.proofofwork.core.model.LeetCodeStatus
import com.proofofwork.core.model.NetworkResult
import com.proofofwork.core.network.GitHubService
import com.proofofwork.core.network.LeetCodeService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val preferences: AppUserPreferences = AppUserPreferences(),
    val tasks: List<TaskEntity> = emptyList(),
    val githubStatus: GitHubStatus = GitHubStatus(),
    val leetcodeStatus: LeetCodeStatus = LeetCodeStatus(),
    val isVerifying: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ProofOfWorkApp
    private val prefsRepo = app.preferencesRepository
    private val taskDao = app.database.taskDao()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Collect User Preferences
        viewModelScope.launch {
            prefsRepo.userPreferencesFlow.collect { prefs ->
                val prevGh = _uiState.value.preferences.githubUsername
                val prevLc = _uiState.value.preferences.leetcodeUsername
                _uiState.update { it.copy(preferences = prefs) }

                // Auto-refresh graph if username was set for the first time
                if ((prevGh.isEmpty() && prefs.githubUsername.isNotEmpty()) ||
                    (prevLc.isEmpty() && prefs.leetcodeUsername.isNotEmpty())) {
                    verifyWorkNow()
                }
            }
        }

        // Collect Daily Tasks
        viewModelScope.launch {
            taskDao.getAllTasksFlow().collect { taskList ->
                _uiState.update { it.copy(tasks = taskList) }
            }
        }
    }

    fun verifyWorkNow() {
        val currentPrefs = _uiState.value.preferences
        val ghUser = currentPrefs.githubUsername
        val lcUser = currentPrefs.leetcodeUsername

        if (ghUser.isBlank() && lcUser.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Please enter your GitHub or LeetCode username in Settings first.")
            }
            return
        }

        _uiState.update { it.copy(isVerifying = true, statusMessage = "Checking your streaks & contributions...", errorMessage = null) }

        viewModelScope.launch {
            val messages = mutableListOf<String>()

            // 1. Verify GitHub
            if (ghUser.isNotBlank()) {
                when (val result = GitHubService.verifyTodayPush(ghUser)) {
                    is NetworkResult.Success -> {
                        val status = result.data
                        prefsRepo.updateGithubVerified(status.isPushedToday)
                        _uiState.update { it.copy(githubStatus = status) }

                        if (status.isPushedToday) {
                            messages.add("✅ GitHub: ${status.commitCount} contribution(s) today! 🔥 ${status.streakDays}d streak (+${currentPrefs.githubRewardMinutes}m)")
                        } else {
                            messages.add("❌ GitHub: No commits today yet. Current streak: ${status.streakDays}d")
                        }
                    }
                    is NetworkResult.Error -> {
                        messages.add("⚠️ GitHub: ${result.message}")
                    }
                    else -> {}
                }
            }

            // 2. Verify LeetCode
            if (lcUser.isNotBlank()) {
                when (val result = LeetCodeService.verifyTodaySolve(lcUser)) {
                    is NetworkResult.Success -> {
                        val status = result.data
                        prefsRepo.updateLeetcodeVerified(status.isSolvedToday)
                        _uiState.update { it.copy(leetcodeStatus = status) }

                        if (status.isSolvedToday) {
                            messages.add("✅ LeetCode: Solved '${status.problemTitle}'! 🔥 ${status.streakDays}d streak (+${currentPrefs.leetcodeRewardMinutes}m)")
                        } else {
                            messages.add("❌ LeetCode: No solve today yet. Streak: ${status.streakDays}d")
                        }
                    }
                    is NetworkResult.Error -> {
                        messages.add("⚠️ LeetCode: ${result.message}")
                    }
                    else -> {}
                }
            }

            _uiState.update {
                it.copy(
                    isVerifying = false,
                    statusMessage = messages.joinToString("\n")
                )
            }
        }
    }

    fun addTask(title: String, rewardMinutes: Int = 15) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskDao.insertTask(
                TaskEntity(
                    title = title.trim(),
                    rewardMinutes = maxOf(5, rewardMinutes)
                )
            )
        }
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            val updated = task.copy(
                isCompletedToday = !task.isCompletedToday,
                completedAtTimestamp = if (!task.isCompletedToday) System.currentTimeMillis() else 0L
            )
            taskDao.updateTask(updated)

            if (updated.isCompletedToday) {
                prefsRepo.addEarnedTaskMinutes(task.rewardMinutes)
            } else {
                prefsRepo.addEarnedTaskMinutes(-task.rewardMinutes)
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.deleteTask(task)
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
