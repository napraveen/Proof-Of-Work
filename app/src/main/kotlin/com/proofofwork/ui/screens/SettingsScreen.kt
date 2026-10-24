package com.proofofwork.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proofofwork.ProofOfWorkApp
import com.proofofwork.ui.components.BentoCard
import com.proofofwork.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repo = ProofOfWorkApp.instance.preferencesRepository
    val prefs by repo.userPreferencesFlow.collectAsState(initial = com.proofofwork.core.data.AppUserPreferences())

    var githubInput by remember(prefs.githubUsername) { mutableStateOf(prefs.githubUsername) }
    var leetcodeInput by remember(prefs.leetcodeUsername) { mutableStateOf(prefs.leetcodeUsername) }

    var freeMinutesSlider by remember(prefs.baseFreeMinutes) { mutableFloatStateOf(prefs.baseFreeMinutes.toFloat()) }
    var githubRewardSlider by remember(prefs.githubRewardMinutes) { mutableFloatStateOf(prefs.githubRewardMinutes.toFloat()) }
    var leetcodeRewardSlider by remember(prefs.leetcodeRewardMinutes) { mutableFloatStateOf(prefs.leetcodeRewardMinutes.toFloat()) }

    var savedMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = OledBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SETTINGS & REWARDS",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OledBlack)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (savedMessage != null) {
                BentoCard(
                    containerColor = Color(0xFF14241C),
                    borderColor = NeonEmerald.copy(alpha = 0.3f)
                ) {
                    Text(text = savedMessage ?: "", color = NeonEmerald, fontSize = 13.sp)
                }
            }

            // 1. REWARD POINTS CONFIGURATION (CUSTOMIZE MINUTES EARNED)
            BentoCard {
                Text(
                    text = "REWARD POINTS CONFIGURATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose how many minutes of Instagram time you earn for each coding accomplishment.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // GitHub Reward
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GitHub Push Reward", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Text(
                        text = "+${githubRewardSlider.toInt()}m",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonEmerald
                    )
                }
                Slider(
                    value = githubRewardSlider,
                    onValueChange = { githubRewardSlider = it },
                    valueRange = 5f..120f,
                    steps = 22,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonEmerald,
                        activeTrackColor = NeonEmerald,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // LeetCode Reward
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = ElectricAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LeetCode Solve Reward", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Text(
                        text = "+${leetcodeRewardSlider.toInt()}m",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = ElectricAmber
                    )
                }
                Slider(
                    value = leetcodeRewardSlider,
                    onValueChange = { leetcodeRewardSlider = it },
                    valueRange = 5f..120f,
                    steps = 22,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricAmber,
                        activeTrackColor = ElectricAmber,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        coroutineScope.launch {
                            repo.updateGithubRewardMinutes(githubRewardSlider.toInt())
                            repo.updateLeetcodeRewardMinutes(leetcodeRewardSlider.toInt())
                            savedMessage = "Updated rewards: +${githubRewardSlider.toInt()}m for GitHub, +${leetcodeRewardSlider.toInt()}m for LeetCode!"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Rewards", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // 2. FREE DAILY ALLOWANCE SETTING
            BentoCard {
                Text(
                    text = "FREE DAILY INSTAGRAM ALLOWANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Free grace time before proof-of-work is required. Set to 0 for strict mode.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${freeMinutesSlider.toInt()} Minutes Daily",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                repo.updateBaseFreeMinutes(freeMinutesSlider.toInt())
                                savedMessage = "Daily free allowance set to ${freeMinutesSlider.toInt()}m!"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply", color = TextPrimary, fontSize = 12.sp)
                    }
                }

                Slider(
                    value = freeMinutesSlider,
                    onValueChange = { freeMinutesSlider = it },
                    valueRange = 0f..60f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = TextPrimary,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    )
                )
            }

            // 3. TODAY'S INSTAGRAM USAGE METRICS
            BentoCard {
                Text(
                    text = "TODAY'S INSTAGRAM USAGE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Time Used Today", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = prefs.formattedUsedTime,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Remaining Allowance", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${prefs.remainingMinutes}m / ${prefs.totalAllowanceMinutes}m",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = if (prefs.remainingMinutes > 0) NeonEmerald else CrimsonAlert
                        )
                    }
                }
            }

            // 3. GITHUB PROFILE CONFIG
            BentoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = NeonEmerald)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GITHUB USERNAME",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = githubInput,
                    onValueChange = { githubInput = it },
                    placeholder = { Text("e.g. napraveen", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repo.updateGithubUsername(githubInput)
                            savedMessage = "GitHub username saved!"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save GitHub", fontWeight = FontWeight.Bold)
                }
            }

            // 4. LEETCODE PROFILE CONFIG
            BentoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = ElectricAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LEETCODE USERNAME",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = leetcodeInput,
                    onValueChange = { leetcodeInput = it },
                    placeholder = { Text("e.g. your_leetcode_handle", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricAmber,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repo.updateLeetcodeUsername(leetcodeInput)
                            savedMessage = "LeetCode username saved!"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricAmber, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save LeetCode", fontWeight = FontWeight.Bold)
                }
            }

            // 5. ACCESSIBILITY PERMISSION CARD
            BentoCard {
                Text(
                    text = "ACCESSIBILITY PERMISSION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Required to detect when Instagram opens. ProofOfWork does not log keystrokes or collect personal data.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonEmerald),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Accessibility Settings", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
