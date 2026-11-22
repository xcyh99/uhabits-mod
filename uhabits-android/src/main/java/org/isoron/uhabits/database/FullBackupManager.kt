package org.isoron.uhabits.database

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import org.isoron.uhabits.BuildConfig
import org.isoron.uhabits.activities.timerecords.TimeRecordStore
import org.isoron.uhabits.utils.BackupFileName
import org.isoron.uhabits.utils.DatabaseUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Creates and inspects the versioned, portable full-app backup container.
 *
 * The ZIP container is intentionally independent from the legacy database export
 * and from WebDAV's database synchronization format.
 */
class FullBackupManager(private val context: Context) {

    data class BackupInfo(
        val createdAt: String,
        val appVersion: String,
        val databaseVersion: Int,
        val entries: Set<String>
    )

    fun suggestedFileName(): String =
        "Shizi_Full_Backup_${BackupFileName.timestamp()}.uhabitsbackup"

    @Throws(IOException::class)
    fun createBackup(destination: Uri) {
        val databaseFile = DatabaseUtils.getDatabaseFile(context)
        require(databaseFile.isFile && databaseFile.length() > 0L) {
            "主数据库不存在或为空"
        }

        checkpointAndValidateDatabase(databaseFile)
        val tempDir = File(context.cacheDir, "full-backup-staging").apply {
            if (!exists() && !mkdirs()) throw IOException("无法创建完整备份临时目录")
        }
        val stagedDatabase = File(tempDir, "uhabits.db")
        databaseFile.copyTo(stagedDatabase, overwrite = true)
        if (stagedDatabase.length() == 0L) throw IOException("主数据库备份为空")

        val payloads = linkedMapOf<String, File>()
        payloads["database/uhabits.db"] = stagedDatabase

        val timeRecords = context.getSharedPreferences("time_records_data", Context.MODE_PRIVATE)
            .getString("records", "[]") ?: "[]"
        // Parse before writing so a malformed module value cannot silently enter a backup.
        JSONArray(timeRecords)
        payloads["modules/time_records.json"] =
            writeTextFile(tempDir, "time_records.json", JSONObject().put("records", JSONArray(timeRecords)).toString())

        val widgetLinks = context.getSharedPreferences("time_record_widget_links", Context.MODE_PRIVATE)
        payloads["widgets/time_record_widget_links.json"] =
            writeTextFile(tempDir, "time_record_widget_links.json", encodeStringPreferences(widgetLinks).toString())

        val defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        payloads["settings/preferences.json"] =
            writeTextFile(tempDir, "preferences.json", encodePreferences(defaultPreferences).toString())

        val checksums = JSONObject()
        payloads.forEach { (name, file) -> checksums.put(name, sha256(file)) }
        val checksumsFile = writeTextFile(tempDir, "checksums.json", checksums.toString(2))

        val dbVersion = readDatabaseVersion(stagedDatabase)
        val manifest = JSONObject()
            .put("format", FORMAT_NAME)
            .put("format_version", FORMAT_VERSION)
            .put("created_at", System.currentTimeMillis())
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("database_version", dbVersion)
            .put("android_sdk", Build.VERSION.SDK_INT)
            .put("device", Build.MANUFACTURER + " " + Build.MODEL)
            .put("modules", JSONArray(listOf("habits_database", "time_records", "time_record_widget_links", "app_preferences")))
            .put("payload_count", payloads.size)
            .put("checksums_sha256", sha256(checksumsFile))

        val output = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("无法打开备份目标文件")
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            payloads.forEach { (name, file) -> addFile(zip, name, file) }
            addFile(zip, "checksums.json", checksumsFile)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        stagedDatabase.delete()
        payloads.values.filter { it != stagedDatabase }.forEach { it.delete() }
        checksumsFile.delete()
    }

