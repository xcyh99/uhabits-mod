package org.isoron.uhabits.activities.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.utils.StyledResources

class ParameterCenterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val preferences = (application as HabitsApplication).component.preferences
        AndroidThemeSwitcher(this, preferences).apply()
        super.onCreate(savedInstanceState)
        title = "参数中心"

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val background = StyledResources(this).getColor(R.attr.contrast0)
        val dark = androidx.core.graphics.ColorUtils.calculateLuminance(background) < 0.5
        val primary = if (dark) Color.WHITE else Color.BLACK
        val secondary = if (dark) Color.LTGRAY else Color.DKGRAY
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(background)
        }
        root.addView(TextView(this).apply {
            text = "参数中心"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(primary)
            setPadding(dp(2), dp(8), dp(2), dp(6))
        })
        root.addView(TextView(this).apply {
            text = "集中管理显示效果与启动行为参数"
            textSize = 12f
            setTextColor(secondary)
            setPadding(dp(2), 0, dp(2), dp(16))
        })

        fun item(name: String, summary: String, key: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(16), dp(14), dp(16))
                background = GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(45, 48, 55) else Color.WHITE)
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), if (dark) Color.rgb(75, 78, 86) else Color.rgb(225, 228, 233))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(Intent(this@ParameterCenterActivity, SettingsActivity::class.java)
                        .putExtra("open_preference_key", key))
                }
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this@ParameterCenterActivity).apply {
                text = name
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(primary)
            })
            labels.addView(TextView(this@ParameterCenterActivity).apply {
                text = summary
                textSize = 12f
                setTextColor(secondary)
                setPadding(0, dp(4), 0, 0)
            })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply {
                text = "›"
                textSize = 26f
                setTextColor(secondary)
            })
            root.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }

        item("白色数字权重", "调整历史组件与标记组件的白色数字显示权重", "whiteTextWeightTargets")
        item("白边参数", "设置 APP 与桌面组件的白边阈值和宽度", "whiteOutline")
        item("启动首页设置", "选择每次打开 APP 时首先显示的页面", "startupPage")

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(background)
            addView(root)
        })
    }
}
