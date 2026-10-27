package com.proofofwork.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proofofwork.core.model.ContributionDay
import com.proofofwork.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HeatmapGrid(
    title: String,
    streakDays: Int,
    subtitle: String,
    days: List<ContributionDay>,
    activeThemeColor: Color,
    icon: ImageVector = Icons.Default.Code,
    username: String = "",
    modifier: Modifier = Modifier
) {
    val weeksCount = 16
    val weeks = buildCalendarMatrix(days, weeksCount)
    val cellSize = 13.dp
    val cellSpacing = 3.dp

    BentoCard(modifier = modifier) {
        // Top Header: Title & Subtitle on left, Non-wrapping Streak Badge on right
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left: Icon + Title + Subtitle
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(activeThemeColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = activeThemeColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = activeThemeColor
                    )
                    if (username.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x1AFFFFFF)
                        ) {
                            Text(
                                text = "@$username",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: Streak Badge (guaranteed single line, never wrapped)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (streakDays > 0) Color(0x26FFA116) else Color(0x1AFFFFFF),
                border = BorderStroke(
                    1.dp,
                    if (streakDays > 0) ElectricAmber.copy(alpha = 0.45f) else Color(0x1AFFFFFF)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (streakDays > 0) "🔥 ${streakDays}D STREAK" else "0D STREAK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        color = if (streakDays > 0) ElectricAmber else TextMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Heatmap Matrix Container (sleek recessed panel)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0C0F17), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x1F2A334E), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day Labels: Mon, Wed, Fri, Sun aligned to cell height
                Column(
                    verticalArrangement = Arrangement.spacedBy(cellSpacing),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    WeekdayLabel("M", cellSize)
                    WeekdayLabel("", cellSize)
                    WeekdayLabel("W", cellSize)
                    WeekdayLabel("", cellSize)
                    WeekdayLabel("F", cellSize)
                    WeekdayLabel("", cellSize)
                    WeekdayLabel("S", cellSize)
                }

                // Week Columns
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (week in weeks) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(cellSpacing)
                        ) {
                            for (day in week) {
                                GridCell(
                                    level = day.level,
                                    isFuture = day.level == -1,
                                    activeColor = activeThemeColor,
                                    size = cellSize
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Legend and Summary at bottom
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        ) {
            Text(
                text = "Last $weeksCount weeks",
                fontSize = 10.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Less", fontSize = 10.sp, color = TextMuted)
                Spacer(modifier = Modifier.width(5.dp))
                LegendDot(Color(0xFF161B26), Color(0x1FFFFFFF))
                Spacer(modifier = Modifier.width(3.dp))
                LegendDot(activeThemeColor.copy(alpha = 0.30f), activeThemeColor.copy(alpha = 0.40f))
                Spacer(modifier = Modifier.width(3.dp))
                LegendDot(activeThemeColor.copy(alpha = 0.55f), activeThemeColor.copy(alpha = 0.65f))
                Spacer(modifier = Modifier.width(3.dp))
                LegendDot(activeThemeColor.copy(alpha = 0.80f), activeThemeColor.copy(alpha = 0.90f))
                Spacer(modifier = Modifier.width(3.dp))
                LegendDot(activeThemeColor, activeThemeColor)
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = "More", fontSize = 10.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun GridCell(
    level: Int,
    isFuture: Boolean,
    activeColor: Color,
    size: Dp
) {
    if (isFuture) {
        Box(
            modifier = Modifier
                .size(size)
                .background(Color.Transparent)
        )
        return
    }

    val (cellColor, borderColor) = when (level) {
        0 -> Color(0xFF161B26) to Color(0x1FFFFFFF)
        1 -> activeColor.copy(alpha = 0.30f) to activeColor.copy(alpha = 0.40f)
        2 -> activeColor.copy(alpha = 0.55f) to activeColor.copy(alpha = 0.65f)
        3 -> activeColor.copy(alpha = 0.80f) to activeColor.copy(alpha = 0.90f)
        else -> activeColor to activeColor
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(cellColor, RoundedCornerShape(3.dp))
            .border(0.5.dp, borderColor, RoundedCornerShape(3.dp))
    )
}

@Composable
private fun LegendDot(color: Color, borderColor: Color) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .background(color, RoundedCornerShape(2.dp))
            .border(0.5.dp, borderColor, RoundedCornerShape(2.dp))
    )
}

@Composable
private fun WeekdayLabel(text: String, size: Dp) {
    Box(
        modifier = Modifier
            .width(14.dp)
            .height(size),
        contentAlignment = Alignment.Center
    ) {
        if (text.isNotEmpty()) {
            Text(
                text = text,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted.copy(alpha = 0.7f),
                lineHeight = 9.sp
            )
        }
    }
}

/**
 * Builds a clean N-column x 7-row matrix (Monday to Sunday) ending at the current week.
 */
private fun buildCalendarMatrix(days: List<ContributionDay>, numWeeks: Int = 16): List<List<ContributionDay>> {
    val dayMap = days.associateBy { it.date }
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val todayCal = Calendar.getInstance()
    val todayMs = todayCal.timeInMillis

    // Find end of current week (Sunday)
    val endOfWeekCal = Calendar.getInstance().apply {
        val dayOfWeek = get(Calendar.DAY_OF_WEEK)
        val daysUntilSunday = if (dayOfWeek == Calendar.SUNDAY) 0 else (8 - dayOfWeek)
        add(Calendar.DAY_OF_YEAR, daysUntilSunday)
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
    }

    val totalDays = numWeeks * 7
    // Go back totalDays from end of current week
    val startCal = (endOfWeekCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -(totalDays - 1))
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
    }

    val weeks = mutableListOf<List<ContributionDay>>()
    var currentWeek = mutableListOf<ContributionDay>()

    val iterateCal = startCal.clone() as Calendar
    for (i in 0 until totalDays) {
        val dateStr = sdf.format(iterateCal.time)
        val isFuture = iterateCal.timeInMillis > todayMs

        val level = if (isFuture) -1 else (dayMap[dateStr]?.level ?: 0)
        val count = dayMap[dateStr]?.count ?: 0

        currentWeek.add(ContributionDay(date = dateStr, level = level, count = count))

        if (currentWeek.size == 7) {
            weeks.add(currentWeek)
            currentWeek = mutableListOf()
        }

        iterateCal.add(Calendar.DAY_OF_YEAR, 1)
    }

    if (currentWeek.isNotEmpty()) {
        weeks.add(currentWeek)
    }

    return weeks
}
