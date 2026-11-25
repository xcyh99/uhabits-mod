package org.isoron.uhabits.activities.settings

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.widgets.WidgetCatalog

class ComponentCenterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        (application as HabitsApplication).component.preferences.let { AndroidThemeSwitcher(this, it).apply() }
        super.onCreate(savedInstanceState)
        title = "组件中心"
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(if (dark) Color.rgb(25, 27, 31) else Color.rgb(246, 247, 249))
        }
        root.addView(TextView(this).apply {
            text = "组件中心"; textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (dark) Color.WHITE else Color.BLACK)
            setPadding(dp(2), dp(8), dp(2), dp(14))
        })
        fun card(title: String, summary: String, action: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(16), dp(14), dp(16))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(45, 48, 55) else Color.WHITE)
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), if (dark) Color.rgb(70, 74, 82) else Color.rgb(230, 233, 238))
                }
                isClickable = true; isFocusable = true; setOnClickListener { action() }
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this@ComponentCenterActivity).apply {
                text = title; textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.WHITE else Color.BLACK)
            })
            labels.addView(TextView(this@ComponentCenterActivity).apply {
                text = summary; textSize = 12f; setTextColor(if (dark) Color.LTGRAY else Color.DKGRAY)
                setPadding(0, dp(4), 0, 0)
            })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply { text = "›"; textSize = 26f; setTextColor(if (dark) Color.LTGRAY else Color.DKGRAY) })
            root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        card("组件添加", "单个添加或批量添加已注册的桌面组件") {
            startActivity(Intent(this, ComponentAddActivity::class.java))
        }
        card("组件设置", "数字时钟、图片组件及其他组件的专属设置") {
            startActivity(Intent(this, ComponentSettingsActivity::class.java))
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }
}

class ComponentSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        (application as HabitsApplication).component.preferences.let { AndroidThemeSwitcher(this, it).apply() }
        super.onCreate(savedInstanceState)
        title = "组件设置"
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(if (dark) Color.rgb(25, 27, 31) else Color.rgb(246, 247, 249))
        }
        fun item(title: String, summary: String, action: () -> Unit) {
            val row = TextView(this).apply {
                text = "$title\n$summary   ›"; textSize = 15f
                setTextColor(if (dark) Color.WHITE else Color.BLACK)
                setPadding(dp(16), dp(15), dp(16), dp(15))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(45, 48, 55) else Color.WHITE)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), if (dark) Color.rgb(70, 74, 82) else Color.rgb(230, 233, 238))
                }
                isClickable = true; isFocusable = true; setOnClickListener { action() }
            }
            root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        item("数字时钟设置", "显示、布局、字体和点击区域") {
            startActivity(Intent(this, DesktopWidgetSettingsActivity::class.java).putExtra("open_time_widget_control_page", true))
        }
        item("图片组件设置", "图片、点击区域及描边等设置") {
            startActivity(Intent(this, PictureShortcutWidgetSettingsActivity::class.java))
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }
}

class ComponentAddActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        (application as HabitsApplication).component.preferences.let { AndroidThemeSwitcher(this, it).apply() }
        super.onCreate(savedInstanceState)
        title = "组件添加"
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val entries = WidgetCatalog.discover(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(if (dark) Color.rgb(25, 27, 31) else Color.rgb(246, 247, 249))
        }
        root.addView(TextView(this).apply {
            text = "选择要添加的桌面组件。勾选多个可依次请求添加，最终由手机桌面确认。"
            textSize = 13f; setTextColor(if (dark) Color.LTGRAY else Color.DKGRAY)
            setPadding(dp(2), dp(4), dp(2), dp(12))
        })
        val checks = entries.map { entry ->
            android.widget.CheckBox(this).apply {
                text = "${entry.title}（已添加 ${entry.installedCount} 个）"
                textSize = 15f; setTextColor(if (dark) Color.WHITE else Color.BLACK)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }.also { root.addView(it) }
        }
        root.addView(android.widget.Button(this).apply {
            text = "添加所选组件"
            setOnClickListener {
                val manager = AppWidgetManager.getInstance(this@ComponentAddActivity)
                if (!manager.isRequestPinAppWidgetSupported) {
                    Toast.makeText(this@ComponentAddActivity, "当前桌面不支持应用内添加，请到桌面长按空白处添加。", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val selected = entries.indices.filter { checks[it].isChecked }
                if (selected.isEmpty()) {
                    Toast.makeText(this@ComponentAddActivity, "请先选择组件", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val accepted = selected.count { index ->
                    runCatching { manager.requestPinAppWidget(entries[index].provider, null, null) }.getOrDefault(false)
                }
                Toast.makeText(this@ComponentAddActivity, "已向桌面发送 $accepted 个添加请求，请逐一确认。", Toast.LENGTH_LONG).show()
                if (accepted > 0) window.decorView.post { moveTaskToBack(true) }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        if (entries.isEmpty()) root.addView(TextView(this).apply {
            text = "暂未发现已注册的桌面组件。"; setTextColor(if (dark) Color.WHITE else Color.BLACK)
        })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }
}
