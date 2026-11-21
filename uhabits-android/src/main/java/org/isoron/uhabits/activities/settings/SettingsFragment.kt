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
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.Bundle
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
import org.isoron.uhabits.activities.habits.list.RESULT_EXPORT_CSV
import org.isoron.uhabits.activities.habits.list.RESULT_EXPORT_DB
import org.isoron.uhabits.activities.habits.list.RESULT_EXPORT_LEGACY_DB
import org.isoron.uhabits.activities.habits.list.RESULT_IMPORT_DATA
import org.isoron.uhabits.activities.habits.list.RESULT_REPAIR_DB
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.database.WebDavSync
import org.isoron.uhabits.database.TwoWaySyncPreview
import org.isoron.uhabits.core.ui.NotificationTray
import org.isoron.uhabits.notifications.AndroidNotificationTray.Companion.createAndroidNotificationChannel
import org.isoron.uhabits.notifications.RingtoneManager
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
            "publicBackupFolder" -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
                startActivityForResult(intent, PUBLIC_BACKUP_REQUEST_CODE)
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

        findPreference("reminderSound").isVisible = false
    }

    private fun showWebDavSettings() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val server = EditText(requireContext()).apply {
            hint = getString(R.string.webdav_server_hint)
            setText(prefs.getString("webdav_server", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val username = EditText(requireContext()).apply {
            hint = getString(R.string.webdav_username)
            setText(prefs.getString("webdav_username", ""))
        }
        val password = EditText(requireContext()).apply {
            hint = getString(R.string.webdav_password)
            setText(prefs.getString("webdav_password", ""))
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

        val previewButton = actionButton(
            "双向同步预览（只读）",
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
        previewButton.setOnClickListener {
            runTwoWaySyncPreview(server, username, password, prefs)
        }
        dialog.show()
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
        prefs.edit()
            .putString("webdav_server", url)
            .putString("webdav_username", user)
            .putString("webdav_password", pass)
            .apply()
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
            "生成只读预览"
        ) { selected ->
            Thread {
                val result = runCatching {
                    TwoWaySyncPreview(WebDavSync(appContext)).createPlan(config, selected)
                }
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    result.fold(
                        { plan ->
                            val summary = buildString {
                                append("本机新增：${plan.totalLocalAdditions} 项\\n")
                                append("服务器新增：${plan.totalRemoteAdditions} 项\\n")
                                append("完全相同、已去重的打卡：${plan.identicalEntries} 条\\n")
                                append("打卡冲突：${plan.conflictingEntries.size} 条\\n")
                                append("习惯设置冲突：${plan.conflictingHabitSettings.size} 项\\n")
                                append("可能需要人工配对的旧习惯：${plan.possibleLegacyMatches.size} 项\\n\\n")
                                append("这是只读预览，尚未修改本机或服务器数据。")
                            }
                            AlertDialog.Builder(requireContext())
                                .setTitle("双向同步预览")
                                .setMessage(summary)
                                .setPositiveButton("关闭", null)
                                .show()
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
        prefs.edit()
            .putString("webdav_server", url)
            .putString("webdav_username", user)
            .putString("webdav_password", pass)
            .apply()

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
            divider = android.graphics.drawable.ColorDrawable(Color.rgb(218, 224, 232))
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
                                position == selectedIndex -> Color.rgb(220, 234, 250)
                                position % 2 == 0 -> Color.rgb(246, 248, 251)
                                else -> Color.WHITE
                            }
                        )
                    }
                    val radio = RadioButton(requireContext()).apply {
                        isClickable = false
                        isFocusable = false
                        isChecked = position == selectedIndex
                        buttonTintList = ColorStateList.valueOf(
                            if (isChecked) Color.rgb(40, 112, 190) else Color.rgb(130, 140, 152)
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
                        setTextColor(Color.rgb(32, 40, 52))
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        maxLines = 2
                    }
                    val details = TextView(requireContext()).apply {
                        text = "${file.modified ?: "修改时间未知"}  ·  ${formatSize(file.size)}"
                        textSize = 11f
                        setTextColor(Color.rgb(100, 112, 128))
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
        val pref = findPreference("publicBackupFolder")
        val uriString = sharedPrefs?.getString("publicBackupFolder", null)
        if (uriString == null) {
            pref.summary = getString(R.string.no_public_backup_folder_selected)
            return
        }
        val uri = Uri.parse(uriString)
        val path = fullPathFor(uri)
        pref.summary = path ?: uriString
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
    }
}
