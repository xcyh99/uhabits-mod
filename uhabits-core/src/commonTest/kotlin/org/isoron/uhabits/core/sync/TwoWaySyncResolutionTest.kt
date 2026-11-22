package org.isoron.uhabits.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TwoWaySyncResolutionTest {
    private fun plan(): TwoWaySyncPlan {
        val localHabit = SyncHabitIdentity("shared", "walk", "Walk", "daily")
        val remoteHabit = SyncHabitIdentity("shared", "walk", "Walk", "weekly")
        val localLegacy = SyncHabitIdentity("legacy-local", "read", "Read")
        val remoteLegacy = SyncHabitIdentity("legacy-remote", "read", "Read")
        return TwoWaySyncPlanner.build(
            localHabits = listOf(localHabit, localLegacy),
            remoteHabits = listOf(remoteHabit, remoteLegacy),
            localEntries = listOf(SyncEntryIdentity("shared", "2026-11-20", 2, "local")),
            remoteEntries = listOf(SyncEntryIdentity("shared", "2026-11-20", 0, "remote"))
        )
    }

    @Test
    fun requiresAnExplicitChoiceForEveryConflict() {
        assertFailsWith<IllegalArgumentException> {
            TwoWaySyncResolution.validate(
                plan(), emptyList(), emptyList(), emptyList()
            )
        }
    }

    @Test
    fun acceptsCompleteConflictChoicesAndOneToOneLegacyPair() {
        val resolution = TwoWaySyncResolution.validate(
            plan(),
            listOf(SyncEntryConflictChoice("shared", "2026-11-20", SyncConflictSide.LOCAL)),
            listOf(SyncHabitSettingsConflictChoice("shared", SyncConflictSide.REMOTE)),
            listOf(SyncLegacyHabitPair("legacy-local", "legacy-remote"))
        )
        assertEquals(1, resolution.entryConflictChoices.size)
        assertEquals(SyncConflictSide.REMOTE, resolution.habitSettingsConflictChoices.single().keep)
        assertEquals(1, resolution.legacyHabitPairs.size)
    }

    @Test
    fun rejectsDuplicateOrIncompleteConflictChoices() {
        val duplicate = listOf(
            SyncEntryConflictChoice("shared", "2026-11-20", SyncConflictSide.LOCAL),
            SyncEntryConflictChoice("shared", "2026-11-20", SyncConflictSide.REMOTE)
        )
        assertFailsWith<IllegalArgumentException> {
            TwoWaySyncResolution.validate(
                plan(), duplicate,
                listOf(SyncHabitSettingsConflictChoice("shared", SyncConflictSide.LOCAL)),
                emptyList()
            )
        }
    }

    @Test
    fun rejectsLegacyPairingToUnknownOrAlreadyMatchedHabit() {
        assertFailsWith<IllegalArgumentException> {
            TwoWaySyncResolution.validate(
                plan(),
                listOf(SyncEntryConflictChoice("shared", "2026-11-20", SyncConflictSide.LOCAL)),
                listOf(SyncHabitSettingsConflictChoice("shared", SyncConflictSide.LOCAL)),
                listOf(SyncLegacyHabitPair("shared", "legacy-remote"))
            )
        }
    }

    @Test
    fun rejectsManyToOneLegacyPairing() {
        val expandedPlan = TwoWaySyncPlanner.build(
            localHabits = listOf(
                SyncHabitIdentity("l1", "same", "Same"),
                SyncHabitIdentity("l2", "same", "Same")
            ),
            remoteHabits = listOf(
                SyncHabitIdentity("r1", "same", "Same"),
                SyncHabitIdentity("r2", "same", "Same")
            ),
            localEntries = emptyList(),
            remoteEntries = emptyList()
        )
        assertFailsWith<IllegalArgumentException> {
            TwoWaySyncResolution.validate(
                expandedPlan, emptyList(), emptyList(),
                listOf(
                    SyncLegacyHabitPair("l1", "r1"),
                    SyncLegacyHabitPair("l2", "r1")
                )
            )
        }
    }
}
