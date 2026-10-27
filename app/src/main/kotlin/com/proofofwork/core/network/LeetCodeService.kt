package com.proofofwork.core.network

import android.util.Log
import com.proofofwork.core.model.ContributionDay
import com.proofofwork.core.model.LeetCodeStatus
import com.proofofwork.core.model.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object LeetCodeService {
    private const val TAG = "LeetCodeService"
    private const val GRAPHQL_URL = "https://leetcode.com/graphql"

    suspend fun verifyTodaySolve(username: String): NetworkResult<LeetCodeStatus> =
        withContext(Dispatchers.IO) {
            if (username.isBlank()) {
                return@withContext NetworkResult.Error("LeetCode username is empty")
            }

            var connection: HttpURLConnection? = null
            try {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val query = """
                    {
                        matchedUser(username: "$username") {
                            submitStatsGlobal {
                                acSubmissionNum {
                                    difficulty
                                    count
                                }
                            }
                            userCalendar(year: $currentYear) {
                                streak
                                totalActiveDays
                                submissionCalendar
                            }
                        }
                        recentAcSubmissionList(username: "$username", limit: 15) {
                            title
                            titleSlug
                            timestamp
                        }
                    }
                """.trimIndent()

                val payload = JSONObject().apply {
                    put("query", query)
                }.toString()

                val url = URL(GRAPHQL_URL)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    doOutput = true
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext NetworkResult.Error("LeetCode returned HTTP: ${connection.responseCode}")
                }

                val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use {
                    it.readText()
                }

                val jsonResponse = JSONObject(responseText)
                val data = jsonResponse.optJSONObject("data")
                val matchedUser = data?.optJSONObject("matchedUser")
                if (matchedUser == null) {
                    return@withContext NetworkResult.Error("LeetCode profile '$username' not found")
                }

                var totalCount = 0
                val submitStats = matchedUser.optJSONObject("submitStatsGlobal")
                val acArray = submitStats?.optJSONArray("acSubmissionNum")
                if (acArray != null) {
                    for (i in 0 until acArray.length()) {
                        val item = acArray.getJSONObject(i)
                        if (item.optString("difficulty") == "All") {
                            totalCount = item.optInt("count", 0)
                            break
                        }
                    }
                }

                val userCalendar = matchedUser.optJSONObject("userCalendar")
                val totalActive = userCalendar?.optInt("totalActiveDays", 0) ?: 0
                val subCalStr = userCalendar?.optString("submissionCalendar", "{}") ?: "{}"

                val recentDaysMap = parseLeetCodeSubmissionCalendar(subCalStr).toMutableMap()

                val todayCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDaySeconds = todayCalendar.timeInMillis / 1000L

                val recentList = data.optJSONArray("recentAcSubmissionList")
                var isSolvedToday = false
                var latestTitle = ""
                var latestSlug = ""

                if (recentList != null) {
                    for (i in 0 until recentList.length()) {
                        val submission = recentList.getJSONObject(i)
                        val timestamp = submission.optLong("timestamp", 0L)
                        if (timestamp >= startOfDaySeconds) {
                            isSolvedToday = true
                            if (latestTitle.isEmpty()) {
                                latestTitle = submission.optString("title", "")
                                latestSlug = submission.optString("titleSlug", "")
                            }
                        }
                    }
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val todayStr = dateFormat.format(todayCalendar.time)
                if (isSolvedToday) {
                    recentDaysMap[todayStr] = ContributionDay(
                        date = todayStr,
                        level = 3,
                        count = maxOf(recentDaysMap[todayStr]?.count ?: 0, 1)
                    )
                }

                // Calculate true active consecutive daily streak backwards from submission calendar
                var streak = 0
                val checkCal = (todayCalendar.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 12)
                }

                val isDoneToday = isSolvedToday || (recentDaysMap[todayStr]?.count ?: 0) > 0
                if (isDoneToday) {
                    streak++
                    checkCal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    // Not solved today yet; check if yesterday was solved to preserve active streak
                    checkCal.add(Calendar.DAY_OF_YEAR, -1)
                }

                while (true) {
                    val dateKey = dateFormat.format(checkCal.time)
                    val count = recentDaysMap[dateKey]?.count ?: 0
                    if (count > 0) {
                        streak++
                        checkCal.add(Calendar.DAY_OF_YEAR, -1)
                    } else {
                        break
                    }
                }

                NetworkResult.Success(
                    LeetCodeStatus(
                        isSolvedToday = isSolvedToday,
                        problemTitle = latestTitle,
                        problemSlug = latestSlug,
                        totalSolved = totalCount,
                        streakDays = streak,
                        totalActiveDays = totalActive,
                        recentDays = recentDaysMap.values.sortedBy { it.date },
                        lastCheckedTimestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query LeetCode GraphQL", e)
                NetworkResult.Error(e.localizedMessage ?: "Could not reach LeetCode")
            } finally {
                connection?.disconnect()
            }
        }

    private fun parseLeetCodeSubmissionCalendar(calendarJson: String): Map<String, ContributionDay> {
        return try {
            val obj = JSONObject(calendarJson)
            val result = mutableMapOf<String, ContributionDay>()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val epochSec = k.toLongOrNull() ?: continue
                val count = obj.optInt(k, 0)
                val dateStr = sdf.format(Date(epochSec * 1000L))
                val level = when {
                    count >= 4 -> 4
                    count >= 2 -> 3
                    count == 1 -> 2
                    else -> 1
                }
                result[dateStr] = ContributionDay(date = dateStr, level = level, count = count)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
