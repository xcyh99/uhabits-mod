package org.isoron.uhabits.activities.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
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
        heading("数字时钟组件（四行布局）")
        root.addView(TextView(this).apply {
            text = "从上到下分别为小时、分钟、日期星期和快捷入口。时间由系统时钟控件自动刷新，不增加应用后台定时唤醒。可在桌面支持的情况下调整组件整体大小。"
            textSize = 13f
            setTextColor(resolveSecondaryText())
        })
        button("添加数字时钟到桌面") { pinWidget() }
        button("预览数字时钟") { showWidgetPreview() }
        button("数字显示样式") { showAppearanceSettings() }
        button("恢复数字时钟默认设置") { confirmReset() }
        heading("点击区域设置")
        slots.forEach { slot ->
            val current = store.getString(key(slot.key), null)?.let(::targetLabel) ?: "未设置"
            button("${slot.title}    ·    $current") { chooseTarget(slot) }
        }
        root.addView(TextView(this).apply {
            text = "当前这里只管理数字时钟组件。白字权重、白边、颜色阈值等显示参数仍由「参数中心」统一管理。"
            textSize = 12f
            setTextColor(resolveSecondaryText())
            setPadding(0, dp(14), 0, 0)
        })
        setContentView(android.widget.ScrollView(this).apply { addView(root) })
    }

    private fun showWidgetPreview() {
        val preview = LayoutInflater.from(this).inflate(R.layout.widget_digital_clock, null, false)
        preview.findViewById<TextView>(R.id.digital_clock_time).apply {
            textSize = store.getFloat(KEY_HOUR_SIZE, 48f)
            setTextColor(store.getInt(KEY_TIME_COLOR, Color.WHITE))
        }
        preview.findViewById<TextView>(R.id.digital_clock_minute).apply {
            textSize = store.getFloat(KEY_MINUTE_SIZE, 48f)
            setTextColor(store.getInt(KEY_TIME_COLOR, Color.WHITE))
        }
        preview.findViewById<android.widget.TextClock>(R.id.digital_clock_date).apply {
            textSize = store.getFloat(KEY_DATE_SIZE, 15f)
            setTextColor(store.getInt(KEY_DATE_COLOR, Color.LTGRAY))
            val format = store.getString(KEY_DATE_FORMAT, "M月d日  EEEE")
            format12Hour = format
            format24Hour = format
        }
        AlertDialog.Builder(this)
            .setTitle("数字时钟组件预览")
            .setView(preview)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("恢复数字时钟默认设置")
            .setMessage("将清除时间、日期星期和四个快捷入口的自定义点击目标，恢复默认行为。不会影响其他组件或参数中心。")
            .setNegativeButton("取消", null)
            .setPositiveButton("恢复默认") { _, _ ->
                slots.forEach { store.edit().remove(key(it.key)).apply() }
                updateWidget()
                render()
                Toast.makeText(this, "数字时钟设置已恢复默认", Toast.LENGTH_SHORT).show()
            }
            .show()
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



    private fun showAppearanceSettings() {
        val items = arrayOf("小时字号", "分钟字号", "日期字号", "时间数字颜色", "日期文字颜色", "日期显示格式")
        AlertDialog.Builder(this)
            .setTitle("数字显示样式")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> chooseSize("小时字号", KEY_HOUR_SIZE, 48f, listOf(28f, 32f, 36f, 40f, 44f, 48f, 52f, 56f, 60f))
                    1 -> chooseSize("分钟字号", KEY_MINUTE_SIZE, 48f, listOf(28f, 32f, 36f, 40f, 44f, 48f, 52f, 56f, 60f))
                    2 -> chooseSize("日期字号", KEY_DATE_SIZE, 15f, listOf(10f, 11f, 12f, 13f, 14f, 15f, 16f, 18f, 20f))
                    3 -> chooseColor("时间数字颜色", KEY_TIME_COLOR, Color.WHITE)
                    4 -> chooseColor("日期文字颜色", KEY_DATE_COLOR, Color.LTGRAY)
                    5 -> chooseDateFormat()
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun chooseSize(title: String, key: String, defaultValue: Float, values: List<Float>) {
        val current = store.getFloat(key, defaultValue)
        val labels = values.map { "${it.toInt()} sp" }.toTypedArray()
        val selected = values.indexOf(current).coerceAtLeast(values.indexOf(defaultValue).coerceAtLeast(0))
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                store.edit().putFloat(key, values[which]).apply()
                updateWidget()
                dialog.dismiss()
                showAppearanceSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun chooseColor(title: String, key: String, defaultValue: Int) {
        val names = arrayOf("白色", "浅灰", "黑色", "黄色", "青色", "浅蓝", "粉色")
        val colors = intArrayOf(
            Color.WHITE, Color.LTGRAY, Color.BLACK, Color.YELLOW,
            Color.CYAN, Color.rgb(120, 190, 255), Color.rgb(255, 170, 190)
        )
        val current = store.getInt(key, defaultValue)
        val selected = colors.indexOf(current)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(names, selected) { dialog, which ->
                store.edit().putInt(key, colors[which]).apply()
                updateWidget()
                dialog.dismiss()
                showAppearanceSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun chooseDateFormat() {
        val formats = listOf(
            "M月d日  EEEE" to "11月24日 星期二",
            "yyyy年M月d日  EEEE" to "2026年11月24日 星期二",
            "EEEE" to "星期二",
            "yyyy-MM-dd  EEE" to "2026-11-24 周二"
        )
        val current = store.getString(KEY_DATE_FORMAT, formats[0].first)
        val selected = formats.indexOfFirst { it.first == current }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("日期显示格式")
            .setSingleChoiceItems(formats.map { it.second }.toTypedArray(), selected) { dialog, which ->
                store.edit().putString(KEY_DATE_FORMAT, formats[which].first).apply()
                updateWidget()
                dialog.dismiss()
                showAppearanceSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun key(slot: String) = "digital_clock_target_$slot"

    companion object {
        private const val KEY_HOUR_SIZE = "digital_clock_hour_size"
        private const val KEY_MINUTE_SIZE = "digital_clock_minute_size"
        private const val KEY_DATE_SIZE = "digital_clock_date_size"
        private const val KEY_TIME_COLOR = "digital_clock_time_color"
        private const val KEY_DATE_COLOR = "digital_clock_date_color"
        private const val KEY_DATE_FORMAT = "digital_clock_date_format"
    }
    private fun resolvePageBackground() = org.isoron.uhabits.utils.StyledResources(this).getColor(R.attr.contrast0)
    private fun resolvePrimaryText(): Int = if (isDark()) Color.WHITE else Color.BLACK
    private fun resolveSecondaryText(): Int = if (isDark()) Color.LTGRAY else Color.DKGRAY
    private fun isDark() = androidx.core.graphics.ColorUtils.calculateLuminance(resolvePageBackground()) < 0.5
}
