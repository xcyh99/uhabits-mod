package org.isoron.uhabits.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.isoron.platform.time.LocalDate

class SyncDayKeyTest {
    @Test
    fun convertsCalendarDayToPortableTimestampAndBack() {
        val dates = listOf(
            LocalDate(2000, 1, 1),
            LocalDate(2024, 2, 29),
            LocalDate(2026, 11, 21),
            LocalDate(2038, 1, 19)
        )
        for (date in dates) {
            val key = date.toCSVString()
            val timestamp = SyncDayKey.toTimestamp(key)
            assertEquals(date.unixTime, timestamp)
            assertEquals(key, SyncDayKey.fromTimestamp(timestamp))
        }
    }

    @Test
    fun rejectsMalformedAndImpossibleCalendarDays() {
        assertFailsWith<IllegalArgumentException> { SyncDayKey.toTimestamp("2026/11/21") }
        assertFailsWith<IllegalArgumentException> { SyncDayKey.toTimestamp("2026-02-29") }
        assertFailsWith<IllegalArgumentException> { SyncDayKey.toTimestamp("2026-13-01") }
    }
}
