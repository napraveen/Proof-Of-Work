package com.proofofwork.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proofofwork.ProofOfWorkApp
import com.proofofwork.core.model.NetworkResult
import com.proofofwork.core.network.GitHubService
import com.proofofwork.core.network.LeetCodeService
import com.proofofwork.ui.components.BentoCard
import com.proofofwork.ui.theme.*
import kotlinx.coroutines.launch

class BlockerOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProofOfWorkTheme {
                val repo = remember { ProofOfWorkApp.instance.preferencesRepository }
                val prefs by repo.userPreferencesFlow.collectAsState(initial = com.proofofwork.core.data.AppUserPreferences())
                val coroutineScope = rememberCoroutineScope()
                var isCheckingNow by remember { mutableStateOf(false) }

                LockScreenContent(
                    isGithubDone = prefs.isGithubPushedToday,
                    isLeetcodeDone = prefs.isLeetcodeSolvedToday,
                    isChecking = isCheckingNow,
                    onQuickVerify = {
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
                                finish() // Automatically dismiss lock screen!
                            }
                        }
                    },
                    onOpenLeetCode = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://leetcode.com/problemset/all/"))
                        startActivity(browserIntent)
                        finish()
                    },
                    onOpenGitHub = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"))
                        startActivity(browserIntent)
                        finish()
                    },
                    onExitToHome = {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun LockScreenContent(
    isGithubDone: Boolean,
    isLeetcodeDone: Boolean,
    isChecking: Boolean,
    onQuickVerify: () -> Unit,
    onOpenLeetCode: () -> Unit,
    onOpenGitHub: () -> Unit,
    onExitToHome: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF208090B))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Lock Icon with glowing background
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(CrimsonAlertGlow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = CrimsonAlert,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "INSTAGRAM IS LOCKED",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "You ran out of screen time. Complete your proof-of-work to mint more minutes.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Tasks requirement Bento card
            BentoCard(
                containerColor = ObsidianSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                RequirementRow(
                    title = "Push to GitHub",
                    reward = "+Earn minutes",
                    isComplete = isGithubDone,
                    color = NeonEmerald
                )
                Spacer(modifier = Modifier.height(12.dp))
                RequirementRow(
                    title = "Solve LeetCode Problem",
                    reward = "+Earn minutes",
                    isComplete = isLeetcodeDone,
                    color = ElectricAmber
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Re-check button
            Button(
                onClick = onQuickVerify,
                enabled = !isChecking,
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("I Just Pushed! Re-Check & Unlock", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Open LeetCode
            OutlinedButton(
                onClick = onOpenLeetCode,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricAmber),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open LeetCode →", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action: Open GitHub
            OutlinedButton(
                onClick = onOpenGitHub,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open GitHub →", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Back to Home
            TextButton(onClick = onExitToHome) {
                Icon(Icons.Default.Home, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Go to Home Screen", color = TextMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun RequirementRow(
    title: String,
    reward: String,
    isComplete: Boolean,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(text = reward, fontSize = 11.sp, color = color)
        }
        Text(
            text = if (isComplete) "✓ VERIFIED" else "PENDING",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isComplete) color else CrimsonAlert
        )
    }
}
