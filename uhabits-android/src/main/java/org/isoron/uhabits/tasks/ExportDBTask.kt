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
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 */
package org.isoron.uhabits.tasks

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.tasks.Task
import org.isoron.uhabits.inject.AppContext
import org.isoron.uhabits.utils.DatabaseUtils.saveDatabaseCopy
import java.io.File
import java.io.IOException

class ExportDBTask(
    @param:AppContext private val context: Context,
    private val listener: Listener,
    private val legacyCompatible: Boolean = false
) : Task {
    private var filename: String? = null

    override suspend fun doInBackground() {
        filename = null
        filename = try {
            // Always stage the backup in the app cache. The user chooses the final
            // destination through ACTION_CREATE_DOCUMENT after this task completes.
            val backupDir = File(context.cacheDir, "backup-export").apply {
                if (!exists() && !mkdirs()) throw IOException("Unable to create backup staging directory")
            }
            val stagedPath = saveDatabaseCopy(context, backupDir)
            val stagedFile = File(stagedPath)
            if (legacyCompatible) convertColorsForLegacyApp(stagedFile)
            val prefix = if (legacyCompatible) {
                "Shizi Habits Original-Compatible Backup"
            } else {
                "Shizi Habits Full Backup"
            }
            val finalFile = File(backupDir, stagedFile.name.replace("Loop Habits Backup", prefix))
            if (stagedFile != finalFile && !stagedFile.renameTo(finalFile)) {
                throw IOException("Unable to rename backup file")
            }
            finalFile.absolutePath
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun convertColorsForLegacyApp(file: File) {
        val db = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        try {
            val rows = mutableListOf<Pair<Long, Int>>()
            db.rawQuery("SELECT id, color FROM Habits", null).use { cursor ->
                while (cursor.moveToNext()) {
                    rows.add(cursor.getLong(0) to cursor.getInt(1))
                }
            }
            db.beginTransaction()
            try {
                for ((id, storedColor) in rows) {
                    // Legacy palette identifiers 0..19 are already compatible.
                    if (storedColor in PaletteColor.LEGACY_COLORS.indices) continue
                    val argb = PaletteColor.fromStoredValue(storedColor).argb
                    val nearest = PaletteColor.LEGACY_COLORS.indices.minByOrNull { index ->
                        val candidate = PaletteColor.LEGACY_COLORS[index]
                        val dr = ((argb shr 16) and 0xFF) - ((candidate shr 16) and 0xFF)
                        val dg = ((argb shr 8) and 0xFF) - ((candidate shr 8) and 0xFF)
                        val dbBlue = (argb and 0xFF) - (candidate and 0xFF)
                        dr * dr + dg * dg + dbBlue * dbBlue
                    } ?: 0
                    db.update(
                        "Habits",
                        ContentValues().apply { put("color", nearest) },
                        "id = ?",
                        arrayOf(id.toString())
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
    }

    override fun onPostExecute() {
        listener.onExportDBFinished(filename)
    }

    fun interface Listener {
        fun onExportDBFinished(filename: String?)
    }
}
