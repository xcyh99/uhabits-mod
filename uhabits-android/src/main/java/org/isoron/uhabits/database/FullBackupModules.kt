package org.isoron.uhabits.database

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * A portable data module participating in the full-app backup container.
 *
 * New features with their own persistent storage register one module here.
 * The database remains a separately managed core payload and legacy database
 * export/synchronization code does not use this registry.
 */
internal interface FullBackupModule {
    val id: String
    val entries: Set<String>
    // New modules are optional by default so archives created before a feature
    // existed remain restorable. Promote entries to required only with a
    // deliberate format migration.
    val requiredEntries: Set<String>
        get() = emptySet()

    fun export(context: Context, stagingRoot: File)
    fun validate(payloads: Map<String, ByteArray>)
    fun snapshot(context: Context, safetyRoot: File)
    fun restore(context: Context, restoreRoot: File)
    fun rollback(context: Context, safetyRoot: File)
}

internal object FullBackupModuleRegistry {
    const val FLOATING_TIMER_ENTRY = "modules/floating_timer_state.json"
    private const val FLOATING_TIMER_PREFS = "floating_timer_state"

    fun create(): List<FullBackupModule> = listOf(
        TimeRecordsModule,
        TimeRecordWidgetLinksModule,
        PictureWidgetConfigurationsModule,
        HabitWidgetConfigurationsModule,
        SimpleWidgetInstancesModule,
        AppPreferencesModule,
        FloatingTimerModule
    )

    val requiredEntries: Set<String>
        get() = create().flatMap { it.requiredEntries }.toSet()

    val allEntries: Set<String>
        get() = create().flatMap { it.entries }.toSet()

    private object TimeRecordsModule : FullBackupModule {
        private const val ENTRY = "modules/time_records.json"
        override val id = "time_records"
        override val entries = setOf(ENTRY)
        override val requiredEntries = entries

        override fun export(context: Context, stagingRoot: File) {
            val raw = context.getSharedPreferences("time_records_data", Context.MODE_PRIVATE)
                .getString("records", "[]") ?: "[]"
            val document = JSONObject()
                .put("format", "SHIZI_TIME_RECORDS")
                .put("version", 1)
                .put("records", JSONArray(raw))
            writePayload(stagingRoot, ENTRY, document.toString())
        }

        override fun validate(payloads: Map<String, ByteArray>) {
            val root = jsonPayload(payloads, ENTRY)
            if (root.optString("format") != "SHIZI_TIME_RECORDS" ||
                root.optInt("version", -1) != 1 ||
                root.optJSONArray("records") == null
            ) throw IOException("时间记录备份格式不受支持")
        }

        override fun snapshot(context: Context, safetyRoot: File) = export(context, safetyRoot)

        override fun restore(context: Context, restoreRoot: File) {
            val records = JSONObject(readPayload(restoreRoot, ENTRY)).getJSONArray("records")
            if (!context.getSharedPreferences("time_records_data", Context.MODE_PRIVATE)
                    .edit().putString("records", records.toString()).commit()
            ) throw IOException("时间记录恢复失败")
        }

        override fun rollback(context: Context, safetyRoot: File) = restore(context, safetyRoot)
    }

    private object TimeRecordWidgetLinksModule : FullBackupModule {
        private const val ENTRY = "widgets/time_record_widget_links.json"
        override val id = "time_record_widget_links"
        override val entries = setOf(ENTRY)
        override val requiredEntries = entries

        override fun export(context: Context, stagingRoot: File) {
            val preferences = context.getSharedPreferences("time_record_widget_links", Context.MODE_PRIVATE)
            val values = JSONObject()
            preferences.all.toSortedMap().forEach { (key, value) ->
                if (value is String) values.put(key, value)
            }
            org.isoron.uhabits.widgets.WidgetRestoreStore.pending(context)
                .filter { it.type == "time_record" && it.providerClass == org.isoron.uhabits.widgets.TimeRecordWidgetProvider::class.java.name }
                .forEach { item -> item.recordId?.let { values.put(item.sourceWidgetId.toString(), it) } }
            val document = JSONObject()
                .put("format", "SHIZI_WIDGET_LINKS")
                .put("version", 1)
                .put("values", values)
            writePayload(stagingRoot, ENTRY, document.toString())
        }

