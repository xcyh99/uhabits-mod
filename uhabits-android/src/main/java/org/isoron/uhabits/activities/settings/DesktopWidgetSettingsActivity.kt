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
        Slot("hour", "小时区域", "单独设置点击小时数字后打开的目标"),
        Slot("minute", "分钟区域", "单独设置点击分钟数字后打开的目标"),
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
            setPadding(dp(16), dp(10), dp(16), dp(28))
            setBackgroundColor(resolvePageBackground())
        }
        fun section(title: String, hint: String? = null) {
            root.addView(TextView(this).apply {
                text = title
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(resolvePrimaryText())
                setPadding(dp(2), dp(18), dp(2), dp(5))
            })
            if (hint != null) root.addView(TextView(this).apply {
                text = hint
                textSize = 12f
                setTextColor(resolveSecondaryText())
                setPadding(dp(2), 0, dp(2), dp(9))
            })
        }
        fun actionCard(title: String, summary: String, action: () -> Unit) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(13), dp(14), dp(13))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (isDark()) Color.rgb(45, 48, 55) else Color.WHITE)
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), if (isDark()) Color.rgb(70, 74, 82) else Color.rgb(230, 233, 238))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
            val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            textColumn.addView(TextView(this).apply {
                text = title
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(resolvePrimaryText())
            })
            textColumn.addView(TextView(this).apply {
                text = summary
                textSize = 12f
                setTextColor(resolveSecondaryText())
                setPadding(0, dp(3), 0, 0)
            })
            card.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(TextView(this).apply {
                text = "›"
                textSize = 27f
                setTextColor(resolveSecondaryText())
                gravity = android.view.Gravity.CENTER
            })
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }

        section("数字时钟组件", "四行布局：小时、分钟、日期星期、快捷入口")
        actionCard("添加到桌面", "将数字时钟组件添加到手机桌面") { pinWidget() }
        actionCard("预览效果", "先查看当前设置，不会修改参数") { showWidgetPreview() }

        section("显示与排版", "每一行可分别调整，互不影响")
        actionCard("数字显示样式", "字号、颜色、日期格式和时间制式") { showAppearanceSettings() }
        actionCard("字符间距与数字变形", "小时、分钟、日期星期分别独立设置") { showSpacingAndTransformSettings() }
        actionCard("四行高度与自动布局", "手动调整或让数字自动适配每一行") { showRowSettings() }
        actionCard("背景颜色与透明度", "调整组件底色和透明程度") { showBackgroundSettings() }

        section("快捷入口", "设置桌面组件底部显示几个快捷入口")
        actionCard("快捷入口数量", "当前显示 ${store.getInt(KEY_SHORTCUT_COUNT, 4).coerceIn(0, 8)} 个") { chooseShortcutCount() }

        section("点击区域", "点击组件不同区域时，打开对应功能或手机应用")
        slots.forEach { slot ->
            if (slot.key.startsWith("shortcut") && slot.key.removePrefix("shortcut").toInt() > store.getInt(KEY_SHORTCUT_COUNT, 4)) return@forEach
            val legacyTimeTarget = if (slot.key == "hour" || slot.key == "minute") store.getString("digital_clock_target_time", null) else null
            val current = (store.getString(key(slot.key), null) ?: legacyTimeTarget)?.let(::targetLabel) ?: "未设置"
            actionCard(slot.title, "当前：$current") { chooseTarget(slot) }
        }

        section("其他")
        actionCard("恢复数字时钟默认设置", "只重置数字时钟，不影响其他组件或参数中心") { confirmReset() }
        root.addView(TextView(this).apply {
            text = "白字权重、白边、颜色阈值等显示参数仍由「参数中心」统一管理。"
            textSize = 12f
            setTextColor(resolveSecondaryText())
            setPadding(dp(2), dp(10), dp(2), 0)
        })
        setContentView(android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    private fun showWidgetPreview() {
        val preview = LayoutInflater.from(this).inflate(R.layout.widget_digital_clock, null, false)
        preview.findViewById<TextView>(R.id.digital_clock_time).apply {
            textSize = store.getFloat(KEY_HOUR_SIZE, 48f)
            setTextColor(store.getInt(KEY_HOUR_COLOR, store.getInt(KEY_TIME_COLOR, Color.WHITE)))
        }
        preview.findViewById<TextView>(R.id.digital_clock_minute).apply {
            textSize = store.getFloat(KEY_MINUTE_SIZE, 48f)
            setTextColor(store.getInt(KEY_MINUTE_COLOR, store.getInt(KEY_TIME_COLOR, Color.WHITE)))
        }
        listOf(
            Triple(R.id.digital_clock_time, "hour", -0.04f),
            Triple(R.id.digital_clock_minute, "minute", -0.04f),
            Triple(R.id.digital_clock_date, "date", -0.04f)
        ).forEach { (id, rowKey, defaultSpacing) ->
            val spacing = store.getFloat("digital_clock_${rowKey}_letter_spacing", defaultSpacing)
            val enabled = store.getBoolean("digital_clock_${rowKey}_transform_enabled", false)
            val scale = if (enabled) store.getFloat("digital_clock_${rowKey}_horizontal_scale", 0.90f) else 1f
            preview.findViewById<TextView>(id).apply {
                letterSpacing = spacing
                scaleX = scale
            }
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
                store.edit().remove("digital_clock_target_time").apply()
                val resetKeys = listOf(KEY_HOUR_SIZE, KEY_MINUTE_SIZE, KEY_DATE_SIZE, KEY_TIME_COLOR, KEY_HOUR_COLOR, KEY_MINUTE_COLOR, KEY_DATE_COLOR, KEY_DATE_FORMAT,
                    KEY_TIME_MODE, KEY_BG_COLOR, KEY_BG_ALPHA, KEY_SHORTCUT_COUNT,
                    KEY_HOUR_HEIGHT, KEY_MINUTE_HEIGHT, KEY_DATE_HEIGHT, KEY_SHORTCUT_HEIGHT, KEY_HOUR_WEIGHT, KEY_MINUTE_WEIGHT, KEY_DATE_WEIGHT, KEY_SHORTCUT_WEIGHT,
                    "digital_clock_letter_spacing", "digital_clock_transform_enabled", "digital_clock_horizontal_scale", KEY_LAYOUT_MODE, KEY_KEEP_DIGIT_RATIO) +
                    listOf("hour", "minute", "date").flatMap { row ->
                        listOf("digital_clock_${row}_letter_spacing", "digital_clock_${row}_transform_enabled", "digital_clock_${row}_horizontal_scale")
                    }
                resetKeys.forEach { store.edit().remove(it).apply() }
                updateWidget()
                render()
                Toast.makeText(this, "数字时钟设置已恢复默认", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun chooseTarget(slot: Slot) {
        val options = mutableListOf<Pair<String, String>>()
        options += "none" to "【不设】点击无动作"
        FeatureShortcutCatalog.getEntries(this).forEach {
            options += "feature:${it.key}" to "【功能】${it.title}"
        }
        val pm = packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        apps.forEach { info ->
            val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
            options += "app:${component.flattenToString()}" to "【应用】${info.loadLabel(pm)}"
        }

        // Every clickable slot shares this searchable target picker.
        val filtered = options.toMutableList()
        val search = android.widget.EditText(this).apply {
            hint = "搜索程序或功能"
            singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val list = android.widget.ListView(this).apply {
            dividerHeight = dp(1)
            isTextFilterEnabled = false
        }
        val empty = TextView(this).apply {
            text = "没有找到匹配的程序或功能"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(resolveSecondaryText())
            visibility = android.view.View.GONE
        }
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = filtered.size
            override fun getItem(position: Int) = filtered[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val row = (convertView as? TextView) ?: TextView(this@DesktopWidgetSettingsActivity).apply {
                    textSize = 15f
                    setTextColor(resolvePrimaryText())
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(13), dp(16), dp(13))
                    minHeight = dp(48)
                }
                val label = getItem(position).second
                val close = label.indexOf('】')
                if (label.startsWith("【") && close > 0) {
                    val styled = android.text.SpannableString(label)
                    val tagColor = when (label.substring(1, close)) {
                        "应用" -> android.graphics.Color.rgb(55, 125, 220)
                        "功能" -> android.graphics.Color.rgb(35, 155, 105)
                        else -> android.graphics.Color.rgb(145, 145, 145)
                    }
                    styled.setSpan(
                        android.text.style.ForegroundColorSpan(tagColor),
                        0, close + 1,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    row.text = styled
                } else {
                    row.text = label
                }
                return row
            }
        }
        list.adapter = adapter
        list.emptyView = empty
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase().orEmpty()
                filtered.clear()
                filtered.addAll(if (query.isEmpty()) options else options.filter { it.second.lowercase().contains(query) })
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            addView(search, LinearLayout.LayoutParams(-1, -2))
            addView(list, LinearLayout.LayoutParams(-1, dp(360)))
            addView(empty, LinearLayout.LayoutParams(-1, dp(360)))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("设置${slot.title}的点击目标")
            .setView(content)
            .setNegativeButton("取消", null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            val selected = filtered[position]
            store.edit().putString(key(slot.key), selected.first).apply()
            updateWidget()
            dialog.dismiss()
            render()
        }
        dialog.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
        val items = arrayOf("小时字号", "分钟字号", "日期字号", "小时数字颜色", "分钟数字颜色", "日期文字颜色", "日期显示格式", "时间制式")
        AlertDialog.Builder(this)
            .setTitle("数字显示样式")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> chooseSize("小时字号", KEY_HOUR_SIZE, 48f)
                    1 -> chooseSize("分钟字号", KEY_MINUTE_SIZE, 48f)
                    2 -> chooseSize("日期字号", KEY_DATE_SIZE, 15f)
                    3 -> chooseColor("小时数字颜色", KEY_HOUR_COLOR, Color.WHITE)
                    4 -> chooseColor("分钟数字颜色", KEY_MINUTE_COLOR, Color.WHITE)
                    5 -> chooseColor("日期文字颜色", KEY_DATE_COLOR, Color.LTGRAY)
                    6 -> chooseDateFormat()
                    7 -> chooseTimeMode()
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun chooseSize(title: String, key: String, defaultValue: Float) {
        val current = store.getFloat(key, defaultValue)
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (current % 1f == 0f) current.toInt().toString() else current.toString())
            setSelection(text.length)
            hint = "输入 1～300"
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24 * resources.displayMetrics.density).toInt(), 0, (24 * resources.displayMetrics.density).toInt(), 0)
            addView(input)
            addView(android.widget.TextView(this@DesktopWidgetSettingsActivity).apply {
                text = "直接输入字号，单位 sp；允许范围 1～300。小时和分钟分别保存、分别生效。"
                textSize = 12f
                setTextColor(resolveSecondaryText())
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("应用", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().toFloatOrNull()
                if (value == null || value !in 1f..300f) {
                    input.error = "请输入 1～300 之间的数字"
                    return@setOnClickListener
                }
                store.edit().putFloat(key, value).apply()
                updateWidget()
                dialog.dismiss()
            }
        }
        dialog.show()
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

    private fun showRowSettings() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        panel.addView(TextView(this).apply {
            text = "行高只决定每一行可显示的窗口高度，不再限制字号。字号按你设置的数值绘制，超出行高的部分由窗口裁切。自动适配只调整数字横向宽度，不改变字号和行高。"
            textSize = 13f
            setTextColor(resolveSecondaryText())
            setPadding(0, 0, 0, dp(10))
        })

        val modeTitle = TextView(this).apply {
            text = "布局方式"
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolvePrimaryText())
            setPadding(0, dp(4), 0, dp(4))
        }
        panel.addView(modeTitle)
        val modeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isDark()) Color.rgb(45, 48, 55) else Color.rgb(245, 247, 250))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), if (isDark()) Color.rgb(70, 74, 82) else Color.rgb(230, 233, 238))
            }
        }
        val modeGroup = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }
        val manualOption = android.widget.RadioButton(this).apply {
            id = android.view.View.generateViewId()
            text = "手动布局"
            textSize = 14f
            setTextColor(resolvePrimaryText())
        }
        val autoOption = android.widget.RadioButton(this).apply {
            id = android.view.View.generateViewId()
            text = "自动适配"
            textSize = 14f
            setTextColor(resolvePrimaryText())
        }
        modeGroup.addView(manualOption)
        modeGroup.addView(autoOption)
        val savedMode = store.getInt(KEY_LAYOUT_MODE, 0).coerceIn(0, 1)
        modeGroup.check(if (savedMode == 1) autoOption.id else manualOption.id)
        modeCard.addView(modeGroup)

        val ratioCheck = android.widget.CheckBox(this).apply {
            text = "保持数字原始比例"
            textSize = 14f
            setTextColor(resolvePrimaryText())
            isChecked = store.getBoolean(KEY_KEEP_DIGIT_RATIO, true)
            isEnabled = savedMode == 1
        }
        modeCard.addView(ratioCheck)
        modeCard.addView(TextView(this).apply {
            text = "选中：数字保持原始宽高比例；不选中：自动模式允许数字横向加宽或变窄。此选项只影响宽度，不影响字号与行高。"
            textSize = 12f
            setTextColor(resolveSecondaryText())
            setPadding(dp(4), dp(2), dp(4), dp(4))
        })
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            ratioCheck.isEnabled = checkedId == autoOption.id
        }
        panel.addView(modeCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val rowSpecs = listOf(
            Triple("第一行 · 小时", KEY_HOUR_HEIGHT, 48),
            Triple("第二行 · 分钟", KEY_MINUTE_HEIGHT, 48),
            Triple("第三行 · 日期星期", KEY_DATE_HEIGHT, 27),
            Triple("第四行 · 快捷入口", KEY_SHORTCUT_HEIGHT, 37)
        )
        val inputs = rowSpecs.map { (label, prefKey, defaultValue) ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(5), 0, dp(5))
            }
            line.addView(TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(resolvePrimaryText())
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val input = android.widget.EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setSingleLine(true)
                setEms(5)
                gravity = android.view.Gravity.CENTER
                hint = "整数 dp"
                setText(store.getInt(prefKey, defaultValue).toString())
            }
            line.addView(input, LinearLayout.LayoutParams(dp(100), -2))
            panel.addView(line)
            input
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("四行高度（整数输入）")
            .setView(panel)
            .setNegativeButton("取消", null)
            .setPositiveButton("应用", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val values = inputs.mapIndexed { index, input ->
                    input.text.toString().trim().toIntOrNull().also { value ->
                        if (value == null || value !in 0..2000) {
                            input.error = "请输入 0～2000 的整数 dp"
                        }
                    }
                }
                if (values.any { it == null }) return@setOnClickListener
                rowSpecs.forEachIndexed { index, spec ->
                    store.edit().putInt(spec.second, values[index]!!).apply()
                }
                store.edit()
                    .putInt(KEY_LAYOUT_MODE, if (modeGroup.checkedRadioButtonId == autoOption.id) 1 else 0)
                    .putBoolean(KEY_KEEP_DIGIT_RATIO, ratioCheck.isChecked)
                    .apply()
                updateWidget()
                dialog.dismiss()
                Toast.makeText(this, "四行高度已保存", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun showSpacingAndTransformSettings() {
        val rows = listOf(
            Triple("第一行 · 小时", "hour", -0.04f),
            Triple("第二行 · 分钟", "minute", -0.04f),
            Triple("第三行 · 日期星期", "date", -0.04f)
        )
        val rowLabels = rows.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("独立字符间距与数字变形")
            .setItems(rowLabels) { _, which -> showRowSpacingSettings(rows[which].second, rows[which].first) }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showRowSpacingSettings(rowKey: String, rowTitle: String) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val spacingKey = "digital_clock_${rowKey}_letter_spacing"
        val enabledKey = "digital_clock_${rowKey}_transform_enabled"
        val scaleKey = "digital_clock_${rowKey}_horizontal_scale"
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        fun card(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isDark()) Color.rgb(48, 51, 58) else Color.rgb(245, 247, 250))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), if (isDark()) Color.rgb(75, 79, 88) else Color.rgb(225, 229, 235))
            }
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.bottomMargin = dp(12)
            panel.addView(this, lp)
        }
        fun label(parent: LinearLayout, value: String, size: Float = 14f, color: Int = resolvePrimaryText()) {
            parent.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
            })
        }
        label(panel, "仅调整$rowTitle，另外两行不会受到影响。", 13f, resolveSecondaryText())
        val spacingCard = card()
        label(spacingCard, "字符间距", 16f)
        val spacingValue = TextView(this).apply {
            textSize = 22f
            setTextColor(resolvePrimaryText())
            setPadding(0, dp(6), 0, dp(2))
        }
        spacingCard.addView(spacingValue)
        label(spacingCard, "范围 -0.20 ～ 0.20；越小字符越紧", 12f, resolveSecondaryText())
        val spacingSeek = android.widget.SeekBar(this).apply {
            max = 40
            progress = ((store.getFloat(spacingKey, -0.04f).coerceIn(-0.20f, 0.20f) + 0.20f) * 100f).toInt()
        }
        spacingCard.addView(spacingSeek)
        val transformCard = card()
        label(transformCard, "横向变形", 16f)
        val transformSwitch = android.widget.Switch(this).apply {
            text = "只对${rowTitle}启用"
            setTextColor(resolvePrimaryText())
            isChecked = store.getBoolean(enabledKey, false)
        }
        transformCard.addView(transformSwitch)
        val scaleValue = TextView(this).apply {
            textSize = 22f
            setTextColor(resolvePrimaryText())
            setPadding(0, dp(6), 0, dp(2))
        }
        label(transformCard, "宽度比例", 14f)
        transformCard.addView(scaleValue)
        label(transformCard, "0.60 为压窄，1.00 为原宽，1.20 为拉宽", 12f, resolveSecondaryText())
        val scaleSeek = android.widget.SeekBar(this).apply {
            max = 60
            progress = ((store.getFloat(scaleKey, 0.90f).coerceIn(0.60f, 1.20f) - 0.60f) * 100f).toInt()
        }
        transformCard.addView(scaleSeek)
        fun updateSpacingLabel(progress: Int) {
            spacingValue.text = String.format(java.util.Locale.US, "%.2f", progress / 100f - 0.20f)
        }
        fun updateScaleLabel(progress: Int) {
            scaleValue.text = String.format(java.util.Locale.US, "%.2f", progress / 100f + 0.60f)
        }
        spacingSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) = updateSpacingLabel(progress)
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        scaleSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) = updateScaleLabel(progress)
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        updateSpacingLabel(spacingSeek.progress)
        updateScaleLabel(scaleSeek.progress)
        fun refreshScale() {
            scaleSeek.isEnabled = transformSwitch.isChecked
            scaleValue.alpha = if (transformSwitch.isChecked) 1f else 0.4f
            scaleSeek.alpha = if (transformSwitch.isChecked) 1f else 0.4f
        }
        transformSwitch.setOnCheckedChangeListener { _, _ -> refreshScale() }
        refreshScale()
        val scroll = android.widget.ScrollView(this).apply {
            addView(panel)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(rowTitle)
            .setView(scroll)
            .setNegativeButton("取消", null)
            .setNeutralButton("恢复本行默认", null)
            .setPositiveButton("保存设置", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                spacingSeek.progress = 16
                transformSwitch.isChecked = false
                scaleSeek.progress = 30
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val spacing = spacingSeek.progress / 100f - 0.20f
                val scale = scaleSeek.progress / 100f + 0.60f
                store.edit()
                    .putFloat(spacingKey, spacing)
                    .putBoolean(enabledKey, transformSwitch.isChecked)
                    .putFloat(scaleKey, scale)
                    .apply()
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
        private const val KEY_HOUR_COLOR = "digital_clock_hour_color"
        private const val KEY_MINUTE_COLOR = "digital_clock_minute_color"
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
        private const val KEY_LAYOUT_MODE = "digital_clock_layout_mode"
        private const val KEY_KEEP_DIGIT_RATIO = "digital_clock_keep_digit_ratio"
    }
    private fun resolvePageBackground() = org.isoron.uhabits.utils.StyledResources(this).getColor(R.attr.contrast0)
    private fun resolvePrimaryText(): Int = if (isDark()) Color.WHITE else Color.BLACK
    private fun resolveSecondaryText(): Int = if (isDark()) Color.LTGRAY else Color.DKGRAY
    private fun isDark() = androidx.core.graphics.ColorUtils.calculateLuminance(resolvePageBackground()) < 0.5
}
