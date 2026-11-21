package org.isoron.uhabits.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.isoron.uhabits.core.DATABASE_VERSION
import org.isoron.uhabits.utils.BackupFileName
import org.isoron.uhabits.utils.DatabaseUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Creates a consistent, private pre-sync snapshot of the active database.
 *
 * The app currently disables SQLite WAL. An exclusive SQLite transaction is
 * held while the main database file is copied, preventing concurrent writers.
 * No database rows are changed by this operation.
 */
class DatabaseSyncSafetyBackup(private val context: Context) {

    fun create(): File {
        val db = DatabaseUtils.openDatabase()
        val backupDirectory = File(context.filesDir, "two-way-sync-backups")
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            throw IOException("无法创建同步安全备份目录")
        }
        val backup = File(
            backupDirectory,
            "SH_WEB_${BackupFileName.deviceId()}_SAF_${BackupFileName.timestamp()}.db"
        )
        if (!backup.createNewFile()) {
            throw IOException("无法创建唯一的同步安全备份文件")
        }
        var transactionStarted = false
        try {
            db.beginTransaction()
            transactionStarted = true

            val source = DatabaseUtils.getDatabaseFile(context)
            if (!source.isFile || source.length() <= 0L) {
                throw IOException("本机数据库文件不存在或为空，已停止同步")
            }

            FileInputStream(source).use { input ->
                FileOutputStream(backup).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }

            if (!backup.isFile || backup.length() <= 0L) {
                throw IOException("同步前安全备份创建失败")
            }

            SQLiteDatabase.openDatabase(
                backup.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { snapshot ->
                val integrity = snapshot.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else ""
                }
                if (!integrity.equals("ok", ignoreCase = true)) {
                    throw IOException("同步前备份完整性检查失败：$integrity")
                }
                if (snapshot.version != DATABASE_VERSION) {
                    throw IOException("同步前备份数据库版本不匹配")
                }
            }

            db.setTransactionSuccessful()
            return backup
        } catch (error: Exception) {
            backup.delete()
            throw error
        } finally {
            if (transactionStarted) db.endTransaction()
        }
    }
}
