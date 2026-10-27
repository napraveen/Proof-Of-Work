package com.proofofwork.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proofofwork.ui.theme.*

@Composable
fun DualRadialProgress(
    isGithubComplete: Boolean,
    isLeetcodeComplete: Boolean,
    isVoiceLoggedToday: Boolean = false,
    isWeekendFreedomActive: Boolean = false,
    remainingMinutes: Int,
    formattedUsedTime: String = "",
    modifier: Modifier = Modifier
) {
    // Determine target percentage for the sweep
    val targetSweep = when {
        isWeekendFreedomActive -> 360f
        remainingMinutes >= 60 -> 360f
        remainingMinutes > 0 -> (remainingMinutes / 60f) * 360f
        else -> 15f
    }

    val animatedSweep by animateFloatAsState(
        targetValue = targetSweep,
        animationSpec = tween(durationMillis = 900),
        label = "walletSweep"
    )

    val activeColor = when {
        isWeekendFreedomActive -> NeonEmerald
        remainingMinutes > 0 -> NeonEmerald
        else -> CrimsonAlert
    }

    val gradientColors = when {
        isWeekendFreedomActive -> listOf(NeonEmerald, CyanPulse, WisprViolet, NeonEmerald)
        remainingMinutes > 0 -> listOf(CyanPulse, NeonEmerald, Color(0xFF00E5FF))
        else -> listOf(CrimsonAlert.copy(alpha = 0.5f), CrimsonAlert)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(190.dp)
            .padding(4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            val strokeWidth = 11.dp.toPx()
            val canvasSize = size.width

            // 1. Background Inactive Ring
            drawCircle(
                color = Color(0xFF131722),
                radius = (canvasSize - strokeWidth) / 2f,
                style = Stroke(width = strokeWidth)
            )

            // Outer subtle boundary ring
            drawCircle(
                color = Color(0x1AFFFFFF),
                radius = canvasSize / 2f,
                style = Stroke(width = 0.8.dp.toPx())
            )

            // 2. Active Animated Gradient Arc
            val arcSize = Size(canvasSize - strokeWidth, canvasSize - strokeWidth)
            val arcOffset = Offset(strokeWidth / 2f, strokeWidth / 2f)

            drawArc(
                brush = Brush.sweepGradient(gradientColors),
                startAngle = -90f,
                sweepAngle = animatedSweep,
                useCenter = false,
                topLeft = arcOffset,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Center typography and status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            if (isWeekendFreedomActive) {
                Text(
                    text = "FREE",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = NeonEmerald,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0x2600F5A0)
                ) {
                    Text(
                        text = "WEEKEND PASS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = NeonEmerald,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$remainingMinutes",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = if (remainingMinutes > 0) TextPrimary else CrimsonAlert,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "MIN",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (remainingMinutes > 0) Color(0x2600F5A0) else Color(0x26FF4757)
                ) {
                    Text(
                        text = if (remainingMinutes > 0) "AVAILABLE" else "LOCKED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        color = if (remainingMinutes > 0) NeonEmerald else CrimsonAlert,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (formattedUsedTime.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$formattedUsedTime used",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
