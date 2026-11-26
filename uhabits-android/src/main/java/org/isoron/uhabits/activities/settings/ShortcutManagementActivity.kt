package org.isoron.uhabits.activities.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.isoron.habits.HabitsApplication
import org.isoron.habits.R
import org.isoron.habits.activities.AndroidThemeSwitcher
import org.isoron.habits.utils.StyledResources

class ShortcutManagementActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val component = (application as HabitsApplication).component
        AndroidThemeSwitcher(this, component.preferences).apply()
        super.onCreate(savedInstanceState)
        title = "快捷中心"
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val background = StyledResources(this).getColor(R.attr.contrast0)
        val dark = androidx.core.graphics.ColorUtils.calculateLuminance(background) < 0.5
        val primary = if (dark) Color.WHITE else Color.BLACK
        val secondary = if (dark) Color.LTGRAY else Color.DKGRAY
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)); setBackgroundColor(background) }
        fun item(name: String, summary: String, action: () -> Unit) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
                this.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(45,48,55) else Color.WHITE); cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), if (dark) Color.rgb(75,78,86) else Color.rgb(225,228,233))
                }
                isClickable = true; isFocusable = true; setOnClickListener { action() }
            }
            card.addView(TextView(this).apply { text = name; textSize = 16f; setTextColor(primary); setTypeface(typeface, android.graphics.Typeface.BOLD) })
            card.addView(TextView(this).apply { text = summary; textSize = 12f; setTextColor(secondary); setPadding(0, dp(4), 0, 0) })
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        item("快捷入口管理", "选择哪些内部功能、项目和外部应用出现在各处的添加候选菜单中。") {
            startActivity(Intent(this, ShortcutEntryManagementActivity::class.java))
        }
        item("快捷菜单管理", "管理十字习惯 APP 内部弹出的快捷菜单；原「快捷入口设置」功能保留。") {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra("open_preference_key", "featureShortcuts"))
        }
        item("桌面快捷管理", "创建、编辑、排序和删除已经添加到手机桌面的快捷方式。") {
            startActivity(Intent(this, DesktopShortcutActivity::class.java))
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }
}

class ShortcutEntryManagementActivity : AppCompatActivity() {
    private val entries = mutableListOf<FeatureShortcutCatalog.Entry>()
    private val selected = mutableSetOf<String>()
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private lateinit var scanButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var countView: TextView
    private val categoryIndicators = mutableMapOf<String, TextView>()
    private var scanning = false
    private var primaryText = Color.BLACK
    private var secondaryText = Color.DKGRAY

