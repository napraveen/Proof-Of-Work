package com.proofofwork.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proofofwork.ui.components.BentoCard
import com.proofofwork.ui.components.DualRadialProgress
import com.proofofwork.ui.components.HeatmapGrid
import com.proofofwork.ui.theme.*
import com.proofofwork.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val prefs = uiState.preferences
    var newTaskTitle by remember { mutableStateOf("") }

    Scaffold(
        containerColor = OledBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(NeonEmerald, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PROOFOFWORK",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OledBlack
                )
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

            // 1. HERO BENTO CARD: DUAL ARC GAUGE
            item {
                BentoCard {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "DAILY SCREEN TIME WALLET",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = TextMuted
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        DualRadialProgress(
                            isGithubComplete = prefs.isGithubPushedToday,
                            isLeetcodeComplete = prefs.isLeetcodeSolvedToday,
                            remainingMinutes = prefs.remainingMinutes,
                            formattedUsedTime = prefs.formattedUsedTime
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Instagram screen time usage banner
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = if (prefs.remainingMinutes > 0) NeonEmerald else CrimsonAlert,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Instagram Used Today",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "${prefs.formattedUsedTime} / ${prefs.totalAllowanceMinutes}m",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stats breakdown chips
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatusChip(
                                label = "GitHub Push",
                                isComplete = prefs.isGithubPushedToday,
                                reward = "+${prefs.githubRewardMinutes}m",
                                activeColor = NeonEmerald
                            )
                            StatusChip(
                                label = "LeetCode",
                                isComplete = prefs.isLeetcodeSolvedToday,
                                reward = "+${prefs.leetcodeRewardMinutes}m",
                                activeColor = ElectricAmber
                            )
                            StatusChip(
                                label = "Free Allowance",
                                isComplete = true,
                                reward = "${prefs.baseFreeMinutes}m",
                                activeColor = TextSecondary
                            )
                        }
                    }
                }
            }

            // 2. VERIFY WORK ACTION BUTTON
            item {
                Button(
                    onClick = { viewModel.verifyWorkNow() },
                    enabled = !uiState.isVerifying,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonEmerald,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (uiState.isVerifying) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "VERIFYING PROOF OF WORK...",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    } else {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VERIFY WORK & TOP UP",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // 3. GITHUB CONTRIBUTION HEATMAP & STREAK
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
            }

            // 4. LEETCODE CONTRIBUTION HEATMAP & STREAK
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
            }

            // 5. PRIORITY TASKS SECTION
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = "DAILY PRIORITY TASKS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "Earn +15m each",
                        fontSize = 11.sp,
                        color = NeonEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Add Task input
            item {
                BentoCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newTaskTitle,
                            onValueChange = { newTaskTitle = it },
                            placeholder = { Text("Add daily task (e.g. Study 45m)...", fontSize = 13.sp, color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonEmerald,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (newTaskTitle.isNotBlank()) {
                                    viewModel.addTask(newTaskTitle, 15)
                                    newTaskTitle = ""
                                }
                            },
                            modifier = Modifier
                                .background(NeonEmerald, RoundedCornerShape(14.dp))
                                .size(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black)
                        }
                    }
                }
            }

            // List of Tasks
            if (uiState.tasks.isEmpty()) {
                item {
                    Text(
                        text = "No custom tasks added. Add tasks to earn extra Instagram minutes!",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(uiState.tasks, key = { it.id }) { task ->
                    BentoCard(
                        containerColor = if (task.isCompletedToday) Color(0xFF101915) else ObsidianCard,
                        borderColor = if (task.isCompletedToday) NeonEmerald.copy(alpha = 0.3f) else ObsidianCardBorder
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = task.isCompletedToday,
                                onCheckedChange = { viewModel.toggleTaskCompletion(task) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = NeonEmerald,
                                    uncheckedColor = TextMuted,
                                    checkmarkColor = Color.Black
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (task.isCompletedToday) TextMuted else TextPrimary,
                                    textDecoration = if (task.isCompletedToday) TextDecoration.LineThrough else null
                                )
                                Text(
                                    text = "+${task.rewardMinutes} mins earned",
                                    fontSize = 11.sp,
                                    color = if (task.isCompletedToday) NeonEmerald else ElectricAmber
                                )
                            }
                            IconButton(onClick = { viewModel.deleteTask(task) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    isComplete: Boolean,
    reward: String,
    activeColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(text = label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isComplete) "✓ $reward" else "Pending",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isComplete) activeColor else TextMuted
        )
    }
}
