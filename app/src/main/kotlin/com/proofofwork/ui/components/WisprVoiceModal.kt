package com.proofofwork.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.proofofwork.core.network.AiWorkVerifierService
import com.proofofwork.core.network.VoiceVerificationResult
import com.proofofwork.ui.theme.*
import java.util.Locale

@Composable
fun WisprVoiceDialog(
    isConfessionMode: Boolean = false,
    isAnalyzing: Boolean = false,
    verificationResult: VoiceVerificationResult? = null,
    onAnalyzeTranscript: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var transcript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var voiceStatusHint by remember {
        mutableStateOf(
            if (isConfessionMode) "Tap Mic or speak confession"
            else "Tap Mic to speak or dictate proof of work"
        )
    }

    val focusRequester = remember { FocusRequester() }
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    val fallbackSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                transcript = if (transcript.isBlank()) spoken else "$transcript $spoken"
                voiceStatusHint = "Speech recorded! Ready to verify."
            }
        }
    }

    fun startSpeechCapture() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (isConfessionMode) "Speak your accountability confession..."
                else "Hey Productivity! Tell us what task you just completed..."
            )
        }

        if (speechRecognizer != null) {
            try {
                isListening = true
                voiceStatusHint = "Listening... Speak naturally (filler words auto-removed)"
                speechRecognizer.startListening(intent)
            } catch (_: Exception) {
                try {
                    fallbackSpeechLauncher.launch(intent)
                } catch (_: Exception) {
                    isListening = false
                    voiceStatusHint = "Tap mic above or type your proof of work!"
                }
            }
        } else {
            try {
                fallbackSpeechLauncher.launch(intent)
            } catch (_: Exception) {
                isListening = false
                voiceStatusHint = "Tap mic above or type your proof of work!"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechCapture()
        } else {
            voiceStatusHint = "Mic permission skipped — type or use keyboard dictation below!"
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                voiceStatusHint = "Listening... Speak what you accomplished!"
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                voiceStatusHint = "Processing speech..."
            }

            override fun onError(error: Int) {
                isListening = false
                voiceStatusHint = "Speak your proof of work or tap Mic again."
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    val cleaned = AiWorkVerifierService.cleanSpokenTranscript(spoken)
                    transcript = if (transcript.isBlank()) cleaned else "$transcript $cleaned"
                    voiceStatusHint = "Cleaned and formatted your speech!"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    voiceStatusHint = "\"$partial...\""
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    val wordCount = remember(transcript) { AiWorkVerifierService.countWords(transcript) }
    val accentColor = if (isConfessionMode) ElectricAmber else WisprViolet

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(ObsidianSurface)
                .border(1.2.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row with weight
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(accentColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = if (isConfessionMode) "VOICE CONFESSION GUARD" else "HEY PRODUCTIVITY • VOICE PROOF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isConfessionMode) "Speak a 5-Min Guilty Break Confession"
                    else "What did you just accomplish?",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isConfessionMode) {
                        "Admit out loud why you're pausing work and promise to return in 5 minutes."
                    } else {
                        "Speak naturally. Our AI Judge verifies your work & mints Instagram minutes."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Pulsing Microphone Orb
                PulsingMicOrb(
                    isListening = isListening,
                    accentColor = accentColor,
                    onClick = {
                        if (isListening) {
                            try {
                                speechRecognizer?.stopListening()
                            } catch (_: Exception) {
                            }
                            isListening = false
                        } else {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                startSpeechCapture()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = voiceStatusHint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isListening) NeonEmerald else TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Voice Dictation Text Field
                OutlinedTextField(
                    value = transcript,
                    onValueChange = { transcript = it },
                    placeholder = {
                        Text(
                            text = if (isConfessionMode) {
                                "e.g., \"I am taking a 5 minute mental break right now and promise to get right back to my coding task.\""
                            } else {
                                "Speak or tap Mic: e.g., \"I just spent 45 minutes refactoring the authentication API and fixed two race condition bugs...\""
                            },
                            fontSize = 12.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )
                    },
                    minLines = 3,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = ObsidianCard,
                        unfocusedContainerColor = ObsidianCard
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Word count & Auto-Clean button bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "$wordCount words dictated",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }

                    if (transcript.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "✨ AI Clean",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1A00F5A0))
                                    .clickable {
                                        transcript = AiWorkVerifierService.cleanSpokenTranscript(transcript)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Text(
                                text = "Clear",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { transcript = "" }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Voice Presets
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isConfessionMode) "SAMPLE CONFESSION TEMPLATE:" else "QUICK VOICE TEMPLATES (TAP TO TEST):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val samples = if (isConfessionMode) {
                            listOf(
                                "😅 5m Focus Break" to "I am taking a 5 minute scroll break right now because I need a quick breather, and I promise to return to my deep work task immediately."
                            )
                        } else {
                            listOf(
                                "💻 45m Coding & PR" to "I just spent 45 minutes implementing the voice verification service in Kotlin and pushed two commits to the repository.",
                                "📚 1h OS Study" to "I finished studying Operating Systems virtual memory paging for 1 hour and summarized 4 pages of lecture notes.",
                                "🏋️ 30m Workout" to "Completed a 30 minute high intensity workout and stretching session to recharge my focus.",
                                "🚫 Anti-Cheat Test" to "I did nothing and watched Netflix on the couch."
                            )
                        }

                        samples.forEach { (label, text) ->
                            SuggestionChip(
                                onClick = { transcript = text },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = ObsidianCard
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = ObsidianCardBorder
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Verification Result Card
                AnimatedVisibility(visible = verificationResult != null) {
                    verificationResult?.let { res ->
                        val cardColor = if (res.isVerified) Color(0xFF10241B) else Color(0xFF2B1218)
                        val borderCol = if (res.isVerified) NeonEmerald else CrimsonAlert
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardColor)
                                .border(1.dp, borderCol.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (res.isVerified) Icons.Default.Verified else Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = borderCol,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (res.isVerified) "VERIFIED • ${res.category}" else "REJECTED BY AI JUDGE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = borderCol,
                                        letterSpacing = 0.6.sp
                                    )
                                }
                                if (res.isVerified) {
                                    Box(
                                        modifier = Modifier
                                            .background(NeonEmerald, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "+${res.rewardMinutes}m INSTA",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = res.summary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = res.aiFeedback,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Engine: ${res.engineUsed} • Confidence: ${res.confidenceScore}%",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                // Primary Submit Button
                Button(
                    onClick = { onAnalyzeTranscript(transcript, isConfessionMode) },
                    enabled = transcript.isNotBlank() && !isAnalyzing,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConfessionMode) ElectricAmber else NeonEmerald,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0x1FFFFFFF),
                        disabledContentColor = TextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI ANALYZING WORK...",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConfessionMode) "LOG CONFESSION & UNLOCK 5M"
                            else "VERIFY VOICE & MINT TIME",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingMicOrb(
    isListening: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 550 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(86.dp)
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(pulseScale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isListening) NeonEmerald.copy(alpha = 0.45f) else accentColor.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Core button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = if (isListening) {
                            listOf(NeonEmerald, CyanPulse)
                        } else {
                            listOf(accentColor, WisprVioletDark)
                        }
                    )
                )
                .clickable(onClick = onClick)
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                contentDescription = "Speak proof of work",
                tint = if (isListening) Color.Black else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
