package com.proofofwork.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class AppUserPreferences(
    val githubUsername: String = "",
    val leetcodeUsername: String = "",
    val geminiApiKey: String = "",
    val baseFreeMinutes: Int = 5,
    val githubRewardMinutes: Int = 25,
    val leetcodeRewardMinutes: Int = 35,
    val codingTaskMinutes: Int = 20,
    val studyTaskMinutes: Int = 15,
    val fitnessTaskMinutes: Int = 15,
    val deepWorkTaskMinutes: Int = 15,
    val isGithubPushedToday: Boolean = false,
    val isLeetcodeSolvedToday: Boolean = false,
    val earnedFromTasksMinutes: Int = 0,
    val earnedFromVoiceMinutes: Int = 0,
    val voiceLogsTodayCount: Int = 0,
    val totalWordsDictated: Int = 0,
    val totalVoiceLogsCount: Int = 0,
    val usedSecondsToday: Int = 0,
    val lastActiveDate: String = "",
    val currentWeekKey: String = "",
    val completedWeekdays: Set<Int> = emptySet(), // 1 = Mon, 2 = Tue, 3 = Wed, 4 = Thu, 5 = Fri
    val weekendFreedomEnabled: Boolean = true,
    val demoSimulate5DayStreak: Boolean = false,
    val confessionUsedToday: Boolean = false,
    val isServiceEnabled: Boolean = true,
    val blockedPackages: Set<String> = setOf("com.instagram.android")
) {
    val effectiveCompletedWeekdays: Set<Int>
        get() = if (demoSimulate5DayStreak) setOf(1, 2, 3, 4, 5) else completedWeekdays

    val isAll5WeekdaysCompleted: Boolean
        get() = effectiveCompletedWeekdays.containsAll(listOf(1, 2, 3, 4, 5))

    val isWeekendDayToday: Boolean
        get() {
            val cal = Calendar.getInstance()
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        }

    /**
     * True when the 5-day weekday streak is achieved AND the user has the Weekend Freedom toggle ON,
     * and today is Saturday/Sunday (or Demo simulation is active).
     */
    val isWeekendFreedomActive: Boolean
        get() = weekendFreedomEnabled && isAll5WeekdaysCompleted && (isWeekendDayToday || demoSimulate5DayStreak)

    val totalEarnedMinutes: Int
        get() = (if (isGithubPushedToday) githubRewardMinutes else 0) +
                (if (isLeetcodeSolvedToday) leetcodeRewardMinutes else 0) +
                earnedFromTasksMinutes +
                earnedFromVoiceMinutes

    val totalAllowanceMinutes: Int
        get() = if (isWeekendFreedomActive) 1440 else (baseFreeMinutes + totalEarnedMinutes)

    val totalAllowanceSeconds: Int
        get() = totalAllowanceMinutes * 60

    val remainingSeconds: Int
        get() = if (isWeekendFreedomActive) 86400 else maxOf(0, totalAllowanceSeconds - usedSecondsToday)

    val remainingMinutes: Int
        get() = if (isWeekendFreedomActive) 999 else if (remainingSeconds > 0) (remainingSeconds + 59) / 60 else 0

    val usedMinutesToday: Int
        get() = usedSecondsToday / 60

    val formattedUsedTime: String
        get() {
            val mins = usedSecondsToday / 60
            val secs = usedSecondsToday % 60
            return "${mins}m ${secs}s"
        }

    val formattedRemainingTime: String
        get() {
            if (isWeekendFreedomActive) return "Unlimited"
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            return "${mins}m ${secs}s"
        }

    fun getRewardForCategory(category: String): Int {
        return when (category.uppercase(Locale.US)) {
            "CODING" -> codingTaskMinutes
            "STUDY", "LEARNING" -> studyTaskMinutes
            "FITNESS", "HEALTH" -> fitnessTaskMinutes
            else -> deepWorkTaskMinutes
        }
    }
}

class UserPreferencesRepository(private val context: Context) {

    @Volatile
    var latestPreferences: AppUserPreferences? = null
        private set

