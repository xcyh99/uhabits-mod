package org.isoron.uhabits.activities.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.utils.StyledResources

class ShortcutManagementActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val component = (application as HabitsApplication).component
        AndroidThemeSwitcher(this, component.preferences).apply()
        super.onCreate(savedInstanceState)
        title = "快捷管理"
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
    private val entries by lazy { FeatureShortcutCatalog.getEntries(this) }
    private val selected by lazy { FeatureShortcutCatalog.getAllowedKeys(this).toMutableSet() }
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var primaryText = Color.BLACK
    private var secondaryText = Color.DKGRAY

    override fun onCreate(savedInstanceState: Bundle?) {
        val component = (application as HabitsApplication).component
        AndroidThemeSwitcher(this, component.preferences).apply()
        super.onCreate(savedInstanceState)
        title = "快捷入口管理"
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val background = StyledResources(this).getColor(R.attr.contrast0)
        val dark = androidx.core.graphics.ColorUtils.calculateLuminance(background) < 0.5
        primaryText = if (dark) Color.WHITE else Color.BLACK
        secondaryText = if (dark) Color.LTGRAY else Color.DKGRAY
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(18)); setBackgroundColor(background) }
        root.addView(TextView(this).apply {
            text = "勾选后，该项目才会出现在各处「添加快捷方式」的候选菜单中。取消勾选只隐藏候选项，不删除已经创建的快捷方式。"
            textSize = 13f; setTextColor(secondaryText); setPadding(0, 0, 0, dp(10))
        })
        search = EditText(this).apply { hint = "搜索项目、功能或应用"; setSingleLine(true); textSize = 15f }
        root.addView(search, LinearLayout.LayoutParams(-1, -2))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val selectAll = android.widget.Button(this@ShortcutEntryManagementActivity).apply {
                text = "全选"
                setOnClickListener {
                    selected.addAll(entries.map { it.key })
                    render(search.text.toString())
                }
            }
            val clearAll = android.widget.Button(this@ShortcutEntryManagementActivity).apply {
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
        root.addView(android.widget.Button(this).apply {
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

    private fun render(text: String) {
        val query = text.trim().lowercase()
        list.removeAllViews()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val visible = entries.filter {
            query.isEmpty() || it.name.lowercase().contains(query) ||
                it.category.lowercase().contains(query)
        }
        list.addView(TextView(this).apply {
            this.text = "已选 ${selected.size} 项 / 共 ${entries.size} 项"
            textSize = 12f
            setTextColor(secondaryText)
            setPadding(0, dp(8), 0, dp(8))
        })
        val categories = listOf("功能", "设置", "习惯", "应用", "系统")
        categories.forEach { category ->
            val items = visible.filter { it.category == category }
            if (items.isEmpty()) return@forEach
            val color = categoryColor(category)
            list.addView(TextView(this).apply {
                this.text = category
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(color)
                setPadding(0, dp(14), 0, dp(5))
            })
            items.forEach { entry ->
                list.addView(CheckBox(this).apply {
                    this.text = entry.name
                    textSize = 14f
                    setTextColor(primaryText)
                    isChecked = entry.key in selected
                    setPadding(dp(12), dp(5), dp(8), dp(5))
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selected.add(entry.key) else selected.remove(entry.key)
                        (list.getChildAt(0) as? TextView)?.text =
                            "已选 ${selected.size} 项 / 共 ${entries.size} 项"
                    }
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }
        if (visible.isEmpty()) {
            list.addView(TextView(this).apply {
                this.text = "没有找到匹配的入口"
                textSize = 14f
                setTextColor(secondaryText)
                setPadding(0, dp(24), 0, dp(24))
                gravity = android.view.Gravity.CENTER
            })
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
