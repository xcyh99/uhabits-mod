package org.isoron.uhabits.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.isoron.uhabits.core.sync.SyncDayKey
import org.isoron.uhabits.core.sync.SyncConflictSide
import org.isoron.uhabits.core.sync.TwoWaySyncMergeProjection
import org.isoron.uhabits.core.sync.TwoWaySyncPlan
import org.isoron.uhabits.core.sync.TwoWaySyncPlanner
import org.isoron.uhabits.core.sync.TwoWaySyncResolution
import org.isoron.uhabits.utils.DatabaseUtils

/**
 * Applies a user-approved merge to the active database in one SQLite
 * transaction. Existing local rows are never deleted; remote-only rows are
 * inserted and explicitly selected remote conflicts update their local row.
 */
class DatabaseSyncMergeWriter(
    private val snapshotReader: DatabaseSyncSnapshotReader = DatabaseSyncSnapshotReader()
) {
    data class Result(
        val habitsInserted: Int,
        val habitSettingsUpdated: Int,
        val entriesInserted: Int,
        val entriesUpdated: Int
    ) {
        val totalChanges: Int
            get() = habitsInserted + habitSettingsUpdated + entriesInserted + entriesUpdated
    }

    fun apply(
        remote: DatabaseSyncSnapshotReader.Snapshot,
        expectedPlan: TwoWaySyncPlan,
        resolution: TwoWaySyncResolution
    ): Result {
        require(expectedPlan.possibleLegacyMatches.isEmpty()) {
            "旧习惯配对尚未确认，已停止同步"
        }

        val db = DatabaseUtils.openDatabase()
        var habitsInserted = 0
        var settingsUpdated = 0
        var entriesInserted = 0
        var entriesUpdated = 0

        // Lock the active database before re-reading it. This closes the gap
        // between stale-plan validation and writes if the user edits a habit.
        db.beginTransaction()
        try {
            val local = snapshotReader.readLocal()
            val currentPlan = TwoWaySyncPlanner.build(
                local.habits,
                remote.habits,
                local.entries,
                remote.entries
            )
            require(currentPlan == expectedPlan) {
                "预览后本机或服务器数据发生变化，请重新预览后再同步"
            }

            val merged = TwoWaySyncMergeProjection.build(
                local = TwoWaySyncMergeProjection.Snapshot(local.habits, local.entries),
                remote = TwoWaySyncMergeProjection.Snapshot(remote.habits, remote.entries),
                plan = expectedPlan,
                resolution = resolution
            )

            val localRowsByUuid = local.habitRows.associateBy { it.uuid!! }
            val remoteRowsByUuid = remote.habitRows.associateBy { it.uuid!! }
            val habitIdByUuid = localRowsByUuid.mapValues { it.value.id!! }.toMutableMap()
            var nextPosition = (local.habitRows.maxOfOrNull { it.position } ?: -1) + 1

            for (habit in merged.habits) {
                val localRow = localRowsByUuid[habit.uuid]
                if (localRow == null) {
                    val source = remoteRowsByUuid[habit.uuid]
                        ?: error("服务器习惯数据缺失：${habit.displayName}")
                    val values = source.toContentValues().apply {
                        put("position", nextPosition++)
                    }
                    val id = db.insertOrThrow("Habits", null, values)
                    habitIdByUuid[habit.uuid] = id
                    habitsInserted++
                } else {
                    val keepRemote = expectedPlan.conflictingHabitSettings.any {
                        it.habitUuid == habit.uuid
                    } && resolution.habitSettingsConflictChoices.any {
                        it.habitUuid == habit.uuid && it.keep == SyncConflictSide.REMOTE
                    }
                    if (keepRemote) {
                        val source = remoteRowsByUuid[habit.uuid]
                            ?: error("服务器习惯设置缺失：${habit.displayName}")
                        val values = source.toContentValues()
                        // Position is device-local ordering, not a portable
                        // habit setting; keep the local order.
                        values.remove("position")
                        val changed = db.update(
                            "Habits",
                            values,
                            "id = ?",
                            arrayOf(localRow.id!!.toString())
                        )
                        check(changed == 1) { "更新习惯设置失败：${habit.displayName}" }
                        settingsUpdated++
                    }
                }
            }

            val localEntryByKey = local.entryRows.associateBy { row ->
                val habitId = row.habitId ?: error("本机打卡记录缺少习惯 ID")
                habitId to row.timestamp
            }
            for (entry in merged.entries) {
                val habitId = habitIdByUuid[entry.habitUuid]
                    ?: error("合并后的打卡记录找不到对应习惯：${entry.habitUuid}")
                val timestamp = SyncDayKey.toTimestamp(entry.dayKey)
                val existing = localEntryByKey[habitId to timestamp]
                if (existing == null) {
                    val values = ContentValues().apply {
                        put("habit", habitId)
                        put("timestamp", timestamp)
                        put("value", entry.value)
                        put("notes", entry.notes)
                    }
                    db.insertOrThrow("Repetitions", null, values)
                    entriesInserted++
                } else if (existing.value != entry.value || existing.notes != entry.notes) {
                    val values = ContentValues().apply {
                        put("value", entry.value)
                        put("notes", entry.notes)
                    }
                    val changed = db.update(
                        "Repetitions",
                        values,
                        "id = ?",
                        arrayOf(existing.id!!.toString())
                    )
                    check(changed == 1) { "更新打卡冲突失败：${entry.dayKey}" }
                    entriesUpdated++
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return Result(habitsInserted, settingsUpdated, entriesInserted, entriesUpdated)
    }

    private fun org.isoron.uhabits.core.database.HabitData.toContentValues() =
        ContentValues().apply {
            put("name", name)
            put("description", description)
            put("question", question)
            put("freq_num", freqNum)
            put("freq_den", freqDen)
            put("color", color)
            put("position", position)
            reminderHour?.let { put("reminder_hour", it) } ?: putNull("reminder_hour")
            reminderMin?.let { put("reminder_min", it) } ?: putNull("reminder_min")
            put("reminder_days", reminderDays)
            put("highlight", highlight)
            put("archived", archived)
            put("type", type)
            put("target_value", targetValue)
            put("target_type", targetType)
            put("unit", unit)
            uuid?.let { put("uuid", it) } ?: putNull("uuid")
            put("category", category)
        }
}
