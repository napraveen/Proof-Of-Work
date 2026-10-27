package com.proofofwork.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proofofwork.core.data.AppUserPreferences
import com.proofofwork.ui.theme.*

/**
 * 1. STREAKS PAGE COMPONENT: Streak Progress Journey
 * Focused strictly on consistency, visual progress, celebrating active streak,
 * and showing the status of the Weekend Freedom Pass reward.
 */
@Composable
fun StreakProgressJourneyCard(
    prefs: AppUserPreferences,
    onToggleWeekdayIndex: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val completedDays = prefs.effectiveCompletedWeekdays
    val isAll5Done = prefs.isAll5WeekdaysCompleted
    val isFreedomActive = prefs.isWeekendFreedomActive
    val progress = (completedDays.size.coerceIn(0, 5) / 5f)
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "weekdayProgress")

    BentoCard(
        modifier = modifier,
        containerColor = if (isFreedomActive) Color(0xFF0E2219) else ObsidianCard,
        borderColor = if (isFreedomActive) NeonEmerald.copy(alpha = 0.5f) else ObsidianCardBorder
    ) {
        // Header Row: Title on Left, Status Pill on Right
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
                        .size(26.dp)
                        .background(
                            if (isFreedomActive) NeonEmerald.copy(alpha = 0.15f) else Color(0x26FFA116),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFreedomActive) Icons.Default.Celebration else Icons.Default.Weekend,
                        contentDescription = null,
                        tint = if (isFreedomActive) NeonEmerald else ElectricAmber,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "5-DAY STREAK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = if (isFreedomActive) NeonEmerald else TextPrimary,
                    maxLines = 1
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isAll5Done) NeonEmerald.copy(alpha = 0.2f) else Color(0x1FFFFFFF),
                border = BorderStroke(
                    1.dp,
                    if (isAll5Done) NeonEmerald.copy(alpha = 0.5f) else Color(0x1AFFFFFF)
                )
            ) {
                Text(
                    text = if (isAll5Done) "🎉 PASS UNLOCKED" else "${completedDays.size}/5 DAYS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = if (isAll5Done) NeonEmerald else TextMuted,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Motivational status text
        Text(
            text = if (isFreedomActive) {
                "🎉 You achieved 5/5 weekdays! Weekend Freedom Pass is ACTIVE — unrestricted Saturday & Sunday scrolling."
            } else if (isAll5Done) {
                "5/5 weekdays completed! Pass ready for the upcoming weekend."
            } else {
                "Complete ${5 - completedDays.size} more weekday(s) of proof-of-work to earn full unrestricted weekend access."
            },
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Progress Bar toward the weekend (no stop indicator dot)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF141824))
        ) {
            if (animatedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = animatedProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isAll5Done) NeonEmerald else CyanPulse)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5 Interactive Weekday Circles (Mon to Fri)
        val dayLabels = listOf(
            1 to "MON",
            2 to "TUE",
            3 to "WED",
            4 to "THU",
            5 to "FRI"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dayLabels.forEach { (dayIdx, label) ->
                val isDone = completedDays.contains(dayIdx)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = onToggleWeekdayIndex != null) {
                            onToggleWeekdayIndex?.invoke(dayIdx)
                        }
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDone) NeonEmerald else Color(0xFF121622)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isDone) NeonEmerald else Color(0x1F2A334E),
                                shape = CircleShape
                            )
                    ) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "$label Done",
                                tint = Color.Black,
                                modifier = Modifier.size(19.dp)
                            )
                        } else {
                            Text(
                                text = "${dayIdx}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isDone) FontWeight.Black else FontWeight.Bold,
                        color = if (isDone) NeonEmerald else TextMuted
                    )
                }
            }
        }
    }
}

/**
 * 2. SETTINGS PAGE COMPONENT: Weekend Freedom Preferences
 * Focused strictly on configuration, toggle controls, and demo simulation.
 * No duplicate visual circles or redundant status clutter.
 */
@Composable
fun WeekendFreedomSettingsCard(
    prefs: AppUserPreferences,
    onToggleWeekendFreedom: (Boolean) -> Unit,
    onToggleDemo5DayStreak: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier,
        borderColor = NeonEmerald.copy(alpha = 0.35f)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(0x2600F5A0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = NeonEmerald,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "WEEKEND FREEDOM PASS CONFIG",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = NeonEmerald
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Configure how screen time behaves on Saturday & Sunday when your 5-day weekday streak is met.",
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Preference 1: Enable/Disable Weekend Freedom Pass
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = "Weekend Freedom Pass",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (prefs.weekendFreedomEnabled) {
                        "Unrestricted Saturday & Sunday access active if 5-day streak is met"
                    } else {
                        "Disabled: Strict daily limits apply 7 days a week"
                    },
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 14.sp
                )
            }

            Switch(
                checked = prefs.weekendFreedomEnabled,
                onCheckedChange = onToggleWeekendFreedom,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = NeonEmerald,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF1E2230)
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preference 2: Demo / Hackathon Judge Simulator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = "Demo Simulation Mode",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Instantly simulates 5/5 weekdays completed to showcase weekend unrestricted unlocking",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 14.sp
                )
            }

            Switch(
                checked = prefs.demoSimulate5DayStreak,
                onCheckedChange = onToggleDemo5DayStreak,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = ElectricAmber,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF1E2230)
                )
            )
        }
    }
}

/**
 * Backward compatibility alias
 */
@Composable
fun WeekdayStreakCard(
    prefs: AppUserPreferences,
    onToggleWeekendFreedom: (Boolean) -> Unit,
    onToggleDemo5DayStreak: (Boolean) -> Unit,
    onToggleWeekdayIndex: ((Int) -> Unit)? = null
) {
    StreakProgressJourneyCard(
        prefs = prefs,
        onToggleWeekdayIndex = onToggleWeekdayIndex
    )
}
