/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.core.io

import me.tatarka.inject.annotations.Inject
import org.isoron.platform.io.Database
import org.isoron.platform.io.DatabaseOpener
import org.isoron.platform.io.FileOpener
import org.isoron.platform.io.UserFile
import org.isoron.platform.io.getVersion
import org.isoron.platform.io.migrateTo
import org.isoron.platform.io.query
import org.isoron.platform.io.querySingle
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.AppScope
import org.isoron.uhabits.core.DATABASE_VERSION
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateHabitCommand
import org.isoron.uhabits.core.commands.EditHabitCommand
import org.isoron.uhabits.core.database.HabitData
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.ModelFactory
import org.isoron.uhabits.core.models.sqlite.SQLiteHabitList
import org.isoron.uhabits.core.utils.isSQLite3File

/**
 * Class that imports data from database files exported by Loop Habit Tracker.
 */
@Inject
class LoopDBImporter(
    @AppScope val habitList: HabitList,
    @AppScope val modelFactory: ModelFactory,
    @AppScope val opener: DatabaseOpener,
    @AppScope val runner: CommandRunner,
    @AppScope logging: Logging,
    @AppScope val fileOpener: FileOpener
) : AbstractImporter() {

    private val logger = logging.getLogger("LoopDBImporter")

    // Kept outside the stack trace so the caller can still identify the last phase if OOM
    // prevents this importer from allocating a wrapped exception.
    var lastImportStage: String = "尚未开始"
        private set

    override suspend fun canHandle(file: UserFile): Boolean {
        lastImportStage = "检查备份文件格式"
        if (!isSQLite3File(file)) return false
        lastImportStage = "打开备份数据库并检查表结构"
        val db = opener.open(file.pathString)
        return try {
            val count = db.querySingle(
                "select count(*) from SQLITE_MASTER where name='Habits' or name='Repetitions'"
            ) { it.getInt(0) }
            if (count == null || count != 2) {
                logger.error("Cannot handle file: tables not found")
                false
            } else if (db.getVersion() > DATABASE_VERSION) {
                logger.error("Cannot handle file: incompatible version: ${db.getVersion()} > $DATABASE_VERSION")
                false
            } else {
                true
            }
        } finally {
            db.close()
        }
    }

    fun reloadHabitListCache() {
        (habitList as? SQLiteHabitList)?.reload()
    }

    override suspend fun importHabitsFromFile(file: UserFile) {
        val db = opener.open(file.pathString)
        var stage = "打开备份数据库"
        var currentHabit = ""
        try {
            stage = "迁移备份数据库"
            lastImportStage = stage
            db.migrateTo(DATABASE_VERSION) { version ->
                lastImportStage = "迁移数据库：执行第 ${version} 版"
                val filename = org.isoron.platform.io.format("%02d.sql", version)
                fileOpener.openResourceFile("migrations/$filename").lines().joinToString("\n")
            }

            stage = "读取习惯列表"
            lastImportStage = stage
            val habitDataList = loadHabits(db)
            stage = "恢复自定义颜色"
            lastImportStage = stage
            restoreShiziCustomColors(db, habitDataList)
            for ((index, habitData) in habitDataList.withIndex()) {
                currentHabit = "${index + 1}/${habitDataList.size} ${habitData.name}"
                stage = "创建或更新习惯"
                lastImportStage = "$stage：$currentHabit"
                var habit = habitList.getByUUID(habitData.uuid)

                if (habit == null) {
                    habit = modelFactory.buildHabit()
                    val imported = habitData.copy(id = null)
                    SQLiteHabitList.copyTo(imported, habit)
                    CreateHabitCommand(modelFactory, habitList, habit).run()
                } else {
                    val modified = modelFactory.buildHabit()
                    SQLiteHabitList.copyTo(habitData.copy(id = habit.id), modified)
                    EditHabitCommand(habitList, habit.id!!, modified).run()
                }

                habit = habitList.getByUUID(habitData.uuid)!!
                val entries = habit.originalEntries

                stage = "读取历史打卡记录"
                lastImportStage = "$stage：$currentHabit"
                db.query(
                    "SELECT timestamp, value, length(notes), notes FROM Repetitions WHERE habit = ? ORDER BY timestamp DESC",
                    habitData.id.toString()
                ) { stmt ->
                    val timestamp = stmt.getLongOrNull(0) ?: return@query
                    val value = stmt.getIntOrNull(1) ?: return@query
                    val notesLength = stmt.getLongOrNull(2) ?: 0L
                    if (notesLength > MAX_IMPORT_TEXT_LENGTH) {
                        throw IllegalStateException(
                            "打卡备注异常过长：习惯 " + habitData.name +
                                "，日期时间戳 " + timestamp + "，长度 " + notesLength + " 字符"
                        )
                    }
                    val notes = stmt.getTextOrNull(3) ?: ""
                    // A blank UNKNOWN row is not a repetition; do not import it or
                    // let it become the oldest date used by history recomputation.
                    if (value == Entry.UNKNOWN && notes.isEmpty()) return@query

                    // LocalDate stores its day offset in a 32-bit Int. Reject corrupt or
                    // unit-mismatched timestamps before conversion can wrap that offset.
                    val earliestAllowed = LocalDate(1900, 1, 1).unixTime
                    // Imported backups may legitimately contain entries dated after the
                    // test/device's current day. Keep a broad sanity bound instead of
                    // rejecting valid future-dated history during migration.
                    val latestAllowed = LocalDate(2100, 1, 1).unixTime
                    if (timestamp < earliestAllowed || timestamp > latestAllowed) {
                        throw IllegalStateException(
                            "打卡日期超出合理范围：习惯 " + habitData.name +
                                "，时间戳 " + timestamp + "（毫秒）"
                        )
                    }
                    val date = LocalDate.fromUnixTime(timestamp)
                    val (_, existingValue, existingNotes) = entries.get(date)
                    if (existingValue != value || existingNotes != notes) {
                        entries.add(Entry(date, value, notes))
                    }
                }
                stage = "重新计算习惯数据"
                lastImportStage = "$stage：$currentHabit"
                habit.recompute()
            }
            // New habits are sorted by add(), and edited habits are sorted by
            // EditHabitCommand. Re-sorting here is only necessary when a newly
            // imported habit has just had its score/status recomputed, because
            // those are the only sort keys that can change after the command.
            val scoreOrStatusOrders = setOf(
                HabitList.Order.BY_SCORE_ASC,
                HabitList.Order.BY_SCORE_DESC,
                HabitList.Order.BY_STATUS_ASC,
                HabitList.Order.BY_STATUS_DESC
            )
            val needsPostImportSort =
                habitList.primaryOrder in scoreOrStatusOrders ||
                    habitList.secondaryOrder in scoreOrStatusOrders

            stage = if (needsPostImportSort) "整理习惯顺序" else "刷新习惯列表"
            lastImportStage = "$stage（${habitList.size()} 项）"
            if (needsPostImportSort) {
                habitList.resort()
            } else {
                // Keep observers in sync without allocating a second full-sort buffer.
                habitList.observable.notifyListeners()
            }
        } catch (e: Throwable) {
            logger.error(IllegalStateException("Import failed at stage: $stage; habit: $currentHabit", e))
            throw IllegalStateException("导入阶段：$stage；习惯：$currentHabit", e)
        } finally {
            db.close()
        }
    }

    private fun restoreShiziCustomColors(db: Database, habits: List<HabitData>) {
        val tableCount = db.querySingle(
            "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='ShiziCustomColors'"
        ) { it.getInt(0) } ?: 0
        if (tableCount == 0) return

        val byUuid = mutableMapOf<String, Int>()
        val byId = mutableMapOf<Long, Int>()
        db.query("SELECT habit_id, habit_uuid, argb, length(habit_uuid) FROM ShiziCustomColors") { stmt ->
            val id = stmt.getLongOrNull(0)
            val uuidLength = stmt.getLongOrNull(3) ?: 0L
            if (uuidLength > MAX_IMPORT_TEXT_LENGTH) {
                throw IllegalStateException("自定义颜色表中的 habit_uuid 异常过长：${uuidLength} 字符")
            }
            val uuid = stmt.getTextOrNull(1)
            val argb = stmt.getInt(2)
            if (id != null) byId[id] = argb
            if (uuid != null) byUuid[uuid] = argb
        }
        for (habit in habits) {
            val color = habit.uuid?.let { byUuid[it] } ?: habit.id?.let { byId[it] }
            if (color != null) habit.color = color
        }
    }

    private fun loadHabits(db: Database): List<HabitData> {
        val result = mutableListOf<HabitData>()
        db.query(
            "SELECT id, name, description, question, freq_num, freq_den, color, " +
                "position, reminder_hour, reminder_min, reminder_days, highlight, " +
                "archived, type, target_value, target_type, unit, uuid, category, " +
                "length(name), length(description), length(question), length(unit), length(uuid), length(category) " +
                "FROM Habits ORDER BY position"
        ) { stmt ->
            val textColumns = listOf("name", "description", "question", "unit", "uuid", "category")
            for (i in textColumns.indices) {
                val length = stmt.getLongOrNull(19 + i) ?: 0L
                if (length > MAX_IMPORT_TEXT_LENGTH) {
                    throw IllegalStateException("习惯表字段 ${textColumns[i]} 异常过长：记录 id=${stmt.getLongOrNull(0)}，长度 ${length} 字符")
                }
            }
            result.add(
                HabitData(
                    id = stmt.getLongOrNull(0),
                    name = stmt.getTextOrNull(1) ?: "",
                    description = stmt.getTextOrNull(2) ?: "",
                    question = stmt.getTextOrNull(3) ?: "",
                    freqNum = stmt.getIntOrNull(4) ?: 1,
                    freqDen = stmt.getIntOrNull(5) ?: 1,
                    color = stmt.getIntOrNull(6) ?: 0,
                    position = stmt.getIntOrNull(7) ?: 0,
                    reminderHour = stmt.getIntOrNull(8),
                    reminderMin = stmt.getIntOrNull(9),
                    reminderDays = stmt.getIntOrNull(10) ?: 0,
                    highlight = stmt.getIntOrNull(11) ?: 0,
                    archived = stmt.getIntOrNull(12) ?: 0,
                    type = stmt.getIntOrNull(13) ?: 0,
                    targetValue = stmt.getRealOrNull(14) ?: 0.0,
                    targetType = stmt.getIntOrNull(15) ?: 0,
                    unit = stmt.getTextOrNull(16) ?: "",
                    uuid = stmt.getTextOrNull(17),
                    category = stmt.getText(18)
                )
            )
        }
        return result
    }

    companion object {
        // Check text size in SQLite before Cursor.getString() allocates the value.
        private const val MAX_IMPORT_TEXT_LENGTH = 16L * 1024L * 1024L
    }
}
