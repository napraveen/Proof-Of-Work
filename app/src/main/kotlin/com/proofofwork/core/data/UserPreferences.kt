package com.proofofwork.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class AppUserPreferences(
    val githubUsername: String = "",
    val leetcodeUsername: String = "",
    val baseFreeMinutes: Int = 15,
    val githubRewardMinutes: Int = 25,
    val leetcodeRewardMinutes: Int = 35,
    val isGithubPushedToday: Boolean = false,
    val isLeetcodeSolvedToday: Boolean = false,
    val earnedFromTasksMinutes: Int = 0,
    val usedSecondsToday: Int = 0,
    val lastActiveDate: String = "",
    val isServiceEnabled: Boolean = true,
    val blockedPackages: Set<String> = setOf("com.instagram.android")
) {
    val totalEarnedMinutes: Int
        get() = (if (isGithubPushedToday) githubRewardMinutes else 0) +
                (if (isLeetcodeSolvedToday) leetcodeRewardMinutes else 0) +
                earnedFromTasksMinutes

    val totalAllowanceMinutes: Int
        get() = baseFreeMinutes + totalEarnedMinutes

    val totalAllowanceSeconds: Int
        get() = totalAllowanceMinutes * 60

    val remainingSeconds: Int
        get() = maxOf(0, totalAllowanceSeconds - usedSecondsToday)

    val remainingMinutes: Int
        get() = if (remainingSeconds > 0) (remainingSeconds + 59) / 60 else 0

    val usedMinutesToday: Int
        get() = usedSecondsToday / 60

    val formattedUsedTime: String
        get() {
            val mins = usedSecondsToday / 60
            val secs = usedSecondsToday % 60
            return when {
                mins == 0 && secs == 0 -> "0m"
                mins == 0 -> "${secs}s"
                secs == 0 -> "${mins}m"
                else -> "${mins}m ${secs}s"
            }
        }
}

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val GITHUB_USERNAME = stringPreferencesKey("github_username")
        val LEETCODE_USERNAME = stringPreferencesKey("leetcode_username")
        val BASE_FREE_MINUTES = intPreferencesKey("base_free_minutes")
        val GITHUB_REWARD_MINUTES = intPreferencesKey("github_reward_minutes")
        val LEETCODE_REWARD_MINUTES = intPreferencesKey("leetcode_reward_minutes")
        val IS_GITHUB_PUSHED_TODAY = booleanPreferencesKey("is_github_pushed_today")
        val IS_LEETCODE_SOLVED_TODAY = booleanPreferencesKey("is_leetcode_solved_today")
        val EARNED_FROM_TASKS_MINUTES = intPreferencesKey("earned_from_tasks_minutes")
        val USED_SECONDS_TODAY = intPreferencesKey("used_seconds_today")
        val LAST_ACTIVE_DATE = stringPreferencesKey("last_active_date")
        val IS_SERVICE_ENABLED = booleanPreferencesKey("is_service_enabled")
        val BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
    }

    val userPreferencesFlow: Flow<AppUserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val todayStr = getTodayDateString()
            val savedDate = preferences[PreferencesKeys.LAST_ACTIVE_DATE] ?: todayStr
            val isNewDay = savedDate != todayStr

            AppUserPreferences(
                githubUsername = preferences[PreferencesKeys.GITHUB_USERNAME] ?: "",
                leetcodeUsername = preferences[PreferencesKeys.LEETCODE_USERNAME] ?: "",
                baseFreeMinutes = preferences[PreferencesKeys.BASE_FREE_MINUTES] ?: 15,
                githubRewardMinutes = preferences[PreferencesKeys.GITHUB_REWARD_MINUTES] ?: 25,
                leetcodeRewardMinutes = preferences[PreferencesKeys.LEETCODE_REWARD_MINUTES] ?: 35,
                isGithubPushedToday = if (isNewDay) false else (preferences[PreferencesKeys.IS_GITHUB_PUSHED_TODAY] ?: false),
                isLeetcodeSolvedToday = if (isNewDay) false else (preferences[PreferencesKeys.IS_LEETCODE_SOLVED_TODAY] ?: false),
                earnedFromTasksMinutes = if (isNewDay) 0 else (preferences[PreferencesKeys.EARNED_FROM_TASKS_MINUTES] ?: 0),
                usedSecondsToday = if (isNewDay) 0 else (preferences[PreferencesKeys.USED_SECONDS_TODAY] ?: 0),
                lastActiveDate = todayStr,
                isServiceEnabled = preferences[PreferencesKeys.IS_SERVICE_ENABLED] ?: true,
                blockedPackages = preferences[PreferencesKeys.BLOCKED_PACKAGES] ?: setOf("com.instagram.android")
            )
        }

    suspend fun updateGithubUsername(username: String) {
        context.dataStore.edit { it[PreferencesKeys.GITHUB_USERNAME] = username.trim() }
    }

    suspend fun updateLeetcodeUsername(username: String) {
        context.dataStore.edit { it[PreferencesKeys.LEETCODE_USERNAME] = username.trim() }
    }

    suspend fun updateBaseFreeMinutes(minutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.BASE_FREE_MINUTES] = maxOf(0, minutes) }
    }

    suspend fun updateGithubRewardMinutes(minutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.GITHUB_REWARD_MINUTES] = maxOf(0, minutes) }
    }

    suspend fun updateLeetcodeRewardMinutes(minutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.LEETCODE_REWARD_MINUTES] = maxOf(0, minutes) }
    }

    suspend fun updateGithubVerified(verified: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.IS_GITHUB_PUSHED_TODAY] = verified
            it[PreferencesKeys.LAST_ACTIVE_DATE] = getTodayDateString()
        }
    }

    suspend fun updateLeetcodeVerified(verified: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.IS_LEETCODE_SOLVED_TODAY] = verified
            it[PreferencesKeys.LAST_ACTIVE_DATE] = getTodayDateString()
        }
    }

    suspend fun addEarnedTaskMinutes(minutes: Int) {
        context.dataStore.edit {
            val current = it[PreferencesKeys.EARNED_FROM_TASKS_MINUTES] ?: 0
            it[PreferencesKeys.EARNED_FROM_TASKS_MINUTES] = current + minutes
            it[PreferencesKeys.LAST_ACTIVE_DATE] = getTodayDateString()
        }
    }

    suspend fun addUsedSeconds(seconds: Int) {
        if (seconds <= 0) return
        context.dataStore.edit {
            val current = it[PreferencesKeys.USED_SECONDS_TODAY] ?: 0
            it[PreferencesKeys.USED_SECONDS_TODAY] = current + seconds
            it[PreferencesKeys.LAST_ACTIVE_DATE] = getTodayDateString()
        }
    }

    suspend fun resetDay() {
        context.dataStore.edit {
            it[PreferencesKeys.IS_GITHUB_PUSHED_TODAY] = false
            it[PreferencesKeys.IS_LEETCODE_SOLVED_TODAY] = false
            it[PreferencesKeys.EARNED_FROM_TASKS_MINUTES] = 0
            it[PreferencesKeys.USED_SECONDS_TODAY] = 0
            it[PreferencesKeys.LAST_ACTIVE_DATE] = getTodayDateString()
        }
    }

    companion object {
        fun getTodayDateString(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }
    }
}