    private object PreferencesKeys {
        val GITHUB_USERNAME = stringPreferencesKey("github_username")
        val LEETCODE_USERNAME = stringPreferencesKey("leetcode_username")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val BASE_FREE_MINUTES = intPreferencesKey("base_free_minutes")
        val GITHUB_REWARD_MINUTES = intPreferencesKey("github_reward_minutes")
        val LEETCODE_REWARD_MINUTES = intPreferencesKey("leetcode_reward_minutes")
        val CODING_TASK_MINUTES = intPreferencesKey("coding_task_minutes")
        val STUDY_TASK_MINUTES = intPreferencesKey("study_task_minutes")
        val FITNESS_TASK_MINUTES = intPreferencesKey("fitness_task_minutes")
        val DEEP_WORK_TASK_MINUTES = intPreferencesKey("deep_work_task_minutes")
        val IS_GITHUB_PUSHED_TODAY = booleanPreferencesKey("is_github_pushed_today")
        val IS_LEETCODE_SOLVED_TODAY = booleanPreferencesKey("is_leetcode_solved_today")
        val EARNED_FROM_TASKS_MINUTES = intPreferencesKey("earned_from_tasks_minutes")
        val EARNED_FROM_VOICE_MINUTES = intPreferencesKey("earned_from_voice_minutes")
        val VOICE_LOGS_TODAY_COUNT = intPreferencesKey("voice_logs_today_count")
        val TOTAL_WORDS_DICTATED = intPreferencesKey("total_words_dictated")
        val TOTAL_VOICE_LOGS_COUNT = intPreferencesKey("total_voice_logs_count")
        val USED_SECONDS_TODAY = intPreferencesKey("used_seconds_today")
        val LAST_ACTIVE_DATE = stringPreferencesKey("last_active_date")
        val CURRENT_WEEK_KEY = stringPreferencesKey("current_week_key")
        val COMPLETED_WEEKDAYS = stringSetPreferencesKey("completed_weekdays")
        val WEEKEND_FREEDOM_ENABLED = booleanPreferencesKey("weekend_freedom_enabled")
        val DEMO_SIMULATE_5DAY_STREAK = booleanPreferencesKey("demo_simulate_5day_streak")
        val CONFESSION_USED_TODAY = booleanPreferencesKey("confession_used_today")
        val IS_SERVICE_ENABLED = booleanPreferencesKey("is_service_enabled")
        val BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
        val MORNING_RESET_FIX_V2 = booleanPreferencesKey("morning_reset_fix_v2_2026_10_28")
    }

