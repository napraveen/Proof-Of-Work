package com.proofofwork.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proofofwork.ProofOfWorkApp
import com.proofofwork.core.data.AppUserPreferences
import com.proofofwork.core.data.UserPreferencesRepository
import com.proofofwork.core.data.database.TaskEntity
import com.proofofwork.core.data.database.VoiceLogEntity
import com.proofofwork.core.model.GitHubStatus
import com.proofofwork.core.model.LeetCodeStatus
import com.proofofwork.core.model.NetworkResult
import com.proofofwork.core.network.AiWorkVerifierService
import com.proofofwork.core.network.GitHubService
import com.proofofwork.core.network.LeetCodeService
import com.proofofwork.core.network.VoiceVerificationResult
import com.proofofwork.core.util.SoundFeedbackHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val preferences: AppUserPreferences = AppUserPreferences(),
    val tasks: List<TaskEntity> = emptyList(),
    val voiceLogs: List<VoiceLogEntity> = emptyList(),
    val githubStatus: GitHubStatus = GitHubStatus(),
    val leetcodeStatus: LeetCodeStatus = LeetCodeStatus(),
    val isVerifying: Boolean = false,
    val isAnalyzingVoice: Boolean = false,
    val lastVoiceResult: VoiceVerificationResult? = null,
    val standupReport: String? = null,
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
                    (prevLc.isEmpty() && prefs.leetcodeUsername.isNotEmpty())
                ) {
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

        // Collect Voice Proof-of-Work Logs
        viewModelScope.launch {
            taskDao.getAllVoiceLogsFlow().collect { logs ->
                _uiState.update { it.copy(voiceLogs = logs) }
            }
        }
    }

    fun analyzeVoiceProof(transcript: String, isConfessionMode: Boolean = false) {
        if (transcript.isBlank() || _uiState.value.isAnalyzingVoice) return

        _uiState.update {
            it.copy(
                isAnalyzingVoice = true,
                lastVoiceResult = null,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val prefs = _uiState.value.preferences
            val pendingTasks = _uiState.value.tasks.filter { !it.isCompletedToday }

            val result = if (isConfessionMode) {
                AiWorkVerifierService.evaluateConfession(transcript)
            } else {
                AiWorkVerifierService.evaluateSpokenWork(transcript, prefs, pendingTasks)
            }

            if (result.isVerified) {
                SoundFeedbackHelper.playSuccessChime()

                // Save Voice Log to Room DB
                val logEntity = VoiceLogEntity(
                    transcript = result.cleanedTranscript,
                    summary = result.summary,
                    category = result.category,
                    effortMinutes = result.effortMinutes,
                    rewardMinutes = result.rewardMinutes,
                    aiFeedback = result.aiFeedback,
                    confidenceScore = result.confidenceScore,
                    wordCount = result.wordCount,
                    isConfession = isConfessionMode,
                    dateString = UserPreferencesRepository.getTodayDateString()
                )
                taskDao.insertVoiceLog(logEntity)

                // If it matched a pending task, mark that task completed too
                result.matchedTaskId?.let { matchedId ->
                    _uiState.value.tasks.firstOrNull { it.id == matchedId }?.let { task ->
                        taskDao.updateTask(
                            task.copy(
                                isCompletedToday = true,
                                completedAtTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }

                // Credit Instagram minutes & word count
                prefsRepo.addEarnedVoiceMinutes(
                    minutes = result.rewardMinutes,
                    wordsSpoken = result.wordCount,
                    isConfession = isConfessionMode
                )

                _uiState.update {
                    it.copy(
                        isAnalyzingVoice = false,
                        lastVoiceResult = result,
                        statusMessage = "🎙️ Verified '${result.summary}'! +${result.rewardMinutes}m added to your Instagram wallet."
                    )
                }
            } else {
                SoundFeedbackHelper.playErrorTone()
                _uiState.update {
                    it.copy(
                        isAnalyzingVoice = false,
                        lastVoiceResult = result
                    )
                }
            }
        }
    }

    fun clearLastVoiceResult() {
        _uiState.update { it.copy(lastVoiceResult = null) }
    }

    fun deleteVoiceLog(log: VoiceLogEntity) {
        viewModelScope.launch {
            if (log.rewardMinutes > 0) {
                prefsRepo.deductEarnedVoiceMinutes(log.rewardMinutes, log.wordCount)
            }
            taskDao.deleteVoiceLog(log)
        }
    }

    fun generateStandupSummary() {
        val state = _uiState.value
        val todayStr = UserPreferencesRepository.getTodayDateString()
        val todayLogs = state.voiceLogs.filter { it.dateString == todayStr }
        val completedTasks = state.tasks.filter { it.isCompletedToday }
        val report = AiWorkVerifierService.generateDailyStandupReport(
            voiceLogs = todayLogs,
            completedTasks = completedTasks,
            prefs = state.preferences
        )
        _uiState.update { it.copy(standupReport = report) }
    }

    fun dismissStandupReport() {
        _uiState.update { it.copy(standupReport = null) }
    }

    fun toggleWeekendFreedom(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateWeekendFreedomEnabled(enabled)
        }
    }

    fun toggleDemo5DayStreak(simulate: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateDemoSimulate5DayStreak(simulate)
        }
    }

    fun toggleWeekdayIndex(dayIdx: Int) {
        viewModelScope.launch {
            prefsRepo.toggleWeekdayCompleted(dayIdx)
        }
    }

    fun verifyWorkNow() {
        if (_uiState.value.isVerifying) return

        _uiState.update {
            it.copy(
                isVerifying = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            // Immediately load freshest screen time and preferences directly from DataStore
            try {
                val fresh = prefsRepo.userPreferencesFlow.first()
                _uiState.update { it.copy(preferences = fresh) }
            } catch (_: Exception) {
            }

            val currentPrefs = _uiState.value.preferences
            val ghUser = currentPrefs.githubUsername
            val lcUser = currentPrefs.leetcodeUsername

            if (ghUser.isBlank() && lcUser.isBlank()) {
                _uiState.update {
                    it.copy(
                        isVerifying = false,
                        errorMessage = "Tip: Use '🎤 Speak Proof of Work' anytime, or connect GitHub/LeetCode in Settings."
                    )
                }
                return@launch
            }

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

    fun addTask(title: String, rewardMinutes: Int = 15, category: String = "DEEP_WORK") {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskDao.insertTask(
                TaskEntity(
                    title = title.trim(),
                    category = category,
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
                SoundFeedbackHelper.playSuccessChime()
                prefsRepo.addEarnedTaskMinutes(task.rewardMinutes)
            } else {
                prefsRepo.addEarnedTaskMinutes(-task.rewardMinutes)
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            if (task.isCompletedToday && task.rewardMinutes > 0) {
                prefsRepo.addEarnedTaskMinutes(-task.rewardMinutes)
            }
            taskDao.deleteTask(task)
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
