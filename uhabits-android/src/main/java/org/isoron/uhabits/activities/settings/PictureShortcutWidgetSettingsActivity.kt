package org.isoron.uhabits.activities.settings

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.widgets.PictureShortcutWidgetConfigureActivity
import org.isoron.uhabits.widgets.PictureShortcutWidgetProvider

class PictureShortcutWidgetSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        (application as HabitsApplication).component.preferences.let {
            AndroidThemeSwitcher(this, it).apply()
        }
        super.onCreate(savedInstanceState)
        title = "图片组件设置"
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(if (dark) Color.rgb(25, 27, 31) else Color.rgb(246, 247, 249))
        }
        root.addView(TextView(this).apply {
            text = "图片组件设置"
            textSize = 19f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (dark) Color.WHITE else Color.BLACK)
            setPadding(dp(2), dp(8), dp(2), dp(8))
        })
        root.addView(TextView(this).apply {
            text = "选择一个已添加到桌面的图片组件，可以重新选择图片、设置左右点击目标和调整分界比例。新增组件请从手机桌面的小组件列表中添加“图片快捷组件”。"
            textSize = 13f
            setTextColor(if (dark) Color.LTGRAY else Color.DKGRAY)
            setPadding(dp(2), 0, dp(2), dp(14))
        })
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(android.content.ComponentName(this, PictureShortcutWidgetProvider::class.java))
        if (ids.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "目前还没有已添加的图片组件。请先在手机桌面的小组件列表中添加“图片快捷组件”，添加时会自动打开图片配置窗口。"
                textSize = 14f
                setTextColor(if (dark) Color.WHITE else Color.BLACK)
                setPadding(dp(12), dp(16), dp(12), dp(16))
            })
        } else {
            ids.forEachIndexed { index, id ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (dark) Color.rgb(45, 48, 55) else Color.WHITE)
                        cornerRadius = dp(12).toFloat()
                        setStroke(dp(1), if (dark) Color.rgb(70, 74, 82) else Color.rgb(230, 233, 238))
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        startActivity(Intent(this@PictureShortcutWidgetSettingsActivity,
                            PictureShortcutWidgetConfigureActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    }
                }
                card.addView(TextView(this).apply {
                    text = "图片组件 ${index + 1}（编号 ${id}）"
                    textSize = 15f
                    setTextColor(if (dark) Color.WHITE else Color.BLACK)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(TextView(this).apply {
                    text = "›"
                    textSize = 26f
                    setTextColor(if (dark) Color.LTGRAY else Color.DKGRAY)
                })
                root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }
}
