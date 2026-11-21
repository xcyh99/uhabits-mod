package org.isoron.uhabits.core.sync

import org.isoron.platform.time.LocalDate

/**
 * Portable calendar-day conversion for sync.
 *
 * Habit repetitions are stored at LocalDate.unixTime, which is a UTC-based
 * day boundary in the app's calendar model. Never apply the device timezone
 * to this value when moving a repetition between databases.
 */
object SyncDayKey {
    fun fromTimestamp(timestamp: Long): String =
        LocalDate.fromUnixTime(timestamp).toCSVString()

    fun toTimestamp(dayKey: String): Long {
        require(Regex("""\\d{4}-\\d{2}-\\d{2}""").matches(dayKey)) {
            "Invalid sync day key: $dayKey"
        }
        val year = dayKey.substring(0, 4).toInt()
        val month = dayKey.substring(5, 7).toInt()
        val day = dayKey.substring(8, 10).toInt()
        val date = LocalDate(year, month, day)
        require(date.toCSVString() == dayKey) {
            "Invalid calendar date in sync day key: $dayKey"
        }
        return date.unixTime
    }
}