        override fun validate(payloads: Map<String, ByteArray>) {
            val root = jsonPayload(payloads, ENTRY)
            if (root.optString("format") != "SHIZI_WIDGET_LINKS" ||
                root.optInt("version", -1) != 1 ||
                root.optJSONObject("values") == null
            ) throw IOException("小组件配置备份格式不受支持")
            val values = root.getJSONObject("values")
            values.keys().forEach { key ->
                if (values.get(key) !is String) throw IOException("小组件配置值无效：$key")
            }
        }

        override fun snapshot(context: Context, safetyRoot: File) = export(context, safetyRoot)

        override fun restore(context: Context, restoreRoot: File) {
            val values = JSONObject(readPayload(restoreRoot, ENTRY)).getJSONObject("values")
            val providerClass = org.isoron.uhabits.widgets.TimeRecordWidgetProvider::class.java.name
            val restored = values.keys().asSequence().mapNotNull { key ->
                val sourceId = key.toIntOrNull() ?: return@mapNotNull null
                val recordId = values.optString(key).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                org.isoron.uhabits.widgets.WidgetRestoreStore.Config(
                    sourceWidgetId = sourceId,
                    providerClass = providerClass,
                    habitIds = emptyList(),
                    type = "time_record",
                    recordId = recordId
                )
            }
            org.isoron.uhabits.widgets.WidgetRestoreStore.mergePending(context, restored)
        }

        override fun rollback(context: Context, safetyRoot: File) {
            org.isoron.uhabits.widgets.WidgetRestoreStore.replaceType(context, "time_record", emptyList())
            restore(context, safetyRoot)
        }
    }


    /**
     * Portable picture-widget settings only. Photos and thumbnails are excluded
     * so a full backup remains a compact data archive.
     */
    private object PictureWidgetConfigurationsModule : FullBackupModule {
        private const val ENTRY = "widgets/picture_widget_configurations.json"
        override val id = "picture_widget_configurations"
        override val entries = setOf(ENTRY)
        override val requiredEntries = emptySet<String>()

        override fun export(context: Context, stagingRoot: File) {
            val prefs = context.getSharedPreferences(
                org.isoron.uhabits.widgets.PictureShortcutWidgetProvider.PREFS, Context.MODE_PRIVATE
            )
            val configs = linkedMapOf<Int, JSONObject>()
            val ids = prefs.all.keys.mapNotNull { key ->
                Regex("^(\\d+)_(left|right|split|imageMode)$").matchEntire(key)
                    ?.groupValues?.get(1)?.toIntOrNull()
            }.toSet()
            ids.forEach { id ->
                val left = prefs.getString("${id}_left", null)
                val right = prefs.getString("${id}_right", null)
                if (left != null || right != null || prefs.contains("${id}_split") || prefs.contains("${id}_imageMode")) {
                    val mode = prefs.getString("${id}_imageMode", "fit") ?: "fit"
                    configs[id] = JSONObject()
                        .put("sourceWidgetId", id)
                        .put("left", left ?: "feature:__feature_center")
                        .put("right", right ?: "feature:__settings")
                        .put("split", prefs.getInt("${id}_split", 5).coerceIn(1, 9))
                        .put("imageMode", if (mode in setOf("fit", "stretch", "crop")) mode else "fit")
                }
            }
            org.isoron.uhabits.widgets.PictureWidgetRestoreStore.pending(context).forEach { item ->
                configs[item.sourceWidgetId] = JSONObject()
                    .put("sourceWidgetId", item.sourceWidgetId)
                    .put("left", item.left).put("right", item.right)
                    .put("split", item.split).put("imageMode", item.imageMode)
            }
            org.isoron.uhabits.widgets.WidgetRestoreStore.pending(context)
                .filter { it.type == "picture" }
                .forEach { item ->
                    configs[item.sourceWidgetId] = JSONObject()
                        .put("sourceWidgetId", item.sourceWidgetId)
                        .put("left", item.pictureLeft ?: "feature:__feature_center")
                        .put("right", item.pictureRight ?: "feature:__settings")
                        .put("split", item.pictureSplit)
                        .put("imageMode", item.pictureImageMode)
                }
            val array = JSONArray()
            configs.values.forEach { array.put(it) }
            writePayload(stagingRoot, ENTRY, JSONObject()
                .put("format", "SHIZI_PICTURE_WIDGET_CONFIGS")
                .put("version", 1).put("configs", array).toString())
        }