    /**
     * Atomically checks if the calendar day (or week) has rolled over inside a DataStore transaction
     * and zeroes out all daily counters BEFORE any new write stamps today's LAST_ACTIVE_DATE.
     */
    private fun ensureCurrentDayAndWeek(prefs: MutablePreferences): Boolean {
        val todayStr = getTodayDateString()
        val savedDate = prefs[PreferencesKeys.LAST_ACTIVE_DATE]
        val migrationApplied = prefs[PreferencesKeys.MORNING_RESET_FIX_V2] ?: false

        var didResetDay = false
        if ((savedDate != null && savedDate != todayStr) || !migrationApplied) {
            prefs[PreferencesKeys.IS_GITHUB_PUSHED_TODAY] = false
            prefs[PreferencesKeys.IS_LEETCODE_SOLVED_TODAY] = false
            prefs[PreferencesKeys.EARNED_FROM_TASKS_MINUTES] = 0
            prefs[PreferencesKeys.EARNED_FROM_VOICE_MINUTES] = 0
            prefs[PreferencesKeys.VOICE_LOGS_TODAY_COUNT] = 0
            prefs[PreferencesKeys.USED_SECONDS_TODAY] = 0
            prefs[PreferencesKeys.CONFESSION_USED_TODAY] = false
            prefs[PreferencesKeys.MORNING_RESET_FIX_V2] = true
            didResetDay = true
        }
        prefs[PreferencesKeys.LAST_ACTIVE_DATE] = todayStr

        val currentWeek = getCurrentWeekKey()
        val savedWeek = prefs[PreferencesKeys.CURRENT_WEEK_KEY]
        if (savedWeek != null && savedWeek != currentWeek) {
            prefs[PreferencesKeys.COMPLETED_WEEKDAYS] = emptySet()
        }
        prefs[PreferencesKeys.CURRENT_WEEK_KEY] = currentWeek
        return didResetDay
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
            val migrationApplied = preferences[PreferencesKeys.MORNING_RESET_FIX_V2] ?: false
            val isNewDay = (savedDate != todayStr) || !migrationApplied

            val thisWeekKey = getCurrentWeekKey()
            val savedWeekKey = preferences[PreferencesKeys.CURRENT_WEEK_KEY] ?: thisWeekKey
            val isNewWeek = savedWeekKey != thisWeekKey

            val rawWeekdays = if (isNewWeek) emptySet() else (preferences[PreferencesKeys.COMPLETED_WEEKDAYS] ?: emptySet())
            val parsedWeekdays = rawWeekdays.mapNotNull { it.toIntOrNull() }.filter { it in 1..5 }.toSet()

            AppUserPreferences(
                githubUsername = preferences[PreferencesKeys.GITHUB_USERNAME] ?: "",
                leetcodeUsername = preferences[PreferencesKeys.LEETCODE_USERNAME] ?: "",
                geminiApiKey = preferences[PreferencesKeys.GEMINI_API_KEY] ?: "",
                baseFreeMinutes = preferences[PreferencesKeys.BASE_FREE_MINUTES] ?: 5,
                githubRewardMinutes = preferences[PreferencesKeys.GITHUB_REWARD_MINUTES] ?: 25,
                leetcodeRewardMinutes = preferences[PreferencesKeys.LEETCODE_REWARD_MINUTES] ?: 35,
                codingTaskMinutes = preferences[PreferencesKeys.CODING_TASK_MINUTES] ?: 20,
                studyTaskMinutes = preferences[PreferencesKeys.STUDY_TASK_MINUTES] ?: 15,
                fitnessTaskMinutes = preferences[PreferencesKeys.FITNESS_TASK_MINUTES] ?: 15,
                deepWorkTaskMinutes = preferences[PreferencesKeys.DEEP_WORK_TASK_MINUTES] ?: 15,
                isGithubPushedToday = if (isNewDay) false else (preferences[PreferencesKeys.IS_GITHUB_PUSHED_TODAY] ?: false),
                isLeetcodeSolvedToday = if (isNewDay) false else (preferences[PreferencesKeys.IS_LEETCODE_SOLVED_TODAY] ?: false),
                earnedFromTasksMinutes = if (isNewDay) 0 else (preferences[PreferencesKeys.EARNED_FROM_TASKS_MINUTES] ?: 0),
                earnedFromVoiceMinutes = if (isNewDay) 0 else (preferences[PreferencesKeys.EARNED_FROM_VOICE_MINUTES] ?: 0),
                voiceLogsTodayCount = if (isNewDay) 0 else (preferences[PreferencesKeys.VOICE_LOGS_TODAY_COUNT] ?: 0),
                totalWordsDictated = preferences[PreferencesKeys.TOTAL_WORDS_DICTATED] ?: 0,
                totalVoiceLogsCount = preferences[PreferencesKeys.TOTAL_VOICE_LOGS_COUNT] ?: 0,
                usedSecondsToday = if (isNewDay) 0 else (preferences[PreferencesKeys.USED_SECONDS_TODAY] ?: 0),
                lastActiveDate = todayStr,
                currentWeekKey = thisWeekKey,
                completedWeekdays = parsedWeekdays,
                weekendFreedomEnabled = preferences[PreferencesKeys.WEEKEND_FREEDOM_ENABLED] ?: true,
                demoSimulate5DayStreak = preferences[PreferencesKeys.DEMO_SIMULATE_5DAY_STREAK] ?: false,
                confessionUsedToday = if (isNewDay) false else (preferences[PreferencesKeys.CONFESSION_USED_TODAY] ?: false),
                isServiceEnabled = preferences[PreferencesKeys.IS_SERVICE_ENABLED] ?: true,
                blockedPackages = preferences[PreferencesKeys.BLOCKED_PACKAGES] ?: setOf("com.instagram.android")
            ).also { mapped ->
                latestPreferences = mapped
            }
        }

    /**
     * Persists the daily rollover reset to disk if the calendar day has changed (or migration v2 is pending).
     * Returns true if a daily reset occurred so callers can also reset daily tasks in Room.
     */
    suspend fun syncDailyResetIfNeeded(): Boolean = withContext(NonCancellable) {
        var didReset = false
        context.dataStore.edit { prefs ->
            didReset = ensureCurrentDayAndWeek(prefs)
        }
        didReset
    }