    /**
     * Validates archive structure and every payload digest without modifying app data.
     * Restore orchestration deliberately remains separate from inspection.
     */
    @Throws(IOException::class)
    fun inspectBackup(source: Uri): BackupInfo {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("无法读取所选备份文件")
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name
                    requireSafeEntryName(name)
                    if (name in files) throw IOException("备份包包含重复文件：$name")
                    val bytes = zip.readBytes()
                    if (bytes.size > MAX_ENTRY_BYTES) throw IOException("备份文件过大：$name")
                    files[name] = bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val manifestBytes = files["manifest.json"] ?: throw IOException("缺少备份说明文件")
        val checksumsBytes = files["checksums.json"] ?: throw IOException("缺少完整性校验文件")
        val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
        if (manifest.optString("format") != FORMAT_NAME) throw IOException("这不是十字习惯完整备份")
        if (manifest.optInt("format_version", -1) != FORMAT_VERSION) {
            throw IOException("暂不支持此完整备份格式版本")
        }
        if (sha256(checksumsBytes) != manifest.optString("checksums_sha256")) {
            throw IOException("备份校验清单已损坏")
        }
        val checksums = JSONObject(String(checksumsBytes, Charsets.UTF_8))
        val expectedNames = checksums.keys().asSequence().toSet()
        expectedNames.forEach { name ->
            val bytes = files[name] ?: throw IOException("备份缺少文件：$name")
            if (sha256(bytes) != checksums.getString(name)) {
                throw IOException("文件校验失败：$name")
            }
        }
        if (!files.containsKey("database/uhabits.db")) throw IOException("备份中没有主数据库")
        if (!files.containsKey("modules/time_records.json")) throw IOException("备份中没有时间记录数据")
        return BackupInfo(
            createdAt = manifest.optString("created_at"),
            appVersion = manifest.optString("app_version"),
            databaseVersion = manifest.optInt("database_version", -1),
            entries = files.keys
        )
    }

    private fun checkpointAndValidateDatabase(file: File) {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                    throw IOException("主数据库正在写入，暂时无法生成一致的备份")
                }
            }
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                    throw IOException("主数据库完整性检查未通过")
                }
            }
        } finally {
            db.close()
        }
    }

    private fun readDatabaseVersion(file: File): Int {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.version
        } finally {
            db.close()
        }
    }

    private fun encodePreferences(preferences: SharedPreferences): JSONObject {
        val result = JSONObject()
        preferences.all.toSortedMap().forEach { (key, value) ->
            // WebDAV credentials are encrypted with a device-bound Android Keystore key.
            // SAF folder grants and notification sound URIs are device-local and not portable.
            if (key.startsWith("webdav_") || key == "publicBackupFolder" || key == "reminderSound") {
                return@forEach
            }
            result.put(key, encodeValue(value))
        }
        return JSONObject()
            .put("format", "SHIZI_PREFERENCES")
            .put("version", 1)
            .put("values", result)
    }

    private fun encodeStringPreferences(preferences: SharedPreferences): JSONObject {
        val values = JSONObject()
        preferences.all.toSortedMap().forEach { (key, value) ->
            if (value is String) values.put(key, value)
        }
        return JSONObject().put("format", "SHIZI_WIDGET_LINKS").put("version", 1).put("values", values)
    }

    private fun encodeValue(value: Any?): JSONObject? {
        val result = JSONObject()
        when (value) {
            is Boolean -> result.put("type", "boolean").put("value", value)
            is Int -> result.put("type", "int").put("value", value)
            is Long -> result.put("type", "long").put("value", value)
            is Float -> result.put("type", "float").put("value", value.toDouble())
            is String -> result.put("type", "string").put("value", value)
            is Set<*> -> result.put("type", "string_set").put("value", JSONArray(value.filterIsInstance<String>().sorted()))
            else -> return null
        }
        return result
    }

    private fun writeTextFile(dir: File, name: String, content: String): File =
        File(dir, name).apply { writeText(content, Charsets.UTF_8) }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun sha256(file: File): String = FileInputStream(file).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun requireSafeEntryName(name: String) {
        if (name.startsWith("/") || name.contains("\\") || name.split('/').any { it == ".." || it.isBlank() }) {
            throw IOException("备份包包含不安全的文件路径")
        }
    }

    private companion object {
        const val FORMAT_NAME = "SHIZI_FULL_BACKUP"
        const val FORMAT_VERSION = 1
        const val MAX_ENTRY_BYTES = 128 * 1024 * 1024
    }
}