        override fun validate(payloads: Map<String, ByteArray>) {
            val root = jsonPayload(payloads, ENTRY)
            val configs = root.optJSONArray("configs")
            if (root.optString("format") != "SHIZI_PICTURE_WIDGET_CONFIGS" ||
                root.optInt("version", -1) != 1 || configs == null
            ) throw IOException("图片组件配置备份格式不受支持")
            for (index in 0 until configs.length()) {
                val item = configs.optJSONObject(index) ?: throw IOException("图片组件配置项无效")
                val split = item.optInt("split", -1)
                if (item.optInt("sourceWidgetId", -1) < 0 ||
                    item.opt("left") !is String || item.opt("right") !is String ||
                    split !in 1..9 || item.optString("imageMode") !in setOf("fit", "stretch", "crop")
                ) throw IOException("图片组件配置字段无效")
            }
        }

        override fun snapshot(context: Context, safetyRoot: File) = export(context, safetyRoot)

        override fun restore(context: Context, restoreRoot: File) {
            val array = JSONObject(readPayload(restoreRoot, ENTRY)).getJSONArray("configs")
            val providerClass = org.isoron.uhabits.widgets.PictureShortcutWidgetProvider::class.java.name
            val restored = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                org.isoron.uhabits.widgets.WidgetRestoreStore.Config(
                    sourceWidgetId = item.getInt("sourceWidgetId"),
                    providerClass = providerClass,
                    habitIds = emptyList(),
                    type = "picture",
                    pictureLeft = item.getString("left"),
                    pictureRight = item.getString("right"),
                    pictureSplit = item.getInt("split"),
                    pictureImageMode = item.getString("imageMode")
                )
            }
            org.isoron.uhabits.widgets.WidgetRestoreStore.mergePending(context, restored)
        }

