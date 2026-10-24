package com.proofofwork.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proofofwork.ui.components.BentoCard
import com.proofofwork.ui.components.HeatmapGrid
import com.proofofwork.ui.theme.*
import com.proofofwork.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreaksScreen(
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val prefs = uiState.preferences

    Scaffold(
        containerColor = OledBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
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
                            text = "STREAKS & ACTIVITY",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.verifyWorkNow() },
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
            // Status & Feedback Messages
            if (uiState.statusMessage != null) {
                item {
                    BentoCard(
                        containerColor = Color(0xFF14241C),
                        borderColor = NeonEmerald.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = uiState.statusMessage ?: "",
                            color = NeonEmerald,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (uiState.errorMessage != null) {
                item {
                    BentoCard(
                        containerColor = Color(0xFF2B1218),
                        borderColor = CrimsonAlert.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = CrimsonAlert,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Streak Overview Hero Card
            item {
                BentoCard {
                    Text(
                        text = "CODING CONSISTENCY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StreakStatColumn(
                            title = "GitHub Streak",
                            days = uiState.githubStatus.streakDays,
                            activeColor = NeonEmerald,
                            isDoneToday = uiState.githubStatus.isPushedToday
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(50.dp)
                                .background(ObsidianCardBorder)
                        )
                        StreakStatColumn(
                            title = "LeetCode Streak",
                            days = uiState.leetcodeStatus.streakDays,
                            activeColor = ElectricAmber,
                            isDoneToday = uiState.leetcodeStatus.isSolvedToday
                        )
                    }
                }
            }

            // 1. GITHUB CONTRIBUTION HEATMAP & STREAK
            if (prefs.githubUsername.isNotBlank()) {
                item {
                    HeatmapGrid(
                        title = "GitHub Activity & Streak",
                        streakDays = uiState.githubStatus.streakDays,
                        subtitle = if (uiState.githubStatus.isPushedToday) {
                            "${uiState.githubStatus.commitCount} contribution(s) today • ${uiState.githubStatus.latestRepo}"
                        } else {
                            "Push today to maintain your streak"
                        },
                        days = uiState.githubStatus.recentContributions,
                        activeThemeColor = NeonEmerald
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
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
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

            // 2. LEETCODE CONTRIBUTION HEATMAP & STREAK
            if (prefs.leetcodeUsername.isNotBlank()) {
                item {
                    HeatmapGrid(
                        title = "LeetCode Activity & Streak",
                        streakDays = uiState.leetcodeStatus.streakDays,
                        subtitle = if (uiState.leetcodeStatus.isSolvedToday) {
                            "Solved '${uiState.leetcodeStatus.problemTitle}' • Total ${uiState.leetcodeStatus.totalSolved}"
                        } else {
                            "Total ${uiState.leetcodeStatus.totalSolved} solved • Solve 1 today to top up"
                        },
                        days = uiState.leetcodeStatus.recentDays,
                        activeThemeColor = ElectricAmber
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
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
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

            // Sync button
            item {
                Button(
                    onClick = { viewModel.verifyWorkNow() },
                    enabled = !uiState.isVerifying,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianCard,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonEmerald)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Streaks & Activity", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
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
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(
                        if (isDoneToday) activeColor.copy(alpha = 0.2f) else Color(0x1AFFFFFF),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isDoneToday) "✓ Done Today" else "Pending",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDoneToday) activeColor else TextMuted
                )
            }
        }
    }
}
