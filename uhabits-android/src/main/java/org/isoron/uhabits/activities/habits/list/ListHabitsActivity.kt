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

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.checkSelfPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.BaseExceptionHandler
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.tasks.TaskRunner
import org.isoron.uhabits.core.ui.ThemeSwitcher.Companion.THEME_DARK
import org.isoron.uhabits.core.utils.MidnightTimer
import org.isoron.uhabits.database.AutoBackup
import org.isoron.uhabits.inject.HabitsActivityComponent
import org.isoron.uhabits.inject.HabitsApplicationComponent
import org.isoron.uhabits.inject.create
import org.isoron.uhabits.utils.applyRootViewInsets
import org.isoron.uhabits.utils.dismissCurrentDialog
import org.isoron.uhabits.utils.restartWithFade

class ListHabitsActivity : AppCompatActivity(), Preferences.Listener {

    var pureBlack: Boolean = false
    lateinit var appComponent: HabitsApplicationComponent
    lateinit var component: HabitsActivityComponent
    lateinit var taskRunner: TaskRunner
    lateinit var rootView: ListHabitsRootView
    lateinit var screen: ListHabitsScreen
    lateinit var prefs: Preferences
    lateinit var midnightTimer: MidnightTimer
    private val scope = CoroutineScope(Dispatchers.Main)

    private var permissionAlreadyRequested = false
    private val permissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                scheduleReminders()
            } else {
                Log.i("ListHabitsActivity", "POST_NOTIFICATIONS denied")
            }
        }

    private lateinit var menu: ListHabitsMenu

    override fun onQuestionMarksChanged() {
        invalidateOptionsMenu()
        menu.behavior.onPreferencesChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchPreferences = (application as HabitsApplication).component.preferences
        if (launchPreferences.isHistoryChartWhiteWeightPreviewPending) {
            startActivity(Intent(this, org.isoron.uhabits.activities.settings.SettingsActivity::class.java)
                .putExtra("manage_parameter_preview", true))
            finish()
            return
        }

        if (savedInstanceState == null && intent?.action == Intent.ACTION_MAIN) {
            val launchPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            val mode = launchPrefs.getString("startup_page_mode", "main") ?: "main"
            val target = when (mode) {
                "last" -> launchPrefs.getString("startup_page_last", "main") ?: "main"
                "custom" -> launchPrefs.getString("startup_page_custom", "timeRecords") ?: "timeRecords"
                else -> "main"
            }
            if (target != "main") {
                val destination = when (target) {
                    "timeRecords" -> Intent(this, org.isoron.uhabits.activities.timerecords.TimeRecordsActivity::class.java)
                    "settings", "featureCenter" -> Intent(this, org.isoron.uhabits.activities.settings.SettingsActivity::class.java).apply {
                        if (target == "featureCenter") putExtra("open_feature_center", true)
                    }
                    else -> null
                }
                if (destination != null) {
                    startActivity(destination)
                    finish()
                    return
                }
            }
        }

        appComponent = (applicationContext as HabitsApplication).component
        component = HabitsActivityComponent::class.create(
            parent = appComponent,
            activityContext = this
        )
        component.themeSwitcher.apply()

        prefs = appComponent.preferences
        prefs.addListener(this)
        pureBlack = prefs.isPureBlackEnabled
        midnightTimer = appComponent.midnightTimer
        rootView = component.listHabitsRootView
        screen = component.listHabitsScreen
        taskRunner = appComponent.taskRunner
        menu = component.listHabitsMenu
        Thread.setDefaultUncaughtExceptionHandler(BaseExceptionHandler(this))
        component.listHabitsBehavior.onStartup()
        rootView.applyRootViewInsets()
        setContentView(rootView)
    }

    override fun onPause() {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putString("startup_page_last", "main").apply()
        org.isoron.uhabits.notifications.StatusBarDaysNotifier.update(this)
        midnightTimer.onPause()
        screen.onDetached()
        dismissCurrentDialog()
        super.onPause()
    }

    override fun onResume() {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putString("startup_page_last", "main").apply()
        screen.refresh()
        org.isoron.uhabits.notifications.StatusBarDaysNotifier.update(this)
        screen.onAttached()
        rootView.postInvalidate()
        midnightTimer.onResume()

        if (appComponent.reminderScheduler.hasHabitsWithReminders()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                scheduleReminders()
            } else {
                if (checkSelfPermission(this, POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
                    scheduleReminders()
                } else {
                    // If we have not requested the permission yet, request it. Otherwide do
                    // nothing. This check is necessary to avoid an infinite onResume loop in case
                    // the user denies the permission.
                    if (!permissionAlreadyRequested) {
                        Log.i("ListHabitsActivity", "Requestion permission: POST_NOTIFICATIONS")
                        permissionLauncher.launch(POST_NOTIFICATIONS)
                        permissionAlreadyRequested = true
                    }
                }
            }
        }

        taskRunner.run {
            try {
                AutoBackup(this@ListHabitsActivity).run()
                appComponent.widgetUpdater.updateWidgets()
            } catch (e: Exception) {
                Log.e("ListHabitActivity", "TaskRunner failed", e)
            }
        }
        if (prefs.theme == THEME_DARK && prefs.isPureBlackEnabled != pureBlack) {
            restartWithFade(ListHabitsActivity::class.java)
        }
        parseIntents()
        super.onResume()
    }

    private fun scheduleReminders() {
        appComponent.reminderScheduler.scheduleAll()
    }

    override fun onCreateOptionsMenu(m: Menu): Boolean {
        menu.onCreate(menuInflater, m)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        invalidateOptionsMenu()
        return menu.onItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        screen.onResult(request, result, data)
    }

    private fun parseIntents() {
        if (intent == null) return
        if (intent.action == ACTION_EDIT) {
            val habitId = intent.extras?.getLong("habit")
            val timestampMillis = intent.extras?.getLong("timestamp")
            if (habitId != null && timestampMillis != null) {
                val date = LocalDate.fromUnixTime(timestampMillis)
                component.listHabitsBehavior.onEdit(habitId, date, 0f, 0f)
            }
        }
        intent = null
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        // A launcher tap can bring this task forward while a preview is awaiting a decision.
        if (intent?.action == Intent.ACTION_MAIN && prefs.isHistoryChartWhiteWeightPreviewPending) {
            startActivity(Intent(this, org.isoron.uhabits.activities.settings.SettingsActivity::class.java)
                .putExtra("manage_parameter_preview", true))
            return
        }

        // A launcher tap can bring an existing task to the foreground instead of
        // creating the activity again. Re-apply the configured startup destination
        // whenever the launcher delivers a new MAIN intent to this singleTask root.
        if (intent?.action == Intent.ACTION_MAIN) {
            val launchPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            val mode = launchPrefs.getString("startup_page_mode", "main") ?: "main"
            val target = when (mode) {
                "last" -> launchPrefs.getString("startup_page_last", "main") ?: "main"
                "custom" -> launchPrefs.getString("startup_page_custom", "timeRecords") ?: "timeRecords"
                else -> "main"
            }
            val destination = when (target) {
                "timeRecords" -> Intent(this, org.isoron.uhabits.activities.timerecords.TimeRecordsActivity::class.java)
                "settings", "featureCenter" -> Intent(this, org.isoron.uhabits.activities.settings.SettingsActivity::class.java).apply {
                    if (target == "featureCenter") putExtra("open_feature_center", true)
                }
                else -> null
            }
            if (destination != null) startActivity(destination)
        }
    }

    companion object {
        const val ACTION_EDIT = "org.isoron.uhabits.ACTION_EDIT"
    }
}
