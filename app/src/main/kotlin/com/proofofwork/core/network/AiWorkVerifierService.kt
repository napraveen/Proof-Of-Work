package com.proofofwork.core.network

import com.proofofwork.core.data.AppUserPreferences
import com.proofofwork.core.data.database.TaskEntity
import com.proofofwork.core.data.database.VoiceLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class VoiceVerificationResult(
    val isVerified: Boolean,
    val summary: String,
    val category: String,
    val effortMinutes: Int,
    val rewardMinutes: Int,
    val confidenceScore: Int,
    val aiFeedback: String,
    val cleanedTranscript: String,
    val wordCount: Int,
    val matchedTaskId: Long? = null,
    val engineUsed: String = "AI Proof Verifier"
)

object AiWorkVerifierService {

    /**
     * Cleans spoken transcript: removes filler words, fixes capitalization and punctuation.
     */
    fun cleanSpokenTranscript(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        val cleaned = trimmed
            .replace(Regex("\\b(um+|uh+|erm+|hmm+|like,\\s*|you know,\\s*|basically,\\s*|i mean,\\s*)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isEmpty()) return trimmed
        val capitalized = cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        return if (capitalized.endsWith(".") || capitalized.endsWith("!") || capitalized.endsWith("?")) {
            capitalized
        } else {
            "$capitalized."
        }
    }

    fun cleanWisprTranscript(raw: String): String = cleanSpokenTranscript(raw)

    fun countWords(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split(Regex("\\s+")).size
    }

    /**
     * Evaluates a user's spoken Proof of Work using Gemini LLM (if API key provided)
     * with automatic fallback to the built-in Semantic AI Analyzer.
     */
    suspend fun evaluateSpokenWork(
        rawTranscript: String,
        prefs: AppUserPreferences,
        pendingTasks: List<TaskEntity> = emptyList()
    ): VoiceVerificationResult = withContext(Dispatchers.IO) {
        val cleaned = cleanSpokenTranscript(rawTranscript)
        val words = countWords(cleaned)

        if (words < 3) {
            return@withContext VoiceVerificationResult(
                isVerified = false,
                summary = "Statement Too Short",
                category = "NONE",
                effortMinutes = 0,
                rewardMinutes = 0,
                confidenceScore = 15,
                aiFeedback = "Speak a full sentence describing what you accomplished! For example: 'I spent 40 minutes refactoring the login screen and fixed two bugs.'",
                cleanedTranscript = cleaned,
                wordCount = words
            )
        }

        // Check if it matches any pending daily priority task
        val matchedTask = findMatchingPendingTask(cleaned, pendingTasks)

        // If user has configured a Gemini API key, call Gemini 2.0 Flash first
        if (prefs.geminiApiKey.isNotBlank()) {
            try {
                val geminiResult = callGeminiVerifier(cleaned, words, prefs, matchedTask)
                if (geminiResult != null) {
                    return@withContext geminiResult
                }
            } catch (_: Exception) {
                // Fallback seamlessly to local semantic engine
            }
        }

        return@withContext evaluateWithSemanticEngine(cleaned, words, prefs, matchedTask)
    }

    /**
     * Evaluates a "Guilty Confession" spoken by the user to unlock a 5-minute emergency break.
     */
    suspend fun evaluateConfession(rawTranscript: String): VoiceVerificationResult = withContext(Dispatchers.IO) {
        val cleaned = cleanSpokenTranscript(rawTranscript)
        val words = countWords(cleaned)

        if (words < 6) {
            return@withContext VoiceVerificationResult(
                isVerified = false,
                summary = "Confession Too Brief",
                category = "CONFESSION",
                effortMinutes = 0,
                rewardMinutes = 0,
                confidenceScore = 20,
                aiFeedback = "Be honest with yourself! Speak at least 6 words acknowledging why you want a quick break and promising to return to focus.",
                cleanedTranscript = cleaned,
                wordCount = words
            )
        }

        VoiceVerificationResult(
            isVerified = true,
            summary = "Accountability Confession Logged",
            category = "CONFESSION",
            effortMinutes = 0,
            rewardMinutes = 5,
            confidenceScore = 100,
            aiFeedback = "Honesty accepted! You've unlocked a 5-minute grace break. Your confession is logged—make sure to crush a real task right after!",
            cleanedTranscript = cleaned,
            wordCount = words,
            engineUsed = "Accountability Guard"
        )
    }

    private fun callGeminiVerifier(
        cleanedTranscript: String,
        wordCount: Int,
        prefs: AppUserPreferences,
        matchedTask: TaskEntity?
    ): VoiceVerificationResult? {
        val apiKey = prefs.geminiApiKey.trim()
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

        val systemPrompt = """
            You are the AI Proof-of-Work Judge inside an anti-doomscrolling productivity app powered by voice dictation.
            The user speaks what task they just completed to earn Instagram screen-time minutes.
            Reward rates configured by user:
            - CODING: ${prefs.codingTaskMinutes} mins base reward
            - STUDY: ${prefs.studyTaskMinutes} mins base reward
            - FITNESS: ${prefs.fitnessTaskMinutes} mins base reward
            - DEEP_WORK: ${prefs.deepWorkTaskMinutes} mins base reward
            
            Analyze the user's spoken statement: "$cleanedTranscript"
            
            Rules:
            1. If the user is joking, saying they did nothing, watching reels, sleeping, or speaking gibberish, set "isVerified": false and "rewardMinutes": 0.
            2. If the user describes a genuine productive activity (coding, studying, reading, exercising, writing, chores, planning, debugging, meetings), set "isVerified": true.
            3. Scale "rewardMinutes" based on the category rate and estimated effort (between 5 and 90 minutes).
            4. Return ONLY valid JSON with these exact keys:
            {
              "isVerified": true,
              "summary": "Short 4-7 word title of work done",
              "category": "CODING" | "STUDY" | "FITNESS" | "DEEP_WORK",
              "effortMinutes": 30,
              "rewardMinutes": 20,
              "confidenceScore": 95,
              "aiFeedback": "1-2 sentence motivating response referencing their specific task"
            }
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", systemPrompt)
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }

        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        if (conn.responseCode !in 200..299) {
            return null
        }

        val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        val root = JSONObject(responseText)
        val textContent = root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val resultJson = JSONObject(textContent)
        val isVerified = resultJson.optBoolean("isVerified", true)
        val category = resultJson.optString("category", "DEEP_WORK").uppercase(Locale.US)
        val effortMins = resultJson.optInt("effortMinutes", 30).coerceIn(5, 300)
        val rawReward = resultJson.optInt("rewardMinutes", prefs.getRewardForCategory(category))
        val rewardMins = if (isVerified) rawReward.coerceIn(5, 120) else 0

        return VoiceVerificationResult(
            isVerified = isVerified,
            summary = resultJson.optString("summary", "Verified Work Session"),
            category = category,
            effortMinutes = effortMins,
            rewardMinutes = matchedTask?.rewardMinutes ?: rewardMins,
            confidenceScore = resultJson.optInt("confidenceScore", 92).coerceIn(0, 100),
            aiFeedback = resultJson.optString("aiFeedback", "Great job completing your task! Screen time credited."),
            cleanedTranscript = cleanedTranscript,
            wordCount = wordCount,
            matchedTaskId = matchedTask?.id,
            engineUsed = "Gemini 2.0 Flash AI"
        )
    }

    private fun evaluateWithSemanticEngine(
        cleaned: String,
        words: Int,
        prefs: AppUserPreferences,
        matchedTask: TaskEntity?
    ): VoiceVerificationResult {
        val lower = cleaned.lowercase(Locale.US)

        // 1. Check for negative / non-work / anti-cheat statements
        val lazyPatterns = listOf(
            "did nothing", "didn't do anything", "watched netflix", "watched youtube",
            "scrolled instagram", "just testing", "test 123", "hello world", "blah blah",
            "give me free time", "unlock instagram please", "i was sleeping", "took a nap"
        )
        if (lazyPatterns.any { lower.contains(it) }) {
            return VoiceVerificationResult(
                isVerified = false,
                summary = "Unverified / Non-Productive Claim",
                category = "NONE",
                effortMinutes = 0,
                rewardMinutes = 0,
                confidenceScore = 12,
                aiFeedback = "Nice try! Our AI detected no productive work in that statement. Finish a real task and record what you accomplished!",
                cleanedTranscript = cleaned,
                wordCount = words,
                engineUsed = "Semantic AI Judge"
            )
        }

        // 2. Categorize based on rich domain vocabulary
        val codingKeywords = listOf(
            "code", "coded", "coding", "bug", "fixed", "commit", "pushed", "github", "pr", "pull request",
            "api", "backend", "frontend", "kotlin", "java", "python", "react", "compose", "android", "database",
            "sql", "refactor", "refactored", "deployed", "leetcode", "algorithm", "function", "test", "tests",
            "built", "implemented", "feature", "app", "ui", "server", "docker", "script", "debug", "debugged"
        )
        val studyKeywords = listOf(
            "study", "studied", "studying", "read", "reading", "book", "chapter", "lecture", "course",
            "notes", "exam", "assignment", "homework", "paper", "research", "learned", "learning",
            "tutorial", "math", "physics", "system design", "os", "operating system", "dbms", "networking",
            "flashcards", "revised", "revision", "class", "module"
        )
        val fitnessKeywords = listOf(
            "gym", "workout", "worked out", "run", "ran", "running", "jog", "jogged", "walk", "walked",
            "pushup", "pushups", "squats", "yoga", "meditated", "meditation", "cardio", "hiit", "exercise",
            "exercised", "plank", "stretch", "cycling", "swim", "steps", "training"
        )
        val productiveVerbs = listOf(
            "finished", "completed", "done", "worked", "wrote", "created", "designed", "organized",
            "cleaned", "solved", "reviewed", "prepared", "drafted", "recorded", "edited", "planned",
            "practiced", "built", "shipped", "submitted", "documented", "spent"
        )

        val codingHits = codingKeywords.count { lower.contains(it) }
        val studyHits = studyKeywords.count { lower.contains(it) }
        val fitnessHits = fitnessKeywords.count { lower.contains(it) }
        val verbHits = productiveVerbs.count { lower.contains(it) }

        val category = when {
            matchedTask != null -> matchedTask.category
            codingHits >= studyHits && codingHits >= fitnessHits && codingHits > 0 -> "CODING"
            studyHits >= codingHits && studyHits >= fitnessHits && studyHits > 0 -> "STUDY"
            fitnessHits > 0 -> "FITNESS"
            else -> "DEEP_WORK"
        }

        val hasProductiveSignal = codingHits > 0 || studyHits > 0 || fitnessHits > 0 || verbHits > 0 || matchedTask != null || words >= 6

        if (!hasProductiveSignal) {
            return VoiceVerificationResult(
                isVerified = false,
                summary = "Unclear Task Description",
                category = "NONE",
                effortMinutes = 0,
                rewardMinutes = 0,
                confidenceScore = 35,
                aiFeedback = "Could not verify a specific completed task. Mention what you built, studied, or completed and roughly how long it took!",
                cleanedTranscript = cleaned,
                wordCount = words,
                engineUsed = "Semantic AI Judge"
            )
        }

        // 3. Extract duration from spoken text (e.g. "45 minutes", "2 hours", "an hour", "half an hour")
        val effortMinutes = extractDurationMinutes(lower)

        // 4. Calculate reward minutes using user's category mapping + effort multiplier
        val baseCategoryReward = prefs.getRewardForCategory(category)
        val effortMultiplier = when {
            effortMinutes >= 90 -> 1.8f
            effortMinutes >= 60 -> 1.4f
            effortMinutes >= 45 -> 1.2f
            effortMinutes <= 15 -> 0.8f
            else -> 1.0f
        }
        val detailBonus = if (words >= 15) 5 else 0
        val calculatedReward = ((baseCategoryReward * effortMultiplier).toInt() + detailBonus).coerceIn(5, 90)
        val finalReward = matchedTask?.rewardMinutes ?: calculatedReward

        // 5. Generate clean summary & personalized AI Coach feedback
        val summary = matchedTask?.title ?: generateConciseSummary(cleaned, category)
        val confidence = minOf(98, 78 + (words * 1).coerceAtMost(14) + (if (codingHits + studyHits + fitnessHits > 0) 6 else 0))

        val feedback = when (category) {
            "CODING" -> "Solid engineering progress! Verified ~${effortMinutes}m of coding work. Minted +${finalReward}m to your Instagram wallet."
            "STUDY" -> "Knowledge compounded! Verified ~${effortMinutes}m of focused study. Minted +${finalReward}m of guilt-free scroll time."
            "FITNESS" -> "Physical & mental discipline! Verified ~${effortMinutes}m of fitness activity. Earned +${finalReward}m of Instagram time."
            else -> "Productive deep work session verified (~${effortMinutes}m)! Added +${finalReward}m to your screen time balance."
        }

        return VoiceVerificationResult(
            isVerified = true,
            summary = summary,
            category = category,
            effortMinutes = effortMinutes,
            rewardMinutes = finalReward,
            confidenceScore = confidence,
            aiFeedback = feedback,
            cleanedTranscript = cleaned,
            wordCount = words,
            matchedTaskId = matchedTask?.id,
            engineUsed = "Semantic AI Judge"
        )
    }

    private fun extractDurationMinutes(lower: String): Int {
        // Match "X hours" / "X hr"
        val hourRegex = Regex("(\\d+)\\s*(hour|hours|hr|hrs)")
        val minRegex = Regex("(\\d+)\\s*(minute|minutes|min|mins)")

        var total = 0
        hourRegex.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { hrs ->
            total += hrs * 60
        }
        minRegex.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { mins ->
            total += mins
        }

        if (total == 0) {
            when {
                lower.contains("an hour and a half") || lower.contains("1.5 hours") -> total = 90
                lower.contains("two hours") || lower.contains("couple of hours") -> total = 120
                lower.contains("an hour") || lower.contains("one hour") -> total = 60
                lower.contains("half an hour") || lower.contains("half hour") || lower.contains("thirty minutes") -> total = 30
                lower.contains("forty five minutes") -> total = 45
                lower.contains("twenty minutes") -> total = 20
                lower.contains("fifteen minutes") -> total = 15
                else -> total = 30 // Default 30m session
            }
        }
        return total.coerceIn(5, 240)
    }

    private fun generateConciseSummary(cleaned: String, category: String): String {
        val stripped = cleaned
            .replace(Regex("^(i just |just |i have |i've |today i |i spent \\d+ (minutes|mins|hours|hrs) (on )?|i )", RegexOption.IGNORE_CASE), "")
            .trim()
            .removeSuffix(".")

        val words = stripped.split(Regex("\\s+"))
        val shortTitle = if (words.size > 8) {
            words.take(8).joinToString(" ") + "..."
        } else {
            stripped
        }
        return shortTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            .ifBlank { "$category Session Completed" }
    }

    private fun findMatchingPendingTask(cleaned: String, tasks: List<TaskEntity>): TaskEntity? {
        val lower = cleaned.lowercase(Locale.US)
        return tasks.filter { !it.isCompletedToday }.firstOrNull { task ->
            val taskWords = task.title.lowercase(Locale.US)
                .split(Regex("\\W+"))
                .filter { it.length >= 4 }
            taskWords.isNotEmpty() && taskWords.count { lower.contains(it) } >= maxOf(1, taskWords.size / 2)
        }
    }

    /**
     * Generates an executive End-of-Day (EOD) Standup Report from all spoken voice logs and verified work.
     */
    fun generateDailyStandupReport(
        voiceLogs: List<VoiceLogEntity>,
        completedTasks: List<TaskEntity>,
        prefs: AppUserPreferences
    ): String {
        val realLogs = voiceLogs.filter { !it.isConfession }
        val totalFocusMins = realLogs.sumOf { it.effortMinutes } + (completedTasks.size * 25)
        val totalWords = voiceLogs.sumOf { it.wordCount }

        val sb = StringBuilder()
        sb.appendLine("🎙️ DAILY PROOF-OF-WORK STANDUP")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("⏱️ Deep Focus Logged: ~${totalFocusMins} mins")
        sb.appendLine("🗣️ Words Dictated: $totalWords words")
        sb.appendLine("🎁 Instagram Time Earned: +${prefs.totalEarnedMinutes}m (Used: ${prefs.formattedUsedTime})")
        sb.appendLine("🔥 Weekday Streak: ${prefs.effectiveCompletedWeekdays.size}/5 days toward Weekend Freedom")
        sb.appendLine()

        if (realLogs.isNotEmpty() || completedTasks.isNotEmpty() || prefs.isGithubPushedToday || prefs.isLeetcodeSolvedToday) {
            sb.appendLine("✅ ACCOMPLISHMENTS:")
            if (prefs.isGithubPushedToday) {
                sb.appendLine("• [CODING] Pushed code to GitHub (+${prefs.githubRewardMinutes}m)")
            }
            if (prefs.isLeetcodeSolvedToday) {
                sb.appendLine("• [CODING] Solved daily LeetCode challenge (+${prefs.leetcodeRewardMinutes}m)")
            }
            realLogs.forEach { log ->
                sb.appendLine("• [${log.category}] ${log.summary} (~${log.effortMinutes}m work → +${log.rewardMinutes}m reward)")
            }
            completedTasks.forEach { task ->
                sb.appendLine("• [TASK] ${task.title} (+${task.rewardMinutes}m reward)")
            }
        } else {
            sb.appendLine("⚠️ No voice logs recorded yet today. Tap 'Speak Proof of Work' and record what you accomplished!")
        }

        return sb.toString().trim()
    }
}
