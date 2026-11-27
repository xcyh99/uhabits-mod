package org.isoron.uhabits.activities.settings

import android.content.ComponentName
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.content.ClipData
import android.view.View
import android.view.DragEvent
import android.widget.CheckBox
import android.widget.ImageView
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.activities.AndroidThemeSwitcher

/** Manages the fourth-row shortcuts of the digital-clock widget. */
class DigitalClockShortcutManagementActivity : AppCompatActivity() {
    private val store by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val rows = (1..8).toMutableList()
    private var maxCount = 4
    private lateinit var list: LinearLayout
    private var dragging = false

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) renderRows()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as HabitsApplication).component.preferences.let { AndroidThemeSwitcher(this, it).apply() }
        super.onCreate(savedInstanceState)
        title = "快捷方式管理"
        maxCount = store.getInt(KEY_COUNT, 4).coerceIn(0, 8)
        loadOrder()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(24))
            setBackgroundColor(resolvePageBackground())
        }
        root.addView(TextView(this).apply {
            text = "显示数量"
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolvePrimaryText())
            setPadding(dp(2), dp(12), dp(2), dp(8))
        })
        val countCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = cardBackground()
        }
        val countLabel = TextView(this).apply {
            text = "最多显示"
            textSize = 15f
            setTextColor(resolvePrimaryText())
        }
        countCard.addView(countLabel, LinearLayout.LayoutParams(0, -2, 1f))
        val countValue = TextView(this).apply {
            text = "$maxCount 个  ›"
            textSize = 15f
            setTextColor(resolvePrimaryText())
            setPadding(dp(8), dp(6), dp(4), dp(6))
            setOnClickListener { chooseCount { maxCount = it; store.edit().putInt(KEY_COUNT, it).apply(); text = "$it 个  ›"; renderRows() } }
        }
        countCard.addView(countValue)
        countCard.setOnClickListener { countValue.performClick() }
        root.addView(countCard)
        root.addView(TextView(this).apply {
            text = "勾选要显示的快捷方式，最多不能超过上面的数量。按住右侧手柄拖动排序。"
            textSize = 12f
            setTextColor(resolveSecondaryText())
            setPadding(dp(2), dp(8), dp(2), dp(10))
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        setContentView(android.widget.ScrollView(this).apply { isFillViewport = true; addView(root) })
        ensureDefaults()
        renderRows()
    }

    private fun ensureDefaults() {
        if (!store.contains(KEY_ENABLED + "1")) {
            val e = store.edit()
            for (i in 1..8) e.putBoolean(KEY_ENABLED + i, i <= minOf(4, maxCount))
            e.apply()
        }
    }

    private fun renderRows() {
        list.removeAllViews()
        rows.forEachIndexed { index, slot ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(8), dp(8))
                background = cardBackground()
                setOnDragListener { _, event ->
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> event.localState is Int
                        DragEvent.ACTION_DRAG_ENTERED -> { alpha = 0.72f; true }
                        DragEvent.ACTION_DRAG_EXITED -> { alpha = 1f; true }
                        DragEvent.ACTION_DRAG_ENDED -> { alpha = 1f; true }
                        DragEvent.ACTION_DROP -> {
                            alpha = 1f
                            val draggedSlot = event.localState as? Int
                            val from = rows.indexOf(draggedSlot)
                            if (from >= 0 && from != index) {
                                val moved = rows.removeAt(from)
                                rows.add(index.coerceIn(0, rows.size), moved)
                                persistOrder()
                                renderRows()
                            }
                            true
                        }
                        else -> true
                    }
                }
            }
            val check = CheckBox(this).apply {
                buttonTintList = android.content.res.ColorStateList.valueOf(if (isChecked) PURPLE else resolveSecondaryText())
                isChecked = store.getBoolean(KEY_ENABLED + slot, slot <= minOf(4, maxCount))
                text = ""
                setOnCheckedChangeListener { _, checked ->
                    val selected = rows.count { store.getBoolean(KEY_ENABLED + it, it <= minOf(4, maxCount)) }
                    if (checked && !store.getBoolean(KEY_ENABLED + slot, slot <= 4) && selected >= maxCount) {
                        isChecked = false
                        Toast.makeText(this@DigitalClockShortcutManagementActivity, "已达到${maxCount}个快捷方式上限，请先取消其他项目。", Toast.LENGTH_SHORT).show()
                    } else {
                        store.edit().putBoolean(KEY_ENABLED + slot, checked).apply()
                        updateWidget()
                    }
                }
            }
            row.addView(check, LinearLayout.LayoutParams(dp(32), dp(44)))
            row.addView(TextView(this).apply {
                text = (index + 1).toString()
                textSize = 13f
                setTextColor(PURPLE)
                gravity = Gravity.CENTER
                width = dp(24)
            })
            val target = store.getString(targetKey(slot), null)
            val name = target?.let(::targetLabel) ?: "未设置"
            val rowIcon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(7) }
                scaleType = when (store.getInt(iconModeKey(slot), 0).coerceIn(0, 2)) {
                    1 -> ImageView.ScaleType.FIT_XY
                    2 -> ImageView.ScaleType.CENTER_CROP
                    else -> ImageView.ScaleType.FIT_CENTER
                }
                val uri = store.getString(iconUriKey(slot), null)?.let(Uri::parse)
                val bitmap = uri?.let { runCatching { contentResolver.openInputStream(it)?.use { stream -> BitmapFactory.decodeStream(stream) } }.getOrNull() }
                if (bitmap != null) {
                    setImageBitmap(bitmap)
                } else {
                    val drawable = when {
                        target?.startsWith("feature:") == true ->
                            FeatureShortcutCatalog.getCachedEntry(this@DigitalClockShortcutManagementActivity, target.removePrefix("feature:"))?.icon
                        target?.startsWith("app:") == true -> {
                            val component = ComponentName.unflattenFromString(target.removePrefix("app:"))
                            component?.let { runCatching { packageManager.getActivityInfo(it, 0).loadIcon(packageManager) }.getOrNull() }
                        }
                        target == "system:settings" -> getDrawable(android.R.drawable.ic_menu_manage)
                        else -> getDrawable(android.R.drawable.ic_menu_view)
                    }
                    setImageDrawable(drawable ?: getDrawable(android.R.drawable.ic_menu_view))
                }
                isClickable = true
                setOnClickListener { startActivity(Intent(this@DigitalClockShortcutManagementActivity, DigitalClockShortcutIconActivity::class.java).putExtra(EXTRA_SLOT, slot)) }
            }
            row.addView(rowIcon)
            row.addView(TextView(this).apply {
                text = name
                textSize = 15f
                setTextColor(resolvePrimaryText())
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), 0, dp(4), 0)
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            val handle = TextView(this).apply {
                text = "☷"
                textSize = 23f
                gravity = Gravity.CENTER
                setTextColor(resolveSecondaryText())
                setPadding(dp(7), 0, dp(7), 0)
                contentDescription = "拖动排序"
                setOnLongClickListener {
                    val clip = ClipData.newPlainText("shortcut-slot", slot.toString())
                    if (android.os.Build.VERSION.SDK_INT >= 24) {
                        startDragAndDrop(clip, View.DragShadowBuilder(this), slot, 0)
                    } else {
                        startDrag(clip, View.DragShadowBuilder(this), slot, 0)
                    }
                    true
                }
            }
            row.addView(handle, LinearLayout.LayoutParams(dp(38), dp(44)))
            row.addView(TextView(this).apply {
                text = "编辑"; textSize = 12f; gravity = Gravity.CENTER
                setTextColor(PURPLE); setPadding(dp(3), dp(10), dp(3), dp(10))
                setOnClickListener { editTarget(slot) }
            }, LinearLayout.LayoutParams(dp(42), dp(44)).apply { marginStart = dp(2) })
            row.addView(TextView(this).apply {
                text = "删除"; textSize = 12f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(211, 76, 76)); setPadding(dp(3), dp(10), dp(3), dp(10))
                setOnClickListener { confirmDelete(slot) }
            }, LinearLayout.LayoutParams(dp(42), dp(44)).apply { marginStart = dp(3) })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
    }

    private fun actionIcon(drawable: Int, description: String, tint: Int, action: () -> Unit) =
        android.widget.ImageView(this).apply {
            setImageResource(drawable)
            imageTintList = android.content.res.ColorStateList.valueOf(tint)
            contentDescription = description
            scaleType = android.widget.ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun chooseCount(onSelected: (Int) -> Unit) {
        val values = (0..8).toList()
        AlertDialog.Builder(this).setTitle("快捷方式数量")
            .setSingleChoiceItems(values.map { "$it 个" }.toTypedArray(), values.indexOf(maxCount)) { dialog, which ->
                val next = values[which]
                val selected = rows.count { store.getBoolean(KEY_ENABLED + it, it <= 4) }
                if (selected > next) {
                    AlertDialog.Builder(this).setMessage("当前已勾选${selected}个。请先取消多余项目，再将数量调整为${next}个。")
                        .setPositiveButton("知道了", null).show()
                } else {
                    onSelected(next)
                    dialog.dismiss()
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun editTarget(slot: Int) {
        FeatureShortcutPicker.show(
            context = this,
            title = "选择快捷方式",
            entries = FeatureShortcutCatalog.getSelectedEntries(this),
            includeNone = false
        ) { selectedKey ->
            store.edit().putString(targetKey(slot), selectedKey).apply()
            renderRows()
            updateWidget()
        }
    }

    private fun confirmDelete(slot: Int) {
        val name = store.getString(targetKey(slot), null)?.let(::targetLabel) ?: "这个快捷方式"
        AlertDialog.Builder(this).setTitle("删除快捷方式")
            .setMessage("确定删除「$name」快捷方式吗？删除后只从快捷方式列表和桌面第四行移除，不会删除原始时间记录及其数据。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                store.edit().remove(targetKey(slot)).putBoolean(KEY_ENABLED + slot, false).apply()
                renderRows()
                updateWidget()
            }.show()
    }

    private fun persistOrder() {
        store.edit().putString(KEY_ORDER, rows.joinToString(",")).apply()
    }

    private fun loadOrder() {
        val saved = store.getString(KEY_ORDER, null)?.split(",")?.mapNotNull { it.toIntOrNull() } ?: return
        val normalized = saved.filter { it in 1..8 }.distinct()
        if (normalized.isNotEmpty()) {
            rows.clear()
            rows.addAll(normalized + (1..8).filterNot { it in normalized })
        }
    }

    private fun targetKey(slot: Int) = "digital_clock_target_shortcut$slot"
    private fun iconUriKey(slot: Int) = "digital_clock_shortcut_icon_uri_$slot"
    private fun iconModeKey(slot: Int) = "digital_clock_shortcut_icon_mode_$slot"
    private fun targetLabel(value: String): String = when {
        value.startsWith("feature:") -> FeatureShortcutCatalog.getCachedEntry(this, value.removePrefix("feature:"))?.name ?: "十字习惯功能"
        value.startsWith("app:") -> {
            val c = ComponentName.unflattenFromString(value.removePrefix("app:"))
            if (c == null) "手机应用" else runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(c.packageName, 0)).toString() }.getOrDefault("手机应用")
        }
        value == "system:settings" -> "系统设置"
        else -> "快捷方式"
    }

    private fun updateWidget() {
        val manager = android.appwidget.AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, org.isoron.uhabits.widgets.DigitalClockWidgetProvider::class.java))
        if (ids.isNotEmpty()) org.isoron.uhabits.widgets.DigitalClockWidgetProvider.updateAll(this, manager, ids)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun cardBackground() = android.graphics.drawable.GradientDrawable().apply {
        setColor(if (isDark()) Color.rgb(45, 48, 55) else Color.WHITE)
        cornerRadius = dp(13).toFloat()
        setStroke(dp(1), if (isDark()) Color.rgb(70, 74, 82) else Color.rgb(230, 233, 238))
    }
    private fun resolvePageBackground() = org.isoron.uhabits.utils.StyledResources(this).getColor(org.isoron.uhabits.R.attr.contrast0)
    private fun resolvePrimaryText(): Int = if (isDark()) Color.WHITE else Color.BLACK
    private fun resolveSecondaryText(): Int = if (isDark()) Color.LTGRAY else Color.DKGRAY
    private fun isDark() = androidx.core.graphics.ColorUtils.calculateLuminance(resolvePageBackground()) < 0.5
    companion object {
        const val EXTRA_SLOT = "digital_clock_shortcut_icon_slot"
        private const val KEY_COUNT = "digital_clock_shortcut_count"
        private const val KEY_ENABLED = "digital_clock_shortcut_enabled_"
        private const val KEY_ORDER = "digital_clock_shortcut_order"
        private val PURPLE = Color.rgb(149, 117, 205)
    }
}
