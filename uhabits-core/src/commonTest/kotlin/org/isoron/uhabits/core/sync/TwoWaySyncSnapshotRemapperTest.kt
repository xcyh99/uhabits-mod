package org.isoron.uhabits.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TwoWaySyncSnapshotRemapperTest {
    private fun habit(uuid: String) = SyncHabitIdentity(uuid, uuid, uuid)
    private fun entry(uuid: String) = SyncEntryIdentity(uuid, "2026-11-20", 2)

    @Test
    fun remapsConfirmedLegacyHabitAndItsEntriesToLocalUuid() {
        val result = TwoWaySyncSnapshotRemapper.remapRemote(
            localHabits = listOf(habit("local-old")),
            remoteHabits = listOf(habit("remote-old")),
            remoteEntries = listOf(entry("remote-old")),
            confirmedPairs = listOf(SyncLegacyHabitPair("local-old", "remote-old"))
        )
        assertEquals("local-old", result.habits.single().uuid)
        assertEquals("local-old", result.entries.single().habitUuid)
    }

    @Test
    fun leavesUnpairedNewRemoteHabitsUnchanged() {
        val result = TwoWaySyncSnapshotRemapper.remapRemote(
            localHabits = listOf(habit("local")),
            remoteHabits = listOf(habit("remote-new")),
            remoteEntries = listOf(entry("remote-new")),
            confirmedPairs = emptyList()
        )
        assertEquals("remote-new", result.habits.single().uuid)
        assertEquals("remote-new", result.entries.single().habitUuid)
    }

    @Test
    fun rejectsPairingAlreadyMatchedOrUnknownHabits() {
        assertFailsWith<IllegalArgumentException> {
            TwoWaySyncSnapshotRemapper.remapRemote(
                localHabits = listOf(habit("same")),
                remoteHabits = listOf(habit("same"), habit("remote")),
                remoteEntries = emptyList(),
                confirmedPairs = listOf(SyncLegacyHabitPair("same", "remote"))
            )
        }
    }

    @Test
    fun rejectsManyToOnePairsBeforeRemapping() {
        assertFailsWith<IllegalArgumentException> {
            TwoWaySyncSnapshotRemapper.remapRemote(
                localHabits = listOf(habit("l1"), habit("l2")),
                remoteHabits = listOf(habit("r1"), habit("r2")),
                remoteEntries = emptyList(),
                confirmedPairs = listOf(
                    SyncLegacyHabitPair("l1", "r1"),
                    SyncLegacyHabitPair("l2", "r1")
                )
            )
        }
    }

    @Test
    fun rejectsRemoteEntriesReferencingMissingHabit() {
        assertFailsWith<IllegalArgumentException> {
            TwoWaySyncSnapshotRemapper.remapRemote(
                localHabits = emptyList(),
                remoteHabits = listOf(habit("remote")),
                remoteEntries = listOf(entry("missing")),
                confirmedPairs = emptyList()
            )
        }
    }
}