    suspend fun updateGithubUsername(username: String) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            it[PreferencesKeys.GITHUB_USERNAME] = username.trim()
        }
    }

    suspend fun updateLeetcodeUsername(username: String) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            it[PreferencesKeys.LEETCODE_USERNAME] = username.trim()
        }
    }

    suspend fun updateGeminiApiKey(apiKey: String) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            it[PreferencesKeys.GEMINI_API_KEY] = apiKey.trim()
        }
    }

    suspend fun updateBaseFreeMinutes(minutes: Int) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            it[PreferencesKeys.BASE_FREE_MINUTES] = maxOf(0, minutes)
        }
    }

    suspend fun updateGithubRewardMinutes(minutes: Int) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            it[PreferencesKeys.GITHUB_REWARD_MINUTES] = maxOf(0, minutes)
        }
    }

    suspend fun updateLeetcodeRewardMinutes(minutes: Int) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            it[PreferencesKeys.LEETCODE_REWARD_MINUTES] = maxOf(0, minutes)
        }
    }

    suspend fun updateTaskCategoryRewards(coding: Int, study: Int, fitness: Int, deepWork: Int) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            it[PreferencesKeys.CODING_TASK_MINUTES] = maxOf(5, coding)
            it[PreferencesKeys.STUDY_TASK_MINUTES] = maxOf(5, study)
            it[PreferencesKeys.FITNESS_TASK_MINUTES] = maxOf(5, fitness)
            it[PreferencesKeys.DEEP_WORK_TASK_MINUTES] = maxOf(5, deepWork)
        }
    }

    suspend fun updateWeekendFreedomEnabled(enabled: Boolean) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            it[PreferencesKeys.WEEKEND_FREEDOM_ENABLED] = enabled
        }
    }

    suspend fun updateDemoSimulate5DayStreak(simulate: Boolean) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            it[PreferencesKeys.DEMO_SIMULATE_5DAY_STREAK] = simulate
        }
    }

    suspend fun toggleWeekdayCompleted(dayIndex: Int) {
        if (dayIndex !in 1..5) return
        context.dataStore.edit { prefs ->
            ensureCurrentDayAndWeek(prefs)
            val currentWeek = getCurrentWeekKey()
            val savedWeek = prefs[PreferencesKeys.CURRENT_WEEK_KEY] ?: currentWeek
            val currentSet = if (savedWeek == currentWeek) {
                (prefs[PreferencesKeys.COMPLETED_WEEKDAYS] ?: emptySet()).toMutableSet()
            } else {
                mutableSetOf()
            }
            val key = dayIndex.toString()
            if (currentSet.contains(key)) {
                currentSet.remove(key)
            } else {
                currentSet.add(key)
            }
            prefs[PreferencesKeys.CURRENT_WEEK_KEY] = currentWeek
            prefs[PreferencesKeys.COMPLETED_WEEKDAYS] = currentSet
        }
    }

    private fun markTodayWeekdayComplete(prefs: MutablePreferences) {
        val currentWeek = getCurrentWeekKey()
        val savedWeek = prefs[PreferencesKeys.CURRENT_WEEK_KEY] ?: currentWeek
        val set = if (savedWeek == currentWeek) {
            (prefs[PreferencesKeys.COMPLETED_WEEKDAYS] ?: emptySet()).toMutableSet()
        } else {
            mutableSetOf()
        }
        val todayIndex = getTodayWeekdayIndex()
        if (todayIndex in 1..5) {
            set.add(todayIndex.toString())
        }
        prefs[PreferencesKeys.CURRENT_WEEK_KEY] = currentWeek
        prefs[PreferencesKeys.COMPLETED_WEEKDAYS] = set
    }

    suspend fun updateGithubVerified(verified: Boolean) = withContext(NonCancellable) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            it[PreferencesKeys.IS_GITHUB_PUSHED_TODAY] = verified
            if (verified) markTodayWeekdayComplete(it)
        }
    }

    suspend fun updateLeetcodeVerified(verified: Boolean) = withContext(NonCancellable) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            it[PreferencesKeys.IS_LEETCODE_SOLVED_TODAY] = verified
            if (verified) markTodayWeekdayComplete(it)
        }
    }

    suspend fun addEarnedTaskMinutes(minutes: Int) = withContext(NonCancellable) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            val current = it[PreferencesKeys.EARNED_FROM_TASKS_MINUTES] ?: 0
            it[PreferencesKeys.EARNED_FROM_TASKS_MINUTES] = maxOf(0, current + minutes)
            if (minutes > 0) markTodayWeekdayComplete(it)
        }
    }

    suspend fun addEarnedVoiceMinutes(minutes: Int, wordsSpoken: Int, isConfession: Boolean = false) = withContext(NonCancellable) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            val currentVoiceMins = it[PreferencesKeys.EARNED_FROM_VOICE_MINUTES] ?: 0
            it[PreferencesKeys.EARNED_FROM_VOICE_MINUTES] = maxOf(0, currentVoiceMins + minutes)

            val todayLogs = it[PreferencesKeys.VOICE_LOGS_TODAY_COUNT] ?: 0
            it[PreferencesKeys.VOICE_LOGS_TODAY_COUNT] = todayLogs + 1

            val totalLogs = it[PreferencesKeys.TOTAL_VOICE_LOGS_COUNT] ?: 0
            it[PreferencesKeys.TOTAL_VOICE_LOGS_COUNT] = totalLogs + 1

            val totalWords = it[PreferencesKeys.TOTAL_WORDS_DICTATED] ?: 0
            it[PreferencesKeys.TOTAL_WORDS_DICTATED] = totalWords + maxOf(0, wordsSpoken)

            if (isConfession) {
                it[PreferencesKeys.CONFESSION_USED_TODAY] = true
            } else if (minutes > 0) {
                markTodayWeekdayComplete(it)
            }
        }
    }

    suspend fun deductEarnedVoiceMinutes(minutes: Int, wordsSpoken: Int) = withContext(NonCancellable) {
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            val currentVoiceMins = it[PreferencesKeys.EARNED_FROM_VOICE_MINUTES] ?: 0
            it[PreferencesKeys.EARNED_FROM_VOICE_MINUTES] = maxOf(0, currentVoiceMins - minutes)

            val todayLogs = it[PreferencesKeys.VOICE_LOGS_TODAY_COUNT] ?: 0
            it[PreferencesKeys.VOICE_LOGS_TODAY_COUNT] = maxOf(0, todayLogs - 1)

            val totalLogs = it[PreferencesKeys.TOTAL_VOICE_LOGS_COUNT] ?: 0
            it[PreferencesKeys.TOTAL_VOICE_LOGS_COUNT] = maxOf(0, totalLogs - 1)

            val totalWords = it[PreferencesKeys.TOTAL_WORDS_DICTATED] ?: 0
            it[PreferencesKeys.TOTAL_WORDS_DICTATED] = maxOf(0, totalWords - maxOf(0, wordsSpoken))
        }
    }

    suspend fun addUsedSeconds(seconds: Int) = withContext(NonCancellable) {
        if (seconds <= 0) return@withContext
        // Immediately update in-memory snapshot for 0ms UI responsiveness
        latestPreferences?.let { current ->
            val todayStr = getTodayDateString()
            val baseUsed = if (current.lastActiveDate != todayStr) 0 else current.usedSecondsToday
            latestPreferences = current.copy(
                usedSecondsToday = baseUsed + seconds,
                lastActiveDate = todayStr
            )
        }
        context.dataStore.edit {
            ensureCurrentDayAndWeek(it)
            val current = it[PreferencesKeys.USED_SECONDS_TODAY] ?: 0
            it[PreferencesKeys.USED_SECONDS_TODAY] = current + seconds
        }
    }

    suspend fun resetDay() = withContext(NonCancellable) {
        context.dataStore.edit {
            it[PreferencesKeys.IS_GITHUB_PUSHED_TODAY] = false
            it[PreferencesKeys.IS_LEETCODE_SOLVED_TODAY] = false
            it[PreferencesKeys.EARNED_FROM_TASKS_MINUTES] = 0
            it[PreferencesKeys.EARNED_FROM_VOICE_MINUTES] = 0
            it[PreferencesKeys.VOICE_LOGS_TODAY_COUNT] = 0
            it[PreferencesKeys.USED_SECONDS_TODAY] = 0
            it[PreferencesKeys.CONFESSION_USED_TODAY] = false
            it[PreferencesKeys.MORNING_RESET_FIX_V2] = true
            it[PreferencesKeys.LAST_ACTIVE_DATE] = getTodayDateString()
        }
    }

    companion object {
        fun getTodayDateString(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }

        fun getCurrentWeekKey(): String {
            val cal = Calendar.getInstance(Locale.US)
            cal.firstDayOfWeek = Calendar.MONDAY
            val year = cal.get(Calendar.YEAR)
            val week = cal.get(Calendar.WEEK_OF_YEAR)
            return "$year-W$week"
        }

        /**
         * Returns 1 for Monday .. 5 for Friday, 6 for Saturday, 7 for Sunday
         */
        fun getTodayWeekdayIndex(): Int {
            val cal = Calendar.getInstance()
            return when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }
        }
    }
}
