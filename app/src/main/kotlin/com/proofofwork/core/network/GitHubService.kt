package com.proofofwork.core.network

import android.util.Log
import com.proofofwork.core.model.ContributionDay
import com.proofofwork.core.model.GitHubStatus
import com.proofofwork.core.model.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

object GitHubService {
    private const val TAG = "GitHubService"
    private const val BASE_URL = "https://api.github.com"

    suspend fun verifyTodayPush(username: String): NetworkResult<GitHubStatus> =
        withContext(Dispatchers.IO) {
            if (username.isBlank()) {
                return@withContext NetworkResult.Error("GitHub username is empty")
            }

            try {
                // 1. Fetch official contribution calendar data & streak
                val calendarData = fetchContributionCalendarData(username)
                val todayCount = calendarData.first
                val streakDays = calendarData.second
                val allDays = calendarData.third

                // 2. Fetch latest pushed repo info
                val latestRepoInfo = fetchLatestRepoInfo(username)

                val isPushed = todayCount > 0 || (latestRepoInfo.third >= getStartOfDayMs())

                NetworkResult.Success(
                    GitHubStatus(
                        isPushedToday = isPushed,
                        commitCount = maxOf(todayCount, if (isPushed) 1 else 0),
                        latestRepo = latestRepoInfo.first,
                        latestCommitMessage = latestRepoInfo.second,
                        streakDays = streakDays,
                        recentContributions = allDays,
                        lastCheckedTimestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "GitHub verification failed", e)
                NetworkResult.Error(e.localizedMessage ?: "Failed to reach GitHub")
            }
        }

    private fun fetchContributionCalendarData(username: String): Triple<Int, Int, List<ContributionDay>> {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://github.com/users/$username/contributions?_t=${System.currentTimeMillis()}")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                connectTimeout = 8_000
                readTimeout = 8_000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val html = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }

                // 1. Parse today's exact count from tooltip
                val now = Calendar.getInstance()
                val monthName = SimpleDateFormat("MMMM", Locale.US).format(now.time)
                val day = now.get(Calendar.DAY_OF_MONTH)

                val countPattern = Pattern.compile("(\\d+)\\s+contributions?\\s+on\\s+$monthName\\s+$day(st|nd|rd|th)", Pattern.CASE_INSENSITIVE)
                val countMatcher = countPattern.matcher(html)
                var todayCount = 0
                while (countMatcher.find()) {
                    val count = countMatcher.group(1)?.toIntOrNull() ?: 0
                    if (count > todayCount) todayCount = count
                }

                // 2. Parse all days into a map: date -> level
                // Regex matches: data-date="YYYY-MM-DD" ... data-level="X"
                val cellPattern = Pattern.compile("data-date=\"([0-9\\-]+)\"[^>]*data-level=\"([0-9])\"")
                val cellMatcher = cellPattern.matcher(html)

                val dayMap = mutableMapOf<String, Int>()
                while (cellMatcher.find()) {
                    val date = cellMatcher.group(1) ?: continue
                    val level = cellMatcher.group(2)?.toIntOrNull() ?: 0
                    dayMap[date] = level
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val todayStr = dateFormat.format(now.time)
                if (todayCount > 0) {
                    dayMap[todayStr] = maxOf(dayMap[todayStr] ?: 0, 2)
                }

                // 3. Calculate true consecutive daily streak backwards
                var streak = 0
                val checkCal = Calendar.getInstance()
                val todayLevel = dayMap[todayStr] ?: 0

                if (todayLevel > 0) {
                    streak++
                    checkCal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    // If not contributed yet today, check yesterday to preserve ongoing streak
                    checkCal.add(Calendar.DAY_OF_YEAR, -1)
                }

                while (true) {
                    val dateKey = dateFormat.format(checkCal.time)
                    val level = dayMap[dateKey] ?: 0
                    if (level > 0) {
                        streak++
                        checkCal.add(Calendar.DAY_OF_YEAR, -1)
                    } else {
                        break
                    }
                }

                val allDays = dayMap.map { (date, level) ->
                    ContributionDay(date = date, level = level)
                }.sortedBy { it.date }

                return Triple(todayCount, streak, allDays)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching contribution calendar data", e)
        } finally {
            connection?.disconnect()
        }
        return Triple(0, 0, emptyList())
    }

    private fun getStartOfDayMs(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun fetchLatestRepoInfo(username: String): Triple<String, String, Long> {
        val urlString = "$BASE_URL/users/$username/repos?sort=pushed&direction=desc&per_page=3&_t=${System.currentTimeMillis()}"
        val repos = getJsonArray(urlString) ?: return Triple("", "", 0L)
        if (repos.length() == 0) return Triple("", "", 0L)

        val first = repos.optJSONObject(0) ?: return Triple("", "", 0L)
        val name = first.optString("name", "")
        val pushedAtStr = first.optString("pushed_at", "")

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val pushedAtMs = try {
            isoFormat.parse(pushedAtStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }

        val commitMsg = fetchLatestCommitMessage(username, name)
        return Triple(name, commitMsg, pushedAtMs)
    }

    private fun fetchLatestCommitMessage(username: String, repo: String): String {
        val commitsUrl = "$BASE_URL/repos/$username/$repo/commits?per_page=1"
        val commits = getJsonArray(commitsUrl) ?: return ""
        if (commits.length() > 0) {
            val first = commits.optJSONObject(0)
            val commitObj = first?.optJSONObject("commit")
            return commitObj?.optString("message", "")?.lines()?.firstOrNull() ?: ""
        }
        return ""
    }

    private fun getJsonArray(urlString: String): JSONArray? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "ProofOfWork-Android")
                connectTimeout = 8_000
                readTimeout = 8_000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val text = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                JSONArray(text)
            } else null
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
