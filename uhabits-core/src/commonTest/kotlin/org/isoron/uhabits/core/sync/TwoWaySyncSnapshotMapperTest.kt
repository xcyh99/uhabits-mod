package org.isoron.uhabits.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.database.EntryData
import org.isoron.uhabits.core.database.HabitData
import org.isoron.uhabits.core.models.Entry

class TwoWaySyncSnapshotMapperTest {
    @Test
    fun mapsLocalDatabaseIdsToPortableHabitUuidsAndIsoDates() {
        val habit = HabitData(id = 41, name = "Walk", uuid = "shared-uuid")
        val date = LocalDate(2026, 11, 21)
        val entries = TwoWaySyncSnapshotMapper.entries(
            listOf(habit),
            listOf(
                EntryData(habitId = 41, timestamp = date.unixTime, value = Entry.YES_MANUAL, notes = "done")
            )
        )
        assertEquals(
            listOf(SyncEntryIdentity("shared-uuid", "2026-11-21", Entry.YES_MANUAL, "done")),
            entries
        )
    }

    @Test
    fun omitsUnknownPlaceholderEntries() {
        val habit = HabitData(id = 1, name = "Walk", uuid = "walk-uuid")
        val entries = TwoWaySyncSnapshotMapper.entries(
            listOf(habit),
            listOf(EntryData(habitId = 1, timestamp = LocalDate(2026, 11, 21).unixTime, value = Entry.UNKNOWN))
        )
        assertEquals(emptyList(), entries)
    }

    @Test
    fun refusesHabitsWithoutUuidInsteadOfGuessingIdentity() {
        assertFailsWith<IllegalArgumentException> {
            TwoWaySyncSnapshotMapper.habits(listOf(HabitData(id = 1, name = "Walk")))
        }
    }

    @Test
    fun includesSyncRelevantHabitSettingsInFingerprint() {
        val first = HabitData(id = 1, name = "Walk", uuid = "walk-uuid", freqNum = 1, freqDen = 1)
        val second = first.copy(freqNum = 2, freqDen = 7)
        val firstIdentity = TwoWaySyncSnapshotMapper.habits(listOf(first)).single()
        val secondIdentity = TwoWaySyncSnapshotMapper.habits(listOf(second)).single()
        assertNotEquals(firstIdentity.settingsFingerprint, secondIdentity.settingsFingerprint)
    }

    @Test
    fun refusesEntriesWhoseHabitIsMissingFromSnapshot() {
        assertFailsWith<IllegalStateException> {
            TwoWaySyncSnapshotMapper.entries(
                listOf(HabitData(id = 1, name = "Walk", uuid = "walk-uuid")),
                listOf(EntryData(habitId = 99, timestamp = LocalDate(2026, 11, 21).unixTime, value = Entry.YES_MANUAL))
            )
        }
    }
}
