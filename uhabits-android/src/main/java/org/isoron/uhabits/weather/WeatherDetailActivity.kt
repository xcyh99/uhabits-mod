package org.isoron.uhabits.weather

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.R
import org.isoron.uhabits.utils.StyledResources
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeatherDetailActivity : AppCompatActivity() {
    private var snapshot: WeatherSnapshot? = null
    private var selectedDate: String? = null
    private lateinit var content: LinearLayout
    private var darkTheme = false
    private val primaryText: Int get() = if (darkTheme) Color.rgb(240, 242, 246) else Color.rgb(35, 43, 54)
    private val secondaryText: Int get() = if (darkTheme) Color.rgb(184, 191, 202) else Color.DKGRAY
    private val cardColor: Int get() = if (darkTheme) Color.rgb(38, 42, 50) else Color.WHITE
    private val cardStroke: Int get() = if (darkTheme) Color.rgb(66, 72, 83) else Color.rgb(230, 233, 238)
    private val selectedCardColor: Int get() = if (darkTheme) Color.rgb(39, 57, 78) else Color.rgb(231, 241, 255)
    private val highTemperatureColor: Int get() = if (darkTheme) Color.rgb(255, 177, 112) else Color.rgb(215, 112, 42)
    private val lowTemperatureColor: Int get() = if (darkTheme) Color.rgb(128, 184, 255) else Color.rgb(56, 126, 205)
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(value: Int) = (value * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as HabitsApplication).component.preferences.let { AndroidThemeSwitcher(this, it).apply() }
        super.onCreate(savedInstanceState)
        val pageBackground = StyledResources(this).getColor(R.attr.contrast0)
        darkTheme = androidx.core.graphics.ColorUtils.calculateLuminance(pageBackground) < 0.5
        title = "天气"
        selectedDate = intent.getStringExtra(EXTRA_DATE)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(pageBackground)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        setContentView(scroll)
        snapshot = WeatherRepository.load(this)
        render()
        WeatherRepository.fetch(this, force = true) { fresh ->
            runOnUiThread {
                if (fresh != null) snapshot = fresh
                render()
            }
        }
    }

    private fun label(text: String, size: Float = 14f, bold: Boolean = false, color: Int = primaryText): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun section(title: String) {
        content.addView(label(title, 17f, true).apply {
            setPadding(0, dp(18), 0, dp(9))
        })
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(11), dp(13), dp(11))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = dp(13).toFloat()
            setStroke(dp(1), cardStroke)
        }
    }

    private fun render() {
        content.removeAllViews()
        val data = snapshot
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val city = data?.city ?: "合肥"
        top.addView(label(city + " 天气", 21f, true), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(Button(this).apply {
            text = "刷新"
            setOnClickListener {
                text = "更新中"
                WeatherRepository.fetch(this@WeatherDetailActivity, force = true) { fresh ->
                    runOnUiThread { if (fresh != null) snapshot = fresh; render() }
                }
            }
        })
        content.addView(top)
        if (data == null) {
            content.addView(label("正在获取天气预报…", 14f).apply { setPadding(0, dp(20), 0, dp(20)) })
            content.addView(label("请检查网络连接后点击刷新。", 12f, color = secondaryText))
            return
        }
        content.addView(label(
            "${data.currentTemperature}°  ${WeatherRepository.weatherText(data.currentCode)}",
            30f, true
        ).apply { setPadding(0, dp(8), 0, dp(2)) })
        content.addView(label("湿度 ${data.humidity}%    风速 ${data.wind} km/h", 13f, color = secondaryText))
        section("未来 7 天")
        val dayStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        data.days.take(7).forEachIndexed { index, day ->
            val item = card().apply {
                gravity = Gravity.CENTER
                addView(label(WeatherRepository.dayLabel(day.date, index), 11f, true))
                addView(label(WeatherRepository.weatherIcon(day.code), 21f).apply { setPadding(0, dp(4), 0, dp(4)) })
                addView(label("${day.high}°", 13f, true, Color.rgb(215, 112, 42)))
                addView(label("${day.low}°", 12f, false, Color.rgb(56, 126, 205)))
                if (day.date == selectedDate) {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(selectedCardColor); cornerRadius = dp(13).toFloat()
                        setStroke(dp(2), Color.rgb(75, 139, 218))
                    }
                }
                setOnClickListener { selectedDate = day.date; render() }
            }
            dayStrip.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(5)
            })
        }
        content.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(dayStrip)
        })
        val day = data.days.firstOrNull { it.date == selectedDate } ?: data.days.first()
        selectedDate = day.date
        section("${day.date} · ${WeatherRepository.weatherText(day.code)}")
        val details = card()
        details.addView(label("最高 ${day.high}°     最低 ${day.low}°", 18f, true))
        details.addView(label("降水概率 ${day.rain}%    最大风速 ${day.wind} km/h", 13f).apply { setPadding(0, dp(7), 0, dp(4)) })
        details.addView(label("日出 ${day.sunrise.substringAfter('T').take(5)}    日落 ${day.sunset.substringAfter('T').take(5)}", 13f))
        content.addView(details)
        section("逐小时天气")
        val hours = WeatherRepository.dayHours(data, day.date)
        if (hours.isEmpty()) {
            content.addView(label("暂无逐小时数据", 13f))
        } else {
            val hourStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            hours.forEach { hour ->
                val item = card().apply {
                    gravity = Gravity.CENTER
                    addView(label(hour.time.substringAfter('T').take(5), 11f))
                    addView(label(WeatherRepository.weatherIcon(hour.code), 20f).apply { setPadding(0, dp(3), 0, dp(3)) })
                    addView(label("${hour.temperature}°", 14f, true))
                    addView(label("湿度 ${hour.humidity}%", 10f))
                    addView(label("降水 ${hour.rain}%", 10f))
                    addView(label("风 ${hour.wind}", 10f))
                }
                hourStrip.addView(item, LinearLayout.LayoutParams(dp(88), -2).apply { marginEnd = dp(7) })
            }
            content.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(hourStrip)
            })
        }
        content.addView(label(
            "天气数据来源：Open-Meteo（CC BY 4.0）",
            10f, color = secondaryText
        ).apply { setPadding(0, dp(22), 0, 0) })
    }

    companion object {
        const val EXTRA_DATE = "weather_selected_date"
    }
}
