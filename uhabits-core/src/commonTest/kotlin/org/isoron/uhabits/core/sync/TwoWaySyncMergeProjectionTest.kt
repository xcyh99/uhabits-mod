package org.isoron.uhabits.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TwoWaySyncMergeProjectionTest {
    private fun habit(uuid: String, fingerprint: String = "same") =
        SyncHabitIdentity(uuid, uuid, uuid, fingerprint)

    private fun entry(uuid: String, day: String, value: Int, notes: String = "") =
        SyncEntryIdentity(uuid, day, value, notes)

    @Test
    fun combinesIndependentAdditionsDeduplicatesAndHonorsConflictChoices() {
        val local = TwoWaySyncMergeProjection.Snapshot(
            habits = listOf(habit("shared", "local-settings"), habit("local-new")),
            entries = listOf(
                entry("shared", "2026-11-20", 1, "local note"),
                entry("shared", "2026-11-19", 1),
                entry("local-new", "2026-11-20", 1)
            )
        )
        val remote = TwoWaySyncMergeProjection.Snapshot(
            habits = listOf(habit("shared", "remote-settings"), habit("remote-new")),
            entries = listOf(
                entry("shared", "2026-11-20", 2, "remote note"),
                entry("shared", "2026-11-18", 1),
                entry("shared", "2026-11-17", 1),
                entry("remote-new", "2026-11-20", 1)
            )
        )
        val plan = TwoWaySyncPlanner.build(local.habits, remote.habits, local.entries, remote.entries)
        val resolution = TwoWaySyncResolution.validate(
            plan,
            listOf(SyncEntryConflictChoice("shared", "2026-11-20", SyncConflictSide.REMOTE)),
            listOf(SyncHabitSettingsConflictChoice("shared", SyncConflictSide.LOCAL)),
            emptyList()
        )

        val merged = TwoWaySyncMergeProjection.build(local, remote, plan, resolution)
        assertEquals(setOf("shared", "local-new", "remote-new"), merged.habits.map { it.uuid }.toSet())
        assertEquals(6, merged.entries.size)
        assertEquals(2, merged.entries.single { it.dayKey == "2026-11-20" && it.habitUuid == "shared" }.value)
        assertEquals("local-settings", merged.habits.single { it.uuid == "shared" }.settingsFingerprint)
        assertEquals(1, merged.entries.count { it.habitUuid == "shared" && it.dayKey == "2026-11-17" })
    }

    @Test
    fun rejectsStalePlanAfterEitherSnapshotChanges() {
        val local = TwoWaySyncMergeProjection.Snapshot(listOf(habit("a")), emptyList())
        val remote = TwoWaySyncMergeProjection.Snapshot(listOf(habit("a")), emptyList())
        val plan = TwoWaySyncPlanner.build(local.habits, remote.habits, emptyList(), emptyList())
        val resolution = TwoWaySyncResolution.validate(plan, emptyList(), emptyList(), emptyList())
        val changedRemote = remote.copy(habits = listOf(habit("a"), habit("b")))
        assertFailsWith<IllegalArgumentException> {
            TwoWaySyncMergeProjection.build(local, changedRemote, plan, resolution)
        }
    }

    @Test
    fun rejectsChangedEntriesEvenWhenHabitSetIsUnchanged() {
        val local = TwoWaySyncMergeProjection.Snapshot(
            listOf(habit("a")),
            listOf(entry("a", "2026-11-20", 1))
        )
        val remote = TwoWaySyncMergeProjection.Snapshot(listOf(habit("a")), emptyList())
        val plan = TwoWaySyncPlanner.build(local.habits, remote.habits, local.entries, remote.entries)
        val resolution = TwoWaySyncResolution.validate(plan, emptyList(), emptyList(), emptyList())
        val changedLocal = local.copy(entries = listOf(entry("a", "2026-11-20", 2)))
        assertFailsWith<IllegalArgumentException> {
            TwoWaySyncMergeProjection.build(changedLocal, remote, plan, resolution)
        }
    }

    @Test
    fun rejectsEntriesForUnknownHabit() {
        val local = TwoWaySyncMergeProjection.Snapshot(listOf(habit("a")), listOf(entry("missing", "2026-11-20", 1)))
        val remote = TwoWaySyncMergeProjection.Snapshot(listOf(habit("a")), emptyList())
        val plan = TwoWaySyncPlanner.build(local.habits, remote.habits, local.entries, remote.entries)
        assertFailsWith<IllegalArgumentException> {
            TwoWaySyncResolution.validate(plan, emptyList(), emptyList(), emptyList())
        }
    }

    @Test
    fun rejectsIncompleteConflictResolution() {
        val local = TwoWaySyncMergeProjection.Snapshot(listOf(habit("a")), listOf(entry("a", "2026-11-20", 1)))
        val remote = TwoWaySyncMergeProjection.Snapshot(listOf(habit("a")), listOf(entry("a", "2026-11-20", 2)))
        val plan = TwoWaySyncPlanner.build(local.habits, remote.habits, local.entries, remote.entries)
        val resolution = TwoWaySyncResolution.validate(plan, emptyList(), emptyList(), emptyList())
        assertFailsWith<IllegalArgumentException> {
            TwoWaySyncMergeProjection.build(local, remote, plan, resolution)
        }
    }
}
