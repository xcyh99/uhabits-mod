package org.isoron.uhabits.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwoWaySyncPlannerTest {
    private fun habit(uuid: String, key: String = "walk") =
        SyncHabitIdentity(uuid, key, "Walk")

    private fun entry(uuid: String, day: String, value: Int, notes: String = "") =
        SyncEntryIdentity(uuid, day, value, notes)

    @Test
    fun mergesIndependentAdditionsAndDeduplicatesIdenticalRecords() {
        val plan = TwoWaySyncPlanner.build(
            localHabits = listOf(habit("shared")),
            remoteHabits = listOf(habit("shared")),
            localEntries = listOf(entry("shared", "2026-11-20", 2)),
            remoteEntries = listOf(
                entry("shared", "2026-11-20", 2),
                entry("shared", "2026-11-21", 2)
            )
        )

        assertEquals(1, plan.remoteOnlyEntries.size)
        assertEquals(1, plan.identicalEntries)
        assertFalse(plan.hasConflicts)
    }

    @Test
    fun reportsDifferentValuesForSameDayAsConflict() {
        val plan = TwoWaySyncPlanner.build(
            localHabits = listOf(habit("shared")),
            remoteHabits = listOf(habit("shared")),
            localEntries = listOf(entry("shared", "2026-11-20", 2, "local")),
            remoteEntries = listOf(entry("shared", "2026-11-20", 0, "remote"))
        )

        assertTrue(plan.hasConflicts)
        assertEquals(1, plan.conflictingEntries.size)
        assertEquals("local", plan.conflictingEntries.single().local.notes)
        assertEquals("remote", plan.conflictingEntries.single().remote.notes)
    }

    @Test
    fun doesNotAutomaticallyMatchLegacyHabitsWithDifferentUuids() {
        val plan = TwoWaySyncPlanner.build(
            localHabits = listOf(habit("old-local")),
            remoteHabits = listOf(habit("old-remote")),
            localEntries = listOf(entry("old-local", "2026-11-20", 2)),
            remoteEntries = listOf(entry("old-remote", "2026-11-21", 2))
        )

        assertTrue(plan.matchedHabitUuids.isEmpty())
        assertEquals(1, plan.remoteOnlyHabits.size)
        assertEquals(1, plan.possibleLegacyMatches.size)
        assertTrue(plan.remoteOnlyEntries.isEmpty())
        assertEquals(1, plan.remoteEntriesForNewHabits.size)
    }

    @Test
    fun ambiguousLegacyMatchesAreNotSuggested() {
        val plan = TwoWaySyncPlanner.build(
            localHabits = listOf(habit("local-1"), habit("local-2")),
            remoteHabits = listOf(habit("remote-1")),
            localEntries = emptyList(),
            remoteEntries = emptyList()
        )

        assertTrue(plan.possibleLegacyMatches.isEmpty())
    }
}
