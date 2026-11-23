package org.isoron.uhabits.activities.timerecords

import java.time.Duration
import java.time.LocalDateTime
import java.time.Period
import java.time.temporal.ChronoUnit

data class DateSpanCalculation(
    val isForward: Boolean,
    val calendarPeriod: Period,
    val totalYears: Long,
    val totalMonths: Long,
    val totalWeeks: Long,
    val remainingDays: Long,
    val totalDays: Long,
    val totalHours: Long,
    val totalMinutes: Long
)

object DateSpanCalculator {
    fun calculate(start: LocalDateTime, end: LocalDateTime): DateSpanCalculation {
        val earlier = minOf(start, end)
        val later = maxOf(start, end)
        val duration = Duration.between(earlier, later)
        val totalDays = duration.toDays()
        return DateSpanCalculation(
            isForward = start <= end,
            calendarPeriod = Period.between(earlier.toLocalDate(), later.toLocalDate()),
            totalYears = ChronoUnit.YEARS.between(earlier, later),
            totalMonths = ChronoUnit.MONTHS.between(earlier, later),
            totalWeeks = totalDays / 7,
            remainingDays = totalDays % 7,
            totalDays = totalDays,
            totalHours = duration.toHours(),
            totalMinutes = duration.toMinutes()
        )
    }
}
