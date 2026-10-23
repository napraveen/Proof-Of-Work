package com.proofofwork.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proofofwork.ui.theme.*

@Composable
fun DualRadialProgress(
    isGithubComplete: Boolean,
    isLeetcodeComplete: Boolean,
    remainingMinutes: Int,
    modifier: Modifier = Modifier
) {
    val animatedGithubSweep by animateFloatAsState(
        targetValue = if (isGithubComplete) 280f else 25f,
        animationSpec = tween(durationMillis = 1000),
        label = "githubSweep"
    )

    val animatedLeetcodeSweep by animateFloatAsState(
        targetValue = if (isLeetcodeComplete) 280f else 25f,
        animationSpec = tween(durationMillis = 1000),
        label = "leetcodeSweep"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(220.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val strokeWidth = 14.dp.toPx()
            val innerStrokeWidth = 10.dp.toPx()
            val startAngle = 130f

            // Outer Arc Background (GitHub)
            drawArc(
                color = Color(0x1A00F5A0),
                startAngle = startAngle,
                sweepAngle = 280f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Outer Arc Active (GitHub)
            drawArc(
                color = if (isGithubComplete) NeonEmerald else NeonEmerald.copy(alpha = 0.4f),
                startAngle = startAngle,
                sweepAngle = animatedGithubSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Inner Arc Background (LeetCode)
            val inset = strokeWidth + 8.dp.toPx()
            val innerSize = Size(size.width - inset * 2, size.height - inset * 2)
            val innerOffset = Offset(inset, inset)

            drawArc(
                color = Color(0x1AFFA116),
                startAngle = startAngle,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = innerOffset,
                size = innerSize,
                style = Stroke(width = innerStrokeWidth, cap = StrokeCap.Round)
            )

            // Inner Arc Active (LeetCode)
            drawArc(
                color = if (isLeetcodeComplete) ElectricAmber else ElectricAmber.copy(alpha = 0.4f),
                startAngle = startAngle,
                sweepAngle = animatedLeetcodeSweep,
                useCenter = false,
                topLeft = innerOffset,
                size = innerSize,
                style = Stroke(width = innerStrokeWidth, cap = StrokeCap.Round)
            )
        }

        // Center typography
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${remainingMinutes}m",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = if (remainingMinutes > 0) TextPrimary else CrimsonAlert
            )
            Text(
                text = if (remainingMinutes > 0) "AVAILABLE" else "LOCKED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = if (remainingMinutes > 0) NeonEmerald else CrimsonAlert
            )
        }
    }
}
