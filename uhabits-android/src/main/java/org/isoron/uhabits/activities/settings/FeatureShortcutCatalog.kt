package org.isoron.uhabits.activities.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import org.isoron.uhabits.R
import org.json.JSONArray

/** One catalog for all shortcut destination pickers. */
object FeatureShortcutCatalog {
    const val KEY_ALLOWED_ENTRIES = "shortcut_candidate_keys_v1"
    data class Entry(val key: String, val name: String, val source: String, val category: String, val icon: Drawable? = null) {
        val title: String get() = "【$source】【$category】 $name"
        val widgetTarget: String get() = if (key.startsWith("app:") || key.startsWith("system:")) key else "feature:$key"
    }
    private val excludedKeys = setOf("featureShortcuts", "desktopShortcuts", "shortcutManagement", "reminderSound", "bugReport", "repairDB", "rateApp", "pref_developer", "pref_sync_base_url", "pref_sync_key", "pref_encryption_key")
    private val recommended = setOf("__feature_center", "__settings", "parameterCenter", "startupPage", "floatingTimer", "desktopDoodle", "statusBarDays", "timeRecords", "puzzleGames", "desktopWidgetSettings", "widgetManager", "historyWhiteWeight")

    fun getEntries(context: Context): List<Entry> {
        val manager = PreferenceManager(context)
        val screen: PreferenceScreen = manager.inflateFromResource(context, R.xml.preferences, null)
        val result = mutableListOf(
            Entry("__feature_center", "整个功能中心", "内", "功能"),
            Entry("__settings", "设置首页", "内", "设置"),
            Entry("system:settings", "手机系统设置", "外", "设置"),
            Entry("historyWhiteWeight", "历史组件白色数字权重", "内", "功能")
        )
        val store = PreferenceManager.getDefaultSharedPreferences(context)
        val legacyTargets = mutableSetOf<String>().apply {
            addAll(store.getStringSet("feature_shortcuts", emptySet()).orEmpty())
            addAll(store.getString("feature_shortcuts_order", null)?.split("|").orEmpty())
            store.all.values.filterIsInstance<String>().forEach { value ->
                if (value.startsWith("feature:")) add(value.removePrefix("feature:"))
            }
            runCatching {
                val raw = store.getString("desktop_shortcut_entries_v1", null)
                if (!raw.isNullOrBlank()) {
                    val array = JSONArray(raw)
                    for (i in 0 until array.length()) array.optJSONObject(i)?.optString("target")?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        fun visit(preference: Preference, parentCategory: String) {
            if (preference is PreferenceCategory) {
                if (preference.key == "devCategory") return
                val name = preference.title?.toString()?.trim().orEmpty()
                val next = if (preference.key == "featureCenterCategory") "功能" else parentCategory
                if (!preference.key.isNullOrBlank() && name.isNotBlank() && preference.key != "featureCenterCategory" && preference.key in legacyTargets) {
                    result.add(Entry(preference.key, name, "内", next))
                }
                for (i in 0 until preference.preferenceCount) visit(preference.getPreference(i), next)
                return
            }
            val key = preference.key
            val name = preference.title?.toString()?.trim().orEmpty()
            if (!key.isNullOrBlank() && key !in excludedKeys && name.isNotBlank() && preference.isSelectable) {
                result.add(Entry(key, name, "内", if (parentCategory == "功能") "功能" else "设置", preference.icon))
            }
        }
        for (i in 0 until screen.preferenceCount) visit(screen.getPreference(i), "设置")
        runCatching {
            val app = context.applicationContext as org.isoron.uhabits.HabitsApplication
            app.component.habitList.filter { !it.isArchived && it.id != null }.forEach { result.add(Entry("habit:${it.id}", it.name, "内", "项目")) }
        }
        runCatching {
            val pm = context.packageManager
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launch, android.content.pm.PackageManager.MATCH_ALL)
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
                .forEach {
                    val component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                    result.add(Entry("app:${component.flattenToString()}", it.loadLabel(pm).toString(), "外", "应用", it.loadIcon(pm)))
                }
        }
        return result.distinctBy { it.key }
    }

    fun getAllowedKeys(context: Context): Set<String> {
        val store = PreferenceManager.getDefaultSharedPreferences(context)
        store.getStringSet(KEY_ALLOWED_ENTRIES, null)?.let { return it.toSet() }
        val allowed = recommended.toMutableSet()
        allowed.addAll(store.getStringSet("feature_shortcuts", emptySet()).orEmpty())
        runCatching {
            val raw = store.getString("desktop_shortcut_entries_v1", null)
            if (!raw.isNullOrBlank()) {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) array.optJSONObject(i)?.optString("target")?.takeIf { it.isNotBlank() }?.let(allowed::add)
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
    fun getSelectableEntries(context: Context) = getEntries(context).filter { it.key in getAllowedKeys(context) }
    fun saveAllowedKeys(context: Context, keys: Set<String>) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putStringSet(KEY_ALLOWED_ENTRIES, keys).apply()
    }
    fun styledTitle(entry: Entry): CharSequence = styledTitle(entry.title)
    fun styledTitle(value: String): CharSequence {
        val styled = SpannableString(value)
        Regex("【([^】]+)】").findAll(value).forEach { match ->
            val color = when (match.groupValues[1]) {
                "内" -> android.graphics.Color.rgb(22, 163, 74)
                "外" -> android.graphics.Color.rgb(224, 82, 82)
                "设置" -> android.graphics.Color.rgb(224, 161, 46)
                "功能", "应用" -> android.graphics.Color.rgb(54, 166, 217)
                "项目" -> android.graphics.Color.rgb(177, 138, 224)
                else -> android.graphics.Color.GRAY
            }
            styled.setSpan(ForegroundColorSpan(color), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return styled
    }
}
