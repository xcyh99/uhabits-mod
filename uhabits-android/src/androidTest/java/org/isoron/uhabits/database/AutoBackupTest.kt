/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.isoron.uhabits.database

import android.net.Uri
import org.isoron.uhabits.AndroidDirFinder
import org.isoron.uhabits.BaseAndroidTest
import androidx.preference.PreferenceManager
import org.junit.Test

class AutoBackupTest : BaseAndroidTest() {
    @Test
    fun testForceCreatesInspectableFullBackup() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(targetContext)
        preferences.edit()
            .remove("publicBackupFolder")
            .putString(AutoBackup.KEY_KEEP_COUNT, "1")
            .commit()
        val basedir = AndroidDirFinder(targetContext).getFilesDir("Backups")!!
        basedir.listFiles()
            ?.filter { it.name.startsWith("Shizi_Auto_Backup_") && it.name.endsWith(".uhabitsbackup") }
            ?.forEach { it.delete() }
        val manualBackup = File(basedir, "Shizi_Full_Backup_20200101_000000_000.uhabitsbackup")
        manualBackup.writeText("manual backup placeholder")

        val result = AutoBackup(targetContext).run(force = true)
        assertTrue("Forced backup should create an archive", result == AutoBackup.Result.CREATED)

        assertTrue("Automatic retention must not delete manual full backups", manualBackup.isFile)
        val backup = basedir.listFiles()
            ?.firstOrNull { it.name.startsWith("Shizi_Auto_Backup_") && it.name.endsWith(".uhabitsbackup") }
        assertNotNull("Full backup archive should exist", backup)
        val info = FullBackupManager(targetContext).inspectBackup(Uri.fromFile(backup!!))
        assertTrue(info.entries.contains("database/uhabits.db"))
        assertTrue(info.entries.contains("modules/time_records.json"))
        assertTrue(info.entries.contains("widgets/time_record_widget_links.json"))
        assertTrue(info.entries.contains("settings/preferences.json"))
        assertTrue(info.entries.contains("modules/floating_timer_state.json"))
    }

    @Test
    fun testRunWithEmptyDirDoesNotCrash() {
        PreferenceManager.getDefaultSharedPreferences(targetContext).edit()
            .remove("publicBackupFolder")
            .putBoolean(AutoBackup.KEY_ENABLED, false)
            .commit()
        val basedir = AndroidDirFinder(targetContext).getFilesDir("Backups")!!
        basedir.listFiles()?.forEach { it.delete() }
        basedir.delete()

        AutoBackup(targetContext).run()
    }
}
