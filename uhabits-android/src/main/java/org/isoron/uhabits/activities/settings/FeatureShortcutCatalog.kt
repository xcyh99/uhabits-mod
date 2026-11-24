package org.isoron.uhabits.activities.settings

import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import org.isoron.uhabits.R

/**
 * Single source of truth for entries that can be opened from the home-screen
 * shortcuts popup. It is derived from the actual Settings preference hierarchy,
 * so additions to Feature Center or Settings do not require a second list.
 */
object FeatureShortcutCatalog {
    data class Entry(val key: String, val title: String)

    private val excludedKeys = setOf(
        "featureShortcuts", "desktopShortcuts", "reminderSound", "bugReport", "repairDB", "rateApp",
        "pref_developer", "pref_sync_base_url", "pref_sync_key", "pref_encryption_key"
    )

    fun getEntries(context: Context): List<Entry> {
        val manager = PreferenceManager(context)
        val screen: PreferenceScreen =
            manager.inflateFromResource(context, R.xml.preferences, null)
        val result = mutableListOf(
            Entry("__feature_center", "【板块】 整个功能中心"),
            Entry("__settings", "【板块】 设置首页")
        )

        fun visit(preference: Preference) {
            if (preference is PreferenceCategory) {
                if (preference.key == "devCategory") return
                val categoryKey = preference.key
                val categoryTitle = preference.title?.toString()?.trim().orEmpty()
                if (!categoryKey.isNullOrBlank() && categoryTitle.isNotBlank()) {
                    result.add(Entry(categoryKey, "【板块】 $categoryTitle"))
                }
                for (index in 0 until preference.preferenceCount) {
                    visit(preference.getPreference(index))
                }
                return
            }

            val key = preference.key
            val title = preference.title?.toString()?.trim().orEmpty()
            if (!key.isNullOrBlank() && key !in excludedKeys &&
                title.isNotBlank() && preference.isSelectable
            ) {
                val featureKeys = setOf("parameterCenter", "timeRecords", "floatingTimer", "desktopDoodle", "statusBarDays", "widgetManager")
                val typeLabel = if (key in featureKeys) "【功能】" else "【设置】"
                result.add(Entry(key, "$typeLabel $title"))
            }
        }

        for (index in 0 until screen.preferenceCount) {
            visit(screen.getPreference(index))
        }

        // Individual habits are valid shortcut destinations as well as settings pages.
        // IDs are stable across renames, so changing a habit name does not break its shortcut.
        runCatching {
            val app = context.applicationContext as org.isoron.uhabits.HabitsApplication
            app.component.habitList
                .filter { !it.isArchived && it.id != null }
                .forEach { habit ->
                    result.add(Entry("habit:${habit.id}", "【项目】 ${habit.name}"))
                }
        }

        return result.distinctBy { it.key }
    }
}
