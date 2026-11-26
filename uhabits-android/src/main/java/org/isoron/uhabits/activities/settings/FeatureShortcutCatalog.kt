package org.isoron.uhabits.activities.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.os.Build
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import org.isoron.uhabits.R

/** One catalog for shortcut destinations. Page entries are discovered from nested PreferenceScreens
 * and Activity metadata; leaf parameter controls are deliberately not collected. */
object FeatureShortcutCatalog {
    const val KEY_ALLOWED_ENTRIES = "shortcut_candidate_keys_v1"
    private const val KEY_CATALOG_CACHE = "shortcut_catalog_cache_v1"
    private const val KEY_CATALOG_CACHE_VERSION = "shortcut_catalog_cache_version"
    private const val CATALOG_CACHE_VERSION = 7
    private const val META_KEY = "org.isoron.uhabits.shortcut.key"
    private const val META_TITLE = "org.isoron.uhabits.shortcut.title"
    private const val META_CATEGORY = "org.isoron.uhabits.shortcut.category"
    private const val META_PAGE_PREFIX = "org.isoron.uhabits.shortcut.page."

    data class Entry(
        val key: String,
        val name: String,
        val source: String,
        val category: String,
        val icon: Drawable? = null,
        val activityName: String? = null,
        val extraName: String? = null,
        val extraValue: String? = null
    ) {
        val title: String get() = name
        val widgetTarget: String get() =
            if (key.startsWith("app:") || key.startsWith("system:")) key else "feature:$key"
    }

    private val categories = listOf("功能", "设置", "习惯", "应用", "系统")
    private val excludedKeys = setOf(
        "featureShortcuts", "desktopShortcuts", "shortcutManagement",
        "reminderSound", "bugReport", "repairDB", "rateApp",
        "pref_developer", "pref_sync_base_url", "pref_sync_key", "pref_encryption_key",
        "parameterEmergencyReset", "clearLocalHabitData"
    )

    // Existing destinations whose click handlers open a real feature or an independent settings page.
    // New nested PreferenceScreens and annotated Activities are discovered without editing this list.
    private data class BuiltIn(val key: String, val title: String, val category: String)
    private val builtIns = listOf(
        BuiltIn("__feature_center", "功能板块", "功能"),
        BuiltIn("__settings", "设置首页", "设置"),
        BuiltIn("system:settings", "手机系统设置", "系统"),

        BuiltIn("timeRecords", "时间记录", "功能"),
        BuiltIn("puzzleGames", "益智游戏", "功能"),
        BuiltIn("floatingTimer", "悬浮计时器", "功能"),
        BuiltIn("desktopDoodle", "桌面涂鸦", "功能"),
        BuiltIn("widgetManager", "组件添加", "功能"),
        BuiltIn("widgetRestore", "组件恢复向导", "功能"),
        BuiltIn("createFullBackup", "创建完整备份", "功能"),
        BuiltIn("restoreFullBackup", "恢复完整备份", "功能"),
        BuiltIn("exportProgramApk", "单独导出本机 APK", "功能"),
        BuiltIn("exportDB", "导出完整数据", "功能"),
        BuiltIn("exportLegacyDB", "导出旧版备份", "功能"),
        BuiltIn("exportCSV", "导出 CSV", "功能"),
        BuiltIn("importData", "导入数据", "功能"),

        BuiltIn("parameterCenter", "参数中心", "设置"),
        BuiltIn("startupPage", "启动首页设置", "设置"),
        BuiltIn("statusBarDays", "状态栏天数设置", "设置"),
        BuiltIn("desktopWidgetSettings", "组件中心", "设置"),
        BuiltIn("desktopWidgetTimeSettings", "时间组件设置", "设置"),
        BuiltIn("autoBackup", "自动备份设置", "设置"),
        BuiltIn("webDavSync", "WebDAV 云端管理", "设置"),
        BuiltIn("settingsBackupRestore", "设置备份与恢复", "设置"),
        BuiltIn("reminderCustomize", "提醒设置", "设置"),
        BuiltIn("shortcutManagement", "快捷中心", "设置"),
        BuiltIn("desktopShortcuts", "桌面快捷管理", "设置"),
        BuiltIn("featureShortcuts", "快捷菜单设置", "设置")
    )

