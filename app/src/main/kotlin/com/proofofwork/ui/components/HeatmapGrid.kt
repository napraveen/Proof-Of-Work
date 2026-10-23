package com.proofofwork.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
) {
    val weeks = buildCalendarMatrix(days)

    BentoCard(modifier = modifier) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = title.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            // Streak Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        if (streakDays > 0) Color(0x26FFA116) else Color(0x1AFFFFFF),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (streakDays > 0) "🔥 ${streakDays}D STREAK" else "0D STREAK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = if (streakDays > 0) ElectricAmber else TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Heatmap Matrix: Weekday labels on left + 10 columns of weeks
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day Labels: Mon, Wed, Fri
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                WeekdayLabel("M")
                WeekdayLabel("")
                WeekdayLabel("W")
                WeekdayLabel("")
                WeekdayLabel("F")
                WeekdayLabel("")
                WeekdayLabel("S")
            }

            // 10 Weeks Columns
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (week in weeks) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (day in week) {
                            GridCell(
                                level = day.level,
                                isFuture = day.level == -1,
                                activeColor = activeThemeColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Legend at bottom
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(end = 4.dp)
        ) {
            Text(text = "Less", fontSize = 10.sp, color = TextMuted)
            Spacer(modifier = Modifier.width(6.dp))
            LegendDot(Color(0xFF141824))
            Spacer(modifier = Modifier.width(3.dp))
            LegendDot(activeThemeColor.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.width(3.dp))
            LegendDot(activeThemeColor.copy(alpha = 0.65f))
            Spacer(modifier = Modifier.width(3.dp))
            LegendDot(activeThemeColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "More", fontSize = 10.sp, color = TextMuted)
        }
    }
}

@Composable
private fun GridCell(
    level: Int,
    isFuture: Boolean,
    activeColor: Color
) {
    if (isFuture) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .background(Color.Transparent)
        )
        return
    }

    val cellColor = when (level) {
        0 -> Color(0xFF131722)
        1 -> activeColor.copy(alpha = 0.35f)
        2 -> activeColor.copy(alpha = 0.6f)
        3 -> activeColor.copy(alpha = 0.85f)
        else -> activeColor
    }

    Box(
        modifier = Modifier
            .size(13.dp)
            .background(cellColor, RoundedCornerShape(3.dp))
            .border(0.5.dp, if (level > 0) activeColor.copy(alpha = 0.4f) else Color(0x1AFFFFFF), RoundedCornerShape(3.dp))
    )
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .background(color, RoundedCornerShape(2.dp))
    )
}

@Composable
private fun WeekdayLabel(text: String) {
    Box(
        modifier = Modifier.size(width = 12.dp, height = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )
    }
}

/**
 * Builds a clean 10-column x 7-row matrix (Monday to Sunday) ending at the current week.
 */
private fun buildCalendarMatrix(days: List<ContributionDay>): List<List<ContributionDay>> {
    val dayMap = days.associateBy { it.date }
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val todayCal = Calendar.getInstance()
    val todayMs = todayCal.timeInMillis

    // Find end of current week (Sunday)
    val endOfWeekCal = Calendar.getInstance().apply {
        // In Java Calendar, Sunday is 1, Monday is 2, Saturday is 7
        val dayOfWeek = get(Calendar.DAY_OF_WEEK)
        val daysUntilSunday = if (dayOfWeek == Calendar.SUNDAY) 0 else (8 - dayOfWeek)
        add(Calendar.DAY_OF_YEAR, daysUntilSunday)
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
    }

    // Go back 10 weeks (70 days) from end of current week
    val startCal = (endOfWeekCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -69) // 70 days total = 10 weeks
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
    }

    val weeks = mutableListOf<List<ContributionDay>>()
    var currentWeek = mutableListOf<ContributionDay>()

    val iterateCal = startCal.clone() as Calendar
    for (i in 0 until 70) {
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
