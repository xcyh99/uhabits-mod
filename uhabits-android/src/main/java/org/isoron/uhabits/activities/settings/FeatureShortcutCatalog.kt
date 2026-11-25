package org.isoron.uhabits.activities.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
    private const val META_KEY = "org.isoron.uhabits.shortcut.key"
    private const val META_TITLE = "org.isoron.uhabits.shortcut.title"
    private const val META_CATEGORY = "org.isoron.uhabits.shortcut.category"

    data class Entry(
        val key: String,
        val name: String,
        val source: String,
        val category: String,
        val icon: Drawable? = null
    ) {
        val title: String get() = "【$category】 $name"
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
        BuiltIn("__feature_center", "功能中心", "功能"),
        BuiltIn("__settings", "设置首页", "设置"),
        BuiltIn("system:settings", "手机系统设置", "系统"),

        BuiltIn("timeRecords", "时间记录", "功能"),
        BuiltIn("puzzleGames", "益智游戏", "功能"),
        BuiltIn("floatingTimer", "悬浮计时器", "功能"),
        BuiltIn("desktopDoodle", "桌面涂鸦", "功能"),
        BuiltIn("widgetManager", "桌面组件管理", "功能"),
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
        BuiltIn("desktopWidgetSettings", "桌面组件设置", "设置"),
        BuiltIn("autoBackup", "自动备份设置", "设置"),
        BuiltIn("webDavSync", "WebDAV 云端管理", "设置"),
        BuiltIn("settingsBackupRestore", "设置备份与恢复", "设置"),
        BuiltIn("reminderCustomize", "提醒设置", "设置"),
        BuiltIn("shortcutManagement", "快捷管理", "设置"),
        BuiltIn("desktopShortcuts", "桌面快捷管理", "设置"),
        BuiltIn("featureShortcuts", "快捷菜单设置", "设置")
    )

    private val recommended = builtIns.map { it.key }.toSet()

    fun getEntries(context: Context): List<Entry> {
        val result = mutableListOf<Entry>()
        builtIns.forEach { result.add(Entry(it.key, it.title, if (it.key.startsWith("system:")) "外" else "内", it.category)) }

        // A nested PreferenceScreen is a page; ordinary Preference/PreferenceCategory leaves
        // are not pages and are intentionally ignored (they are usually individual parameters).
        runCatching {
            val manager = PreferenceManager(context)
            val root: PreferenceScreen = manager.inflateFromResource(context, R.xml.preferences, null)
            fun visit(preference: Preference) {
                if (preference is PreferenceScreen && preference !== root) {
                    val key = preference.key
                    val title = preference.title?.toString()?.trim().orEmpty()
                    if (!key.isNullOrBlank() && title.isNotBlank() && key !in excludedKeys) {
                        result.add(Entry(key, title, "内", "设置", preference.icon))
                    }
                }
                if (preference is PreferenceGroup) {
                    for (i in 0 until preference.preferenceCount) visit(preference.getPreference(i))
                }
            }
            visit(root)
        }

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
                    result.add(Entry(key, title, "内", category))
                }
            }
        }

        // Habits are always collected individually and open their complete habit page.
        runCatching {
            val app = context.applicationContext as org.isoron.uhabits.HabitsApplication
            app.component.habitList.filter { !it.isArchived && it.id != null }.forEach {
                result.add(Entry("habit:${it.id}", it.name, "内", "习惯"))
            }
        }

        // Installed launcher apps are discovered from the system, not maintained manually.
        runCatching {
            val pm = context.packageManager
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launch, PackageManager.MATCH_ALL)
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
                .forEach {
                    val component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                    result.add(Entry("app:${component.flattenToString()}", it.loadLabel(pm).toString(), "外", "应用", it.loadIcon(pm)))
                }
        }
        val entries = result.distinctBy { it.key }
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
            })
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(KEY_CATALOG_CACHE, array.toString()).apply()
    }

    /** Reads the last full catalog snapshot without performing discovery. */
    fun getCachedEntries(context: Context): List<Entry> {
        val store = PreferenceManager.getDefaultSharedPreferences(context)
        val raw = store.getString(KEY_CATALOG_CACHE, null)
        if (!raw.isNullOrBlank()) {
            return runCatching {
                val array = org.json.JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val key = item.optString("key")
                    val name = item.optString("name")
                    val category = item.optString("category")
                    if (key.isBlank() || name.isBlank()) null
                    else Entry(key, name, item.optString("source"), category)
                }
            }.getOrDefault(emptyList())
        }
        // First-run fallback: built-in defaults only; never trigger a global scan.
        return builtIns.map { Entry(it.key, it.title, "内", it.category) }
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
        store.getStringSet(KEY_ALLOWED_ENTRIES, null)?.let { return it.toSet() }
        val allowed = recommended.toMutableSet()
        allowed.addAll(store.getStringSet("feature_shortcuts", emptySet()).orEmpty())
        runCatching {
            val raw = store.getString("desktop_shortcut_entries_v1", null)
            if (!raw.isNullOrBlank()) {
                val array = org.json.JSONArray(raw)
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.optString("target")?.takeIf { it.isNotBlank() }?.let(allowed::add)
                }
            }
        }
        store.all.values.forEach { value ->
            if (value is String) when {
                value.startsWith("feature:") -> allowed.add(value.removePrefix("feature:"))
                value.startsWith("app:") || value.startsWith("system:") -> allowed.add(value)
            }
        }
        store.edit().putStringSet(KEY_ALLOWED_ENTRIES, allowed).apply()
        return allowed
    }

    fun getSelectableEntries(context: Context) = getSelectedEntries(context)

    fun saveAllowedKeys(context: Context, keys: Set<String>) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putStringSet(KEY_ALLOWED_ENTRIES, keys).apply()
    }

    fun styledTitle(entry: Entry): CharSequence = styledTitle(entry.title)

    fun styledTitle(value: String): CharSequence {
        val styled = SpannableString(value)
        Regex("【([^】]+)】").findAll(value).forEach { match ->
            val color = when (match.groupValues[1]) {
                "功能" -> android.graphics.Color.rgb(52, 120, 212)
                "设置" -> android.graphics.Color.rgb(137, 86, 200)
                "习惯" -> android.graphics.Color.rgb(38, 155, 101)
                "应用" -> android.graphics.Color.rgb(229, 138, 50)
                "系统" -> android.graphics.Color.rgb(113, 128, 150)
                else -> android.graphics.Color.GRAY
            }
            styled.setSpan(ForegroundColorSpan(color), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return styled
    }
}
