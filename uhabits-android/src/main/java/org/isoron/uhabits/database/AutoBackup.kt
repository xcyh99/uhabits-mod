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

package org.isoron.uhabits.database

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import org.isoron.platform.time.DateUtils
import org.isoron.uhabits.AndroidDirFinder
import org.isoron.uhabits.utils.BackupFileName
import java.io.File

/**
 * Daily full-app backups. Legacy .db backups are left untouched; retention only
 * applies to archives created by this manager.
 */
class AutoBackup(private val context: Context) {

    private val backupPattern = Regex("^Shizi_Full_Backup_\\d{8}_\\d{6}_\\d{3}\\.uhabitsbackup$")

    fun run(force: Boolean = false) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        if (!settings.getBoolean(KEY_ENABLED, true)) {
            Log.i("AutoBackup", "Automatic backups are disabled")
            return
        }
        val intervalDays = settings.getString(KEY_INTERVAL_DAYS, "1")?.toIntOrNull()
            ?.coerceIn(1, 30) ?: 1
        val keep = settings.getString(KEY_KEEP_COUNT, "5")?.toIntOrNull()
            ?.coerceIn(1, 50) ?: 5
        val intervalMillis = DateUtils.DAY_LENGTH * intervalDays
        Log.i("AutoBackup", "Checking automatic full backups (intervalDays=$intervalDays, force=$force)...")
        val uriString = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("publicBackupFolder", null)
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            val dir = if (uri.scheme == "content") {
                DocumentFile.fromTreeUri(context, uri)
            } else {
                File(uri.path ?: return).let { DocumentFile.fromFile(it) }
            }
            if (dir != null) {
                runInPublicDir(dir, keep)
                return
            }
        }

        val basedir = AndroidDirFinder(context).getFilesDir("Backups") ?: return
        runInPrivateDir(basedir, keep)
    }

    private fun runInPrivateDir(dir: File, keep: Int) {
        val files = dir.listFiles()
            ?.filter { it.isFile && backupPattern.matches(it.name) }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()
        val newestTimestamp = files.lastOrNull()?.lastModified() ?: 0L
        if (force || System.currentTimeMillis() - newestTimestamp >= intervalMillis) {
            val target = File(dir, "Shizi_Full_Backup_${BackupFileName.timestamp()}.uhabitsbackup")
            runCatching {
                FullBackupManager(context).createBackup(target)
                // Retention is applied only after the new archive has been written successfully.
                val updatedFiles = dir.listFiles()
                    ?.filter { it.isFile && backupPattern.matches(it.name) }
                    ?.sortedBy { it.lastModified() }
                    ?: emptyList()
                removeOldestPrivate(updatedFiles, keep.coerceAtLeast(1))
                recordSuccess()
            }.onFailure {
                target.delete()
                recordFailure()
                Log.e("AutoBackup", "Unable to create automatic full backup", it)
            }
        } else {
            recordSuccess(newestTimestamp)
            Log.i("AutoBackup", "Fresh full backup found (timestamp=$newestTimestamp)")
        }
    }

    private fun runInPublicDir(dir: DocumentFile, keep: Int) {
        val files = dir.listFiles()
            .filter { it.isFile && it.name?.let(backupPattern::matches) == true }
            .sortedBy { it.lastModified() }
        val newestTimestamp = files.lastOrNull()?.lastModified() ?: 0L
        if (System.currentTimeMillis() - newestTimestamp > DateUtils.DAY_LENGTH) {
            val name = "Shizi_Full_Backup_${BackupFileName.timestamp()}.uhabitsbackup"
            val target = dir.createFile("application/octet-stream", name)
            if (target == null) {
                recordFailure()
                Log.e("AutoBackup", "Unable to create automatic full backup file")
                return
            }
            runCatching {
                FullBackupManager(context).createBackup(target.uri)
                // Keep the previous valid archives until the replacement is complete.
                val updatedFiles = dir.listFiles()
                    .filter { it.isFile && it.name?.let(backupPattern::matches) == true }
                    .sortedBy { it.lastModified() }
                removeOldestPublic(updatedFiles, keep.coerceAtLeast(1))
                recordSuccess()
            }.onFailure {
                target.delete()
                recordFailure()
                Log.e("AutoBackup", "Unable to create automatic full backup", it)
            }
        } else {
            recordSuccess(newestTimestamp)
            Log.i("AutoBackup", "Fresh full backup found (timestamp=$newestTimestamp)")
        }
    }

    private fun recordSuccess(timestamp: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SUCCESS, timestamp)
            .remove(KEY_LAST_FAILURE)
            .apply()
    }

    private fun recordFailure() {
        context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_FAILURE, System.currentTimeMillis())
            .apply()
    }

    private fun removeOldestPrivate(files: List<File>, keep: Int) {
        for (index in 0 until (files.size - keep).coerceAtLeast(0)) {
            Log.i("AutoBackup", "Removing old full backup ${files[index]}")
            files[index].delete()
        }
    }

    private fun removeOldestPublic(files: List<DocumentFile>, keep: Int) {
        for (index in 0 until (files.size - keep).coerceAtLeast(0)) {
            Log.i("AutoBackup", "Removing old full backup ${files[index].uri}")
            files[index].delete()
        }
    }

    companion object {
        const val STATUS_PREFS = "auto_backup_status"
        const val KEY_LAST_SUCCESS = "last_success"
        const val KEY_LAST_FAILURE = "last_failure"
        const val KEY_ENABLED = "auto_backup_enabled"
        const val KEY_INTERVAL_DAYS = "auto_backup_interval_days"
        const val KEY_KEEP_COUNT = "auto_backup_keep_count"
    }
}
