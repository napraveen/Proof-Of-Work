package com.proofofwork.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proofofwork.core.data.AppUserPreferences
import com.proofofwork.core.data.database.TaskEntity
import com.proofofwork.core.data.database.VoiceLogEntity
import com.proofofwork.ui.components.BentoCard
import com.proofofwork.ui.components.DualRadialProgress
import com.proofofwork.ui.components.WisprVoiceDialog
import com.proofofwork.ui.theme.*
import com.proofofwork.viewmodel.DashboardUiState
import com.proofofwork.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    autoOpenVoiceModal: Boolean = false,
    onConsumedAutoOpenVoice: () -> Unit = {},
    onNavigateToSettings: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var showVoiceDialog by remember { mutableStateOf(false) }
    var isConfessionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(autoOpenVoiceModal) {
        if (autoOpenVoiceModal) {
            isConfessionDialog = false
            viewModel.clearLastVoiceResult()
            showVoiceDialog = true
            onConsumedAutoOpenVoice()
        }
    }

    if (showVoiceDialog) {
        WisprVoiceDialog(
            isConfessionMode = isConfessionDialog,
            isAnalyzing = uiState.isAnalyzingVoice,
            verificationResult = uiState.lastVoiceResult,
            onAnalyzeTranscript = { transcript, confession ->
                viewModel.analyzeVoiceProof(transcript, confession)
            },
            onDismiss = {
                showVoiceDialog = false
                viewModel.clearLastVoiceResult()
            }
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.verifyWorkNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DashboardContent(
        uiState = uiState,
        onOpenVoiceModal = {
            isConfessionDialog = false
            viewModel.clearLastVoiceResult()
            showVoiceDialog = true
        },
        onVerifyWork = { viewModel.verifyWorkNow() },
        onAddTask = { title, mins -> viewModel.addTask(title, mins) },
        onToggleTask = { viewModel.toggleTaskCompletion(it) },
        onDeleteTask = { viewModel.deleteTask(it) },
        onDeleteVoiceLog = { viewModel.deleteVoiceLog(it) },
        onGenerateStandup = { viewModel.generateStandupSummary() },
        onDismissStandup = { viewModel.dismissStandupReport() },
        onNavigateToSettings = onNavigateToSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onOpenVoiceModal: () -> Unit,
    onVerifyWork: () -> Unit,
    onAddTask: (String, Int) -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onDeleteVoiceLog: (VoiceLogEntity) -> Unit = {},
    onGenerateStandup: () -> Unit = {},
    onDismissStandup: () -> Unit = {},
    onNavigateToSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = uiState.preferences
    var newTaskTitle by remember { mutableStateOf("") }
    var selectedTaskReward by remember { mutableIntStateOf(15) }

    Scaffold(
        containerColor = OledBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(NeonEmerald, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PROOFOFWORK",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.6.sp,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
                    val spinRotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 750, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "spinRotation"
                    )

                    var manualSpinTrigger by remember { mutableStateOf(false) }
                    val manualRotation by animateFloatAsState(
                        targetValue = if (manualSpinTrigger) 360f else 0f,
                        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                        finishedListener = { manualSpinTrigger = false },
                        label = "manualSpin"
                    )

                    val currentRotation = if (uiState.isVerifying) spinRotation else manualRotation

                    IconButton(
                        onClick = {
                            manualSpinTrigger = true
                            onVerifyWork()
                            Toast.makeText(context, "Syncing screen time & stats...", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !uiState.isVerifying
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh & Sync",
                            tint = if (uiState.isVerifying) CyanPulse else NeonEmerald,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(currentRotation)
                        )
                    }
                    if (onNavigateToSettings != null) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = TextSecondary
                            )
                        }
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
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. HERO BENTO CARD: SCREEN TIME WALLET
            item {
                BentoCard {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "SCREEN TIME WALLET",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        DualRadialProgress(
                            isGithubComplete = prefs.isGithubPushedToday,
                            isLeetcodeComplete = prefs.isLeetcodeSolvedToday,
                            isVoiceLoggedToday = prefs.earnedFromVoiceMinutes > 0,
                            isWeekendFreedomActive = prefs.isWeekendFreedomActive,
                            remainingMinutes = prefs.remainingMinutes,
                            formattedUsedTime = prefs.formattedUsedTime
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Instagram screen time usage banner
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF111520), RoundedCornerShape(14.dp))
                                .border(1.dp, Color(0x1F2A334E), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = if (prefs.remainingMinutes > 0 || prefs.isWeekendFreedomActive) NeonEmerald else CrimsonAlert,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Instagram Screen Time",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = if (prefs.isWeekendFreedomActive) {
                                        "${prefs.formattedUsedTime} / Unlimited"
                                    } else {
                                        "${prefs.formattedUsedTime} / ${prefs.totalAllowanceMinutes}m"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (prefs.remainingMinutes > 0 || prefs.isWeekendFreedomActive) NeonEmerald else CrimsonAlert,
                                    maxLines = 1
                                )
                            }

                            if (!prefs.isWeekendFreedomActive && prefs.totalAllowanceMinutes > 0) {
                                Spacer(modifier = Modifier.height(7.dp))
                                val usedRatio = (prefs.usedMinutesToday.toFloat() / prefs.totalAllowanceMinutes.toFloat()).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF1E2435))
                                ) {
                                    if (usedRatio > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction = usedRatio)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(if (usedRatio >= 1f) CrimsonAlert else CyanPulse)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2x2 Bento Grid: Earnings Breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            EarningSourceCard(
                                label = "Voice Proof",
                                reward = "+${prefs.earnedFromVoiceMinutes}m",
                                isComplete = prefs.earnedFromVoiceMinutes > 0,
                                activeColor = WisprViolet,
                                icon = Icons.Default.GraphicEq,
                                modifier = Modifier.weight(1f)
                            )
                            EarningSourceCard(
                                label = "GitHub Push",
                                reward = "+${prefs.githubRewardMinutes}m",
                                isComplete = prefs.isGithubPushedToday,
                                activeColor = NeonEmerald,
                                icon = Icons.Default.Code,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            EarningSourceCard(
                                label = "LeetCode",
                                reward = "+${prefs.leetcodeRewardMinutes}m",
                                isComplete = prefs.isLeetcodeSolvedToday,
                                activeColor = ElectricAmber,
                                icon = Icons.Default.Terminal,
                                modifier = Modifier.weight(1f)
                            )
                            EarningSourceCard(
                                label = "Standup Tasks",
                                reward = "+${prefs.earnedFromTasksMinutes}m",
                                isComplete = prefs.earnedFromTasksMinutes > 0,
                                activeColor = CyanPulse,
                                icon = Icons.Default.TaskAlt,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. VOICE COMMAND HERO CARD (PRIMARY CTA)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF221338), Color(0xFF131726))
                            )
                        )
                        .border(1.2.dp, WisprViolet.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        // Header row with proper flex weights so text NEVER wraps vertically
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = WisprViolet,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = "VOICE PROOF OF WORK",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    color = WisprViolet,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x2200F5A0))
                                    .clickable { onGenerateStandup() }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "📋 EOD Standup",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonEmerald
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Speak what you did → Unlock Instagram",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Say what you coded, studied, or accomplished. Our LLM Judge analyzes your wording & mints Instagram minutes.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Large Primary Button + Compact Secondary Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onOpenVoiceModal,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WisprViolet,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SPEAK WHAT I DID",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            OutlinedButton(
                                onClick = onVerifyWork,
                                enabled = !uiState.isVerifying,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = NeonEmerald
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                if (uiState.isVerifying) {
                                    CircularProgressIndicator(
                                        color = NeonEmerald,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync Git", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Git/LC", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Optional EOD Standup Report Card
            if (uiState.standupReport != null) {
                item {
                    BentoCard(
                        containerColor = Color(0xFF101D24),
                        borderColor = CyanPulse.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DAILY AI VOICE STANDUP SUMMARY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = CyanPulse,
                                letterSpacing = 1.sp
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Daily Standup", uiState.standupReport))
                                        Toast.makeText(context, "Standup copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = CyanPulse, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = onDismissStandup,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.standupReport,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 3. VOICE PROOF-OF-WORK FEED (SPOKEN ACCOMPLISHMENTS)
            if (uiState.voiceLogs.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    ) {
                        Text(
                            text = "SPOKEN PROOF-OF-WORK LOGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "${uiState.voiceLogs.size} verified",
                            fontSize = 11.sp,
                            color = WisprViolet,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                items(uiState.voiceLogs.take(5), key = { "voice_${it.id}" }) { log ->
                    BentoCard(
                        containerColor = if (log.isConfession) Color(0xFF241B10) else Color(0xFF151324),
                        borderColor = if (log.isConfession) ElectricAmber.copy(alpha = 0.35f) else WisprViolet.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (log.isConfession) ElectricAmber.copy(alpha = 0.2f) else WisprViolet.copy(alpha = 0.2f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "🎙️ ${log.category}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (log.isConfession) ElectricAmber else WisprViolet
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${log.wordCount} words • ${log.confidenceScore}% AI",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "+${log.rewardMinutes}m",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonEmerald
                                )
                                IconButton(
                                    onClick = { onDeleteVoiceLog(log) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete",
                                        tint = TextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = log.summary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "\"${log.transcript}\"",
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🤖 ${log.aiFeedback}",
                            fontSize = 11.sp,
                            color = NeonEmerald.copy(alpha = 0.9f),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // 4. CUSTOM TASK-TO-INSTAGRAM MINUTES MAPPER
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(
                        text = "TASK → INSTAGRAM MINUTES MAPPER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Map custom tasks to scroll time • Complete or speak to unlock",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            // Add Task with balanced, wrap-free chips
            item {
                BentoCard {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newTaskTitle,
                                onValueChange = { newTaskTitle = it },
                                placeholder = {
                                    Text(
                                        "Add a task (e.g. Finish React PR)...",
                                        fontSize = 13.sp,
                                        color = TextMuted
                                    )
                                },
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
                                        onAddTask(newTaskTitle, selectedTaskReward)
                                        newTaskTitle = ""
                                    }
                                },
                                modifier = Modifier
                                    .background(NeonEmerald, RoundedCornerShape(14.dp))
                                    .size(50.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Reward Minutes Selector Chips: Equal 4-column row so +45m never cuts off
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(10, 15, 25, 45).forEach { mins ->
                                val isSelected = selectedTaskReward == mins
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) NeonEmerald else Color(0x14FFFFFF)
                                        )
                                        .clickable { selectedTaskReward = mins }
                                        .padding(vertical = 7.dp)
                                ) {
                                    Text(
                                        text = "+${mins}m",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isSelected) Color.Black else TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // List of Mapped Tasks
            if (uiState.tasks.isEmpty()) {
                item {
                    Text(
                        text = "No custom tasks added. Add tasks above or tap 'SPEAK WHAT I DID'!",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            } else {
                items(uiState.tasks, key = { "task_${it.id}" }) { task ->
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
                                onCheckedChange = { onToggleTask(task) },
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
                                    text = if (task.isCompletedToday) "✓ Unlocked +${task.rewardMinutes}m Instagram"
                                    else "Maps to +${task.rewardMinutes}m Instagram usage",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (task.isCompletedToday) NeonEmerald else ElectricAmber
                                )
                            }
                            IconButton(onClick = { onDeleteTask(task) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun EarningSourceCard(
    label: String,
    reward: String,
    isComplete: Boolean,
    activeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF111520),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isComplete) activeColor.copy(alpha = 0.35f) else Color(0x1F2A334E)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            if (isComplete) activeColor.copy(alpha = 0.18f) else Color(0x14FFFFFF),
                            androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isComplete) activeColor else TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isComplete) activeColor.copy(alpha = 0.2f) else Color(0x14FFFFFF)
                ) {
                    Text(
                        text = if (isComplete) "✓ $reward" else reward,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isComplete) activeColor else TextMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isComplete) TextPrimary else TextSecondary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090B)
@Composable
fun DashboardScreenPreview() {
    ProofOfWorkTheme {
        DashboardContent(
            uiState = DashboardUiState(
                preferences = AppUserPreferences(
                    baseFreeMinutes = 5,
                    githubRewardMinutes = 25,
                    leetcodeRewardMinutes = 35,
                    earnedFromVoiceMinutes = 20,
                    totalWordsDictated = 142,
                    isGithubPushedToday = true,
                    usedSecondsToday = 600
                ),
                tasks = listOf(
                    TaskEntity(id = 1, title = "Review System Design Chapter 4", isCompletedToday = true, rewardMinutes = 15),
                    TaskEntity(id = 2, title = "Finish Auth API Refactor", isCompletedToday = false, rewardMinutes = 25)
                )
            ),
            onOpenVoiceModal = {},
            onVerifyWork = {},
            onAddTask = { _, _ -> },
            onToggleTask = {},
            onDeleteTask = {}
        )
    }
}