        override fun rollback(context: Context, safetyRoot: File) {
            org.isoron.uhabits.widgets.WidgetRestoreStore.replaceType(context, "picture", emptyList())
            restore(context, safetyRoot)
        }
    }


    /**
     * Stores the habit selections for each habit-based widget instance.
     * Widget IDs are device-local, so restore records are queued and rebound
     * only after the launcher allocates a new ID.
     */
    private object HabitWidgetConfigurationsModule : FullBackupModule {
        private const val ENTRY = "widgets/habit_widget_configurations.json"
        override val id = "habit_widget_configurations"
        override val entries = setOf(ENTRY)
        override val requiredEntries = emptySet<String>()

        override fun export(context: Context, stagingRoot: File) {
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val widgetPreferences = org.isoron.uhabits.core.preferences.WidgetPreferences(
                org.isoron.uhabits.preferences.SharedPreferencesStorage(context)
            )
            val configs = JSONArray()
            val seenKeys = mutableSetOf<String>()
            manager.installedProviders
                .mapNotNull { it.provider }
                .filter { it.packageName == context.packageName }
                .distinct()
                .forEach { provider ->
                    val providerClass = runCatching {
                        Class.forName(provider.className)
                    }.getOrNull() ?: return@forEach
                    if (!org.isoron.uhabits.widgets.BaseWidgetProvider::class.java.isAssignableFrom(providerClass)) {
                        return@forEach
                    }
                    manager.getAppWidgetIds(provider).forEach { widgetId ->
                        val habitIds = widgetPreferences.getHabitIdsFromWidgetId(widgetId)
                        if (habitIds.isNotEmpty()) {
                            seenKeys.add("${provider.className}#${widgetId}")
                            val ids = JSONArray()
                            habitIds.forEach { ids.put(it) }
                            configs.put(JSONObject()
                                .put("sourceWidgetId", widgetId)
                                .put("providerClass", provider.className)
                                .put("habitIds", ids))
                        }
                    }
                }
            org.isoron.uhabits.widgets.WidgetRestoreStore.pending(context)
                .filter { it.type == "habit" && seenKeys.add("${it.providerClass}#${it.sourceWidgetId}") }
                .forEach { item ->
                    val ids = JSONArray()
                    item.habitIds.forEach { ids.put(it) }
                    configs.put(JSONObject()
                        .put("sourceWidgetId", item.sourceWidgetId)
                        .put("providerClass", item.providerClass)
                        .put("habitIds", ids))
                }
            writePayload(stagingRoot, ENTRY, JSONObject()
                .put("format", "SHIZI_HABIT_WIDGET_CONFIGS")
                .put("version", 1)
                .put("configs", configs)
                .toString())
        }

        override fun validate(payloads: Map<String, ByteArray>) {
            val root = jsonPayload(payloads, ENTRY)
            val configs = root.optJSONArray("configs")
            if (root.optString("format") != "SHIZI_HABIT_WIDGET_CONFIGS" ||
                root.optInt("version", -1) != 1 || configs == null
            ) throw IOException("习惯组件配置备份格式不受支持")
            val seen = mutableSetOf<String>()
            for (index in 0 until configs.length()) {
                val item = configs.optJSONObject(index) ?: throw IOException("习惯组件配置项无效")
                val sourceId = item.optInt("sourceWidgetId", -1)
                val provider = item.optString("providerClass")
                val ids = item.optJSONArray("habitIds")
                if (sourceId < 0 || provider.isBlank() || ids == null || ids.length() == 0 ||
                    !seen.add("$provider#$sourceId")
                ) throw IOException("习惯组件配置字段无效")
                for (i in 0 until ids.length()) {
                    if (ids.opt(i) !is Number || ids.optLong(i) <= 0L) {
                        throw IOException("习惯组件包含无效的习惯编号")
                    }
                }
            }
        }

        override fun snapshot(context: Context, safetyRoot: File) = export(context, safetyRoot)

        override fun restore(context: Context, restoreRoot: File) {
            val array = JSONObject(readPayload(restoreRoot, ENTRY)).getJSONArray("configs")
            val restored = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val ids = item.getJSONArray("habitIds")
                WidgetRestoreStore.Config(
                    sourceWidgetId = item.getInt("sourceWidgetId"),
                    providerClass = item.getString("providerClass"),
                    habitIds = (0 until ids.length()).map { ids.getLong(it) }
                )
            }
            WidgetRestoreStore.mergePending(context, restored)
        }

        override fun rollback(context: Context, safetyRoot: File) {
            org.isoron.uhabits.widgets.WidgetRestoreStore.replaceType(context, "habit", emptyList())
            restore(context, safetyRoot)
        }
    }


    /**
     * Clock and batch-add widgets have no per-instance settings. Their global
     * display preferences are backed up separately; this module preserves the
     * number and provider type of their desktop instances.
     */
    private object SimpleWidgetInstancesModule : FullBackupModule {
        private const val ENTRY = "widgets/simple_widget_instances.json"
        override val id = "simple_widget_instances"
        override val entries = setOf(ENTRY)
        override val requiredEntries = emptySet<String>()

        private val providerClasses = setOf(
            org.isoron.uhabits.widgets.DigitalClockWidgetProvider::class.java.name,
            org.isoron.uhabits.widgets.BatchAddWidgetProvider::class.java.name
        )

        override fun export(context: Context, stagingRoot: File) {
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val configs = JSONArray()
            manager.installedProviders
                .mapNotNull { it.provider }
                .filter { it.packageName == context.packageName && it.className in providerClasses }
                .distinct()
                .forEach { provider ->
                    manager.getAppWidgetIds(provider).forEach { widgetId ->
                        configs.put(JSONObject()
                            .put("sourceWidgetId", widgetId)
                            .put("providerClass", provider.className)
                            .put("type", "simple"))
                    }
                }
            org.isoron.uhabits.widgets.WidgetRestoreStore.pending(context)
                .filter { it.type == "simple" && it.providerClass in providerClasses }
                .forEach { item ->
                    if ((0 until configs.length()).none { index ->
                        val existing = configs.getJSONObject(index)
                        existing.optInt("sourceWidgetId") == item.sourceWidgetId &&
                            existing.optString("providerClass") == item.providerClass
                    }) {
                        configs.put(JSONObject()
                            .put("sourceWidgetId", item.sourceWidgetId)
                            .put("providerClass", item.providerClass)
                            .put("type", "simple"))
                    }
                }
            writePayload(stagingRoot, ENTRY, JSONObject()
                .put("format", "SHIZI_SIMPLE_WIDGET_INSTANCES")
                .put("version", 1)
                .put("configs", configs)
                .toString())
        }

        override fun validate(payloads: Map<String, ByteArray>) {
            val root = jsonPayload(payloads, ENTRY)
            val configs = root.optJSONArray("configs")
            if (root.optString("format") != "SHIZI_SIMPLE_WIDGET_INSTANCES" ||
                root.optInt("version", -1) != 1 || configs == null
            ) throw IOException("其他组件实例备份格式不受支持")
            val seen = mutableSetOf<String>()
            for (index in 0 until configs.length()) {
                val item = configs.optJSONObject(index) ?: throw IOException("组件实例配置项无效")
                val sourceId = item.optInt("sourceWidgetId", -1)
                val provider = item.optString("providerClass")
                if (sourceId < 0 || provider !in providerClasses ||
                    item.optString("type") != "simple" ||
                    !seen.add("$provider#$sourceId")
                ) throw IOException("组件实例配置字段无效")
            }
        }

        override fun snapshot(context: Context, safetyRoot: File) = export(context, safetyRoot)

        override fun restore(context: Context, restoreRoot: File) {
            val array = JSONObject(readPayload(restoreRoot, ENTRY)).getJSONArray("configs")
            val restored = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                org.isoron.uhabits.widgets.WidgetRestoreStore.Config(
                    sourceWidgetId = item.getInt("sourceWidgetId"),
                    providerClass = item.getString("providerClass"),
                    habitIds = emptyList(),
                    type = "simple"
                )
            }
            org.isoron.uhabits.widgets.WidgetRestoreStore.mergePending(context, restored)
        }

        override fun rollback(context: Context, safetyRoot: File) {
            org.isoron.uhabits.widgets.WidgetRestoreStore.replaceType(context, "simple", emptyList())
            restore(context, safetyRoot)
        }
    }

    private object AppPreferencesModule : FullBackupModule {
        private const val ENTRY = "settings/preferences.json"
        override val id = "app_preferences"
        override val entries = setOf(ENTRY)
        override val requiredEntries = entries

        override fun export(context: Context, stagingRoot: File) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            writePayload(stagingRoot, ENTRY, encodePreferences(preferences, excludeDeviceLocal = true).toString())
        }

        override fun validate(payloads: Map<String, ByteArray>) {
            validatePreferencesDocument(jsonPayload(payloads, ENTRY), "软件设置")
        }

        override fun snapshot(context: Context, safetyRoot: File) = export(context, safetyRoot)

        override fun restore(context: Context, restoreRoot: File) {
            restorePreferencesDocument(
                PreferenceManager.getDefaultSharedPreferences(context),
                JSONObject(readPayload(restoreRoot, ENTRY)),
                clearExisting = false,
                skipDeviceLocal = true
            )
        }

        override fun rollback(context: Context, safetyRoot: File) = restore(context, safetyRoot)
    }

    private object FloatingTimerModule : FullBackupModule {
        override val id = "floating_timer"
        override val entries = setOf(FLOATING_TIMER_ENTRY)
        // Optional so backups created before this module existed remain valid.
        override val requiredEntries = emptySet<String>()

        override fun export(context: Context, stagingRoot: File) {
            val preferences = context.getSharedPreferences(FLOATING_TIMER_PREFS, Context.MODE_PRIVATE)
            val mode = preferences.getString("mode", "down") ?: "down"
            val duration = preferences.getLong("duration", 0L)
            val base = preferences.getLong("base", duration)
            val running = preferences.getBoolean("running", false)
            val anchor = preferences.getLong("anchor", 0L)
            val elapsed = if (running) (SystemClock.elapsedRealtime() - anchor).coerceAtLeast(0L) else 0L
            val portableBase = if (mode == "up") {
                (base + elapsed).coerceAtLeast(0L)
            } else {
                (base - elapsed).coerceAtLeast(0L)
            }
            val values = JSONObject()
                .put("mode", encodeValue(mode))
                .put("duration", encodeValue(duration))
                .put("base", encodeValue(portableBase))
                .put("running", encodeValue(false))
            val document = JSONObject()
                .put("format", "SHIZI_FLOATING_TIMER")
                .put("version", 1)
                .put("values", values)
            writePayload(stagingRoot, FLOATING_TIMER_ENTRY, document.toString())
        }

        override fun validate(payloads: Map<String, ByteArray>) {
            val root = jsonPayload(payloads, FLOATING_TIMER_ENTRY)
            val values = root.optJSONObject("values")
            if (root.optString("format") != "SHIZI_FLOATING_TIMER" ||
                root.optInt("version", -1) != 1 || values == null
            ) throw IOException("悬浮计时器备份格式不受支持")
            val expected = mapOf(
                "mode" to "string",
                "duration" to "long",
                "base" to "long",
                "running" to "boolean"
            )
            if (values.length() != expected.size) throw IOException("悬浮计时器备份字段不完整")
            expected.forEach { (key, type) ->
                val item = values.optJSONObject(key)
                    ?: throw IOException("悬浮计时器备份缺少字段：$key")
                val value = item.opt("value")
                val validValue = when (type) {
                    "string" -> value is String
                    "long" -> value is Number
                    "boolean" -> value is Boolean
                    else -> false
                }
                if (item.optString("type") != type || !item.has("value") || !validValue) {
                    throw IOException("悬浮计时器备份字段类型错误：$key")
                }
            }
            if (values.getJSONObject("mode").optString("value") !in setOf("up", "down")) {
                throw IOException("悬浮计时器模式无效")
            }
            values.getJSONObject("duration").getLong("value")
            values.getJSONObject("base").getLong("value")
            values.getJSONObject("running").getBoolean("value")
        }

        override fun snapshot(context: Context, safetyRoot: File) {
            val preferences = context.getSharedPreferences(FLOATING_TIMER_PREFS, Context.MODE_PRIVATE)
            writePayload(safetyRoot, FLOATING_TIMER_ENTRY, encodePreferences(preferences, false).toString())
        }

        override fun restore(context: Context, restoreRoot: File) {
            val root = JSONObject(readPayload(restoreRoot, FLOATING_TIMER_ENTRY))
            val values = root.getJSONObject("values")
            val preferences = context.getSharedPreferences(FLOATING_TIMER_PREFS, Context.MODE_PRIVATE)
            val editor = preferences.edit()
                .putString("mode", values.getJSONObject("mode").getString("value"))
                .putLong("duration", values.getJSONObject("duration").getLong("value"))
                .putLong("base", values.getJSONObject("base").getLong("value"))
                .putBoolean("running", false)
                .putLong("anchor", 0L)
            // x/y remain local to this device's screen.
            if (!editor.commit()) throw IOException("悬浮计时器状态恢复失败")
        }

        override fun rollback(context: Context, safetyRoot: File) {
            val preferences = context.getSharedPreferences(FLOATING_TIMER_PREFS, Context.MODE_PRIVATE)
            restorePreferencesDocument(
                preferences,
                JSONObject(readPayload(safetyRoot, FLOATING_TIMER_ENTRY)),
                clearExisting = true,
                skipDeviceLocal = false
            )
        }
    }

    private fun validatePreferencesDocument(root: JSONObject, label: String) {
        if (root.optString("format") != "SHIZI_PREFERENCES" ||
            root.optInt("version", -1) != 1 || root.optJSONObject("values") == null
        ) throw IOException("${label}备份格式不受支持")
        val values = root.getJSONObject("values")
        values.keys().forEach { key ->
            val item = values.optJSONObject(key) ?: throw IOException("${label}备份字段无效：$key")
            val type = item.optString("type")
            if (!item.has("value")) throw IOException("${label}备份字段缺少值：$key")
            val value = item.get("value")
            val valid = when (type) {
                "boolean" -> value is Boolean
                "int", "long", "float" -> value is Number
                "string" -> value is String
                "string_set" -> value is JSONArray && (0 until value.length()).all {
                    value.opt(it) is String
                }
                else -> false
            }
            if (!valid) throw IOException("备份包含不支持的${label}类型或值：$key")
        }
    }

    private fun encodePreferences(preferences: SharedPreferences, excludeDeviceLocal: Boolean): JSONObject {
        val values = JSONObject()
        preferences.all.toSortedMap().forEach { (key, value) ->
            if (excludeDeviceLocal && isDeviceLocalPreference(key)) return@forEach
            encodeValue(value)?.let { values.put(key, it) }
        }
        return JSONObject()
            .put("format", "SHIZI_PREFERENCES")
            .put("version", 1)
            .put("values", values)
    }

    private fun restorePreferencesDocument(
        preferences: SharedPreferences,
        document: JSONObject,
        clearExisting: Boolean,
        skipDeviceLocal: Boolean
    ) {
        validatePreferencesDocument(document, "软件设置")
        val values = document.getJSONObject("values")
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

    private fun encodeValue(value: Any?): JSONObject? {
        val result = JSONObject()
        when (value) {
            is Boolean -> result.put("type", "boolean").put("value", value)
            is Int -> result.put("type", "int").put("value", value)
            is Long -> result.put("type", "long").put("value", value)
            is Float -> result.put("type", "float").put("value", value.toDouble())
            is String -> result.put("type", "string").put("value", value)
            is Set<*> -> result.put("type", "string_set")
                .put("value", JSONArray(value.filterIsInstance<String>().sorted()))
            else -> return null
        }
        return result
    }

    private fun isDeviceLocalPreference(key: String): Boolean =
        key.startsWith("webdav_") || key == "publicBackupFolder" || key == "reminderSound"

    private fun jsonPayload(payloads: Map<String, ByteArray>, entry: String): JSONObject {
        val bytes = payloads[entry] ?: throw IOException("备份缺少文件：$entry")
        return try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (error: Exception) {
            throw IOException("备份文件格式错误：$entry", error)
        }
    }

    private fun writePayload(root: File, entry: String, content: String) {
        val target = File(root, entry)
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
    }

    private fun readPayload(root: File, entry: String): String {
        val file = File(root, entry)
        if (!file.isFile) throw IOException("备份缺少文件：$entry")
        return file.readText(Charsets.UTF_8)
    }
}

// Build validation marker for the 990B3 lightweight component restore wizard.