    private val recommended = builtIns.filterNot { it.key == "widgetRestore" }.map { it.key }.toSet() + setOf("pictureShortcutWidgetSettings")
    private const val KEY_ALLOWED_MIGRATION_VERSION = "shortcut_candidate_migration_version"
    private const val ALLOWED_MIGRATION_VERSION = 7

    fun getEntries(context: Context, onProgress: ((String) -> Unit)? = null): List<Entry> {
        val result = mutableListOf<Entry>()
        onProgress?.invoke("正在整理应用内置功能")
        builtIns.forEach { result.add(Entry(it.key, it.title, "", it.category)) }
        onProgress?.invoke("内置功能整理完成，共 ${result.size} 项")

        // A nested PreferenceScreen is a page; ordinary Preference/PreferenceCategory leaves
        // are not pages and are intentionally ignored (they are usually individual parameters).
        onProgress?.invoke("正在扫描设置页面")
        runCatching {
            val manager = PreferenceManager(context)
            val root: PreferenceScreen = manager.inflateFromResource(context, R.xml.preferences, null)
            fun visit(preference: Preference) {
                if (preference is PreferenceScreen && preference !== root) {
                    val key = preference.key
                    val title = preference.title?.toString()?.trim().orEmpty()
                    if (!key.isNullOrBlank() && title.isNotBlank() && key !in excludedKeys) {
                        result.add(Entry(key, title, "", "设置", preference.icon))
                    }
                }
                if (preference is PreferenceGroup) {
                    for (i in 0 until preference.preferenceCount) visit(preference.getPreference(i))
                }
            }
            visit(root)
        }

        onProgress?.invoke("设置页面扫描完成，正在扫描独立功能页面")
        // Activity authors can expose a new independent page by adding the three metadata
        // fields below to its manifest declaration. The catalog itself needs no code change.
        runCatching {
            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
            val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, flags)
            }
            packageInfo.activities.orEmpty().forEach { activity ->
                val metadata = activity.metaData ?: return@forEach
                val key = metadata.getString(META_KEY)?.trim().orEmpty()
                val title = metadata.getString(META_TITLE)?.trim().orEmpty()
                val category = metadata.getString(META_CATEGORY)?.trim().orEmpty()
                if (key.isNotBlank() && title.isNotBlank() && category in categories && key !in excludedKeys) {
                    result.add(Entry(key, title, "", category, activityName = activity.name))
                }

                // An Activity may expose additional in-Activity pages through
                // namespaced manifest metadata. Each page gets its own shortcut key
                // and can pass an intent extra to open that page directly.
                metadata.keySet().filter {
                    it.startsWith(META_PAGE_PREFIX) && it.endsWith(".title")
                }.forEach { titleMeta ->
                    val pageKey = titleMeta.removePrefix(META_PAGE_PREFIX).removeSuffix(".title")
                    val pageTitle = metadata.getString(titleMeta)?.trim().orEmpty()
                    val pageCategory = metadata.getString("$META_PAGE_PREFIX$pageKey.category")?.trim().orEmpty()
                    val extraName = metadata.getString("$META_PAGE_PREFIX$pageKey.extraName")?.trim()?.takeIf { it.isNotBlank() }
                    val extraValue = metadata.get("$META_PAGE_PREFIX$pageKey.extraValue")?.toString()
                    if (pageKey.isNotBlank() && pageTitle.isNotBlank() && pageCategory in categories &&
                        pageKey !in excludedKeys) {
                        result.add(Entry(
                            pageKey, pageTitle, "", pageCategory,
                            activityName = activity.name,
                            extraName = extraName,
                            extraValue = extraValue
                        ))
                    }
                }
            }
        }

        onProgress?.invoke("独立功能页面扫描完成，正在登记本应用入口")
        // Include this app itself as a selectable launcher destination.
        runCatching {
            val ownComponent = ComponentName(context, org.isoron.uhabits.activities.habits.list.ListHabitsActivity::class.java)
            result.add(Entry("app:${ownComponent.flattenToString()}", "十字习惯", "", "应用"))
        }

        onProgress?.invoke("本应用入口登记完成，正在扫描习惯列表")
        // Habits are always collected individually and open their complete habit page.
        runCatching {
            val app = context.applicationContext as org.isoron.uhabits.HabitsApplication
            app.component.habitList.filter { !it.isArchived && it.id != null }.forEach {
                result.add(Entry("habit:${it.id}", it.name, "", "习惯"))
            }
        }

        onProgress?.invoke("习惯列表扫描完成，正在扫描手机已安装应用")
        // Installed launcher apps are discovered from the system, not maintained manually.
        runCatching {
            val pm = context.packageManager
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchers = pm.queryIntentActivities(launch, PackageManager.MATCH_ALL)
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
            launchers.forEachIndexed { index, item ->
                val component = ComponentName(item.activityInfo.packageName, item.activityInfo.name)
                result.add(Entry("app:${component.flattenToString()}", item.loadLabel(pm).toString(), "", "应用", item.loadIcon(pm)))
                onProgress?.invoke("正在扫描已安装应用：${index + 1}/${launchers.size}")
            }
        }
        onProgress?.invoke("应用扫描完成，正在整理并保存入口目录")
        // Prefer the first current definition and collapse duplicate visible names
        // within the same category, even if an old alias used a different internal key.
        val entries = result.distinctBy { it.key }
            .distinctBy { "${it.category}:${it.name.trim().lowercase()}" }
        // Full discovery is reserved for shortcut management. Ordinary menus read this snapshot.
        saveCatalogCache(context, entries)
        return entries
    }

    private fun saveCatalogCache(context: Context, entries: List<Entry>) {
        val array = org.json.JSONArray()
        entries.forEach { entry ->
            array.put(org.json.JSONObject().apply {
                put("key", entry.key)
                put("name", entry.name)
                put("source", entry.source)
                put("category", entry.category)
                entry.activityName?.let { put("activityName", it) }
                entry.extraName?.let { put("extraName", it) }
                entry.extraValue?.let { put("extraValue", it) }
            })
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(KEY_CATALOG_CACHE, array.toString())
            .putInt(KEY_CATALOG_CACHE_VERSION, CATALOG_CACHE_VERSION)
            .apply()
    }

    /** Reads the last full catalog snapshot without performing discovery. */
    fun getCachedEntries(context: Context): List<Entry> {
        val store = PreferenceManager.getDefaultSharedPreferences(context)
        val raw = store.getString(KEY_CATALOG_CACHE, null)
        if (!raw.isNullOrBlank() && store.getInt(KEY_CATALOG_CACHE_VERSION, 0) >= CATALOG_CACHE_VERSION) {
            return runCatching {
                val array = org.json.JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val key = item.optString("key")
                    val name = item.optString("name")
                    val category = item.optString("category")
                    if (key.isBlank() || name.isBlank()) null
                    else Entry(
                        key, name, item.optString("source"), category,
                        activityName = item.optString("activityName").takeIf { it.isNotBlank() },
                        extraName = item.optString("extraName").takeIf { it.isNotBlank() },
                        extraValue = item.optString("extraValue").takeIf { it.isNotBlank() }
                    )
                }
            }.getOrDefault(emptyList())
        }
        // Ordinary consumers never trigger discovery. Only shortcut-entry management scans.
        return emptyList()
    }

    /** Lightweight list for ordinary selection menus. */
    fun getSelectedEntries(context: Context): List<Entry> {
        val allowed = getAllowedKeys(context)
        return getCachedEntries(context).filter { it.key in allowed }
    }

    fun getCachedEntry(context: Context, key: String): Entry? =
        getCachedEntries(context).firstOrNull { it.key == key }


    fun getAllowedKeys(context: Context): Set<String> {
        val store = PreferenceManager.getDefaultSharedPreferences(context)
        val version = store.getInt(KEY_ALLOWED_MIGRATION_VERSION, 0)

        // Version 6 starts with the current catalog only. Historical shortcut aliases are not imported.
        if (version < ALLOWED_MIGRATION_VERSION) {
            // v7 adds the direct clock-settings destination without discarding
            // the user's existing v6 selections.
            val current = if (version < 6) recommended.toMutableSet()
                else store.getStringSet(KEY_ALLOWED_ENTRIES, recommended)?.toMutableSet()
                    ?: recommended.toMutableSet()
            current.add("desktopWidgetTimeSettings")
            val ownComponent = ComponentName(
                context,
                org.isoron.uhabits.activities.habits.list.ListHabitsActivity::class.java
            )
            current.add("app:${ownComponent.flattenToString()}")
            store.edit()
                .putStringSet(KEY_ALLOWED_ENTRIES, current)
                .remove("feature_shortcuts")
                .remove("feature_shortcuts_order")
                .remove(KEY_CATALOG_CACHE)
                .putInt(KEY_CATALOG_CACHE_VERSION, 0)
                .putInt(KEY_ALLOWED_MIGRATION_VERSION, ALLOWED_MIGRATION_VERSION)
                .apply()
            return current
        }

        return store.getStringSet(KEY_ALLOWED_ENTRIES, recommended)
            ?.toSet()
            ?: recommended
    }

    /**
     * Creates a direct destination for an Activity-backed shortcut page.
     * The page metadata is discovered from the manifest, so adding another
     * page does not require another hard-coded routing branch.
     */
    fun createIntent(context: Context, key: String): Intent? {
        val cached = getCachedEntries(context).firstOrNull { it.key == key }
        val entry = when {
            cached?.activityName != null -> cached
            cached != null -> null
            else -> null
        }
        // Keep only direct routes that are part of the current catalog.
        val fallback = when (key) {
            "desktopWidgetSettings" -> Intent(context, ComponentCenterActivity::class.java)
            "desktopWidgetTimeSettings" -> Intent(context, DesktopWidgetSettingsActivity::class.java)
                .putExtra("open_time_widget_control_page", true)
            "widgetManager" -> Intent(context, ComponentAddActivity::class.java)
            "pictureShortcutWidgetSettings" -> Intent(context, PictureShortcutWidgetSettingsActivity::class.java)
            "widgetRestore" -> Intent(context, org.isoron.uhabits.widgets.PictureWidgetRestoreActivity::class.java)
            else -> null
        }
        if (entry == null) return fallback?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val activityName = entry.activityName ?: return fallback
        return Intent().setClassName(context, activityName).apply {
            val name = entry.extraName
            if (!name.isNullOrBlank()) {
                when (entry.extraValue?.lowercase()) {
                    "true" -> putExtra(name, true)
                    "false" -> putExtra(name, false)
                    else -> entry.extraValue?.let { putExtra(name, it) }
                }
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    fun getSelectableEntries(context: Context) = getSelectedEntries(context)

    fun saveAllowedKeys(context: Context, keys: Set<String>) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putStringSet(KEY_ALLOWED_ENTRIES, keys).apply()
    }

    fun styledTitle(entry: Entry): CharSequence =
        styledTitle("【${entry.category}】 ${entry.title}")

    fun styledTitle(value: String): CharSequence {
        val clean = value.replace(Regex("【(?:内|外)】\\s*"), "").trim()
        val styled = SpannableString(clean)
        val colors = mapOf(
            "功能" to Color.rgb(52, 120, 212),
            "设置" to Color.rgb(137, 86, 200),
            "习惯" to Color.rgb(38, 155, 101),
            "应用" to Color.rgb(229, 138, 50),
            "系统" to Color.rgb(113, 128, 150)
        )
        Regex("【([^】]+)】").findAll(clean).forEach { match ->
            val color = colors[match.groupValues[1]] ?: return@forEach
            styled.setSpan(ForegroundColorSpan(color), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return styled
    }
}
