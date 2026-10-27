package com.proofofwork.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proofofwork.ProofOfWorkApp
import com.proofofwork.core.data.AppUserPreferences
import com.proofofwork.core.data.UserPreferencesRepository
import com.proofofwork.core.data.database.VoiceLogEntity
import com.proofofwork.core.model.NetworkResult
import com.proofofwork.core.network.AiWorkVerifierService
import com.proofofwork.core.network.GitHubService
import com.proofofwork.core.network.LeetCodeService
import com.proofofwork.core.network.VoiceVerificationResult
import com.proofofwork.core.util.SoundFeedbackHelper
import com.proofofwork.ui.components.BentoCard
import com.proofofwork.ui.components.WisprVoiceDialog
import com.proofofwork.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BlockerOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Intercept back button at the activity level to prevent bypassing into the blocked app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitToHome()
            }
        })

        setContent {
            ProofOfWorkTheme {
                val app = remember { ProofOfWorkApp.instance }
                val repo = remember { app.preferencesRepository }
                val taskDao = remember { app.database.taskDao() }
                val prefsState by repo.userPreferencesFlow.collectAsState(initial = null)
                val safeLockedPrefs = remember {
                    AppUserPreferences(
                        baseFreeMinutes = 0,
                        usedSecondsToday = 86400,
                        weekendFreedomEnabled = false
                    )
                }
                val prefs = prefsState ?: safeLockedPrefs
                val coroutineScope = rememberCoroutineScope()

                var isCheckingNow by remember { mutableStateOf(false) }
                var showVoiceModal by remember { mutableStateOf(false) }
                var isConfessionMode by remember { mutableStateOf(false) }
                var isAnalyzingVoice by remember { mutableStateOf(false) }
                var voiceResult by remember { mutableStateOf<VoiceVerificationResult?>(null) }

                var initialBalanceRecorded by remember { mutableStateOf(false) }
                var initialRemainingSeconds by remember { mutableIntStateOf(0) }

                // Auto-unlock ONLY when the user actually gains time (or Weekend Freedom activates) after the lock screen opened
                LaunchedEffect(prefsState?.remainingSeconds, prefsState?.isWeekendFreedomActive) {
                    val current = prefsState ?: return@LaunchedEffect
                    if (!initialBalanceRecorded) {
                        initialRemainingSeconds = current.remainingSeconds
                        initialBalanceRecorded = true
                    } else {
                        if ((current.remainingSeconds > 0 && initialRemainingSeconds <= 0) || current.isWeekendFreedomActive) {
                            if (!isFinishing) {
                                finish()
                            }
                        }
                    }
                }

                fun performQuickVerify() {
                    if (isCheckingNow) return
                    coroutineScope.launch {
                        isCheckingNow = true
                        var verified = false

                        if (!prefs.isGithubPushedToday && prefs.githubUsername.isNotBlank()) {
                            val result = GitHubService.verifyTodayPush(prefs.githubUsername)
                            if (result is NetworkResult.Success && result.data.isPushedToday) {
                                repo.updateGithubVerified(true)
                                verified = true
                            }
                        }

                        if (!prefs.isLeetcodeSolvedToday && prefs.leetcodeUsername.isNotBlank()) {
                            val result = LeetCodeService.verifyTodaySolve(prefs.leetcodeUsername)
                            if (result is NetworkResult.Success && result.data.isSolvedToday) {
                                repo.updateLeetcodeVerified(true)
                                verified = true
                            }
                        }

                        isCheckingNow = false
                        if (verified) {
                            SoundFeedbackHelper.playSuccessChime()
                            if (!isFinishing) {
                                finish() // Dismiss lock screen when verified
                            }
                        }
                    }
                }

                if (showVoiceModal) {
                    WisprVoiceDialog(
                        isConfessionMode = isConfessionMode,
                        isAnalyzing = isAnalyzingVoice,
                        verificationResult = voiceResult,
                        onAnalyzeTranscript = { transcript, confession ->
                            coroutineScope.launch {
                                isAnalyzingVoice = true
                                val res = if (confession) {
                                    AiWorkVerifierService.evaluateConfession(transcript)
                                } else {
                                    AiWorkVerifierService.evaluateSpokenWork(transcript, prefs)
                                }
                                voiceResult = res
                                isAnalyzingVoice = false

                                if (res.isVerified) {
                                    SoundFeedbackHelper.playSuccessChime()
                                    taskDao.insertVoiceLog(
                                        VoiceLogEntity(
                                            transcript = res.cleanedTranscript,
                                            summary = res.summary,
                                            category = res.category,
                                            effortMinutes = res.effortMinutes,
                                            rewardMinutes = res.rewardMinutes,
                                            aiFeedback = res.aiFeedback,
                                            confidenceScore = res.confidenceScore,
                                            wordCount = res.wordCount,
                                            isConfession = confession,
                                            dateString = UserPreferencesRepository.getTodayDateString()
                                        )
                                    )
                                    repo.addEarnedVoiceMinutes(
                                        minutes = res.rewardMinutes,
                                        wordsSpoken = res.wordCount,
                                        isConfession = confession
                                    )
                                    delay(900)
                                    showVoiceModal = false
                                    finish() // Immediately unlock Instagram!
                                } else {
                                    SoundFeedbackHelper.playErrorTone()
                                }
                            }
                        },
                        onDismiss = {
                            showVoiceModal = false
                            voiceResult = null
                        }
                    )
                }

                LockScreenContent(
                    isGithubDone = prefs.isGithubPushedToday,
                    isLeetcodeDone = prefs.isLeetcodeSolvedToday,
                    isVoiceDone = prefs.earnedFromVoiceMinutes > 0,
                    confessionUsedToday = prefs.confessionUsedToday,
                    weekdayCount = prefs.effectiveCompletedWeekdays.size,
                    isChecking = isCheckingNow,
                    formattedUsedTime = prefs.formattedUsedTime,
                    onSpeakProofOfWork = {
                        isConfessionMode = false
                        voiceResult = null
                        showVoiceModal = true
                    },
                    onSpeakConfession = {
                        isConfessionMode = true
                        voiceResult = null
                        showVoiceModal = true
                    },
                    onQuickVerify = { performQuickVerify() },
                    onOpenLeetCode = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://leetcode.com/problemset/all/")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(browserIntent)
                        finish()
                    },
                    onOpenGitHub = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(browserIntent)
                        finish()
                    },
                    onExitToHome = {
                        exitToHome()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
    }

    override fun onPause() {
        super.onPause()
        isShowing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isShowing = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            exitToHome()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun exitToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        @Volatile
        var isShowing: Boolean = false
            private set
    }
}

