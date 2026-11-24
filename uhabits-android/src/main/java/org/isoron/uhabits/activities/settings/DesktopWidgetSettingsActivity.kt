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
        Slot("date", "日期星期区域", "点击日期或星期后打开")
    ) + (1..8).map { Slot("shortcut$it", "快捷入口$it", "底部第${it}个位置") }
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
        button("四行高度与间距") { showRowSettings() }
        button("背景颜色与透明度") { showBackgroundSettings() }
        button("快捷入口数量") { chooseShortcutCount() }
        button("恢复数字时钟默认设置") { confirmReset() }
        heading("点击区域设置")
        slots.forEach { slot ->
            if (slot.key.startsWith("shortcut") && slot.key.removePrefix("shortcut").toInt() > store.getInt(KEY_SHORTCUT_COUNT, 4)) return@forEach
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
                listOf(KEY_HOUR_SIZE, KEY_MINUTE_SIZE, KEY_DATE_SIZE, KEY_TIME_COLOR, KEY_DATE_COLOR, KEY_DATE_FORMAT,
                    KEY_TIME_MODE, KEY_BG_COLOR, KEY_BG_ALPHA, KEY_SHORTCUT_COUNT,
                    KEY_HOUR_HEIGHT, KEY_MINUTE_HEIGHT, KEY_DATE_HEIGHT, KEY_SHORTCUT_HEIGHT, KEY_HOUR_WEIGHT, KEY_MINUTE_WEIGHT, KEY_DATE_WEIGHT, KEY_SHORTCUT_WEIGHT).forEach {
                    store.edit().remove(it).apply()
                }
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
        val items = arrayOf("小时字号", "分钟字号", "日期字号", "时间数字颜色", "日期文字颜色", "日期显示格式", "时间制式")
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
                    6 -> chooseTimeMode()
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
        val picker = org.isoron.uhabits.activities.common.dialogs.ColorPickerDialog().apply {
            setInitialColor(store.getInt(key, defaultValue))
            setDialogTitle(title)
            setListener { color ->
                store.edit().putInt(key, color.argb).apply()
                updateWidget()
            }
        }
        picker.show(supportFragmentManager, "digital_clock_color_picker_$key")
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


    private fun chooseTimeMode() {
        val labels = arrayOf("跟随手机系统", "12 小时制", "24 小时制")
        val selected = store.getInt(KEY_TIME_MODE, 0).coerceIn(0, 2)
        AlertDialog.Builder(this).setTitle("时间制式")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                store.edit().putInt(KEY_TIME_MODE, which).apply()
                updateWidget()
                dialog.dismiss()
                showAppearanceSettings()
            }.setNegativeButton("取消", null).show()
    }

    private fun chooseShortcutCount() {
        val values = (0..8).toList()
        val current = store.getInt(KEY_SHORTCUT_COUNT, 4).coerceIn(0, 8)
        val labels = values.map { if (it == 0) "不显示快捷入口" else "$it 个" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("快捷入口数量")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                store.edit().putInt(KEY_SHORTCUT_COUNT, values[which]).apply()
                updateWidget()
                dialog.dismiss()
                render()
            }.setNegativeButton("取消", null).show()
    }

    /**
     * The launcher owns the widget container size. Use only dimensions explicitly
     * advertised by AppWidgetOptions; never invent a fallback maximum.
     */
    private fun readAvailableWidgetContentHeight(): Int? {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, DigitalClockWidgetProvider::class.java))
        if (ids.isEmpty()) return null
        val heights = ids.mapNotNull { id ->
            val options = manager.getAppWidgetOptions(id)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            @Suppress("DEPRECATION")
            val sizes = if (android.os.Build.VERSION.SDK_INT >= 31) {
                options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            } else null
            val advertisedHeight = maxHeight.takeIf { it > 0 }
                ?: sizes?.maxOfOrNull { it.height.toInt() }?.takeIf { it > 0 }
            advertisedHeight?.minus(15)?.coerceAtLeast(0)
        }
        // Settings are shared by all clock widgets; use the smallest advertised
        // content height so one setting does not overflow a smaller instance.
        return heights.minOrNull()
    }

    private fun showRowSettings() {
        if (android.os.Build.VERSION.SDK_INT < 31) {
            AlertDialog.Builder(this)
                .setTitle("当前系统暂不支持")
                .setMessage("四行独立高度需要桌面组件动态尺寸接口（Android 12 / API 31 或更高）。当前系统无法可靠应用这些高度，因此暂不显示可调滑块。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }
        val availableHeight = readAvailableWidgetContentHeight()
        if (availableHeight == null || availableHeight <= 0) {
            AlertDialog.Builder(this)
                .setTitle("暂时无法获取桌面高度")
                .setMessage("请先把数字时钟组件添加到桌面，再重新打开此设置。如果鸿蒙桌面没有向组件提供尺寸信息，APP不会用猜测数值代替。")
                .setPositiveButton("重新检测") { _, _ -> showRowSettings() }
                .setNegativeButton("关闭", null)
                .show()
            return
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val info = TextView(this).apply {
            text = "桌面提供的组件高度上限已自动读取。扣除组件上下内边距后，四行可分配高度为 $availableHeight dp。四个滑块均从 0 到 $availableHeight dp。"
            textSize = 13f
            setTextColor(resolveSecondaryText())
            setPadding(0, 0, 0, dp(12))
        }
        panel.addView(info)
        val rowSpecs = listOf(
            Triple("第一行 · 小时", "digital_clock_hour_height", 48),
            Triple("第二行 · 分钟", "digital_clock_minute_height", 48),
            Triple("第三行 · 日期星期", "digital_clock_date_height", 27),
            Triple("第四行 · 快捷入口", "digital_clock_shortcut_height", 37)
        )
        val bars = mutableListOf<android.widget.SeekBar>()
        val valueLabels = mutableListOf<TextView>()
        rowSpecs.forEach { (label, prefKey, defaultValue) ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val name = TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(resolvePrimaryText())
            }
            val value = TextView(this).apply {
                textSize = 14f
                setTextColor(resolvePrimaryText())
                gravity = android.view.Gravity.END
                minWidth = dp(58)
            }
            line.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(value, LinearLayout.LayoutParams(-2, -2))
            panel.addView(line)
            val seek = android.widget.SeekBar(this).apply {
                max = availableHeight
                progress = store.getInt(prefKey, defaultValue).coerceIn(0, availableHeight)
            }
            panel.addView(seek, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(7)
            })
            bars += seek
            valueLabels += value
        }
        val summary = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(8), 0, dp(4))
        }
        val warning = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(2), 0, dp(4))
        }
        panel.addView(summary)
        panel.addView(warning)

        fun updateSummary() {
            val sum = bars.sumOf { it.progress }
            val remaining = availableHeight - sum
            bars.forEachIndexed { index, bar -> valueLabels[index].text = "${bar.progress} dp" }
            if (remaining >= 0) {
                summary.text = "四行合计：$sum dp    剩余：$remaining dp    每个间隔：${"%.2f".format(remaining / 3.0)} dp"
                summary.setTextColor(0xFF25834D.toInt())
                warning.text = "空间充足：剩余空间平均分配到三个间隔。"
                warning.setTextColor(0xFF25834D.toInt())
            } else {
                summary.text = "四行合计：$sum dp    超出：${-remaining} dp"
                summary.setTextColor(Color.RED)
                warning.text = "高度不足！请增加桌面组件高度，或调低某一行高度。"
                warning.setTextColor(Color.RED)
            }
        }
        bars.forEach { bar ->
            bar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) = updateSummary()
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            })
        }
        updateSummary()

        val dialog = AlertDialog.Builder(this)
            .setTitle("四行高度与自动间距")
            .setView(panel)
            .setNegativeButton("关闭", null)
            .setPositiveButton("应用", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val sum = bars.sumOf { it.progress }
                if (sum > availableHeight) {
                    Toast.makeText(this, "四行高度超过桌面可用高度，请先调整。", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                rowSpecs.forEachIndexed { index, spec ->
                    store.edit().putInt(spec.second, bars[index].progress).apply()
                }
                updateWidget()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showBackgroundSettings() {
        val choices = arrayOf("选择背景颜色", "调整背景透明度")
        AlertDialog.Builder(this).setTitle("背景设置")
            .setItems(choices) { _, which ->
                if (which == 0) chooseBackgroundColor() else chooseBackgroundAlpha()
            }.setNegativeButton("关闭", null).show()
    }

    private fun chooseBackgroundColor() {
        val current = store.getInt(KEY_BG_COLOR, Color.rgb(35, 45, 60))
        val picker = org.isoron.uhabits.activities.common.dialogs.ColorPickerDialog().apply {
            setInitialColor(current)
            setDialogTitle("选择组件背景颜色")
            setListener { color ->
                store.edit().putInt(KEY_BG_COLOR, color.argb).apply()
                updateWidget()
            }
        }
        picker.show(supportFragmentManager, "digital_clock_background_picker")
    }

    private fun chooseBackgroundAlpha() {
        val seek = android.widget.SeekBar(this).apply {
            max = 100
            progress = store.getInt(KEY_BG_ALPHA, 90).coerceIn(0, 100)
        }
        AlertDialog.Builder(this).setTitle("背景透明度")
            .setMessage("0% 为完全透明，100% 为完全不透明。")
            .setView(seek)
            .setPositiveButton("应用") { _, _ ->
                store.edit().putInt(KEY_BG_ALPHA, seek.progress).apply()
                updateWidget()
            }.setNegativeButton("取消", null).show()
    }

    private fun key(slot: String) = "digital_clock_target_$slot"

    companion object {
        private const val KEY_HOUR_SIZE = "digital_clock_hour_size"
        private const val KEY_MINUTE_SIZE = "digital_clock_minute_size"
        private const val KEY_DATE_SIZE = "digital_clock_date_size"
        private const val KEY_TIME_COLOR = "digital_clock_time_color"
        private const val KEY_DATE_COLOR = "digital_clock_date_color"
        private const val KEY_DATE_FORMAT = "digital_clock_date_format"
        private const val KEY_TIME_MODE = "digital_clock_time_mode"
        private const val KEY_BG_COLOR = "digital_clock_bg_color"
        private const val KEY_BG_ALPHA = "digital_clock_bg_alpha"
        private const val KEY_SHORTCUT_COUNT = "digital_clock_shortcut_count"
        private const val KEY_HOUR_HEIGHT = "digital_clock_hour_height"
        private const val KEY_MINUTE_HEIGHT = "digital_clock_minute_height"
        private const val KEY_DATE_HEIGHT = "digital_clock_date_height"
        private const val KEY_SHORTCUT_HEIGHT = "digital_clock_shortcut_height"
        private const val KEY_HOUR_WEIGHT = "digital_clock_hour_weight"
        private const val KEY_MINUTE_WEIGHT = "digital_clock_minute_weight"
        private const val KEY_DATE_WEIGHT = "digital_clock_date_weight"
        private const val KEY_SHORTCUT_WEIGHT = "digital_clock_shortcut_weight"
    }
    private fun resolvePageBackground() = org.isoron.uhabits.utils.StyledResources(this).getColor(R.attr.contrast0)
    private fun resolvePrimaryText(): Int = if (isDark()) Color.WHITE else Color.BLACK
    private fun resolveSecondaryText(): Int = if (isDark()) Color.LTGRAY else Color.DKGRAY
    private fun isDark() = androidx.core.graphics.ColorUtils.calculateLuminance(resolvePageBackground()) < 0.5
}
