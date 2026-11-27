package org.isoron.uhabits.activities.settings

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
import org.isoron.uhabits.activities.timerecords.TimeRecordsActivity
import org.isoron.uhabits.R
import org.isoron.uhabits.utils.StyledResources

class TimeCenterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val component = (application as HabitsApplication).component
        AndroidThemeSwitcher(this, component.preferences).apply()
        super.onCreate(savedInstanceState)
        title = "时间中心"

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val pageBackground = Color.rgb(25, 27, 31)
        val dark = true
        val primary = Color.WHITE
        val secondary = Color.LTGRAY

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(pageBackground)
        }
        root.addView(TextView(this).apply {
            text = "时间中心"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(primary)
            setPadding(dp(2), dp(8), dp(2), dp(6))
        })
        root.addView(TextView(this).apply {
            text = "时间相关功能集中管理"
            textSize = 12f
            setTextColor(secondary)
            setPadding(dp(2), 0, dp(2), dp(16))
        })

        fun item(name: String, summary: String, action: () -> Unit) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(16), dp(14), dp(16))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(45, 48, 55) else Color.WHITE)
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), if (dark) Color.rgb(75, 78, 86) else Color.rgb(225, 228, 233))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this@TimeCenterActivity).apply {
                text = name
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(primary)
            })
            labels.addView(TextView(this@TimeCenterActivity).apply {
                text = summary
                textSize = 12f
                setTextColor(secondary)
                setPadding(0, dp(4), 0, 0)
            })
            card.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(TextView(this).apply {
                text = "›"
                textSize = 26f
                setTextColor(secondary)
            })
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }

        item("大字钟", "时钟、时分秒正计时和倒计时；含独立桌面组件") {
            startActivity(Intent(this, BigClockActivity::class.java))
        }
        item("时间记录", "查看和管理时间记录") {
            startActivity(Intent(this, TimeRecordsActivity::class.java))
        }
        item("状态栏天数", "管理显示项目、勾选状态和排列顺序") {
            startActivity(Intent(this, StatusBarDaysManagementActivity::class.java))
        }
        item("悬浮计时器", "启动和设置悬浮计时器") {
            startActivity(Intent(this, FloatingTimerManagementActivity::class.java))
        }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }
}