@Composable
private fun LockScreenContent(
    isGithubDone: Boolean,
    isLeetcodeDone: Boolean,
    isVoiceDone: Boolean,
    confessionUsedToday: Boolean,
    weekdayCount: Int,
    isChecking: Boolean,
    formattedUsedTime: String,
    onSpeakProofOfWork: () -> Unit,
    onSpeakConfession: () -> Unit,
    onQuickVerify: () -> Unit,
    onOpenLeetCode: () -> Unit,
    onOpenGitHub: () -> Unit,
    onExitToHome: () -> Unit
) {
    BackHandler {
        onExitToHome()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF508090B))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            }
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pulsing Lock Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(84.dp)
                    .background(CrimsonAlertGlow, CircleShape)
                    .border(1.5.dp, WisprViolet.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.MicNone,
                    contentDescription = "Voice Locked",
                    tint = WisprViolet,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "INSTAGRAM LOCKED",
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "You've used $formattedUsedTime today.\nSpeak what task you completed to mint more scroll time!",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Primary Hero CTA: Speak Proof of Work
            Button(
                onClick = onSpeakProofOfWork,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WisprViolet,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SPEAK PROOF OF WORK (UNLOCK)",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status Bento Card
            BentoCard {
                Text(
                    text = "TODAY'S PROOF-OF-WORK STATUS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                LockRequirementRow(
                    title = "Speak Proof of Work",
                    subtitle = "AI Judge verifies & mints +15m to +45m",
                    isDone = isVoiceDone,
                    accentColor = WisprViolet
                )

                Spacer(modifier = Modifier.height(10.dp))

                LockRequirementRow(
                    title = "Push Code to GitHub",
                    subtitle = "Commit & push to any repository",
                    isDone = isGithubDone,
                    accentColor = NeonEmerald
                )

                Spacer(modifier = Modifier.height(10.dp))

                LockRequirementRow(
                    title = "Weekday Streak ($weekdayCount/5 Days)",
                    subtitle = "Complete Mon–Fri for full Sat & Sun access",
                    isDone = weekdayCount >= 5,
                    accentColor = ElectricAmber
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Auto-check GitHub/LeetCode button
            OutlinedButton(
                onClick = onQuickVerify,
                enabled = !isChecking,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonEmerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        color = NeonEmerald,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CHECKING GITHUB & LEETCODE...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VERIFY GITHUB / LEETCODE PUSH", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Row: GitHub, LeetCode, or 5m Guilty Voice Confession
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!confessionUsedToday) {
                    OutlinedButton(
                        onClick = onSpeakConfession,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricAmber),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("😅 5m Voice Confession", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenGitHub,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Open GitHub", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = onOpenLeetCode,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text("Open LeetCode", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onExitToHome) {
                Text(
                    text = "Back to Home Screen",
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LockRequirementRow(
    title: String,
    subtitle: String,
    isDone: Boolean,
    accentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isDone) accentColor else CrimsonAlert,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDone) accentColor else TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}
