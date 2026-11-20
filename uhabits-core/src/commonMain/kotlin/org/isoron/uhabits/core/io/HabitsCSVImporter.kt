/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 */
package org.isoron.uhabits.core.io

import me.tatarka.inject.annotations.Inject
import org.isoron.platform.io.UserFile
import org.isoron.platform.io.ZipEntry
import org.isoron.platform.io.ZipReader
import org.isoron.platform.io.parseCsvLine
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.core.models.ModelFactory
import org.isoron.uhabits.core.models.NumericalHabitType
import org.isoron.uhabits.core.models.PaletteColor

/**
 * Imports ZIP archives produced by this application's own CSV exporter.
 * Habit colors are read from the exported HEX values, including custom colors.
 */
@Inject
class HabitsCSVImporter(
    private val habitList: HabitList,
    private val modelFactory: ModelFactory
) : AbstractImporter() {

    override suspend fun canHandle(file: UserFile): Boolean {
        return try {
            readArchive(file).any { it.name == "Habits.csv" }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun importHabitsFromFile(file: UserFile) {
        val entries = readArchive(file).associateBy { it.name }
        val habitsFile = entries["Habits.csv"] ?: return
        val lines = habitsFile.content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return

        for ((index, line) in lines.drop(1).withIndex()) {
            val columns = parseCsvLine(line)
            if (columns.size < 13) continue

            val habit = modelFactory.buildHabit()
            habit.position = columns[0].toIntOrNull() ?: (index + 1)
            habit.name = columns[1]
            habit.type = runCatching { HabitType.valueOf(columns[2]) }.getOrDefault(HabitType.YES_NO)
            habit.question = columns[3]
            habit.description = columns[4]
            habit.frequency = Frequency(
                columns[5].toIntOrNull() ?: 1,
                columns[6].toIntOrNull() ?: 1
            )
            habit.color = PaletteColor.fromHex(columns[7]) ?: PaletteColor(0)
            habit.unit = columns[8]
            habit.targetType = runCatching {
                NumericalHabitType.valueOf(columns[9])
            }.getOrDefault(NumericalHabitType.AT_LEAST)
            habit.targetValue = columns[10].toDoubleOrNull() ?: 0.0
            habit.isArchived = columns[11].equals("true", ignoreCase = true)
            habit.category = columns[12]

            habitList.add(habit)
            val directory = (index + 1).toString().padStart(3, '0') +
                " " + sanitizeFilename(habit.name)
            val checkmarks = entries["$directory/Checkmarks.csv"]
            if (checkmarks != null) {
                importEntries(habit, checkmarks)
            }
            habit.recompute()
        }
        habitList.resort()
    }

    private suspend fun readArchive(file: UserFile): List<ZipEntry> {
        // This importer is only for ZIP archives. Check the signature before
        // reading the whole file, so ordinary SQLite backups and CSV files are
        // not loaded into memory just to reject them.
        val header = file.readBytes(2)
        if (header.size < 2 || header[0] != 0x50.toByte() || header[1] != 0x4b.toByte()) {
            return emptyList()
        }
        return ZipReader(file.readBytes(Int.MAX_VALUE)).entries()
    }

    private fun importEntries(habit: Habit, file: ZipEntry) {
        val rows = file.content.lines().filter { it.isNotBlank() }
        for (line in rows.drop(1)) {
            val columns = parseCsvLine(line)
            if (columns.size < 2) continue
            val dateParts = columns[0].split("-")
            if (dateParts.size != 3) continue
            val date = runCatching {
                LocalDate(
                    dateParts[0].toInt(),
                    dateParts[1].toInt(),
                    dateParts[2].toInt()
                )
            }.getOrNull() ?: continue
            val value = parseEntryValue(columns[1]) ?: continue
            val notes = columns.getOrElse(2) { "" }
            if (value != Entry.UNKNOWN) {
                habit.originalEntries.add(Entry(date, value, notes))
            }
        }
    }

    private fun parseEntryValue(raw: String): Int? {
        return when (raw.trim()) {
            "YES_MANUAL" -> Entry.YES_MANUAL
            "YES_AUTO" -> Entry.YES_AUTO
            "NO" -> Entry.NO
            "SKIP" -> Entry.SKIP
            "UNKNOWN" -> Entry.UNKNOWN
            else -> raw.trim().toIntOrNull()
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace("[^ a-zA-Z0-9._-]+".toRegex(), "")
            .take(100)
            .trim()
    }
}
