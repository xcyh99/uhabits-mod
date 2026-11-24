package org.isoron.uhabits.activities.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.widgets.DigitalClockWidgetProvider

/** Central settings page for all home-screen widgets; individual widget settings live here. */
class DesktopWidgetSettingsActivity : AppCompatActivity() {
    private val store by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val slots = listOf(
        Slot("time", "时间区域", "点击桌面时间后打开"),
        Slot("date", "日期星期区域", "点击日期或星期后打开"),
        Slot("shortcut1", "快捷入口一", "底部第一个位置"),
        Slot("shortcut2", "快捷入口二", "底部第二个位置"),
        Slot("shortcut3", "快捷入口三", "底部第三个位置"),
        Slot("shortcut4", "快捷入口四", "底部第四个位置")
    )
    private data class Slot(val key: String, val title: String, val hint: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as HabitsApplication).component.preferences.let {
            AndroidThemeSwitcher(this, it).apply()
        }
        super.onCreate(savedInstanceState)
        title = "桌面组件设置"
        render()
    }

    override fun onResume() {
        super.onResume()
        updateWidget()
    }

    private fun render() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(24))
            setBackgroundColor(resolvePageBackground())
        }
        fun heading(text: String) {
            root.addView(TextView(this).apply {
                this.text = text
                textSize = 18f
                setTextColor(resolvePrimaryText())
                setPadding(0, dp(14), 0, dp(8))
            })
        }
        fun button(text: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                this.text = text
                isAllCaps = false
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        }
        heading("数字时钟组件（2×3）")
        root.addView(TextView(this).apply {
            text = "顶部时间、中间日期星期、底部四个快捷入口。每个区域都可以独立指定打开的手机应用或十字习惯功能。"
            textSize = 13f
            setTextColor(resolveSecondaryText())
        })
        button("添加数字时钟到桌面") { pinWidget() }
        heading("点击区域设置")
        slots.forEach { slot ->
            val current = store.getString(key(slot.key), null)?.let(::targetLabel) ?: "未设置"
            button("${slot.title}    ·    $current") { chooseTarget(slot) }
        }
        heading("其他桌面组件")
        button("历史组件参数") {
            openExistingSettings("historyWhiteWeight")
        }
        button("标记组件参数") {
            openExistingSettings("checkmarkWhiteWeight")
        }
        button("组件白边参数") {
            openExistingSettings("whiteOutline")
        }
        root.addView(TextView(this).apply {
            text = "说明：白字权重、白边、颜色阈值等显示参数仍由「参数中心」统一管理，这里不重复设置。"
            textSize = 12f
            setTextColor(resolveSecondaryText())
            setPadding(0, dp(14), 0, 0)
        })
        setContentView(android.widget.ScrollView(this).apply { addView(root) })
    }

    private fun chooseTarget(slot: Slot) {
        val options = mutableListOf<Pair<String, String>>()
        options += "none" to "不设置（点击无动作）"
        FeatureShortcutCatalog.getEntries(this).forEach {
            options += "feature:${it.key}" to "十字习惯 · ${it.title}"
        }
        val pm = packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        apps.forEach { info ->
            val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
            options += "app:${component.flattenToString()}" to "手机应用 · ${info.loadLabel(pm)}"
        }
        val labels = options.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("设置${slot.title}的点击目标")
            .setItems(labels) { _, which ->
                store.edit().putString(key(slot.key), options[which].first).apply()
                updateWidget()
                render()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun targetLabel(value: String): String {
        if (value == "none") return "不设置"
        if (value.startsWith("feature:")) {
            return FeatureShortcutCatalog.getEntries(this).firstOrNull { it.key == value.removePrefix("feature:") }?.title ?: "十字习惯功能"
        }
        if (value.startsWith("app:")) {
            val component = ComponentName.unflattenFromString(value.removePrefix("app:")) ?: return "手机应用"
            return runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(component.packageName, 0)).toString() }.getOrDefault("手机应用")
        }
        return "未设置"
    }

    private fun pinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, DigitalClockWidgetProvider::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported) {
            if (!manager.requestPinAppWidget(provider, null, null)) {
                Toast.makeText(this, "桌面未接受添加请求，请从桌面组件列表中添加数字时钟。", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "请长按桌面空白处，在组件列表中添加十字习惯数字时钟。", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateWidget() {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, DigitalClockWidgetProvider::class.java))
        if (ids.isNotEmpty()) DigitalClockWidgetProvider.updateAll(this, manager, ids)
    }

    private fun openExistingSettings(key: String) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra("open_preference_key", key))
    }

    private fun key(slot: String) = "digital_clock_target_$slot"
    private fun resolvePageBackground() = org.isoron.uhabits.utils.StyledResources(this).getColor(R.attr.contrast0)
    private fun resolvePrimaryText(): Int = if (isDark()) Color.WHITE else Color.BLACK
    private fun resolveSecondaryText(): Int = if (isDark()) Color.LTGRAY else Color.DKGRAY
    private fun isDark() = androidx.core.graphics.ColorUtils.calculateLuminance(resolvePageBackground()) < 0.5
}
