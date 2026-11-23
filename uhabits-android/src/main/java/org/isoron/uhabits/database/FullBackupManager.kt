package org.isoron.uhabits.database

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import org.isoron.uhabits.BuildConfig
import org.isoron.uhabits.core.DATABASE_VERSION
import org.isoron.uhabits.utils.BackupFileName
import org.isoron.uhabits.utils.DatabaseUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
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
        val output = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("无法打开备份目标文件")
        output.use { writeBackup(it) }
    }

    @Throws(IOException::class)
    fun createBackup(destination: File) {
        FileOutputStream(destination).use { writeBackup(it) }
    }

    private fun writeBackup(destination: java.io.OutputStream) {
        val databaseFile = DatabaseUtils.getDatabaseFile(context)
        require(databaseFile.isFile && databaseFile.length() > 0L) {
            "主数据库不存在或为空"
        }

        val tempDir = File.createTempFile("full-backup-staging-", "", context.cacheDir).apply {
            if (!delete() || !mkdirs()) throw IOException("无法创建完整备份临时目录")
        }
        try {
        val stagedDatabase = File(tempDir, "uhabits.db")
        copyConsistentDatabaseSnapshot(databaseFile, stagedDatabase)
        if (stagedDatabase.length() == 0L) throw IOException("主数据库备份为空")

        val payloads = linkedMapOf<String, File>()
        payloads["database/uhabits.db"] = stagedDatabase

        val registeredModules = FullBackupModuleRegistry.create()
        registeredModules.forEach { module ->
            module.export(context, tempDir)
            module.entries.forEach { entry ->
                val payload = File(tempDir, entry)
                if (!payload.isFile) throw IOException("备份模块 ${module.id} 未生成文件：$entry")
                if (payloads.put(entry, payload) != null) {
                    throw IOException("多个备份模块使用了相同文件路径：$entry")
                }
            }
        }

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
            .put("modules", JSONArray(listOf("habits_database") + registeredModules.map { it.id }))
            .put("payload_count", payloads.size)
            .put("checksums_sha256", sha256(checksumsFile))

        ZipOutputStream(BufferedOutputStream(destination)).use { zip ->
            payloads.forEach { (name, file) -> addFile(zip, name, file) }
            addFile(zip, "checksums.json", checksumsFile)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        } finally {
            // Clean up even when ZIP creation or the destination stream fails.
            tempDir.deleteRecursively()
        }
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
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name
                    requireSafeEntryName(name)
                    if (name in files) throw IOException("备份包包含重复文件：$name")
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var entryBytes = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytes += count
                        totalBytes += count
                        if (entryBytes > MAX_ENTRY_BYTES) throw IOException("备份文件过大：$name")
                        if (totalBytes > MAX_TOTAL_BYTES) throw IOException("备份包总大小超过允许范围")
                        output.write(buffer, 0, count)
                    }
                    files[name] = output.toByteArray()
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
        if (!expectedNames.containsAll(REQUIRED_ENTRIES)) {
            throw IOException("备份包缺少必要的数据模块或校验记录")
        }
        expectedNames.forEach { name ->
            val bytes = files[name] ?: throw IOException("备份缺少文件：$name")
            if (sha256(bytes) != checksums.getString(name)) {
                throw IOException("文件校验失败：$name")
            }
        }
        if (!files.keys.containsAll(REQUIRED_ENTRIES)) {
            throw IOException("备份中缺少主数据库、时间记录或设置数据")
        }
        FullBackupModuleRegistry.create().forEach { module ->
            val presentEntries = module.entries.filter { it in files }
            if (presentEntries.isNotEmpty()) {
                if (!presentEntries.containsAll(module.entries)) {
                    throw IOException("备份模块 ${module.id} 的数据不完整")
                }
                module.validate(presentEntries.associateWith { files.getValue(it) })
            }
        }
        return BackupInfo(
            createdAt = manifest.optString("created_at"),
            appVersion = manifest.optString("app_version"),
            databaseVersion = manifest.optInt("database_version", -1),
            entries = files.keys
        )
    }

    /**
     * Validates the selected archive and stages it for application on the next
     * cold application start. The live database is never replaced while its
     * SQLite connections are open.
     */
    @Throws(IOException::class)
    fun prepareRestore(source: Uri): BackupInfo {
        val info = inspectBackup(source)
        if (info.databaseVersion > DATABASE_VERSION) {
            throw IOException("此备份来自更新版本的数据库，当前版本无法安全恢复")
        }

        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("无法读取所选备份文件")
        val staging = File(context.filesDir, PENDING_RESTORE_DIR + ".tmp")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("无法创建恢复暂存目录")

        try {
            val restorableEntries = setOf("database/uhabits.db") + FullBackupModuleRegistry.allEntries
            var totalBytes = 0L
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name in restorableEntries) {
                        requireSafeEntryName(entry.name)
                        val target = File(staging, entry.name)
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                if (totalBytes > MAX_TOTAL_BYTES) throw IOException("备份包解压后超过允许大小")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val database = File(staging, "database/uhabits.db")
            if (!database.isFile || database.length() == 0L) throw IOException("备份中没有有效主数据库")
            validateDatabaseFile(database)
            FullBackupModuleRegistry.create().forEach { module ->
                val presentEntries = module.entries.filter { File(staging, it).isFile }
                if (presentEntries.isNotEmpty()) {
                    if (!presentEntries.containsAll(module.entries)) {
                        throw IOException("备份模块 ${module.id} 的数据不完整")
                    }
                    module.validate(presentEntries.associateWith { File(staging, it).readBytes() })
                }
            }
            if (readDatabaseVersion(database) != info.databaseVersion) {
                throw IOException("备份说明中的数据库版本与实际数据库不一致")
            }

            val pending = File(context.filesDir, PENDING_RESTORE_DIR)
            pending.deleteRecursively()
            if (!staging.renameTo(pending)) throw IOException("无法保存待恢复数据")
            return info
        } catch (error: Exception) {
            staging.deleteRecursively()
            if (error is IOException) throw error
            throw IOException(error.message ?: "备份恢复准备失败", error)
        }
    }

    /**
     * Called by HabitsApplication before opening SQLite. A pre-restore safety
     * copy is retained so a failed restore can be rolled back.
     */
    @Throws(IOException::class)
    fun applyPendingRestoreIfPresent(): Boolean {
        val pending = File(context.filesDir, PENDING_RESTORE_DIR)
        if (!pending.isDirectory) return false

        val databaseFile = DatabaseUtils.getDatabaseFile(context)
        databaseFile.parentFile?.mkdirs()
        val safetyDir = File(
            context.filesDir,
            "restore-safety/${BackupFileName.timestamp()}"
        )
        if (!safetyDir.mkdirs() && !safetyDir.isDirectory) {
            throw IOException("无法创建恢复前安全备份目录，已取消恢复")
        }
        if (!databaseFile.isFile || databaseFile.length() == 0L) {
            throw IOException("当前数据库不存在或为空，无法建立恢复前安全副本")
        }
        val safetyDatabase = File(safetyDir, "uhabits.db")
        val currentWal = File(databaseFile.absolutePath + "-wal")
        val safetyWal = File(safetyDatabase.absolutePath + "-wal")
        databaseFile.copyTo(safetyDatabase, overwrite = true)
        if (!safetyDatabase.isFile || safetyDatabase.length() != databaseFile.length()) {
            throw IOException("恢复前数据库安全副本校验失败，已取消恢复")
        }
        if (currentWal.isFile && currentWal.length() > 0L) {
            currentWal.copyTo(safetyWal, overwrite = true)
            if (!safetyWal.isFile || safetyWal.length() != currentWal.length()) {
                throw IOException("恢复前 WAL 安全副本校验失败，已取消恢复")
            }
        }

        val registeredModules = FullBackupModuleRegistry.create()
        val modulesToRestore = registeredModules.filter { module ->
            module.entries.any { File(pending, it).isFile }
        }
        val safetyModuleRoot = File(safetyDir, "modules")
        modulesToRestore.forEach { module -> module.snapshot(context, safetyModuleRoot) }

        try {
            val stagedDatabase = File(pending, "database/uhabits.db")
            validateDatabaseFile(stagedDatabase)
            val replacement = File(databaseFile.parentFile, databaseFile.name + ".restore")
            stagedDatabase.copyTo(replacement, overwrite = true)
            File(databaseFile.absolutePath + "-wal").delete()
            File(databaseFile.absolutePath + "-shm").delete()
            if (databaseFile.exists() && !databaseFile.delete()) {
                throw IOException("无法替换当前数据库")
            }
            if (!replacement.renameTo(databaseFile)) {
                replacement.copyTo(databaseFile, overwrite = true)
                replacement.delete()
            }

            modulesToRestore.forEach { module -> module.restore(context, pending) }
            pending.deleteRecursively()
            return true
        } catch (error: Exception) {
            if (safetyDatabase.isFile) {
                databaseFile.delete()
                File(databaseFile.absolutePath + "-wal").delete()
                File(databaseFile.absolutePath + "-shm").delete()
                safetyDatabase.copyTo(databaseFile, overwrite = true)
                if (safetyWal.isFile) {
                    safetyWal.copyTo(File(databaseFile.absolutePath + "-wal"), overwrite = true)
                }
            }
            modulesToRestore.forEach { module ->
                runCatching { module.rollback(context, safetyModuleRoot) }
            }
            File(context.filesDir, "full-backup-restore-failed.txt")
                .writeText(error.message ?: "未知恢复错误")
            // Keep the staged archive so the user can retry after correcting the
            // underlying issue; never discard the only copy of the requested restore.
            throw IOException(error.message ?: "完整备份恢复失败，已尝试恢复原数据；待恢复备份仍保留", error)
        }
    }

    private fun validateDatabaseFile(file: File) {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            if (db.version > DATABASE_VERSION) {
                throw IOException("备份数据库版本高于当前 App 支持的版本")
            }
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                    throw IOException("备份数据库完整性检查未通过")
                }
            }
        } finally {
            db.close()
        }
    }

    private fun restorePreferences(document: String) {
        restoreTypedPreferences(
            document,
            PreferenceManager.getDefaultSharedPreferences(context),
            clearExisting = false,
            skipDeviceLocal = true
        )
    }

    private fun restoreTypedPreferences(
        document: String,
        preferences: SharedPreferences,
        clearExisting: Boolean,
        skipDeviceLocal: Boolean
    ) {
        val settingsDocument = JSONObject(document)
        if (settingsDocument.optString("format") != "SHIZI_PREFERENCES" ||
            settingsDocument.optInt("version", -1) != 1
        ) throw IOException("软件设置备份格式不受支持")
        val values = settingsDocument.optJSONObject("values")
            ?: throw IOException("软件设置备份内容不完整")
        val editor = preferences.edit()
        if (clearExisting) {
            editor.clear()
        } else if (skipDeviceLocal) {
            preferences.all.keys.forEach { key ->
                if (!isDeviceLocalPreference(key)) editor.remove(key)
            }
        }
        values.keys().forEach { key ->
            if (skipDeviceLocal && isDeviceLocalPreference(key)) return@forEach
            val item = values.getJSONObject(key)
            val value = item.get("value")
            when (item.getString("type")) {
                "boolean" -> editor.putBoolean(key, value as Boolean)
                "int" -> editor.putInt(key, (value as Number).toInt())
                "long" -> editor.putLong(key, (value as Number).toLong())
                "float" -> editor.putFloat(key, (value as Number).toFloat())
                "string" -> editor.putString(key, value as String)
                "string_set" -> {
                    val array = value as JSONArray
                    editor.putStringSet(key, buildSet {
                        for (index in 0 until array.length()) add(array.getString(index))
                    })
                }
                else -> throw IOException("不支持的软件设置类型：${item.optString("type")}")
            }
        }
        if (!editor.commit()) throw IOException("软件设置恢复失败")
    }

    private fun encodeFloatingTimerState(preferences: SharedPreferences): JSONObject {
        val mode = preferences.getString("mode", "down") ?: "down"
        val duration = preferences.getLong("duration", 0L)
        val base = preferences.getLong("base", duration)
        val running = preferences.getBoolean("running", false)
        val anchor = preferences.getLong("anchor", 0L)
        val elapsed = if (running) {
            (SystemClock.elapsedRealtime() - anchor).coerceAtLeast(0L)
        } else 0L
        val portableBase = when (mode) {
            "up" -> (base + elapsed).coerceAtLeast(0L)
            else -> (base - elapsed).coerceAtLeast(0L)
        }
        val values = JSONObject()
            .put("mode", encodeValue(mode))
            .put("duration", encodeValue(duration))
            .put("base", encodeValue(portableBase))
            // Running timers are restored paused: elapsedRealtime() is not portable
            // across devices or reboots, so carrying the old anchor would be wrong.
            .put("running", encodeValue(false))
        return JSONObject()
            .put("format", FLOATING_TIMER_FORMAT)
            .put("version", 1)
            .put("values", values)
    }

    private fun validateFloatingTimerState(document: String) {
        val root = JSONObject(document)
        val values = root.optJSONObject("values")
        if (root.optString("format") != FLOATING_TIMER_FORMAT ||
            root.optInt("version", -1) != 1 || values == null
        ) throw IOException("悬浮计时器备份格式不受支持")
        val expectedTypes = mapOf(
            "mode" to "string",
            "duration" to "long",
            "base" to "long",
            "running" to "boolean"
        )
        if (values.length() != expectedTypes.size) throw IOException("悬浮计时器备份字段不完整")
        expectedTypes.forEach { (key, type) ->
            val item = values.optJSONObject(key)
                ?: throw IOException("悬浮计时器备份缺少字段：$key")
            if (item.optString("type") != type || !item.has("value")) {
                throw IOException("悬浮计时器备份字段类型错误：$key")
            }
        }
        if (values.getJSONObject("mode").optString("value") !in setOf("up", "down")) {
            throw IOException("悬浮计时器模式无效")
        }
    }

    private fun restoreFloatingTimerState(document: String) {
        validateFloatingTimerState(document)
        val values = JSONObject(document).getJSONObject("values")
        val preferences = context.getSharedPreferences(FLOATING_TIMER_PREFS, Context.MODE_PRIVATE)
        val editor = preferences.edit()
            .putString("mode", values.getJSONObject("mode").getString("value"))
            .putLong("duration", values.getJSONObject("duration").getLong("value"))
            .putLong("base", values.getJSONObject("base").getLong("value"))
            .putBoolean("running", false)
            .putLong("anchor", 0L)
        // x/y are deliberately left untouched: window coordinates belong to the
        // current device's screen dimensions, not to portable timer data.
        if (!editor.commit()) throw IOException("悬浮计时器状态恢复失败")
    }

    private fun restoreTimeRecords(document: String) {
        val recordDocument = JSONObject(document)
        if (recordDocument.optString("format") != "SHIZI_TIME_RECORDS" ||
            recordDocument.optInt("version", -1) != 1
        ) throw IOException("时间记录备份格式不受支持")
        val records = recordDocument.optJSONArray("records")
            ?: throw IOException("时间记录备份内容不完整")
        if (!context.getSharedPreferences("time_records_data", Context.MODE_PRIVATE)
                .edit().putString("records", records.toString()).commit()
        ) throw IOException("时间记录恢复失败")
    }

    private fun restoreWidgetLinks(document: String) {
        val linksDocument = JSONObject(document)
        if (linksDocument.optString("format") != "SHIZI_WIDGET_LINKS" ||
            linksDocument.optInt("version", -1) != 1
        ) throw IOException("小组件配置备份格式不受支持")
        val values = linksDocument.optJSONObject("values")
            ?: throw IOException("小组件配置备份内容不完整")
        val preferences = context.getSharedPreferences("time_record_widget_links", Context.MODE_PRIVATE)
        val editor = preferences.edit().clear()
        values.keys().forEach { key -> editor.putString(key, values.getString(key)) }
        if (!editor.commit()) throw IOException("时间记录小组件配置恢复失败")
    }

    private fun isDeviceLocalPreference(key: String): Boolean =
        key.startsWith("webdav_") || key == "publicBackupFolder" || key == "reminderSound"

    private fun copyConsistentDatabaseSnapshot(source: File, destination: File) {
        val db = SQLiteDatabase.openDatabase(
            source.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        try {
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                    throw IOException("主数据库正在写入，暂时无法生成一致的备份")
                }
            }

            // A write reservation prevents another connection from changing
            // the main database or WAL while both files are copied.
            db.beginTransactionNonExclusive()
            try {
                source.copyTo(destination, overwrite = true)
                val sourceWal = File(source.absolutePath + "-wal")
                val destinationWal = File(destination.absolutePath + "-wal")
                if (sourceWal.isFile && sourceWal.length() > 0L) {
                    sourceWal.copyTo(destinationWal, overwrite = true)
                } else {
                    destinationWal.delete()
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }

        // Replay any frames copied from WAL into the staged database, leaving
        // a single self-contained SQLite file in the archive.
        val staged = SQLiteDatabase.openDatabase(
            destination.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        try {
            staged.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                    throw IOException("备份暂存数据库的 WAL 无法完成检查点")
                }
            }
            staged.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                    throw IOException("备份数据库完整性检查未通过")
                }
            }
        } finally {
            staged.close()
        }
        File(destination.absolutePath + "-wal").delete()
        File(destination.absolutePath + "-shm").delete()
    }

    private fun readDatabaseVersion(file: File): Int {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.version
        } finally {
            db.close()
        }
    }

    private fun encodePreferences(
        preferences: SharedPreferences,
        excludeDeviceLocal: Boolean = true
    ): JSONObject {
        val result = JSONObject()
        preferences.all.toSortedMap().forEach { (key, value) ->
            // WebDAV credentials are encrypted with a device-bound Android Keystore key.
            // SAF folder grants and notification sound URIs are device-local and not portable.
            if (excludeDeviceLocal &&
                (key.startsWith("webdav_") || key == "publicBackupFolder" || key == "reminderSound")
            ) {
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
        File(dir, name).apply {
            parentFile?.mkdirs()
            writeText(content, Charsets.UTF_8)
        }

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
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun requireSafeEntryName(name: String) {
        if (name.startsWith("/") || name.contains("\\") || name.split('/').any { it == ".." || it.isBlank() }) {
            throw IOException("备份包包含不安全的文件路径")
        }
    }

    private companion object {
        const val FORMAT_NAME = "SHIZI_FULL_BACKUP"
        const val FORMAT_VERSION = 1
        const val FLOATING_TIMER_PREFS = "floating_timer_state"
        const val FLOATING_TIMER_FORMAT = "SHIZI_FLOATING_TIMER"
        const val FLOATING_TIMER_ENTRY = "modules/floating_timer_state.json"
        const val MAX_ENTRY_BYTES = 128 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
        const val PENDING_RESTORE_DIR = "full-backup-pending"
        val REQUIRED_ENTRIES = setOf("database/uhabits.db") + FullBackupModuleRegistry.requiredEntries
        val RESTORABLE_ENTRIES = REQUIRED_ENTRIES + FullBackupModuleRegistry.allEntries
    }
}
