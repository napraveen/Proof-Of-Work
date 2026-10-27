package com.proofofwork.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proofofwork.core.data.AppUserPreferences
import com.proofofwork.core.model.ContributionDay
import com.proofofwork.core.model.GitHubStatus
import com.proofofwork.core.model.LeetCodeStatus
import com.proofofwork.ui.components.BentoCard
import com.proofofwork.ui.components.HeatmapGrid
import com.proofofwork.ui.components.StreakProgressJourneyCard
import com.proofofwork.ui.components.WeekdayStreakCard
import com.proofofwork.ui.theme.*
import com.proofofwork.viewmodel.DashboardUiState
import com.proofofwork.viewmodel.DashboardViewModel

@Composable
fun StreaksScreen(
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    StreaksContent(
        uiState = uiState,
        onVerifyWork = { viewModel.verifyWorkNow() },
        onToggleWeekendFreedom = { viewModel.toggleWeekendFreedom(it) },
        onToggleDemo5DayStreak = { viewModel.toggleDemo5DayStreak(it) },
        onToggleWeekdayIndex = { viewModel.toggleWeekdayIndex(it) },
        onNavigateToSettings = onNavigateToSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreaksContent(
    uiState: DashboardUiState,
    onVerifyWork: () -> Unit,
    onToggleWeekendFreedom: (Boolean) -> Unit = {},
    onToggleDemo5DayStreak: (Boolean) -> Unit = {},
    onToggleWeekdayIndex: (Int) -> Unit = {},
    onNavigateToSettings: () -> Unit
) {
    val prefs = uiState.preferences

    Scaffold(
        containerColor = OledBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color(0x26FFA116), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = ElectricAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "STREAKS & STATS",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onVerifyWork,
                        enabled = !uiState.isVerifying
                    ) {
                        if (uiState.isVerifying) {
                            CircularProgressIndicator(
                                color = NeonEmerald,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = NeonEmerald
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OledBlack)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 5-DAY WEEKDAY STREAK PROGRESS JOURNEY
            item {
                StreakProgressJourneyCard(
                    prefs = prefs,
                    onToggleWeekdayIndex = onToggleWeekdayIndex
                )
            }

            // 2. CODING CONSISTENCY HERO CARD (BALANCED 2-COLUMN GRID)
            item {
                BentoCard {
                    Text(
                        text = "CODING CONSISTENCY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            StreakStatColumn(
                                title = "GitHub Streak",
                                days = uiState.githubStatus.streakDays,
                                activeColor = NeonEmerald,
                                isDoneToday = uiState.githubStatus.isPushedToday
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(52.dp)
                                .background(ObsidianCardBorder)
                        )
                        Box(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                            StreakStatColumn(
                                title = "LeetCode Streak",
                                days = uiState.leetcodeStatus.streakDays,
                                activeColor = ElectricAmber,
                                isDoneToday = uiState.leetcodeStatus.isSolvedToday
                            )
                        }
                    }
                }
            }

            // 4. GITHUB CONTRIBUTION HEATMAP & STREAK
            if (prefs.githubUsername.isNotBlank()) {
                item {
                    HeatmapGrid(
                        title = "GitHub",
                        streakDays = uiState.githubStatus.streakDays,
                        subtitle = if (uiState.githubStatus.isPushedToday) {
                            "${uiState.githubStatus.commitCount} commit(s) today • ${uiState.githubStatus.latestRepo}"
                        } else {
                            "Push today to maintain streak"
                        },
                        days = uiState.githubStatus.recentContributions,
                        activeThemeColor = NeonEmerald,
                        icon = Icons.Default.Code,
                        username = prefs.githubUsername
                    )
                }
            } else {
                item {
                    BentoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = NeonEmerald)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("GitHub Not Connected", fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add your GitHub username in Settings to track commit heatmaps and earn daily screen time.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onNavigateToSettings,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonEmerald)
                        ) {
                            Text("Connect GitHub →", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 5. LEETCODE CONTRIBUTION HEATMAP & STREAK
            if (prefs.leetcodeUsername.isNotBlank()) {
                item {
                    HeatmapGrid(
                        title = "LeetCode",
                        streakDays = uiState.leetcodeStatus.streakDays,
                        subtitle = if (uiState.leetcodeStatus.isSolvedToday) {
                            "Solved '${uiState.leetcodeStatus.problemTitle}'"
                        } else {
                            "Total ${uiState.leetcodeStatus.totalSolved} solved • 0 today"
                        },
                        days = uiState.leetcodeStatus.recentDays,
                        activeThemeColor = ElectricAmber,
                        icon = Icons.Default.Terminal,
                        username = prefs.leetcodeUsername
                    )
                }
            } else {
                item {
                    BentoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = ElectricAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LeetCode Not Connected", fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add your LeetCode username in Settings to track problem streaks and earn daily minutes.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onNavigateToSettings,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricAmber)
                        ) {
                            Text("Connect LeetCode →", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 6. SYNC BUTTON
            item {
                Button(
                    onClick = onVerifyWork,
                    enabled = !uiState.isVerifying,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianCard,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(17.dp), tint = NeonEmerald)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Streaks & Activity", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun StreakStatColumn(
    title: String,
    days: Int,
    activeColor: Color,
    isDoneToday: Boolean
) {
    Column {
        Text(text = title, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${days}d",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = if (days > 0) activeColor else TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(
                        if (isDoneToday) activeColor.copy(alpha = 0.2f) else Color(0x1AFFFFFF),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isDoneToday) "✓ Done" else "Pending",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDoneToday) activeColor else TextMuted
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090B)
@Composable
fun StreaksScreenPreview() {
    ProofOfWorkTheme {
        StreaksContent(
            uiState = DashboardUiState(
                preferences = AppUserPreferences(
                    githubUsername = "developer",
                    leetcodeUsername = "developer",
                    totalWordsDictated = 245,
                    totalVoiceLogsCount = 9
                ),
                githubStatus = GitHubStatus(
                    streakDays = 7,
                    isPushedToday = true,
                    commitCount = 4,
                    latestRepo = "ProofOfWork",
                    recentContributions = listOf(
                        ContributionDay(date = "2026-10-24", level = 3, count = 4),
                        ContributionDay(date = "2026-10-23", level = 2, count = 2)
                    )
                ),
                leetcodeStatus = LeetCodeStatus(
                    streakDays = 12,
                    isSolvedToday = false,
                    totalSolved = 140
                )
            ),
            onVerifyWork = {},
            onNavigateToSettings = {}
        )
    }
}
