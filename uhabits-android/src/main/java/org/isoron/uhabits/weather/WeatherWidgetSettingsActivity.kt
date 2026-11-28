package org.isoron.uhabits.weather

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.activities.AndroidThemeSwitcher

/** User-adjustable appearance parameters for the weather home-screen widget. */
class WeatherWidgetSettingsActivity : AppCompatActivity() {
    companion object {
        const val PREF_CURVE_GAIN = "weather_widget_curve_gain"
        const val DEFAULT_CURVE_GAIN = 1.45f
        private const val MIN_GAIN = 0.8f
        private const val MAX_GAIN = 2.2f
        private const val STEPS = 28
    }

    private val store by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private var valueLabel: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val preferences = (application as HabitsApplication).component.preferences
        AndroidThemeSwitcher(this, preferences).apply()
        super.onCreate(savedInstanceState)
        title = "天气组件设置"

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val primary = if (dark) Color.WHITE else Color.BLACK
        val secondary = if (dark) Color.LTGRAY else Color.DKGRAY
        val page = if (dark) Color.rgb(25, 27, 31) else Color.rgb(246, 247, 249)
        val cardColor = if (dark) Color.rgb(45, 48, 55) else Color.WHITE
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
            setBackgroundColor(page)
        }
        root.addView(TextView(this).apply {
            text = "天气组件参数"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(primary)
            setPadding(dp(2), dp(8), dp(2), dp(8))
        })
        root.addView(TextView(this).apply {
            text = "调整桌面天气组件的曲线表现。修改后会自动应用到已添加的天气组件。"
            textSize = 13f
            setTextColor(secondary)
            setPadding(dp(2), 0, dp(2), dp(16))
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(70, 74, 82) else Color.rgb(230, 233, 238))
            }
        }
        card.addView(TextView(this).apply {
            text = "温度曲线起伏幅度"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(primary)
        })
        card.addView(TextView(this).apply {
            text = "数值越大，升温和降温的折线起伏越明显；只改变曲线纵向幅度，不改变实际温度数字、线宽和字号。"
            textSize = 12f
            setTextColor(secondary)
            setPadding(0, dp(5), 0, dp(10))
        })
        valueLabel = TextView(this).apply {
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(primary)
        }
        card.addView(valueLabel)
        val seek = SeekBar(this).apply {
            max = STEPS
            progress = gainToProgress(store.getFloat(PREF_CURVE_GAIN, DEFAULT_CURVE_GAIN))
        }
        card.addView(seek, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4)
        })
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@WeatherWidgetSettingsActivity).apply {
                text = "较平缓"
                textSize = 11f
                setTextColor(secondary)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@WeatherWidgetSettingsActivity).apply {
                text = "起伏更明显"
                textSize = 11f
                gravity = Gravity.END
                setTextColor(secondary)
            })
        })
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })

        val reset = Button(this).apply {
            text = "恢复默认幅度"
            setOnClickListener {
                seek.progress = gainToProgress(DEFAULT_CURVE_GAIN)
                saveGain(DEFAULT_CURVE_GAIN)
            }
        }
        root.addView(reset, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "默认值：1.45 倍。可在 0.8～2.2 倍之间调整。"
            textSize = 12f
            setTextColor(secondary)
            setPadding(dp(2), dp(10), dp(2), 0)
        })

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val gain = progressToGain(progress)
                valueLabel?.text = String.format(java.util.Locale.CHINA, "%.2f 倍", gain)
                if (fromUser) store.edit().putFloat(PREF_CURVE_GAIN, gain).apply()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) {
                saveGain(progressToGain(seek.progress))
            }
        })
        valueLabel?.text = String.format(
            java.util.Locale.CHINA, "%.2f 倍",
            store.getFloat(PREF_CURVE_GAIN, DEFAULT_CURVE_GAIN).coerceIn(MIN_GAIN, MAX_GAIN)
        )
        setContentView(android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    private fun gainToProgress(gain: Float): Int =
        (((gain.coerceIn(MIN_GAIN, MAX_GAIN) - MIN_GAIN) / (MAX_GAIN - MIN_GAIN)) * STEPS)
            .toInt().coerceIn(0, STEPS)

    private fun progressToGain(progress: Int): Float =
        MIN_GAIN + (MAX_GAIN - MIN_GAIN) * progress.coerceIn(0, STEPS) / STEPS

    private fun saveGain(gain: Float) {
        store.edit().putFloat(PREF_CURVE_GAIN, gain.coerceIn(MIN_GAIN, MAX_GAIN)).apply()
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, WeatherWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(ComponentName(this, WeatherWidgetProvider::class.java))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }
}
