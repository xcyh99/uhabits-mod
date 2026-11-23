package org.isoron.uhabits.activities.timerecords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DateSpanCalculatorTest {
    @Test
    fun calculatesLeapDaySpanAndPartialHours() {
        val result = DateSpanCalculator.calculate(
            LocalDateTime.of(2024, 2, 28, 10, 0),
            LocalDateTime.of(2024, 3, 1, 11, 0)
        )

        assertTrue(result.isForward)
        assertEquals(2L, result.calendarPeriod.days.toLong())
        assertEquals(2L, result.totalDays)
        assertEquals(49L, result.totalHours)
        assertEquals(2940L, result.totalMinutes)
        assertEquals(0L, result.totalWeeks)
        assertEquals(2L, result.remainingDays)
    }

    @Test
    fun reverseRangeKeepsPositiveSpanAndMarksDirection() {
        val start = LocalDateTime.of(2025, 1, 1, 8, 30)
        val end = LocalDateTime.of(2025, 1, 10, 7, 15)

        val forward = DateSpanCalculator.calculate(start, end)
        val reverse = DateSpanCalculator.calculate(end, start)

        assertTrue(forward.isForward)
        assertFalse(reverse.isForward)
        assertEquals(forward.totalMinutes, reverse.totalMinutes)
        assertEquals(forward.totalDays, reverse.totalDays)
        assertEquals(forward.calendarPeriod, reverse.calendarPeriod)
        assertEquals(8L, forward.totalDays)
        assertEquals(1L, forward.totalWeeks)
        assertEquals(1L, forward.remainingDays)
    }

    @Test
    fun exactMonthAndYearBoundariesUseCompletedUnits() {
        val result = DateSpanCalculator.calculate(
            LocalDateTime.of(2024, 1, 1, 0, 0),
            LocalDateTime.of(2025, 1, 1, 0, 0)
        )

        assertEquals(1L, result.totalYears)
        assertEquals(12L, result.totalMonths)
        assertEquals(366L, result.totalDays)
        assertEquals(52L, result.totalWeeks)
        assertEquals(2L, result.remainingDays)
    }

    @Test
    fun sameInstantProducesZeroSpan() {
        val instant = LocalDateTime.of(2026, 11, 23, 12, 0)

        val result = DateSpanCalculator.calculate(instant, instant)

        assertTrue(result.isForward)
        assertEquals(0L, result.totalMinutes)
        assertEquals(0L, result.totalDays)
        assertEquals(0, result.calendarPeriod.days)
    }
}
