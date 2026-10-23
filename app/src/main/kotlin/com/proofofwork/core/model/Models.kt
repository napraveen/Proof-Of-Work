package com.proofofwork.core.model

data class ContributionDay(
    val date: String,
    val level: Int,
    val count: Int = 0
)

data class GitHubStatus(
    val isPushedToday: Boolean = false,
    val commitCount: Int = 0,
    val latestRepo: String = "",
    val latestCommitMessage: String = "",
    val streakDays: Int = 0,
    val recentContributions: List<ContributionDay> = emptyList(),
    val lastCheckedTimestamp: Long = 0L
)

data class LeetCodeStatus(
    val isSolvedToday: Boolean = false,
    val problemTitle: String = "",
    val problemSlug: String = "",
    val totalSolved: Int = 0,
    val streakDays: Int = 0,
    val totalActiveDays: Int = 0,
    val recentDays: List<ContributionDay> = emptyList(),
    val lastCheckedTimestamp: Long = 0L
)

data class WalletBalance(
    val baseFreeMinutes: Int = 15,
    val earnedFromGitHubMinutes: Int = 0,
    val earnedFromLeetCodeMinutes: Int = 0,
    val earnedFromTasksMinutes: Int = 0,
    val usedMinutesToday: Int = 0,
    val lastResetDate: String = ""
) {
    val totalAvailableMinutes: Int
        get() = maxOf(0, (baseFreeMinutes + earnedFromGitHubMinutes + earnedFromLeetCodeMinutes + earnedFromTasksMinutes) - usedMinutesToday)

    val totalEarnedMinutes: Int
        get() = earnedFromGitHubMinutes + earnedFromLeetCodeMinutes + earnedFromTasksMinutes
}

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}