    override fun onCreate(savedInstanceState: Bundle?) {
        val component = (application as HabitsApplication).component
        AndroidThemeSwitcher(this, component.preferences).apply()
        super.onCreate(savedInstanceState)
        title = "快捷入口管理"
        entries.addAll(FeatureShortcutCatalog.getCachedEntries(this))
        selected.addAll(FeatureShortcutCatalog.getAllowedKeys(this))

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val background = StyledResources(this).getColor(R.attr.contrast0)
        val dark = androidx.core.graphics.ColorUtils.calculateLuminance(background) < 0.5
        primaryText = if (dark) Color.WHITE else Color.BLACK
        secondaryText = if (dark) Color.LTGRAY else Color.DKGRAY
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(18))
            setBackgroundColor(background)
        }
        root.addView(TextView(this).apply {
            text = "勾选后，该项目才会出现在各处「添加快捷方式」的候选菜单中。取消勾选只隐藏候选项，不删除已经创建的快捷方式。"
            textSize = 13f
            setTextColor(secondaryText)
            setPadding(0, 0, 0, dp(8))
        })

        scanButton = Button(this).apply {
            text = "开始扫描"
            setOnClickListener { scanCatalog() }
        }
        root.addView(scanButton, LinearLayout.LayoutParams(-1, dp(46)))
        status = TextView(this).apply {
            text = if (entries.isEmpty()) "尚未扫描。点击「开始扫描」发现可用入口。" else "已读取上次保存的入口目录；如需发现新入口，请手动扫描。"
            textSize = 12f
            setTextColor(secondaryText)
            setPadding(0, dp(6), 0, dp(2))
        }
        root.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = android.view.View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(4)))

        search = EditText(this).apply {
            hint = "搜索项目、功能或应用"
            setSingleLine(true)
            textSize = 15f
        }
        root.addView(search, LinearLayout.LayoutParams(-1, -2))

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val selectAll = Button(this@ShortcutEntryManagementActivity).apply {
                text = "全选"
                setOnClickListener {
                    selected.addAll(entries.map { it.key })
                    render(search.text.toString())
                }
            }
            val clearAll = Button(this@ShortcutEntryManagementActivity).apply {
                text = "全部取消"
                setOnClickListener {
                    selected.clear()
                    render(search.text.toString())
                }
            }
            addView(selectAll, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(6) })
            addView(clearAll, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(6) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(4) })

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(Button(this).apply {
            text = "保存"
            setOnClickListener {
                FeatureShortcutCatalog.saveAllowedKeys(this@ShortcutEntryManagementActivity, selected)
                Toast.makeText(this@ShortcutEntryManagementActivity, "候选范围已保存，所有添加入口同步生效", Toast.LENGTH_LONG).show()
                finish()
            }
        })
        setContentView(root)
        render("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun scanCatalog() {
        if (scanning) return
        scanning = true
        scanButton.isEnabled = false
        progress.visibility = android.view.View.VISIBLE
        status.text = "正在准备扫描..."
        val previousKeys = entries.map { it.key }.toSet()
        Thread {
            val result = runCatching {
                FeatureShortcutCatalog.getEntries(this) { stage ->
                    runOnUiThread {
                        if (!isFinishing) status.text = stage
                    }
                }
            }
            runOnUiThread {
                scanning = false
                scanButton.isEnabled = true
                progress.visibility = android.view.View.GONE
                result.fold(
                    onSuccess = { discovered ->
                        val newCount = discovered.count { it.key !in previousKeys }
                        val existingCount = discovered.size - newCount
                        entries.clear()
                        entries.addAll(discovered)
                        status.text = "扫描完成：发现 ${discovered.size} 项；新增 ${newCount} 项；已有 ${existingCount} 项。新发现的入口默认不勾选。"
                        render(search.text.toString())
                    },
                    onFailure = { error ->
                        status.text = "扫描失败：${error.message ?: "未知错误"}。上次保存的目录保持不变。"
                        Toast.makeText(this, "扫描失败，未覆盖原有入口目录", Toast.LENGTH_LONG).show()
                        render(search.text.toString())
                    }
                )
            }
        }.start()
    }

    private fun render(text: String) {
        val query = text.trim().lowercase()
        list.removeAllViews()
        categoryIndicators.clear()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val visible = entries.filter {
            query.isEmpty() || it.name.lowercase().contains(query) ||
                it.category.lowercase().contains(query)
        }
        countView = TextView(this).apply {
            textSize = 12f
            setTextColor(secondaryText)
            setPadding(0, dp(8), 0, dp(8))
        }
        list.addView(countView)
        updateCount()

        if (entries.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "当前没有已保存的入口目录。请点击上方「开始扫描」。"
                textSize = 14f
                setTextColor(secondaryText)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(30), dp(8), dp(30))
            })
            return
        }

        val categories = listOf("功能", "设置", "习惯", "应用", "系统")
        categories.forEach { category ->
            val categoryEntries = entries.filter { it.category == category }
            val visibleItems = visible.filter { it.category == category }
            if (visibleItems.isEmpty()) return@forEach

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(4))
                isClickable = true
                isFocusable = true
            }
            header.addView(TextView(this).apply {
                this.text = category
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(categoryColor(category))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            val indicator = TextView(this).apply {
                textSize = 24f
                gravity = Gravity.CENTER
                minWidth = dp(42)
                setPadding(dp(8), 0, dp(2), 0)
                contentDescription = "${category}分类全选状态"
            }
            categoryIndicators[category] = indicator
            header.addView(indicator)
            val toggleCategory = {
                val allSelected = categoryEntries.isNotEmpty() && categoryEntries.all { it.key in selected }
                if (allSelected) categoryEntries.forEach { selected.remove(it.key) }
                else categoryEntries.forEach { selected.add(it.key) }
                render(search.text.toString())
            }
            header.setOnClickListener { toggleCategory() }
            indicator.setOnClickListener { toggleCategory() }
            list.addView(header)

            visibleItems.forEach { entry ->
                list.addView(CheckBox(this).apply {
                    this.text = FeatureShortcutCatalog.styledTitle(entry)
                    textSize = 14f
                    setTextColor(primaryText)
                    isChecked = entry.key in selected
                    setPadding(dp(12), dp(5), dp(8), dp(5))
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selected.add(entry.key) else selected.remove(entry.key)
                        updateCount()
                        updateCategoryIndicators()
                    }
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }

        if (visible.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "没有找到匹配的入口"
                textSize = 14f
                setTextColor(secondaryText)
                setPadding(0, dp(24), 0, dp(24))
                gravity = Gravity.CENTER
            })
        }
        updateCategoryIndicators()
    }

    private fun updateCount() {
        if (!::countView.isInitialized) return
        countView.text = "已选 ${entries.count { it.key in selected }} 项 / 共 ${entries.size} 项"
    }

    private fun updateCategoryIndicators() {
        categoryIndicators.forEach { (category, view) ->
            val items = entries.filter { it.category == category }
            val chosen = items.count { it.key in selected }
            when {
                items.isEmpty() || chosen == 0 -> {
                    view.text = "☐"
                    view.setTextColor(Color.GRAY)
                }
                chosen == items.size -> {
                    view.text = "☑"
                    view.setTextColor(Color.rgb(46, 160, 67))
                }
                else -> {
                    view.text = "◩"
                    view.setTextColor(Color.rgb(230, 145, 35))
                }
            }
        }
    }

    private fun categoryColor(category: String): Int = when (category) {
        "功能" -> Color.rgb(52, 120, 212)
        "设置" -> Color.rgb(137, 86, 200)
        "习惯" -> Color.rgb(38, 155, 101)
        "应用" -> Color.rgb(229, 138, 50)
        "系统" -> Color.rgb(113, 128, 150)
        else -> secondaryText
    }
}
