package org.isoron.uhabits.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.isoron.uhabits.core.DATABASE_VERSION
import org.isoron.uhabits.core.database.EntryData
import org.isoron.uhabits.core.database.HabitData
import org.isoron.uhabits.core.sync.SyncEntryIdentity
import org.isoron.uhabits.core.sync.SyncHabitIdentity
import org.isoron.uhabits.core.sync.TwoWaySyncSnapshotMapper
import org.isoron.uhabits.utils.DatabaseUtils
import java.io.File

/**
 * Reads database snapshots without modifying either database.
 *
 * Remote files are opened read-only and are never migrated in place. A schema
 * mismatch or failed SQLite integrity check stops sync before planning changes.
 */
class DatabaseSyncSnapshotReader {
    data class Snapshot(
        val habits: List<SyncHabitIdentity>,
        val entries: List<SyncEntryIdentity>,
        val habitRows: List<HabitData>,
        val entryRows: List<EntryData>
    )

    fun readLocal(): Snapshot = read(DatabaseUtils.openDatabase())

    fun readRemote(file: File): Snapshot {
        require(file.isFile && file.length() > 0L) {
            "远端数据库文件不存在或为空"
        }
        val db = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        return try {
            read(db)
        } finally {
            db.close()
        }
    }

    private fun read(db: SQLiteDatabase): Snapshot {
        val integrity = db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            if (!cursor.moveToFirst()) "" else cursor.getString(0)
        }
        require(integrity.equals("ok", ignoreCase = true)) {
            "数据库完整性检查未通过：$integrity"
        }
        require(db.version == DATABASE_VERSION) {
            "数据库版本不兼容（当前 ${db.version}，需要 $DATABASE_VERSION），为保护数据已停止同步"
        }

        val habitRows = readHabits(db)
        val entryRows = readEntries(db)
        return Snapshot(
            habits = TwoWaySyncSnapshotMapper.habits(habitRows),
            entries = TwoWaySyncSnapshotMapper.entries(habitRows, entryRows),
            habitRows = habitRows,
            entryRows = entryRows
        )
    }

    private fun readHabits(db: SQLiteDatabase): List<HabitData> =
        db.rawQuery(
            """SELECT id, name, description, question, freq_num, freq_den, color,
               position, reminder_hour, reminder_min, reminder_days, highlight,
               archived, type, target_value, target_type, unit, uuid, category
               FROM Habits ORDER BY position""",
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        HabitData(
                            id = cursor.getLong(0),
                            name = cursor.getString(1) ?: "",
                            description = cursor.getString(2) ?: "",
                            question = cursor.getString(3) ?: "",
                            freqNum = cursor.getInt(4),
                            freqDen = cursor.getInt(5),
                            color = cursor.getInt(6),
                            position = cursor.getInt(7),
                            reminderHour = cursor.intOrNull(8),
                            reminderMin = cursor.intOrNull(9),
                            reminderDays = cursor.getInt(10),
                            highlight = cursor.getInt(11),
                            archived = cursor.getInt(12),
                            type = cursor.getInt(13),
                            targetValue = cursor.getDouble(14),
                            targetType = cursor.getInt(15),
                            unit = cursor.getString(16) ?: "",
                            uuid = cursor.getString(17),
                            category = cursor.getString(18) ?: ""
                        )
                    )
                }
            }
        }

    private fun readEntries(db: SQLiteDatabase): List<EntryData> =
        db.rawQuery(
            "SELECT id, habit, timestamp, value, notes FROM Repetitions ORDER BY timestamp",
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        EntryData(
                            id = cursor.getLong(0),
                            habitId = cursor.getLong(1),
                            timestamp = cursor.getLong(2),
                            value = cursor.getInt(3),
                            notes = cursor.getString(4) ?: ""
                        )
                    )
                }
            }
        }

    private fun Cursor.intOrNull(index: Int): Int? =
        if (isNull(index)) null else getInt(index)
}
