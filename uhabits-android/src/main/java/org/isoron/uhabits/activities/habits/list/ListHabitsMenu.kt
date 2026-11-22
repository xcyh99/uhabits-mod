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

package org.isoron.uhabits.activities.habits.list

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.PopupMenu
import androidx.preference.PreferenceManager
import android.content.Intent
import org.isoron.uhabits.activities.settings.SettingsActivity
import org.isoron.uhabits.activities.timerecords.TimeRecordsActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import me.tatarka.inject.annotations.Inject
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.ThemeSwitcher
import org.isoron.uhabits.core.ui.screens.habits.list.ListHabitsMenuBehavior
import org.isoron.uhabits.inject.ActivityContext
import org.isoron.uhabits.inject.ActivityScope
import org.isoron.uhabits.utils.StyledResources

@Inject
@ActivityScope
class ListHabitsMenu(
    @ActivityContext context: Context,
    private val preferences: Preferences,
    private val themeSwitcher: ThemeSwitcher,
    val behavior: ListHabitsMenuBehavior
) {
    val activity = context as AppCompatActivity
    private var menu: Menu? = null
    private var isSearchActive = false

    fun onCreate(inflater: MenuInflater, menu: Menu) {
        this.menu = menu
        menu.clear()
        inflater.inflate(R.menu.list_habits, menu)
        createSearchBar(menu)
        createMenuItems(menu)
        updateArrows(menu)
    }

    private fun createMenuItems(menu: Menu) {
        val nightModeItem = menu.findItem(R.id.actionToggleNightMode)
        val hideArchivedItem = menu.findItem(R.id.actionHideArchived)
        val hideCompletedItem = menu.findItem(R.id.actionHideCompleted)
        // Bind the shortcut action directly to the menu item. This keeps the
        // toolbar shortcut responsive even when the activity's options-menu
        // callback is rebuilt during invalidateOptionsMenu().
        menu.findItem(R.id.actionShortcuts)?.setOnMenuItemClickListener {
            showShortcutsMenu()
            true
        }
        nightModeItem.isChecked = themeSwitcher.isNightMode
        hideArchivedItem.isChecked = !preferences.showArchived
        hideCompletedItem.isChecked = !preferences.showCompleted
        if (preferences.areQuestionMarksEnabled || preferences.isSkipEnabled) {
            hideCompletedItem.title = activity.resources.getString(R.string.hide_entered)
        } else {
            hideCompletedItem.title = activity.resources.getString(R.string.hide_completed)
        }
    }

    private fun createSearchBar(menu: Menu) {
        val searchContainer = menu.findItem(R.id.actionSearchContainer)
        searchContainer.isVisible = isSearchActive
        menu.setGroupVisible(R.id.actionItems, !isSearchActive)
        with(searchContainer.actionView as SearchView) {
            queryHint = activity.getString(R.string.search)
            isIconified = false
            setQuery(behavior.searchQuery, false)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false
                override fun onQueryTextChange(newText: String): Boolean {
                    behavior.onSearchQueryChanged(newText)
                    return true
                }
            })
            setOnCloseListener {
                isSearchActive = false
                menu.setGroupVisible(R.id.actionItems, true)
                activity.invalidateOptionsMenu()
                true
            }
        }
    }

    private fun updateArrows(menu: Menu) {
        val styledResources = StyledResources(activity)
        val sortManual = menu.findItem(R.id.actionSortManual)
        val sortName = menu.findItem(R.id.actionSortName)
        val sortColor = menu.findItem(R.id.actionSortColor)
        val sortScore = menu.findItem(R.id.actionSortScore)
        val sortStatus = menu.findItem(R.id.actionSortStatus)
        val arrowUp = styledResources.getDrawable(R.attr.iconArrowUp)
        val arrowDown = styledResources.getDrawable(R.attr.iconArrowDown)
        when (preferences.defaultPrimaryOrder) {
            HabitList.Order.BY_NAME_ASC -> sortName.icon = arrowDown
            HabitList.Order.BY_NAME_DESC -> sortName.icon = arrowUp
            HabitList.Order.BY_COLOR_ASC -> sortColor.icon = arrowDown
            HabitList.Order.BY_COLOR_DESC -> sortColor.icon = arrowUp
            HabitList.Order.BY_SCORE_ASC -> sortScore.icon = arrowDown
            HabitList.Order.BY_SCORE_DESC -> sortScore.icon = arrowUp
            HabitList.Order.BY_STATUS_ASC -> sortStatus.icon = arrowDown
            HabitList.Order.BY_STATUS_DESC -> sortStatus.icon = arrowUp
            HabitList.Order.BY_POSITION -> sortManual.icon = arrowUp
        }
    }

    private fun showShortcutsMenu() {
        // Prefer the actual toolbar action view as the popup anchor. The
        // action item is not guaranteed to be a child of the Toolbar when
        // AppCompat rebuilds the options menu, so retain safe fallbacks.
        val actionView = activity.findViewById<android.view.View>(R.id.actionShortcuts)
        val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val anchor = actionView ?: toolbar ?: activity.window.decorView
        val popup = PopupMenu(activity, anchor)
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val savedOrder = prefs.getString("feature_shortcuts_order", null)
            ?.split("|")?.filter { it.isNotBlank() }
        val selected = savedOrder?.takeIf { it.isNotEmpty() }
            ?: prefs.getStringSet("feature_shortcuts", emptySet())?.toList().orEmpty()
        val available = selected.mapNotNull { key -> shortcutTitle(key)?.let { key to it } }
        popup.menu.add(activity.getString(R.string.configure_shortcuts)).setOnMenuItemClickListener {
            activity.startActivity(
                Intent(activity, SettingsActivity::class.java)
                    .putExtra("open_preference_key", "featureShortcuts")
            )
            true
        }
        if (available.isEmpty()) {
            popup.menu.add(activity.getString(R.string.no_shortcuts_configured)).isEnabled = false
        } else {
            available.forEachIndexed { index, pair ->
                popup.menu.add(0, index + 1, index, pair.second).setOnMenuItemClickListener {
                    if (pair.first == "timeRecords") {
                        activity.startActivity(Intent(activity, TimeRecordsActivity::class.java))
                    } else {
                        activity.startActivity(
                            Intent(activity, SettingsActivity::class.java)
                                .putExtra("open_preference_key", pair.first)
                        )
                    }
                    true
                }
            }
        }
        popup.show()
    }

    private fun shortcutTitle(key: String): String? = when (key) {
        "timeRecords" -> activity.getString(R.string.time_records)
        "createFullBackup" -> activity.getString(R.string.full_backup_create_title)
        "restoreFullBackup" -> activity.getString(R.string.full_backup_restore_title)
        "exportDB" -> activity.getString(R.string.export_full_backup)
        "exportLegacyDB" -> activity.getString(R.string.export_legacy_backup)
        "exportCSV" -> activity.getString(R.string.export_to_csv)
        "importData" -> activity.getString(R.string.import_data)
        "webDavSync" -> activity.getString(R.string.webdav_sync)
        "settingsBackupRestore" -> activity.getString(R.string.settings_backup_restore)
        "publicBackupFolder" -> activity.getString(R.string.select_public_backup_folder)
        "clearLocalHabitData" -> activity.getString(R.string.clear_local_habit_data)
        "pref_short_toggle" -> activity.getString(R.string.pref_toggle_title)
        "pref_midnight_delay" -> activity.getString(R.string.pref_midnight_delay_title)
        "pref_skip_enabled" -> activity.getString(R.string.pref_skip_title)
        "pref_unknown_enabled" -> activity.getString(R.string.pref_unknown_title)
        "pref_checkmark_reverse_order" -> activity.getString(R.string.reverse_days)
        "pref_pure_black" -> activity.getString(R.string.use_pure_black)
        "pref_disable_animation" -> activity.getString(R.string.pref_animations_title)
        "pref_widget_opacity" -> activity.getString(R.string.widget_opacity_title)
        "pref_first_weekday" -> activity.getString(R.string.first_day_of_the_week)
        "pref_sticky_notifications" -> activity.getString(R.string.sticky_notifications)
        "reminderCustomize" -> activity.getString(R.string.customize_notification)
        else -> null
    }

    fun onItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.actionToggleNightMode -> {
                behavior.onToggleNightMode()
                return true
            }

            R.id.actionCreateHabit -> {
                behavior.onCreateHabit()
                return true
            }

            R.id.actionFAQ -> {
                behavior.onViewFAQ()
                return true
            }

            R.id.actionAbout -> {
                behavior.onViewAbout()
                return true
            }

            R.id.actionSettings -> {
                behavior.onViewSettings()
                return true
            }

            R.id.actionHideArchived -> {
                behavior.onToggleShowArchived()
                activity.invalidateOptionsMenu()
                return true
            }

            R.id.actionHideCompleted -> {
                behavior.onToggleShowCompleted()
                activity.invalidateOptionsMenu()
                return true
            }

            R.id.actionSortColor -> {
                behavior.onSortByColor()
                return true
            }

            R.id.actionSortManual -> {
                behavior.onSortByManually()
                return true
            }

            R.id.actionSortName -> {
                behavior.onSortByName()
                return true
            }

            R.id.actionSortScore -> {
                behavior.onSortByScore()
                return true
            }

            R.id.actionSortStatus -> {
                behavior.onSortByStatus()
                return true
            }

            R.id.actionShortcuts -> {
                showShortcutsMenu()
                return true
            }

            R.id.actionSearch -> {
                isSearchActive = true
                activity.invalidateOptionsMenu()
                return true
            }

            else -> return false
        }
    }
}
