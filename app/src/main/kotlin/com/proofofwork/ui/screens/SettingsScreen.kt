package com.proofofwork.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proofofwork.ProofOfWorkApp
import com.proofofwork.core.data.AppUserPreferences
import com.proofofwork.ui.components.BentoCard
import com.proofofwork.ui.components.WeekendFreedomSettingsCard
import com.proofofwork.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val repo = ProofOfWorkApp.instance.preferencesRepository
    val prefs by repo.userPreferencesFlow.collectAsState(initial = AppUserPreferences())

    SettingsContent(
        prefs = prefs,
        onSaveVoiceCategoryRewards = { coding, study, fitness, deepWork ->
            coroutineScope.launch {
                repo.updateTaskCategoryRewards(coding, study, fitness, deepWork)
            }
        },
        onSaveRewards = { gh, lc ->
            coroutineScope.launch {
                repo.updateGithubRewardMinutes(gh)
                repo.updateLeetcodeRewardMinutes(lc)
            }
        },
        onSaveBaseFree = { freeMins ->
            coroutineScope.launch {
                repo.updateBaseFreeMinutes(freeMins)
            }
        },
        onSaveGeminiKey = { key ->
            coroutineScope.launch {
                repo.updateGeminiApiKey(key)
            }
        },
        onSaveGithub = { ghUser ->
            coroutineScope.launch {
                repo.updateGithubUsername(ghUser)
            }
        },
        onSaveLeetcode = { lcUser ->
            coroutineScope.launch {
                repo.updateLeetcodeUsername(lcUser)
            }
        },
        onToggleWeekendFreedom = { enabled ->
            coroutineScope.launch {
                repo.updateWeekendFreedomEnabled(enabled)
            }
        },
        onToggleDemo5DayStreak = { simulate ->
            coroutineScope.launch {
                repo.updateDemoSimulate5DayStreak(simulate)
            }
        },
        onToggleWeekdayIndex = { dayIdx ->
            coroutineScope.launch {
                repo.toggleWeekdayCompleted(dayIdx)
            }
        },
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    prefs: AppUserPreferences,
    onSaveVoiceCategoryRewards: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onSaveRewards: (Int, Int) -> Unit,
    onSaveBaseFree: (Int) -> Unit,
    onSaveGeminiKey: (String) -> Unit = {},
    onSaveGithub: (String) -> Unit,
    onSaveLeetcode: (String) -> Unit,
    onToggleWeekendFreedom: (Boolean) -> Unit = {},
    onToggleDemo5DayStreak: (Boolean) -> Unit = {},
    onToggleWeekdayIndex: (Int) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var githubInput by remember(prefs.githubUsername) { mutableStateOf(prefs.githubUsername) }
    var leetcodeInput by remember(prefs.leetcodeUsername) { mutableStateOf(prefs.leetcodeUsername) }
    var geminiKeyInput by remember(prefs.geminiApiKey) { mutableStateOf(prefs.geminiApiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    var codingTaskSlider by remember(prefs.codingTaskMinutes) { mutableFloatStateOf(prefs.codingTaskMinutes.toFloat()) }
    var studyTaskSlider by remember(prefs.studyTaskMinutes) { mutableFloatStateOf(prefs.studyTaskMinutes.toFloat()) }
    var fitnessTaskSlider by remember(prefs.fitnessTaskMinutes) { mutableFloatStateOf(prefs.fitnessTaskMinutes.toFloat()) }
    var deepWorkSlider by remember(prefs.deepWorkTaskMinutes) { mutableFloatStateOf(prefs.deepWorkTaskMinutes.toFloat()) }

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
                        text = "SETTINGS & AI CONFIG",
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

            // 1. WEEKEND FREEDOM PASS CONFIGURATION
            WeekendFreedomSettingsCard(
                prefs = prefs,
                onToggleWeekendFreedom = onToggleWeekendFreedom,
                onToggleDemo5DayStreak = onToggleDemo5DayStreak
            )

            // 2. VOICE TASK → INSTAGRAM MINUTES MAPPING
            BentoCard(
                borderColor = WisprViolet.copy(alpha = 0.35f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = WisprViolet, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VOICE TASK → INSTAGRAM MINUTES MAPPING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = WisprViolet
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Set base Instagram minutes unlocked when you complete and record each type of work.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                CategoryRewardSlider(
                    title = "💻 Coding & Debugging",
                    value = codingTaskSlider,
                    onValueChange = { codingTaskSlider = it },
                    accentColor = WisprViolet
                )
                CategoryRewardSlider(
                    title = "📚 Study & Learning",
                    value = studyTaskSlider,
                    onValueChange = { studyTaskSlider = it },
                    accentColor = CyanPulse
                )
                CategoryRewardSlider(
                    title = "🏋️ Fitness & Workout",
                    value = fitnessTaskSlider,
                    onValueChange = { fitnessTaskSlider = it },
                    accentColor = NeonEmerald
                )
                CategoryRewardSlider(
                    title = "⚡ General Deep Work",
                    value = deepWorkSlider,
                    onValueChange = { deepWorkSlider = it },
                    accentColor = ElectricAmber
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onSaveVoiceCategoryRewards(
                            codingTaskSlider.toInt(),
                            studyTaskSlider.toInt(),
                            fitnessTaskSlider.toInt(),
                            deepWorkSlider.toInt()
                        )
                        savedMessage = "Saved Voice Task-to-Minutes mapping!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WisprViolet, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Voice Mapping", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // 3. LLM VERIFIER CONFIG (GOOGLE GEMINI + BUILT-IN SEMANTIC ENGINE)
            BentoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = WisprViolet)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LLM PROOF-OF-WORK VERIFIER (GEMINI AI)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (prefs.geminiApiKey.isNotBlank()) {
                        "✓ Live Gemini 2.0 Flash API active + Built-in Semantic AI fallback."
                    } else {
                        "✓ Built-in Semantic AI Engine is active out-of-the-box! Optionally add a Gemini API key for cloud LLM verification."
                    },
                    fontSize = 12.sp,
                    color = if (prefs.geminiApiKey.isNotBlank()) NeonEmerald else TextSecondary,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = geminiKeyInput,
                    onValueChange = { geminiKeyInput = it },
                    placeholder = { Text("Optional: Paste Gemini API Key (AIza...)", color = TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility",
                                tint = TextMuted
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WisprViolet,
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
                        onSaveGeminiKey(geminiKeyInput)
                        savedMessage = if (geminiKeyInput.isBlank()) "Using Built-in Semantic AI Engine!" else "Gemini LLM API Key saved!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WisprViolet, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save LLM Key", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // 4. GITHUB & LEETCODE REWARD POINTS CONFIGURATION
            BentoCard {
                Text(
                    text = "GITHUB & LEETCODE REWARDS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose how many minutes of Instagram time you earn for automated coding checks.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                CategoryRewardSlider(
                    title = "GitHub Push Reward",
                    value = githubRewardSlider,
                    onValueChange = { githubRewardSlider = it },
                    accentColor = NeonEmerald,
                    maxRange = 120f
                )

                CategoryRewardSlider(
                    title = "LeetCode Solve Reward",
                    value = leetcodeRewardSlider,
                    onValueChange = { leetcodeRewardSlider = it },
                    accentColor = ElectricAmber,
                    maxRange = 120f
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onSaveRewards(githubRewardSlider.toInt(), leetcodeRewardSlider.toInt())
                        savedMessage = "Updated rewards: +${githubRewardSlider.toInt()}m for GitHub, +${leetcodeRewardSlider.toInt()}m for LeetCode!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Rewards", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // 5. FREE DAILY ALLOWANCE SETTING
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
                            onSaveBaseFree(freeMinutesSlider.toInt())
                            savedMessage = "Daily free allowance set to ${freeMinutesSlider.toInt()}m!"
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

            // 6. GITHUB PROFILE CONFIG
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
                    placeholder = { Text("e.g. your_username", color = TextMuted) },
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
                        onSaveGithub(githubInput)
                        savedMessage = "GitHub username saved!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save GitHub", fontWeight = FontWeight.Bold)
                }
            }

            // 7. LEETCODE PROFILE CONFIG
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
                        onSaveLeetcode(leetcodeInput)
                        savedMessage = "LeetCode username saved!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricAmber, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save LeetCode", fontWeight = FontWeight.Bold)
                }
            }

            // 8. ACCESSIBILITY PERMISSION CARD
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

@Composable
private fun CategoryRewardSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    accentColor: Color,
    maxRange: Float = 60f
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(
                text = "+${value.toInt()}m",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = accentColor
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 5f..maxRange,
            steps = ((maxRange - 5f) / 5f).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color(0x33FFFFFF)
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090B)
@Composable
fun SettingsScreenPreview() {
    ProofOfWorkTheme {
        SettingsContent(
            prefs = AppUserPreferences(
                githubUsername = "developer",
                leetcodeUsername = "developer",
                baseFreeMinutes = 5,
                githubRewardMinutes = 25,
                leetcodeRewardMinutes = 35,
                usedSecondsToday = 900
            ),
            onSaveRewards = { _, _ -> },
            onSaveBaseFree = {},
            onSaveGithub = {},
            onSaveLeetcode = {}
        )
    }
}
