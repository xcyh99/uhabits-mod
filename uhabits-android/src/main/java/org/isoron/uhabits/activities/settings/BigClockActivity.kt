package org.isoron.uhabits.activities.settings

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.utils.StyledResources
import org.isoron.uhabits.widgets.BigClockWidgetProvider
import org.json.JSONArray
import java.util.Locale

/** Independent large clock with wall clock, stopwatch and countdown modes. */
class BigClockActivity : AppCompatActivity() {
    private val state by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var value: TextView
    private lateinit var clock: TextClock
    private lateinit var details: TextView
    private lateinit var countdownFields: List<EditText>
    private lateinit var lapList: TextView
    private lateinit var countdownPanel: LinearLayout
    private lateinit var stopwatchPanel: LinearLayout
    private var lastCountdownFinished = false

    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val component = (application as HabitsApplication).component
        AndroidThemeSwitcher(this, component.preferences).apply()
        super.onCreate(savedInstanceState)
        title = "大字钟"
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val background = StyledResources(this).getColor(R.attr.contrast0)
        val dark = androidx.core.graphics.ColorUtils.calculateLuminance(background) < .5
        val primary = if (dark) Color.WHITE else Color.BLACK
        val secondary = if (dark) Color.LTGRAY else Color.DKGRAY
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
            setBackgroundColor(background)
        }
        root.addView(TextView(this).apply {
            text = "大字钟"
            textSize = 21f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(primary)
        })
        root.addView(TextView(this).apply {
            text = "时钟、正计时和倒计时，独立运行，按秒显示。"
            textSize = 13f
            setTextColor(secondary)
            setPadding(0, dp(5), 0, dp(18))
        })

        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("时间" to "clock", "正计时" to "up", "倒计时" to "down").forEach { (label, mode) ->
            modes.addView(Button(this).apply {
                text = label
                setOnClickListener { state.edit().putString("mode", mode).apply(); render(); BigClockWidgetProvider.updateAll(this@BigClockActivity) }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(modes)

        clock = TextClock(this).apply {
            format12Hour = "h:mm:ss a"
            format24Hour = "HH:mm:ss"
            textSize = 62f
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            setTextColor(primary)
            maxLines = 1
        }
        value = TextView(this).apply {
            textSize = 62f
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            setTextColor(primary)
            maxLines = 1
        }
        details = TextView(this).apply { textSize = 13f; gravity = Gravity.CENTER; setTextColor(secondary); setPadding(0, dp(4), 0, dp(12)) }
        root.addView(clock, LinearLayout.LayoutParams(-1, dp(112)))
        root.addView(value, LinearLayout.LayoutParams(-1, dp(112)))
        root.addView(details, LinearLayout.LayoutParams(-1, -2))

        stopwatchPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val stopwatchButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        stopwatchButtons.addView(button("开始 / 暂停") { toggleStopwatch() }, LinearLayout.LayoutParams(0, -2, 1f))
        stopwatchButtons.addView(button("计圈") { addLap() }, LinearLayout.LayoutParams(0, -2, 1f))
        stopwatchButtons.addView(button("重置") { resetStopwatch() }, LinearLayout.LayoutParams(0, -2, 1f))
        stopwatchPanel.addView(stopwatchButtons)
        lapList = TextView(this).apply { textSize = 13f; setTextColor(secondary); setPadding(0, dp(12), 0, 0) }
        stopwatchPanel.addView(lapList)
        root.addView(stopwatchPanel, LinearLayout.LayoutParams(-1, -2))

        countdownPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        countdownPanel.addView(TextView(this).apply { text = "设置倒计时时长"; textSize = 14f; setTextColor(secondary); gravity = Gravity.CENTER })
        val fieldsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val duration = state.getLong("down_duration", 0L) / 1000L
        fun field(hintText: String, initial: Long) = EditText(this).apply {
            hint = hintText
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            gravity = Gravity.CENTER
            textSize = 18f
            setText(initial.toString().padStart(2, '0'))
        }
        countdownFields = listOf(field("时", duration / 3600), field("分", duration % 3600 / 60), field("秒", duration % 60))
        countdownFields.forEachIndexed { index, edit ->
            fieldsRow.addView(edit, LinearLayout.LayoutParams(0, dp(52), 1f))
            if (index < 2) fieldsRow.addView(TextView(this).apply { text = ":"; textSize = 20f; setTextColor(primary) })
        }
        countdownPanel.addView(fieldsRow)
        val countdownButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        countdownButtons.addView(button("设置时长") { setCountdown() }, LinearLayout.LayoutParams(0, -2, 1f))
        countdownButtons.addView(button("开始 / 暂停") { toggleCountdown() }, LinearLayout.LayoutParams(0, -2, 1f))
        countdownButtons.addView(button("重置") { resetCountdown() }, LinearLayout.LayoutParams(0, -2, 1f))
        countdownPanel.addView(countdownButtons)

        root.addView(countdownPanel, LinearLayout.LayoutParams(-1, -2))
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
        render()
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }

    override fun onResume() { super.onResume(); handler.removeCallbacks(ticker); handler.post(ticker) }
    override fun onPause() { handler.removeCallbacks(ticker); super.onPause() }

    private fun elapsed(): Long {
        val base = state.getLong("up_base", 0L)
        return (base + if (state.getBoolean("up_running", false)) (SystemClock.elapsedRealtime() - state.getLong("up_anchor", 0L)).coerceAtLeast(0) else 0).coerceAtLeast(0)
    }
    private fun remaining(): Long {
        val base = state.getLong("down_base", state.getLong("down_duration", 0L))
        return (base - if (state.getBoolean("down_running", false)) (SystemClock.elapsedRealtime() - state.getLong("down_anchor", 0L)).coerceAtLeast(0) else 0).coerceAtLeast(0)
    }
    private fun toggleStopwatch() {
        if (state.getBoolean("up_running", false)) state.edit().putLong("up_base", elapsed()).putBoolean("up_running", false).apply()
        else state.edit().putLong("up_anchor", SystemClock.elapsedRealtime()).putBoolean("up_running", true).apply()
        sync()
    }
    private fun addLap() {
        val laps = runCatching { JSONArray(state.getString("laps", "[]")) }.getOrDefault(JSONArray())
        laps.put(elapsed())
        state.edit().putString("laps", laps.toString()).apply()
        render()
    }
    private fun resetStopwatch() {
        state.edit().putLong("up_base", 0L).putLong("up_anchor", SystemClock.elapsedRealtime()).putBoolean("up_running", false).putString("laps", "[]").apply()
        sync()
    }
    private fun countdownDuration(): Long {
        val h = countdownFields[0].text.toString().toLongOrNull() ?: 0L
        val m = countdownFields[1].text.toString().toLongOrNull() ?: 0L
        val s = countdownFields[2].text.toString().toLongOrNull() ?: 0L
        return ((h.coerceAtMost(99) * 3600 + m.coerceAtMost(59) * 60 + s.coerceAtMost(59)) * 1000L)
    }
    private fun setCountdown() {
        val duration = countdownDuration()
        state.edit().putLong("down_duration", duration).putLong("down_base", duration).putLong("down_anchor", SystemClock.elapsedRealtime()).putBoolean("down_running", false).apply()
        lastCountdownFinished = false
        sync()
    }
    private fun toggleCountdown() {
        if (state.getBoolean("down_running", false)) state.edit().putLong("down_base", remaining()).putBoolean("down_running", false).apply()
        else {
            val current = state.getLong("down_base", state.getLong("down_duration", 0L))
            val start = if (current <= 0L) countdownDuration() else current
            if (start <= 0L) { Toast.makeText(this, "请先设置倒计时时长", Toast.LENGTH_SHORT).show(); return }
            state.edit().putLong("down_base", start).putLong("down_duration", maxOf(start, state.getLong("down_duration", 0L))).putLong("down_anchor", SystemClock.elapsedRealtime()).putBoolean("down_running", true).apply()
        }
        lastCountdownFinished = false
        sync()
    }
    private fun resetCountdown() {
        val duration = state.getLong("down_duration", countdownDuration())
        state.edit().putLong("down_base", duration).putLong("down_anchor", SystemClock.elapsedRealtime()).putBoolean("down_running", false).apply()
        lastCountdownFinished = false
        sync()
    }
    private fun sync() { render(); BigClockWidgetProvider.updateAll(this) }
    private fun render() {
        val mode = state.getString("mode", "clock") ?: "clock"
        clock.visibility = if (mode == "clock") android.view.View.VISIBLE else android.view.View.GONE
        value.visibility = if (mode == "clock") android.view.View.GONE else android.view.View.VISIBLE
        stopwatchPanel.visibility = if (mode == "up") android.view.View.VISIBLE else android.view.View.GONE
        countdownPanel.visibility = if (mode == "down") android.view.View.VISIBLE else android.view.View.GONE
        when (mode) {
            "up" -> {
                value.text = format(elapsed())
                details.text = if (state.getBoolean("up_running", false)) "正在正计时" else "已暂停"
                val laps = runCatching { JSONArray(state.getString("laps", "[]")) }.getOrDefault(JSONArray())
                lapList.text = (0 until laps.length()).map { index -> "第" + (index + 1) + "圈    " + format(laps.getLong(index)) }.reversed().joinToString("\n")
            }
            "down" -> {
                val ms = remaining()
                value.text = format(ms)
                details.text = if (state.getBoolean("down_running", false)) "正在倒计时" else if (ms == 0L) "倒计时结束" else "已暂停"
                if (state.getBoolean("down_running", false) && ms == 0L && !lastCountdownFinished) {
                    lastCountdownFinished = true
                    state.edit().putBoolean("down_running", false).putLong("down_base", 0L).apply()
                    try { (getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) {}
                    Toast.makeText(this, "倒计时结束", Toast.LENGTH_LONG).show()
                    BigClockWidgetProvider.updateAll(this)
                }
            }
            else -> details.text = "当前时间"
        }
    }
    private fun format(ms: Long): String {
        val total = ms / 1000L
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }
    companion object { const val PREFS = "big_clock_state" }
}
