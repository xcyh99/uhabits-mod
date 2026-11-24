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
package org.isoron.uhabits.activities.settings

import androidx.appcompat.app.AlertDialog
import android.app.backup.BackupManager
import android.content.Intent
import androidx.preference.PreferenceManager
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.BaseAdapter
import android.widget.Toast
import android.text.InputType
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.JavaLocalDateFormatter
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.habits.list.RESULT_BUG_REPORT
import org.isoron.uhabits.activities.habits.list.ListHabitsActivity
import org.isoron.uhabits.activities.habits.list.RESULT_EXPORT_CSV
import org.isoron.uhabits.activities.habits.list.RESULT_EXPORT_DB
import org.isoron.uhabits.activities.habits.list.RESULT_EXPORT_LEGACY_DB
import org.isoron.uhabits.activities.habits.list.RESULT_IMPORT_DATA
import org.isoron.uhabits.activities.habits.list.RESULT_REPAIR_DB
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.models.sqlite.SQLiteHabitList
import org.isoron.uhabits.database.WebDavSync
import org.isoron.uhabits.database.FullBackupManager
import org.isoron.uhabits.database.AutoBackup
import org.isoron.uhabits.database.WebDavFullBackup
import org.isoron.uhabits.database.WebDavSettingsBackup
import org.isoron.uhabits.database.TwoWaySyncPreview
import org.isoron.uhabits.database.DatabaseSyncMergeWriter
import org.isoron.uhabits.database.DatabaseSyncSafetyBackup
import org.isoron.uhabits.core.sync.SyncConflictSide
import org.isoron.uhabits.core.sync.SyncEntryConflictChoice
import org.isoron.uhabits.core.sync.SyncHabitSettingsConflictChoice
import org.isoron.uhabits.core.sync.TwoWaySyncResolution
import org.isoron.uhabits.core.sync.SyncLegacyHabitPair
import org.isoron.uhabits.core.sync.TwoWaySyncSnapshotRemapper
import org.isoron.uhabits.core.sync.TwoWaySyncPlanner
import org.isoron.uhabits.core.ui.NotificationTray
import org.isoron.uhabits.notifications.AndroidNotificationTray.Companion.createAndroidNotificationChannel
import org.isoron.uhabits.notifications.RingtoneManager
import org.isoron.uhabits.utils.WebDavCredentialsStore
import org.isoron.uhabits.utils.StyledResources
import org.isoron.uhabits.utils.applyBottomInset
import org.isoron.uhabits.utils.startActivitySafely
import org.isoron.uhabits.widgets.WidgetUpdater
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
    private var sharedPrefs: SharedPreferences? = null
    private var ringtoneManager: RingtoneManager? = null
    private lateinit var prefs: Preferences
    private var widgetUpdater: WidgetUpdater? = null

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RINGTONE_REQUEST_CODE -> {
                ringtoneManager!!.update(data)
                updateRingtoneDescription()
                return
            }
            PUBLIC_BACKUP_REQUEST_CODE -> {
                val uri = data?.data ?: return
                val flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)
                sharedPrefs?.edit()?.putString("publicBackupFolder", uri.toString())?.apply()
                updatePublicBackupFolderSummary()
                updateAutoBackupStatusSummary()
                return
            }
            FULL_BACKUP_CREATE_REQUEST_CODE -> {
                if (resultCode != android.app.Activity.RESULT_OK) return
                val uri = data?.data ?: return
                createFullBackup(uri)
                return
            }
            FULL_BACKUP_RESTORE_REQUEST_CODE -> {
                if (resultCode != android.app.Activity.RESULT_OK) return
                val uri = data?.data ?: return
                inspectAndConfirmRestore(uri)
                return
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)
        val appContext = requireContext().applicationContext
        if (appContext is HabitsApplication) {
            prefs = appContext.component.preferences
            widgetUpdater = appContext.component.widgetUpdater
        }
        setResultOnPreferenceClick("importData", RESULT_IMPORT_DATA)
        setResultOnPreferenceClick("exportCSV", RESULT_EXPORT_CSV)
        setResultOnPreferenceClick("exportDB", RESULT_EXPORT_DB)
        setResultOnPreferenceClick("exportLegacyDB", RESULT_EXPORT_LEGACY_DB)
        setResultOnPreferenceClick("repairDB", RESULT_REPAIR_DB)
        setResultOnPreferenceClick("bugReport", RESULT_BUG_REPORT)
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        // NOP
    }

    override fun onPause() {
        sharedPrefs!!.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val sr = StyledResources(context!!)
        view.setBackgroundColor(sr.getColor(R.attr.contrast0))
        super.onViewCreated(view, savedInstanceState)
        val targetPreference = requireActivity().intent.getStringExtra("open_preference_key")
        val openFeatureCenter = requireActivity().intent.getBooleanExtra("open_feature_center", false)
        if ((targetPreference != null && targetPreference != "widgetManager") || openFeatureCenter) {
            view.post {
                if (targetPreference == "historyWhiteWeight") {
                    showHistoryWhiteWeightSetting()
                    return@post
                }
                if (targetPreference == "checkmarkWhiteWeight") {
                    showCheckmarkWhiteWeightSetting()
                    return@post
                }
                val key = when (targetPreference) {
                    "autoBackupNow", "autoBackupStatus", "publicBackupFolder" -> "autoBackup"
                    else -> targetPreference ?: "featureCenterCategory"
                }
                val directActionKeys = setOf(
                    "createFullBackup",
                    "restoreFullBackup",
                    "exportDB",
                    "exportLegacyDB",
                    "exportCSV",
                    "importData",
                    "featureShortcuts",
                    "widgetManager",
                    "webDavSync",
                    "settingsBackupRestore",
                    "autoBackup",
                    "clearLocalHabitData",
                    "reminderCustomize",
                    "startupPage",
                    "floatingTimer",
                    "desktopDoodle",
                    "statusBarDays",
                    "parameterCenter",
                    "parameterEmergencyReset",
                    "desktopShortcuts"
                )
                if (key in directActionKeys) {
                    findPreference(key)?.performClick()
                } else {
                    scrollToPreference(key)
                }
            }
        }
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater?,
        parent: ViewGroup?,
        savedInstanceState: Bundle?
    ): RecyclerView? {
        return super.onCreateRecyclerView(inflater, parent, savedInstanceState)
            .also { it.applyBottomInset() }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val key = preference.key ?: return false
        when (key) {
            "createFullBackup" -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, FullBackupManager(requireContext()).suggestedFileName())
                }
                startActivityForResult(intent, FULL_BACKUP_CREATE_REQUEST_CODE)
                return true
            }
            "restoreFullBackup" -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                }
                startActivityForResult(intent, FULL_BACKUP_RESTORE_REQUEST_CODE)
                return true
            }
            "timeRecords" -> {
                startActivity(Intent(requireContext(), org.isoron.uhabits.activities.timerecords.TimeRecordsActivity::class.java))
                return true
            }
            "parameterCenter" -> {
                showParameterCenter()
                return true
            }
            "parameterEmergencyReset" -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("参数紧急恢复")
                    .setMessage("将参数中心全部恢复为安全默认值（历史组件和标记组件白色数字权重均恢复为 6.6）。此操作不会删除或修改习惯、打卡记录和时间记录。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确认恢复") { _, _ ->
                        prefs.restoreParameterDefaults()
                        widgetUpdater?.updateWidgets()
                        Toast.makeText(requireContext(), "参数已恢复为安全默认值（两个权重均为 6.6）", Toast.LENGTH_LONG).show()
                    }
                    .show()
                return true
            }
            "widgetManager" -> {
                showWidgetManager()
                return true
            }
            "startupPage" -> {
                showStartupPageSettings()
                return true
            }
            "floatingTimer" -> {
                val context = requireContext()
                if (!Settings.canDrawOverlays(context)) {
                    Toast.makeText(context, "请允许十字习惯 APP 显示在其他应用上层，然后再次点击悬浮计时器", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                    startActivity(intent)
                } else {
                    val intent = Intent(context, FloatingTimerService::class.java).setAction(FloatingTimerService.ACTION_SETUP)
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
                }
                return true
            }
            "desktopDoodle" -> {
                val context = requireContext()
                if (!Settings.canDrawOverlays(context)) {
                    Toast.makeText(context, "请允许十字习惯 APP 显示在其他应用上层，然后再次点击桌面涂鸦", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                    startActivity(intent)
                } else {
                    val intent = Intent(context, DesktopDoodleService::class.java).setAction(DesktopDoodleService.ACTION_START)
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
                    // Bring the system launcher forward so doodling starts on the desktop.
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                }
                return true
            }
            "statusBarDays" -> {
                showStatusBarDaysSettings()
                return true
            }
            "featureShortcuts" -> {
                showFeatureShortcutSettings()
                return true
            }
            "desktopShortcuts" -> {
                startActivity(Intent(requireContext(), DesktopShortcutActivity::class.java))
                return true
            }
            "reminderSound" -> {
                showRingtonePicker()
                return true
            }
            "reminderCustomize" -> {
                createAndroidNotificationChannel(requireContext())
                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                intent.putExtra(Settings.EXTRA_CHANNEL_ID, NotificationTray.REMINDERS_CHANNEL_ID)
                startActivity(intent)
                return true
            }
            "rateApp" -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.playStoreURL)))
                activity?.startActivitySafely(intent)
                return true
            }
            "webDavSync" -> {
                showWebDavSettings()
                return true
            }
            "settingsBackupRestore" -> {
                showSettingsBackupRestore()
                return true
            }
            "clearLocalHabitData" -> {
                confirmClearLocalHabitData()
                return true
            }
            "autoBackup" -> {
                showAutoBackupSettings()
                return true
            }
            "publicBackupFolder" -> {
                openPublicBackupFolderPicker()
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onResume() {
        super.onResume()
        ringtoneManager = RingtoneManager(requireActivity())
        sharedPrefs = preferenceManager.sharedPreferences
        sharedPrefs!!.registerOnSharedPreferenceChangeListener(this)
        if (!prefs.isDeveloper) {
            val devCategory = findPreference("devCategory") as PreferenceCategory
            devCategory.isVisible = false
        }
        updateWeekdayPreference()
        updatePublicBackupFolderSummary()
        updateAutoBackupStatusSummary()

        findPreference("webDavSync").title =
            "☆ " + getString(R.string.webdav_sync)
        findPreference("reminderSound").isVisible = false
    }


    fun openBatchWidgetPicker(intent: Intent) {
        showWidgetManager()
    }


    private fun showParameterCenter() {
        AlertDialog.Builder(requireContext())
            .setTitle("参数中心")
            .setItems(arrayOf("历史组件白色数字权重", "标记组件白色数字权重")) { _, which ->
                when (which) {
                    0 -> showHistoryWhiteWeightSetting()
                    1 -> showCheckmarkWhiteWeightSetting()
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showHistoryWhiteWeightSetting() {
        val context = requireContext()
        val current = prefs.committedHistoryChartWhiteWeight
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true)
            setText(current.toString())
            setSelection(text.length)
            hint = "范围：0.1 – 100.0"
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
            addView(input)
            addView(TextView(context).apply {
                text = "数值越大，历史打卡方格越倾向使用白色数字。正式值：$current。\n有效范围：0.1–100.0。每次正式修改前会自动保存上一有效值。"
                textSize = 12f
                setPadding(0, 12, 0, 0)
            })
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("历史组件白色数字权重")
            .setView(container)
            .setNeutralButton("桌面预览", null)
            .setNegativeButton("恢复默认", null)
            .setPositiveButton("直接确定", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim().toDoubleOrNull()
                if (value == null || value.isNaN() || value.isInfinite() ||
                    value < Preferences.MIN_HISTORY_WHITE_WEIGHT ||
                    value > Preferences.MAX_HISTORY_WHITE_WEIGHT
                ) {
                    input.error = "请输入 0.1–100.0 之间的有效数字"
                    return@setOnClickListener
                }
                runCatching { prefs.committedHistoryChartWhiteWeight = value }
                    .onSuccess {
                        widgetUpdater?.updateWidgets()
                        Toast.makeText(context, "已保存为正式值：$value", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .onFailure {
                        Toast.makeText(context, "保存失败，原参数保持不变", Toast.LENGTH_LONG).show()
                    }
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                prefs.restoreParameterDefaults()
                widgetUpdater?.updateWidgets()
                Toast.makeText(context, "参数已恢复默认值 6.6", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val value = input.text.toString().trim().toDoubleOrNull()
                if (value == null || value.isNaN() || value.isInfinite() ||
                    value < Preferences.MIN_HISTORY_WHITE_WEIGHT ||
                    value > Preferences.MAX_HISTORY_WHITE_WEIGHT
                ) {
                    input.error = "请输入 0.1–100.0 之间的有效数字"
                    return@setOnClickListener
                }
                runCatching { prefs.beginHistoryChartWhiteWeightPreview(value) }
                    .onSuccess {
                        widgetUpdater?.updateWidgets()
                        dialog.dismiss()
                        Toast.makeText(context, "临时预览已应用：$value", Toast.LENGTH_SHORT).show()
                        val home = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(home)
                        // Leave the main activity at the top of the app task. Its next
                        // launcher delivery will route to the preview manager.
                        requireActivity().finish()
                    }
                    .onFailure {
                        Toast.makeText(context, "无法启动预览，正式参数未改变", Toast.LENGTH_LONG).show()
                    }
            }
        }
        dialog.show()
    }

    private fun showCheckmarkWhiteWeightSetting() {
        val context = requireContext()
        val current = prefs.committedCheckmarkWhiteWeight
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true)
            setText(current.toString())
            setSelection(text.length)
            hint = "范围：0.1 – 100.0"
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
            addView(input)
            addView(TextView(context).apply {
                text = "数值越大，标记组件已打卡数字越倾向使用白色数字。正式值：$current。\n有效范围：0.1–100.0。每次正式修改前会自动保存上一有效值。"
                textSize = 12f
                setPadding(0, 12, 0, 0)
            })
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("标记组件白色数字权重")
            .setView(container)
            .setNeutralButton("桌面预览", null)
            .setNegativeButton("恢复默认", null)
            .setPositiveButton("直接确定", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim().toDoubleOrNull()
                if (value == null || value.isNaN() || value.isInfinite() ||
                    value < Preferences.MIN_HISTORY_WHITE_WEIGHT ||
                    value > Preferences.MAX_HISTORY_WHITE_WEIGHT
                ) {
                    input.error = "请输入 0.1–100.0 之间的有效数字"
                    return@setOnClickListener
                }
                runCatching { prefs.committedCheckmarkWhiteWeight = value }
                    .onSuccess {
                        widgetUpdater?.updateWidgets()
                        Toast.makeText(context, "已保存为正式值：$value", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .onFailure {
                        Toast.makeText(context, "保存失败，原参数保持不变", Toast.LENGTH_LONG).show()
                    }
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                prefs.committedCheckmarkWhiteWeight = Preferences.DEFAULT_CHECKMARK_WHITE_WEIGHT
                widgetUpdater?.updateWidgets()
                Toast.makeText(context, "标记组件权重已恢复默认值 6.6", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val value = input.text.toString().trim().toDoubleOrNull()
                if (value == null || value.isNaN() || value.isInfinite() ||
                    value < Preferences.MIN_HISTORY_WHITE_WEIGHT ||
                    value > Preferences.MAX_HISTORY_WHITE_WEIGHT
                ) {
                    input.error = "请输入 0.1–100.0 之间的有效数字"
                    return@setOnClickListener
                }
                runCatching { prefs.beginCheckmarkWhiteWeightPreview(value) }
                    .onSuccess {
                        widgetUpdater?.updateWidgets()
                        dialog.dismiss()
                        Toast.makeText(context, "临时预览已应用：$value", Toast.LENGTH_SHORT).show()
                        val home = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(home)
                        // Leave the main activity at the top of the app task. Its next
                        // launcher delivery will route to the preview manager.
                        requireActivity().finish()
                    }
                    .onFailure {
                        Toast.makeText(context, "无法启动预览，正式参数未改变", Toast.LENGTH_LONG).show()
                    }
            }
        }
        dialog.show()
    }

    /** Called by SettingsActivity when the launcher returns to an active preview. */
    fun openParameterPreviewManager() {
        if (prefs.checkmarkWhiteWeightPreview != null) {
            openCheckmarkParameterPreviewManager()
            return
        }
        val context = requireContext()
        val preview = prefs.historyChartWhiteWeightPreview ?: return
        val committed = prefs.committedHistoryChartWhiteWeight
        prefs.markHistoryChartWhiteWeightPreviewHandled()
        AlertDialog.Builder(context)
            .setTitle("临时预览管理")
            .setMessage("当前正式值：$committed\n临时预览值：$preview\n\n请选择如何处理这次预览。")
            .setCancelable(false)
            .setPositiveButton("保存为永久配置") { _, _ ->
                val saved = prefs.saveHistoryChartWhiteWeightPreview()
                widgetUpdater?.updateWidgets()
                Toast.makeText(context, "已保存为正式值：$saved", Toast.LENGTH_SHORT).show()
                returnToConfiguredStartup()
            }
            .setNegativeButton("取消本次预览") { _, _ ->
                val restored = prefs.cancelHistoryChartWhiteWeightPreview()
                widgetUpdater?.updateWidgets()
                Toast.makeText(context, "已恢复正式值：$restored", Toast.LENGTH_SHORT).show()
                returnToConfiguredStartup()
            }
            .setNeutralButton("暂时不处理") { _, _ ->
                prefs.markHistoryChartWhiteWeightPreviewHandled()
                Toast.makeText(context, "暂时保留预览值：$preview", Toast.LENGTH_SHORT).show()
                returnToConfiguredStartup()
            }
            .show()
    }

    private fun openCheckmarkParameterPreviewManager() {
        val context = requireContext()
        val preview = prefs.checkmarkWhiteWeightPreview ?: return
        val committed = prefs.committedCheckmarkWhiteWeight
        prefs.markCheckmarkWhiteWeightPreviewHandled()
        AlertDialog.Builder(context)
            .setTitle("临时预览管理")
            .setMessage("当前正式值：$committed\n临时预览值：$preview\n\n请选择如何处理这次预览。")
            .setCancelable(false)
            .setPositiveButton("保存为永久配置") { _, _ ->
                val saved = prefs.saveCheckmarkWhiteWeightPreview()
                widgetUpdater?.updateWidgets()
                Toast.makeText(context, "已保存为正式值：$saved", Toast.LENGTH_SHORT).show()
                returnToConfiguredStartup()
            }
            .setNegativeButton("取消本次预览") { _, _ ->
                val restored = prefs.cancelCheckmarkWhiteWeightPreview()
                widgetUpdater?.updateWidgets()
                Toast.makeText(context, "已恢复正式值：$restored", Toast.LENGTH_SHORT).show()
                returnToConfiguredStartup()
            }
            .setNeutralButton("暂时不处理") { _, _ ->
                prefs.markCheckmarkWhiteWeightPreviewHandled()
                Toast.makeText(context, "暂时保留预览值：$preview", Toast.LENGTH_SHORT).show()
                returnToConfiguredStartup()
            }
            .show()
    }

    private fun returnToConfiguredStartup() {
        val context = requireContext()
        val intent = Intent(context, ListHabitsActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        requireActivity().finish()
    }

    private fun showStartupPageSettings() {
        val store = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val modes = arrayOf("主界面", "上次打开的界面", "指定其他界面")
        val current = store.getString("startup_page_mode", "main") ?: "main"
        val selected = when (current) { "last" -> 1; "custom" -> 2; else -> 0 }
        AlertDialog.Builder(requireContext())
            .setTitle("启动首页设置")
            .setSingleChoiceItems(modes, selected) { dialog, which ->
                when (which) {
                    0 -> store.edit().putString("startup_page_mode", "main").apply()
                    1 -> store.edit().putString("startup_page_mode", "last").apply()
                    2 -> {
                        val pages = arrayOf("时间记录", "设置页面", "功能中心")
                        val values = arrayOf("timeRecords", "settings", "featureCenter")
                        val currentPage = store.getString("startup_page_custom", "timeRecords")
                        val pageIndex = values.indexOf(currentPage).let { if (it < 0) 0 else it }
                        AlertDialog.Builder(requireContext())
                            .setTitle("选择启动页面")
                            .setSingleChoiceItems(pages, pageIndex) { pageDialog, pageWhich ->
                                store.edit()
                                    .putString("startup_page_mode", "custom")
                                    .putString("startup_page_custom", values[pageWhich])
                                    .apply()
                                Toast.makeText(requireContext(), "启动首页已设置为：${pages[pageWhich]}", Toast.LENGTH_SHORT).show()
                                pageDialog.dismiss()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
                if (which != 2) Toast.makeText(requireContext(), "启动首页已设置为：${modes[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showStatusBarDaysSettings() {
        val context = requireContext()
        val store = PreferenceManager.getDefaultSharedPreferences(context)
        val modes = arrayOf("关闭", "距离上次打卡", "倒计时", "正计时")
        val modeValues = arrayOf("off", "checkin", "countdown", "elapsed")
        val current = store.getString("status_bar_days_mode", "off") ?: "off"
        val selected = modeValues.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(context)
            .setTitle("状态栏天数")
            .setSingleChoiceItems(modes, selected) { dialog, which ->
                val mode = modeValues[which]
                if (mode == "off") {
                    store.edit()
                        .putString("status_bar_days_mode", "off")
                        .putStringSet("status_bar_days_items", emptySet())
                        .apply()
                    org.isoron.uhabits.notifications.StatusBarDaysNotifier.update(context)
                    Toast.makeText(context, "状态栏项目已全部关闭", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setSingleChoiceItems
                }

                val labels: List<String>
                val ids: List<String>
                val title: String
                if (mode == "checkin") {
                    val habits = (context.applicationContext as HabitsApplication).component.habitList
                        .filter { !it.isArchived && it.id != null }
                    if (habits.isEmpty()) {
                        Toast.makeText(context, "目前没有可选择的习惯", Toast.LENGTH_SHORT).show()
                        return@setSingleChoiceItems
                    }
                    labels = habits.map { it.name }
                    ids = habits.map { it.id!!.toString() }
                    title = "勾选要显示的习惯（可多选）"
                } else {
                    val type = if (mode == "countdown") {
                        org.isoron.uhabits.activities.timerecords.TimeRecord.COUNTDOWN
                    } else {
                        org.isoron.uhabits.activities.timerecords.TimeRecord.ELAPSED
                    }
                    val records = org.isoron.uhabits.activities.timerecords.TimeRecordStore
                        .getAll(context).filter { it.type == type }
                    if (records.isEmpty()) {
                        Toast.makeText(context, "时间管理中还没有对应的项目，请先创建", Toast.LENGTH_SHORT).show()
                        return@setSingleChoiceItems
                    }
                    labels = records.map { it.title }
                    ids = records.map { it.id }
                    title = if (mode == "countdown") "勾选要显示的倒计时项目（可多选）"
                        else "勾选要显示的正计时项目（可多选）"
                }

                val configured = store.getStringSet("status_bar_days_items", emptySet()).orEmpty()
                val checkedItems = configured.toMutableSet()
                if (configured.isEmpty() && current == mode) {
                    val legacyId = if (mode == "checkin") {
                        store.getLong("status_bar_days_habit_id", -1L).takeIf { it >= 0 }?.toString()
                    } else store.getString("status_bar_days_record_id", null)
                    if (legacyId != null) checkedItems.add("$mode:$legacyId")
                }
                val checked = BooleanArray(ids.size) { index -> "$mode:${ids[index]}" in checkedItems }
                val selectedNow = checked.copyOf()
                fun saveSelection() {
                    val updated = configured.toMutableSet()
                    // Replace only this category; selections in the other categories remain intact.
                    updated.removeAll { it.startsWith("$mode:") }
                    ids.forEachIndexed { index, id ->
                        if (selectedNow[index]) updated.add("$mode:$id")
                    }
                    val nextMode = if (updated.isEmpty()) "off" else mode
                    store.edit()
                        .putString("status_bar_days_mode", nextMode)
                        .putStringSet("status_bar_days_items", updated)
                        .apply()
                    org.isoron.uhabits.notifications.StatusBarDaysNotifier.update(context)
                    Toast.makeText(
                        context,
                        if (selectedNow.count { it } == 0) "已取消此类项目的显示"
                        else "已选择 ${selectedNow.count { it }} 个项目",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMultiChoiceItems(labels.toTypedArray(), checked) { _, index, isChecked ->
                        selectedNow[index] = isChecked
                    }
                    .setNegativeButton("取消", null)
                    .setNeutralButton("确定") { pickDialog, _ ->
                        saveSelection()
                        pickDialog.dismiss()
                    }
                    .setPositiveButton("继续添加") { pickDialog, _ ->
                        saveSelection()
                        pickDialog.dismiss()
                        dialog.dismiss()
                        showStatusBarDaysSettings()
                    }
                    .show()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showWidgetManager() {
        val context = requireContext()
        val widgets = listOf(
            "历史" to org.isoron.uhabits.widgets.HistoryWidgetProvider::class.java,
            "标记" to org.isoron.uhabits.widgets.CheckmarkWidgetProvider::class.java,
            "得分" to org.isoron.uhabits.widgets.ScoreWidgetProvider::class.java,
            "连续记录" to org.isoron.uhabits.widgets.StreakWidgetProvider::class.java,
            "频率" to org.isoron.uhabits.widgets.FrequencyWidgetProvider::class.java,
            "目标" to org.isoron.uhabits.widgets.TargetWidgetProvider::class.java,
            "距离上次打卡" to org.isoron.uhabits.widgets.DaysSinceLastCheckinWidgetProvider::class.java,
            "正计时 / 倒计时" to org.isoron.uhabits.widgets.TimeRecordWidgetProvider::class.java,
            "批量添加" to org.isoron.uhabits.widgets.BatchAddWidgetProvider::class.java
        )
        val labels = widgets.map { it.first }.toTypedArray()
        var selectedIndex = -1
        var positiveButton: Button? = null
        val dialog = AlertDialog.Builder(context)
            .setTitle("选择要添加的桌面组件（单选）")
            .setSingleChoiceItems(labels, -1) { _, which ->
                selectedIndex = which
                positiveButton?.isEnabled = true
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("开始添加") { _, _ ->
                val manager = android.appwidget.AppWidgetManager.getInstance(context)
                if (!manager.isRequestPinAppWidgetSupported) {
                    Toast.makeText(context, "当前桌面不支持应用内添加组件，请到桌面长按空白处添加。", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                if (selectedIndex !in widgets.indices) return@setPositiveButton

                val provider = widgets[selectedIndex].second
                val store = PreferenceManager.getDefaultSharedPreferences(context)
                store.edit().remove("widget_pin_queue").apply()
                if (manager.requestPinAppWidget(
                        android.content.ComponentName(context, provider), null, null
                    )) {
                    Toast.makeText(context, "已请求添加组件。请在桌面确认。", Toast.LENGTH_LONG).show()
                    // The launcher must become foreground so its pin-confirmation UI
                    // is not covered by this app. Do this after the selection dialog
                    // has finished dismissing.
                    requireActivity().window.decorView.post {
                        requireActivity().moveTaskToBack(true)
                    }
                } else {
                    Toast.makeText(context, "桌面未接受添加请求。", Toast.LENGTH_LONG).show()
                }
            }
            .create()
        dialog.setOnShowListener {
            positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton?.isEnabled = selectedIndex in widgets.indices
        }
        dialog.show()
    }
    private fun showFeatureShortcutSettings() {
        // Discover shortcut candidates from the same preference tree used by Settings.
        // New user-facing entries in Feature Center or Settings appear here automatically.
        val entries = FeatureShortcutCatalog.getEntries(requireContext())
        val keys = entries.map { it.key }
        val labels = entries.map { it.title }.toTypedArray()
        val store = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val savedOrder = store.getString("feature_shortcuts_order", null)
            ?.split("|")?.filter { it in keys }
        val legacySelection = store.getStringSet("feature_shortcuts", emptySet()).orEmpty()
        val selected = (savedOrder?.takeIf { it.isNotEmpty() } ?: keys.filter { it in legacySelection })
            .distinct().toMutableList()
        val checked = BooleanArray(keys.size) { keys[it] in selected }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.shortcut_settings)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val key = keys[which]
                if (isChecked && key !in selected) selected.add(key)
                if (!isChecked) selected.remove(key)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showShortcutOrderDialog(selected, store)
            }
            .show()
    }

    private fun showShortcutOrderDialog(
        selected: MutableList<String>,
        store: SharedPreferences,
        initialSelectedIndex: Int = 0
    ) {
        if (selected.isEmpty()) {
            saveShortcutOrder(selected, store)
            return
        }
        var selectedIndex = initialSelectedIndex.coerceIn(0, selected.lastIndex)
        val titles = FeatureShortcutCatalog.getEntries(requireContext()).associate { it.key to it.title }
        val labels = selected.map { key -> titles[key] ?: key }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.shortcut_order_title)
            .setSingleChoiceItems(labels, selectedIndex) { _, which -> selectedIndex = which }
            .setPositiveButton(R.string.shortcut_move_up) { _, _ ->
                if (selectedIndex > 0) {
                    val item = selected.removeAt(selectedIndex)
                    selected.add(selectedIndex - 1, item)
                    selectedIndex -= 1
                }
                showShortcutOrderDialog(selected, store, selectedIndex)
            }
            .setNegativeButton(R.string.shortcut_move_down) { _, _ ->
                if (selectedIndex < selected.lastIndex) {
                    val item = selected.removeAt(selectedIndex)
                    selected.add(selectedIndex + 1, item)
                    selectedIndex += 1
                }
                showShortcutOrderDialog(selected, store, selectedIndex)
            }
            .setNeutralButton(android.R.string.ok) { _, _ -> saveShortcutOrder(selected, store) }
            .show()
    }

    private fun saveShortcutOrder(selected: List<String>, store: SharedPreferences) {
        store.edit()
            .putString("feature_shortcuts_order", selected.joinToString("|"))
            .putStringSet("feature_shortcuts", selected.toSet())
            .apply()
        Toast.makeText(
            requireContext(),
            getString(R.string.shortcut_settings_saved, selected.size),
            Toast.LENGTH_SHORT
        ).show()
        // Shortcut configuration is launched as a focused task from the
        // main-screen popup. After saving, return directly to the main app
        // instead of leaving the user on the Settings screen.
        requireActivity().finish()
        requireActivity().overridePendingTransition(0, 0)
    }

    private fun createFullBackup(uri: Uri) {
        val appContext = requireContext().applicationContext
        Toast.makeText(requireContext(), "正在创建完整备份…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching { FullBackupManager(appContext).createBackup(uri) }
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                result.fold(
                    {
                        Toast.makeText(requireContext(), "完整备份已创建", Toast.LENGTH_LONG).show()
                    },
                    { error ->
                        Toast.makeText(
                            requireContext(),
                            error.message ?: "完整备份创建失败",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }.start()
    }

    private fun inspectAndConfirmRestore(uri: Uri) {
        val appContext = requireContext().applicationContext
        Toast.makeText(requireContext(), "正在检查完整备份…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching { FullBackupManager(appContext).inspectBackup(uri) }
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                result.fold(
                    { info ->
                        val details = "备份版本：${info.appVersion}\n数据库版本：${info.databaseVersion}\n\n包含内容：\n" +
                            info.entries.sorted().joinToString("\n")
                        AlertDialog.Builder(requireContext())
                            .setTitle("确认完整恢复")
                            .setMessage("恢复将替换本机主数据库、时间记录和相关设置。当前数据会先保存一份安全副本。\n\n$details")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("确认恢复") { _, _ ->
                                prepareAndRestartForRestore(uri, appContext)
                            }
                            .show()
                    },
                    { error ->
                        Toast.makeText(
                            requireContext(),
                            error.message ?: "备份文件检查失败",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }.start()
    }

    private fun prepareAndRestartForRestore(
        uri: Uri,
        appContext: android.content.Context
    ) {
        Toast.makeText(requireContext(), "正在准备恢复数据…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching { FullBackupManager(appContext).prepareRestore(uri) }
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                result.fold(
                    {
                        Toast.makeText(requireContext(), "备份已验证，正在重启并恢复…", Toast.LENGTH_LONG).show()
                        val restartIntent = Intent(appContext, ListHabitsActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        val restart = android.app.PendingIntent.getActivity(
                            appContext,
                            FULL_BACKUP_RESTART_REQUEST_CODE,
                            restartIntent,
                            android.app.PendingIntent.FLAG_CANCEL_CURRENT or
                                android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                        val alarm = appContext.getSystemService(android.content.Context.ALARM_SERVICE)
                            as android.app.AlarmManager
                        alarm.set(
                            android.app.AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + 800L,
                            restart
                        )
                        requireActivity().finishAffinity()
                        android.os.Process.killProcess(android.os.Process.myPid())
                    },
                    { error ->
                        Toast.makeText(
                            requireContext(),
                            error.message ?: "恢复准备失败，原数据未更改",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }.start()
    }

    private fun showSettingsBackupRestore() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_backup_restore)
            .setItems(arrayOf(
                getString(R.string.settings_backup_upload),
                getString(R.string.settings_backup_download)
            )) { _, which ->
                runSettingsBackupAction(which == 0)
            }
            .setNegativeButton(R.string.clear_label, null)
            .show()
    }

    private fun runSettingsBackupAction(upload: Boolean) {
        val credentials = WebDavCredentialsStore(requireContext()).load()
        val server = credentials.server.trim()
        val username = credentials.username.trim()
        val password = credentials.password
        if (!server.startsWith("https://") || username.isBlank() || password.isBlank()) {
            Toast.makeText(
                requireContext(),
                R.string.webdav_https_required,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val config = WebDavSync.Config(server, username, password)
        val appContext = requireContext().applicationContext
        Thread {
            val backup = WebDavSettingsBackup(appContext)
            if (upload) {
                val result = runCatching { backup.upload(config) }
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    result.fold(
                        {
                            Toast.makeText(
                                requireContext(),
                                R.string.settings_backup_success_upload,
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        { error ->
                            Toast.makeText(
                                requireContext(),
                                error.message ?: getString(R.string.webdav_operation_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            } else {
                val result = runCatching { backup.listBackupFiles(config) }
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    result.fold(
                        { files -> showSettingsBackupPicker(files, config, appContext) },
                        { error ->
                            Toast.makeText(
                                requireContext(),
                                error.message ?: getString(R.string.webdav_operation_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }.start()
    }

    private fun showSettingsBackupPicker(
        files: List<String>,
        config: WebDavSync.Config,
        appContext: android.content.Context
    ) {
        if (files.isEmpty()) {
            Toast.makeText(requireContext(), "服务器目录中没有找到软件设置备份（SET.json）", Toast.LENGTH_LONG).show()
            return
        }
        val labels = files.map { name ->
            val device = name.removePrefix("WEB_").substringBefore("_SET_")
            val timestamp = name.substringAfter("_SET_").removeSuffix(".json")
            "$device  ·  $timestamp"
        }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("选择要恢复的软件设置")
            .setItems(labels) { _, which ->
                val selected = files[which]
                AlertDialog.Builder(requireContext())
                    .setTitle("确认恢复设置")
                    .setMessage("将使用以下备份覆盖本机对应的软件设置：\n\n$selected\n\n此操作不会修改习惯和打卡数据库。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确认恢复") { _, _ ->
                        Thread {
                            val result = runCatching {
                                WebDavSettingsBackup(appContext).download(config, selected)
                            }
                            activity?.runOnUiThread {
                                if (!isAdded) return@runOnUiThread
                                result.fold(
                                    {
                                        Toast.makeText(
                                            requireContext(),
                                            R.string.settings_backup_success_download,
                                            Toast.LENGTH_LONG
                                        ).show()
                                        updateWeekdayPreference()
                                        widgetUpdater?.updateWidgets()
                                        requireActivity().recreate()
                                    },
                                    { error ->
                                        Toast.makeText(
                                            requireContext(),
                                            error.message ?: getString(R.string.webdav_operation_failed),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        }.start()
                    }
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmClearLocalHabitData() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_local_habit_data_first_title)
            .setMessage(R.string.clear_local_habit_data_first_message)
            .setNegativeButton(R.string.clear_label, null)
            .setPositiveButton(R.string.next_step) { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.clear_local_habit_data_second_title)
                    .setMessage(R.string.clear_local_habit_data_second_message)
                    .setNegativeButton(R.string.clear_label, null)
                    .setPositiveButton(R.string.confirm_clear) { _, _ ->
                        val result = runCatching {
                            val app = requireContext().applicationContext as HabitsApplication
                            app.component.habitList.removeAll()
                            widgetUpdater?.updateWidgets()
                        }
                        result.fold(
                            {
                                Toast.makeText(
                                    requireContext(),
                                    R.string.clear_local_habit_data_done,
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            { error ->
                                Toast.makeText(
                                    requireContext(),
                                    error.message ?: "清空失败，数据未确认清理完成",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                    .show()
            }
            .show()
    }

    private fun showWebDavSettings() {
        val credentials = WebDavCredentialsStore(requireContext()).load()
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val server = EditText(requireContext()).apply {
            hint = getString(R.string.webdav_server_hint)
            setText(credentials.server)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val username = EditText(requireContext()).apply {
            hint = getString(R.string.webdav_username)
            setText(credentials.username)
        }
        val password = EditText(requireContext()).apply {
            hint = getString(R.string.webdav_password)
            setText(credentials.password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun actionButton(label: String, color: String) = Button(requireContext()).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
            minHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val testButton = actionButton(
            getString(R.string.webdav_test_connection),
            "#7E57C2"
        )
        val uploadButton = actionButton(
            getString(R.string.webdav_upload),
            "#D32F2F"
        )
        val downloadButton = actionButton(
            getString(R.string.webdav_download),
            "#2E7D32"
        )

        val fullUploadButton = actionButton("完整备份上传", "#6A1B9A")
        val fullDownloadButton = actionButton("完整备份下载", "#00838F")
        val fullBackupRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(fullUploadButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginEnd = dp(6)
            })
            addView(fullDownloadButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginStart = dp(6)
            })
        }

        val previewButton = actionButton(
            "双向同步",
            "#1565C0"
        )

        val actionRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(uploadButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginEnd = dp(6)
            })
            addView(downloadButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginStart = dp(6)
            })
        }

        val form = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, pad / 2, pad, pad)
            addView(server)
            addView(username)
            addView(password)
            addView(testButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(10)
            })
            addView(actionRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ))
            addView(fullBackupRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(10) })
            addView(previewButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { topMargin = dp(10) })
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.webdav_sync)
            .setMessage(R.string.webdav_enter_details)
            .setView(form)
            .create()

        testButton.setOnClickListener {
            runWebDavAction(server, username, password, prefs, 0)
        }
        uploadButton.setOnClickListener {
            runWebDavAction(server, username, password, prefs, 1)
        }
        downloadButton.setOnClickListener {
            runWebDavAction(server, username, password, prefs, 2)
        }
        fullUploadButton.setOnClickListener {
            runWebDavFullBackupAction(server, username, password, true)
        }
        fullDownloadButton.setOnClickListener {
            runWebDavFullBackupAction(server, username, password, false)
        }
        previewButton.setOnClickListener {
            runTwoWaySyncPreview(server, username, password, prefs)
        }
        dialog.show()
    }


    private fun runWebDavFullBackupAction(
        server: EditText,
        username: EditText,
        password: EditText,
        upload: Boolean
    ) {
        val url = server.text.toString().trim()
        val user = username.text.toString().trim()
        val pass = password.text.toString()
        if (!url.startsWith("https://") || user.isBlank() || pass.isBlank()) {
            Toast.makeText(requireContext(), R.string.webdav_https_required, Toast.LENGTH_LONG).show()
            return
        }
        try {
            WebDavCredentialsStore(requireContext()).save(url, user, pass)
        } catch (error: Exception) {
            Toast.makeText(requireContext(), error.message ?: "WebDAV 凭据保存失败", Toast.LENGTH_LONG).show()
            return
        }

        val appContext = requireContext().applicationContext
        val config = WebDavSync.Config(url, user, pass)
        Thread {
            val transport = WebDavFullBackup(appContext)
            if (upload) {
                val result = runCatching { transport.upload(config) }
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    val message = result.fold(
                        { "完整备份已上传：$it" },
                        { it.message ?: "完整备份上传失败" }
                    )
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            } else {
                val result = runCatching { transport.listBackupFiles(config) }
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    result.fold(
                        { files ->
                            if (files.isEmpty()) {
                                Toast.makeText(requireContext(), "云端没有完整备份文件", Toast.LENGTH_LONG).show()
                            } else {
                                showRemoteFilePicker(files, "选择云端完整备份", "下载完整备份") { selected ->
                                    Thread {
                                        val downloaded = runCatching { transport.download(config, selected) }
                                        activity?.runOnUiThread {
                                            if (!isAdded) return@runOnUiThread
                                            val message = downloaded.fold(
                                                { "完整备份已下载：$it" },
                                                { it.message ?: "完整备份下载失败" }
                                            )
                                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                                        }
                                    }.start()
                                }
                            }
                        },
                        { error ->
                            Toast.makeText(
                                requireContext(),
                                error.message ?: "读取云端完整备份失败",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }.start()
    }

    private fun runTwoWaySyncPreview(
        server: EditText,
        username: EditText,
        password: EditText,
        prefs: SharedPreferences
    ) {
        val url = server.text.toString().trim()
        val user = username.text.toString().trim()
        val pass = password.text.toString()
        if (!url.startsWith("https://") || user.isBlank() || pass.isBlank()) {
            Toast.makeText(requireContext(), R.string.webdav_https_required, Toast.LENGTH_LONG).show()
            return
        }
        try {
            WebDavCredentialsStore(requireContext()).save(url, user, pass)
        } catch (error: Exception) {
            Toast.makeText(
                requireContext(),
                error.message ?: "WebDAV 凭据加密保存失败",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val appContext = requireContext().applicationContext
        val config = WebDavSync.Config(url, user, pass)
        Thread {
            val result = runCatching { WebDavSync(appContext).listBackupFiles(config) }
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                result.fold(
                    { files ->
                        if (files.isEmpty()) {
                            Toast.makeText(requireContext(), "服务器目录中没有可用于同步的数据库备份", Toast.LENGTH_LONG).show()
                        } else {
                            showTwoWaySyncRemotePicker(files, config, appContext)
                        }
                    },
                    { error ->
                        Toast.makeText(
                            requireContext(),
                            error.message ?: getString(R.string.webdav_operation_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }.start()
    }

    private fun showTwoWaySyncRemotePicker(
        files: List<WebDavSync.RemoteFile>,
        config: WebDavSync.Config,
        appContext: android.content.Context
    ) {
        showRemoteFilePicker(
            files,
            "选择用于双向同步的远端数据库",
            "分析并同步"
        ) { selected ->
            Thread {
                val result = runCatching {
                    TwoWaySyncPreview(WebDavSync(appContext)).createSession(config, selected)
                }
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    result.fold(
                        { session ->
                            val plan = session.plan
                            val summary = buildString {
                                append("本机新增：${plan.totalLocalAdditions} 项\n")
                                append("服务器新增：${plan.totalRemoteAdditions} 项\n")
                                append("完全相同、已去重的打卡：${plan.identicalEntries} 条\n")
                                append("打卡冲突：${plan.conflictingEntries.size} 条\n")
                                append("习惯设置冲突：${plan.conflictingHabitSettings.size} 项\n")
                                append("可能需要人工配对的旧习惯：${plan.possibleLegacyMatches.size} 项\n\n")
                                append("继续后会先创建本机安全备份，再合并数据并上传合并结果。")
                            }
                            if (plan.possibleLegacyMatches.isNotEmpty()) {
                                val pairsText = plan.possibleLegacyMatches.joinToString("\n") {
                                    "本机「${it.displayName}」 ↔ 服务器「${it.displayName}」"
                                }
                                AlertDialog.Builder(requireContext())
                                    .setTitle("发现可能相同的旧习惯")
                                    .setMessage(
                                        summary + "\n\n以下习惯名称相同但 UUID 不同：\n" +
                                            pairsText +
                                            "\n\n只有你确认它们是同一个习惯后，才会合并对应打卡记录。"
                                    )
                                    .setNegativeButton("取消", null)
                                    .setPositiveButton("确认配对并继续") { _, _ ->
                                        val remapped = runCatching { remapLegacySyncSession(session) }
                                        remapped.fold(
                                            { mapped ->
                                                val mappedSummary = buildString {
                                                    append("已按你的确认配对旧习惯。\n")
                                                    append("本机新增：${mapped.plan.totalLocalAdditions} 项\n")
                                                    append("服务器新增：${mapped.plan.totalRemoteAdditions} 项\n")
                                                    append("打卡冲突：${mapped.plan.conflictingEntries.size} 条\n")
                                                    append("习惯设置冲突：${mapped.plan.conflictingHabitSettings.size} 项\n\n")
                                                    append("继续后会先创建本机安全备份，再合并数据并上传。")
                                                }
                                                showTwoWaySyncConfirmation(mapped, config, appContext, mappedSummary)
                                            },
                                            { error ->
                                                Toast.makeText(
                                                    requireContext(),
                                                    error.message ?: "旧习惯配对失败，数据未修改",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        )
                                    }
                                    .show()
                            } else {
                                showTwoWaySyncConfirmation(session, config, appContext, summary)
                            }
                        },
                        { error ->
                            AlertDialog.Builder(requireContext())
                                .setTitle("无法生成同步预览")
                                .setMessage(error.message ?: "读取数据库失败，数据未修改。")
                                .setPositiveButton("知道了", null)
                                .show()
                        }
                    )
                }
            }.start()
        }
    }

    private fun remapLegacySyncSession(
        session: TwoWaySyncPreview.Session
    ): TwoWaySyncPreview.Session {
        val pairs = session.plan.possibleLegacyMatches.map {
            SyncLegacyHabitPair(it.localUuid, it.remoteUuid)
        }
        val mapped = TwoWaySyncSnapshotRemapper.remapRemote(
            localHabits = session.local.habits,
            remoteHabits = session.remote.habits,
            remoteEntries = session.remote.entries,
            confirmedPairs = pairs
        )
        val uuidMap = pairs.associate { it.remoteUuid to it.localUuid }
        val mappedRemote = session.remote.copy(
            habits = mapped.habits,
            entries = mapped.entries,
            habitRows = session.remote.habitRows.map { row ->
                row.copy(uuid = row.uuid?.let { uuidMap[it] ?: it })
            }
        )
        val mappedPlan = TwoWaySyncPlanner.build(
            session.local.habits,
            mappedRemote.habits,
            session.local.entries,
            mappedRemote.entries
        )
        return TwoWaySyncPreview.Session(mappedPlan, session.local, mappedRemote)
    }

    private fun showTwoWaySyncConfirmation(
        session: TwoWaySyncPreview.Session,
        config: WebDavSync.Config,
        appContext: android.content.Context,
        summary: String
    ) {
        val plan = session.plan
        val hasConflicts = plan.hasConflicts
        val choices = arrayOf("冲突时保留本机版本", "冲突时保留服务器版本")
        var selectedChoice = 0
        val builder = AlertDialog.Builder(requireContext())
            .setTitle("双向同步确认")
            .setMessage(summary)
        if (hasConflicts) {
            builder.setSingleChoiceItems(choices, selectedChoice) { _, which ->
                selectedChoice = which
            }
        } else {
            builder.setMessage(summary + "\n\n没有发现冲突，将自动合并双方新增数据。")
        }
        builder.setNegativeButton("取消", null)
        builder.setPositiveButton("确认合并并上传", null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val keep = if (selectedChoice == 0) SyncConflictSide.LOCAL else SyncConflictSide.REMOTE
                val resolution = runCatching {
                    TwoWaySyncResolution.validate(
                        plan = plan,
                        entryChoices = plan.conflictingEntries.map {
                            SyncEntryConflictChoice(it.habitUuid, it.dayKey, keep)
                        },
                        settingsChoices = plan.conflictingHabitSettings.map {
                            SyncHabitSettingsConflictChoice(it.habitUuid, keep)
                        },
                        legacyPairs = emptyList()
                    )
                }
                if (resolution.isFailure) {
                    Toast.makeText(
                        requireContext(),
                        resolution.exceptionOrNull()?.message ?: "冲突选择无效",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                runTwoWaySyncMerge(session, config, appContext, resolution.getOrThrow())
            }
        }
        dialog.show()
    }

    private fun runTwoWaySyncMerge(
        session: TwoWaySyncPreview.Session,
        config: WebDavSync.Config,
        appContext: android.content.Context,
        resolution: TwoWaySyncResolution
    ) {
        Thread {
            var localMerge: DatabaseSyncMergeWriter.Result? = null
            val result = runCatching {
                DatabaseSyncSafetyBackup(appContext).create()
                localMerge = DatabaseSyncMergeWriter().apply(
                    remote = session.remote,
                    expectedPlan = session.plan,
                    resolution = resolution
                )
                WebDavSync(appContext).upload(
                    config,
                    WebDavSync.UploadKind.TWO_WAY_MERGED
                )
                localMerge!!
            }
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread

                // The merge writer updates SQLite directly, bypassing the
                // in-memory SQLiteHabitList cache. Invalidate that cache now;
                // ListHabitsActivity will reload it when it resumes.
                if (localMerge != null) {
                    val app = appContext as HabitsApplication
                    (app.component.habitList as SQLiteHabitList).reload()
                    widgetUpdater?.updateWidgets()
                }

                if (result.isSuccess) {
                    val merged = result.getOrThrow()
                    AlertDialog.Builder(requireContext())
                        .setTitle("双向同步完成")
                        .setMessage(
                            "本机已创建同步前安全备份。\n" +
                                "新增习惯：${merged.habitsInserted}\n" +
                                "更新习惯设置：${merged.habitSettingsUpdated}\n" +
                                "新增打卡：${merged.entriesInserted}\n" +
                                "更新冲突打卡：${merged.entriesUpdated}\n\n" +
                                "合并后的数据库已上传到服务器。"
                        )
                        .setPositiveButton("完成", null)
                        .show()
                } else {
                    val message = if (localMerge != null) {
                        "本机数据已完成合并，但上传服务器失败。你的本机数据和同步前安全备份都保留着；请检查网络后重新上传本机备份。\n\n错误：${result.exceptionOrNull()?.message ?: "未知错误"}"
                    } else {
                        result.exceptionOrNull()?.message ?: "同步失败，未完成数据合并。"
                    }
                    AlertDialog.Builder(requireContext())
                        .setTitle("双向同步未完全完成")
                        .setMessage(message)
                        .setPositiveButton("知道了", null)
                        .show()
                }
            }
        }.start()
    }

    private fun runWebDavAction(
        server: EditText,
        username: EditText,
        password: EditText,
        prefs: SharedPreferences,
        action: Int
    ) {
        val url = server.text.toString().trim()
        val user = username.text.toString().trim()
        val pass = password.text.toString()
        if (!url.startsWith("https://") || user.isBlank() || pass.isBlank()) {
            Toast.makeText(requireContext(), R.string.webdav_https_required, Toast.LENGTH_LONG).show()
            return
        }
        try {
            WebDavCredentialsStore(requireContext()).save(url, user, pass)
        } catch (error: Exception) {
            Toast.makeText(
                requireContext(),
                error.message ?: "WebDAV 凭据加密保存失败",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val appContext = requireContext().applicationContext
        val config = WebDavSync.Config(url, user, pass)
        Thread {
            if (action == 2) {
                val result = runCatching { WebDavSync(appContext).listBackupFiles(config) }
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    result.fold(
                        { files -> showRemoteBackupPicker(files, config, appContext) },
                        { error ->
                            Toast.makeText(
                                requireContext(),
                                error.message ?: getString(R.string.webdav_operation_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
                return@Thread
            }

            val result = runCatching {
                val sync = WebDavSync(appContext)
                when (action) {
                    0 -> {
                        sync.test(config)
                        getString(R.string.webdav_connection_success)
                    }
                    else -> {
                        sync.upload(config)
                        getString(R.string.webdav_upload_success)
                    }
                }
            }
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                val message = result.getOrElse {
                    it.message ?: getString(R.string.webdav_operation_failed)
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun showRemoteBackupPicker(
        files: List<WebDavSync.RemoteFile>,
        config: WebDavSync.Config,
        appContext: android.content.Context
    ) {
        if (files.isEmpty()) {
            Toast.makeText(requireContext(), "服务器目录中没有找到备份文件（.db）", Toast.LENGTH_LONG).show()
            return
        }
        showRemoteFilePicker(files, "选择要下载的备份", "下载所选文件") { selected ->
            downloadRemoteBackup(selected, config, appContext)
        }
    }

    /**
     * Shared, compact file picker for download and two-way sync.
     * Each file gets its own padded row, readable filename and muted metadata.
     */
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showRemoteFilePicker(
        files: List<WebDavSync.RemoteFile>,
        title: String,
        positiveLabel: String,
        onSelected: (WebDavSync.RemoteFile) -> Unit
    ) {
        var selectedIndex = -1
        val styled = StyledResources(requireContext())
        val rowColor = styled.getColor(R.attr.contrast0)
        val alternateRowColor = styled.getColor(R.attr.contrast20)
        val selectedRowColor = styled.getColor(R.attr.highlightedBackgroundColor)
        val primaryTextColor = styled.getColor(R.attr.contrast100)
        val secondaryTextColor = styled.getColor(R.attr.contrast80)
        val dividerColor = styled.getColor(R.attr.contrast40)
        fun formatSize(size: Long?): String {
            if (size == null) return "大小未知"
            return when {
                size >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f MB", size / 1048576.0)
                size >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", size / 1024.0)
                else -> "$size B"
            }
        }

        val list = ListView(requireContext()).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
            divider = android.graphics.drawable.ColorDrawable(dividerColor)
            dividerHeight = dp(1)
            setPadding(0, dp(4), 0, dp(4))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360))
            clipToPadding = false
            adapter = object : BaseAdapter() {
                override fun getCount() = files.size
                override fun getItem(position: Int) = files[position]
                override fun getItemId(position: Int): Long = position.toLong()

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val file = files[position]
                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(12), dp(9), dp(12), dp(9))
                        setBackgroundColor(
                            when {
                                position == selectedIndex -> selectedRowColor
                                position % 2 == 0 -> rowColor
                                else -> alternateRowColor
                            }
                        )
                    }
                    val radio = RadioButton(requireContext()).apply {
                        isClickable = false
                        isFocusable = false
                        isChecked = position == selectedIndex
                        buttonTintList = ColorStateList.valueOf(
                            if (isChecked) primaryTextColor else secondaryTextColor
                        )
                    }
                    row.addView(radio, LinearLayout.LayoutParams(dp(40), dp(40)))

                    val textColumn = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val name = TextView(requireContext()).apply {
                        text = file.name
                        textSize = 14f
                        setTextColor(primaryTextColor)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        maxLines = 2
                    }
                    val details = TextView(requireContext()).apply {
                        text = "渠道：WebDAV  ·  ${file.modified ?: "修改时间未知"}  ·  ${formatSize(file.size)}"
                        textSize = 11f
                        setTextColor(secondaryTextColor)
                        maxLines = 1
                    }
                    textColumn.addView(name)
                    textColumn.addView(details)
                    row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    return row
                }
            }
            setOnItemClickListener { _, _, position, _ ->
                selectedIndex = position
                (adapter as BaseAdapter).notifyDataSetChanged()
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(list)
            .setNegativeButton("取消", null)
            .setPositiveButton(positiveLabel) { _, _ ->
                if (selectedIndex !in files.indices) {
                    Toast.makeText(requireContext(), "请先选择一个备份文件", Toast.LENGTH_SHORT).show()
                } else {
                    onSelected(files[selectedIndex])
                }
            }
            .show()
    }

    private fun downloadRemoteBackup(
        remoteFile: WebDavSync.RemoteFile,
        config: WebDavSync.Config,
        appContext: android.content.Context
    ) {
        Thread {
            val result = runCatching {
                WebDavSync(appContext).download(config, remoteFile)
            }
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                val message = result.fold(
                    { getString(R.string.webdav_download_success) },
                    { it.message ?: getString(R.string.webdav_operation_failed) }
                )
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun updateWeekdayPreference() {
        val weekdayPref = findPreference("pref_first_weekday") as ListPreference
        val currentFirstWeekday = prefs.firstWeekday.daysSinceSunday + 1
        val dayNames = JavaLocalDateFormatter(Locale.getDefault()).longWeekdayNames(DayOfWeek.SATURDAY)
        val dayValues = arrayOf("7", "1", "2", "3", "4", "5", "6")
        weekdayPref.entries = dayNames
        weekdayPref.entryValues = dayValues
        weekdayPref.setDefaultValue(currentFirstWeekday.toString())
        weekdayPref.summary = dayNames[currentFirstWeekday % 7]
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?
    ) {
        if (key == "pref_widget_opacity" && widgetUpdater != null) {
            Log.d("SettingsFragment", "updating widgets")
            widgetUpdater!!.updateWidgets()
        }
        if (key == AutoBackup.KEY_ENABLED ||
            key == AutoBackup.KEY_INTERVAL_DAYS ||
            key == AutoBackup.KEY_KEEP_COUNT ||
            key == "publicBackupFolder"
        ) {
            updateAutoBackupStatusSummary()
        }
        BackupManager.dataChanged("org.isoron.uhabits")
        updateWeekdayPreference()
    }

    private fun setResultOnPreferenceClick(key: String, result: Int) {
        val pref = findPreference(key)
        pref.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                requireActivity().setResult(result)
                requireActivity().finish()
                true
            }
    }

    private fun showRingtonePicker() {
        val existingRingtoneUri = ringtoneManager!!.getURI()
        val defaultRingtoneUri = Settings.System.DEFAULT_NOTIFICATION_URI
        val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(
            android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
            android.media.RingtoneManager.TYPE_NOTIFICATION
        )
        intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        intent.putExtra(
            android.media.RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
            defaultRingtoneUri
        )
        intent.putExtra(
            android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            existingRingtoneUri
        )
        startActivityForResult(intent, RINGTONE_REQUEST_CODE)
    }

    private fun updateRingtoneDescription() {
        val ringtoneName = ringtoneManager!!.getName() ?: return
        val ringtonePreference = findPreference("reminderSound")
        ringtonePreference.summary = ringtoneName
    }

    private fun updatePublicBackupFolderSummary() {
        // The folder picker is now inside the Auto Backup dialog, so older
        // versions of the settings tree may not contain a standalone preference.
        // Keep this summary updater safe for both layouts.
        val pref = findPreference("publicBackupFolder") ?: return
        val uriString = sharedPrefs?.getString("publicBackupFolder", null)
        if (uriString == null) {
            pref.summary = getString(R.string.no_public_backup_folder_selected)
            return
        }
        val uri = Uri.parse(uriString)
        val path = fullPathFor(uri)
        pref.summary = path ?: uriString
    }

    private fun showAutoBackupSettings() {
        val context = requireContext()
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = settings.getBoolean(AutoBackup.KEY_ENABLED, true)
        val interval = settings.getString(AutoBackup.KEY_INTERVAL_DAYS, "1")?.toIntOrNull()
            ?.coerceIn(1, 30) ?: 1
        val keep = settings.getString(AutoBackup.KEY_KEEP_COUNT, "5")?.toIntOrNull()
            ?.coerceIn(1, 50) ?: 5
        val folder = settings.getString("publicBackupFolder", null)
        val location = if (folder.isNullOrBlank()) "应用内部备份目录" else "已选择外部备份目录"
        val options = arrayOf(
            "自动备份开关：${if (enabled) "开启" else "关闭"}",
            "备份间隔：每 $interval 天",
            "保留数量：$keep 份",
            "备份保存位置：$location",
            "立即备份",
            "查看备份状态"
        )
        AlertDialog.Builder(context)
            .setTitle("自动备份")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> AlertDialog.Builder(context)
                        .setTitle("自动备份开关")
                        .setSingleChoiceItems(arrayOf("开启", "关闭"), if (enabled) 0 else 1) { dialog, selected ->
                            settings.edit().putBoolean(AutoBackup.KEY_ENABLED, selected == 0).apply()
                            updateAutoBackupStatusSummary()
                            dialog.dismiss()
                            showAutoBackupSettings()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    1 -> {
                        val values = arrayOf("1", "2", "3", "7")
                        val labels = arrayOf("1 天", "2 天", "3 天", "7 天")
                        val selected = values.indexOf(interval.toString()).coerceAtLeast(0)
                        AlertDialog.Builder(context)
                            .setTitle("自动备份间隔")
                            .setSingleChoiceItems(labels, selected) { dialog, choice ->
                                settings.edit().putString(AutoBackup.KEY_INTERVAL_DAYS, values[choice]).apply()
                                updateAutoBackupStatusSummary()
                                dialog.dismiss()
                                showAutoBackupSettings()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    2 -> {
                        val values = arrayOf("3", "5", "10", "20")
                        val labels = arrayOf("3 份", "5 份", "10 份", "20 份")
                        val selected = values.indexOf(keep.toString()).let { if (it < 0) 1 else it }
                        AlertDialog.Builder(context)
                            .setTitle("自动备份保留数量")
                            .setSingleChoiceItems(labels, selected) { dialog, choice ->
                                settings.edit().putString(AutoBackup.KEY_KEEP_COUNT, values[choice]).apply()
                                updateAutoBackupStatusSummary()
                                dialog.dismiss()
                                showAutoBackupSettings()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    3 -> openPublicBackupFolderPicker()
                    4 -> {
                        Toast.makeText(context, "正在创建完整备份…", Toast.LENGTH_SHORT).show()
                        val host = activity
                        Thread {
                            val result = AutoBackup(context.applicationContext).run(force = true)
                            host?.runOnUiThread {
                                if (!isAdded) return@runOnUiThread
                                updateAutoBackupStatusSummary()
                                val message = when (result) {
                                    AutoBackup.Result.CREATED -> "完整备份已成功创建"
                                    AutoBackup.Result.SKIPPED -> "没有创建新备份，请检查自动备份设置"
                                    AutoBackup.Result.FAILED -> "备份失败，请检查保存位置和存储空间"
                                }
                                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                                showAutoBackupSettings()
                            }
                        }.start()
                    }
                    5 -> AlertDialog.Builder(context)
                        .setTitle("自动备份状态")
                        .setMessage(autoBackupStatusDescription())
                        .setPositiveButton("知道了", null)
                        .show()
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun openPublicBackupFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        startActivityForResult(intent, PUBLIC_BACKUP_REQUEST_CODE)
    }

    private fun autoBackupStatusDescription(): String {
        val context = requireContext()
        val backupPrefs = context.getSharedPreferences(
            AutoBackup.STATUS_PREFS,
            android.content.Context.MODE_PRIVATE
        )
        val lastSuccess = backupPrefs.getLong(AutoBackup.KEY_LAST_SUCCESS, 0L)
        val lastFailure = backupPrefs.getLong(AutoBackup.KEY_LAST_FAILURE, 0L)
        val folderUri = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("publicBackupFolder", null)
        val location = if (folderUri.isNullOrBlank()) {
            "应用内部 Backups 文件夹"
        } else {
            fullPathFor(Uri.parse(folderUri)) ?: "已选择的公共文件夹"
        }
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        val status = when {
            lastFailure > lastSuccess ->
                "最近一次备份尝试失败：${formatter.format(java.util.Date(lastFailure))}"
            lastSuccess > 0L ->
                "最近成功备份：${formatter.format(java.util.Date(lastSuccess))}"
            else -> "尚无成功备份记录"
        }
        return "保存位置：$location\n$status\n\n自动备份在打开主页面时检查，不保证应用关闭后仍按固定时间后台执行。"
    }

    private fun updateAutoBackupStatusSummary() {
        val preference = findPreference("autoBackup") ?: return
        val folderUri = sharedPrefs?.getString("publicBackupFolder", null)
        val location = if (folderUri.isNullOrBlank()) {
            "应用内部 Backups 文件夹"
        } else {
            fullPathFor(Uri.parse(folderUri)) ?: "已选择的公共文件夹"
        }
        val settings = sharedPrefs
        val enabled = settings?.getBoolean(AutoBackup.KEY_ENABLED, true) ?: true
        val intervalDays = settings?.getString(AutoBackup.KEY_INTERVAL_DAYS, "1")?.toIntOrNull()
            ?.coerceIn(1, 30) ?: 1
        val keepCount = settings?.getString(AutoBackup.KEY_KEEP_COUNT, "5")?.toIntOrNull()
            ?.coerceIn(1, 50) ?: 5
        val configuration = if (enabled) {
            "已开启 · 每 $intervalDays 天 · 保留 $keepCount 份"
        } else {
            "已关闭 · 手动备份仍可使用"
        }
        preference.summary = "$configuration\n保存位置：$location"
    }

    private fun fullPathFor(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val (type, rel) = docId.split(":", limit = 2).let {
                    it[0] to it.getOrElse(1) { "" }
                }
                val base = if (type.equals("primary", true)) {
                    Environment.getExternalStorageDirectory().absolutePath
                } else {
                    "/storage/$type"
                }
                if (rel.isEmpty()) base else "$base/$rel"
            }
            "file" -> java.io.File(uri.path!!).absolutePath
            else -> null
        }
    }

    companion object {
        private const val RINGTONE_REQUEST_CODE = 1
        private const val PUBLIC_BACKUP_REQUEST_CODE = 2
        private const val FULL_BACKUP_CREATE_REQUEST_CODE = 3
        private const val FULL_BACKUP_RESTORE_REQUEST_CODE = 4
        private const val FULL_BACKUP_RESTART_REQUEST_CODE = 5
    }
}
