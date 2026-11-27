package org.isoron.uhabits.activities.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.utils.StyledResources
import org.isoron.uhabits.notifications.BigClockAlarmReceiver
import java.util.Locale

/** Full-page controls for the independently running floating timer overlay. */
class FloatingTimerManagementActivity : AppCompatActivity() {
    private val state by lazy { getSharedPreferences("floating_timer_state", MODE_PRIVATE) }
    private fun syncEnabled() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).getBoolean("big_clock_sync_floating", false)
    private fun bigState() = getSharedPreferences("big_clock_state", MODE_PRIVATE)
    private lateinit var modeGroup: RadioGroup
    private lateinit var hours: EditText
    private lateinit var minutes: EditText
    private lateinit var seconds: EditText
    private lateinit var status: TextView
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val component = (application as HabitsApplication).component
        AndroidThemeSwitcher(this, component.preferences).apply()
        super.onCreate(savedInstanceState)
        title = "悬浮计时器"
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val background = StyledResources(this).getColor(R.attr.contrast0)
        val dark = androidx.core.graphics.ColorUtils.calculateLuminance(background) < .5
        val primary = if (dark) Color.WHITE else Color.BLACK
        val secondary = if (dark) Color.LTGRAY else Color.DKGRAY

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(24))
            setBackgroundColor(background)
        }
        root.addView(TextView(this).apply {
            text = "悬浮计时器"
            textSize = 21f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(primary)
        })
        root.addView(TextView(this).apply {
            text = "本页面负责设置和控制悬浮窗口。离开本页面不会停止计时。"
            textSize = 13f
            setTextColor(secondary)
            setPadding(0, dp(6), 0, dp(14))
        })
        status = TextView(this).apply {
            textSize = 16f
            setTextColor(primary)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            this.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (dark) Color.rgb(45, 48, 55) else Color.WHITE)
                cornerRadius = dp(12).toFloat()
            }
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        root.addView(TextView(this).apply { text = "计时方式"; textSize = 15f; setTextColor(primary) })
        modeGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        modeGroup.addView(RadioButton(this).apply { id = ID_UP; text = "正计时"; setTextColor(primary) })
        modeGroup.addView(RadioButton(this).apply { id = ID_DOWN; text = "倒计时"; setTextColor(primary) })
        modeGroup.check(if ((if (syncEnabled()) bigState().getString("mode", "down") else state.getString("mode", "down")) == "up") ID_UP else ID_DOWN)
        root.addView(modeGroup)

        root.addView(TextView(this).apply {
            text = "倒计时时长（时、分、秒）"
            textSize = 14f
            setTextColor(secondary)
            setPadding(0, dp(10), 0, dp(4))
        })
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun timeField(hintText: String, value: Long): EditText = EditText(this).apply {
            hint = hintText
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            textSize = 18f
            gravity = Gravity.CENTER
            setText(value.toString().padStart(2, '0'))
        }
        val duration = (if (syncEnabled()) bigState().getLong("down_duration", 0L) else state.getLong("duration", 0L)) / 1000
        hours = timeField("时", duration / 3600)
        minutes = timeField("分", (duration % 3600) / 60)
        seconds = timeField("秒", duration % 60)
        listOf(hours, minutes, seconds).forEachIndexed { index, field ->
            timeRow.addView(field, LinearLayout.LayoutParams(0, dp(54), 1f))
            if (index < 2) timeRow.addView(TextView(this).apply { text = ":"; textSize = 20f; setTextColor(primary) })
        }
        root.addView(timeRow)

        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
        root.addView(button("保存计时设置") { saveSettings() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(button("开始 / 继续") { startOrResume() }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(button("暂停") { pauseTimer() }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(button("重置") { resetTimer() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(controls)
        root.addView(button("关闭悬浮窗口") {
            // Hiding the overlay must not pause either the independent timer
            // or a timer currently shared with Big Clock.
            stopService(Intent(this, FloatingTimerService::class.java))
            refreshStatus()
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(button("允许显示在其他应用上层") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, -2))
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        if (::status.isInitialized) handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun durationMs(): Long {
        val h = hours.text.toString().toLongOrNull() ?: 0L
        val m = minutes.text.toString().toLongOrNull() ?: 0L
        val s = seconds.text.toString().toLongOrNull() ?: 0L
        return ((h.coerceAtMost(99) * 3600 + m.coerceAtMost(59) * 60 + s.coerceAtMost(59)) * 1000L)
    }

    private fun saveSettings() {
        val down = modeGroup.checkedRadioButtonId == ID_DOWN
        val duration = durationMs()
        if (syncEnabled()) {
            val p = bigState()
            val edit = p.edit().putString("mode", if (down) "down" else "up")
                .putLong(if (down) "down_duration" else "up_base", if (down) duration else 0L)
                .putLong(if (down) "down_base" else "up_base", if (down) duration else 0L)
                .putLong(if (down) "down_anchor" else "up_anchor", SystemClock.elapsedRealtime())
                .putBoolean(if (down) "down_running" else "up_running", false)
            edit.apply()
            if (down) BigClockAlarmReceiver.cancel(this)
        } else state.edit().putString("mode", if (down) "down" else "up")
            .putLong("duration", duration).putLong("base", if (down) duration else 0L)
            .putLong("anchor", SystemClock.elapsedRealtime()).putBoolean("running", false).apply()
        refreshStatus()
        Toast.makeText(this, "计时设置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun startOrResume() {
        val down = modeGroup.checkedRadioButtonId == ID_DOWN
        val expectedMode = if (down) "down" else "up"
        if (syncEnabled()) {
            val p = bigState()
            val runningKey = if (down) "down_running" else "up_running"
            if (p.getBoolean(runningKey, false)) { refreshStatus(); return }
            val baseKey = if (down) "down_base" else "up_base"
            if (down && p.getLong(baseKey, p.getLong("down_duration", 0L)) <= 0L) {
                val duration = durationMs()
                if (duration <= 0L) { Toast.makeText(this, "请先设置倒计时时长", Toast.LENGTH_SHORT).show(); return }
                p.edit().putLong("down_duration", duration).putLong("down_base", duration).apply()
            }
            p.edit().putString("mode", expectedMode).putLong(if (down) "down_anchor" else "up_anchor", SystemClock.elapsedRealtime())
                .putBoolean(runningKey, true).apply()
            if (down) BigClockAlarmReceiver.schedule(this, p.getLong("down_base", 0L))
            else BigClockAlarmReceiver.cancel(this)
        } else {
            if (state.getBoolean("running", false)) { refreshStatus(); return }
            if (!state.contains("mode") || state.getString("mode", "down") != expectedMode) saveSettings()
            if (down && state.getLong("base", 0L) <= 0L) {
                val duration = durationMs()
                if (duration <= 0L) { Toast.makeText(this, "请先设置倒计时时长", Toast.LENGTH_SHORT).show(); return }
                state.edit().putLong("duration", duration).putLong("base", duration).apply()
            }
            state.edit().putLong("anchor", SystemClock.elapsedRealtime()).putBoolean("running", true).apply()
        }
        if (!Settings.canDrawOverlays(this)) {
            if (syncEnabled()) bigState().edit().putBoolean(if (down) "down_running" else "up_running", false).apply()
            else state.edit().putBoolean("running", false).apply()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val intent = Intent(this, FloatingTimerService::class.java).setAction(FloatingTimerService.ACTION_START)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        refreshStatus()
    }

    private fun pauseTimer() {
        val down = modeGroup.checkedRadioButtonId == ID_DOWN
        if (syncEnabled()) {
            val p = bigState()
            val runningKey = if (down) "down_running" else "up_running"
            if (!p.getBoolean(runningKey, false)) { refreshStatus(); return }
            val baseKey = if (down) "down_base" else "up_base"
            val anchor = p.getLong(if (down) "down_anchor" else "up_anchor", 0L)
            val base = p.getLong(baseKey, 0L)
            val delta = (SystemClock.elapsedRealtime() - anchor).coerceAtLeast(0)
            val current = if (down) (base - delta).coerceAtLeast(0) else base + delta
            p.edit().putLong(baseKey, current).putBoolean(runningKey, false).apply()
            if (down) BigClockAlarmReceiver.cancel(this)
        } else {
            if (!state.getBoolean("running", false)) { refreshStatus(); return }
            val isDown = state.getString("mode", "down") == "down"
            val base = state.getLong("base", 0L)
            val anchor = state.getLong("anchor", 0L)
            val delta = (SystemClock.elapsedRealtime() - anchor).coerceAtLeast(0)
            val current = if (isDown) (base - delta).coerceAtLeast(0) else base + delta
            state.edit().putLong("base", current).putBoolean("running", false).apply()
        }
        refreshStatus()
    }

    private fun resetTimer() {
        val down = modeGroup.checkedRadioButtonId == ID_DOWN
        val duration = durationMs()
        if (syncEnabled()) {
            val p = bigState()
            p.edit().putString("mode", if (down) "down" else "up")
                .putLong(if (down) "down_duration" else "up_base", if (down) duration else 0L)
                .putLong(if (down) "down_base" else "up_base", if (down) duration else 0L)
                .putLong(if (down) "down_anchor" else "up_anchor", SystemClock.elapsedRealtime())
                .putBoolean(if (down) "down_running" else "up_running", false).apply()
            if (down) BigClockAlarmReceiver.cancel(this)
        } else state.edit().putString("mode", if (down) "down" else "up")
            .putLong("duration", duration).putLong("base", if (down) duration else 0L)
            .putLong("anchor", SystemClock.elapsedRealtime()).putBoolean("running", false).apply()
        refreshStatus()
    }

    private fun refreshStatus() {
        val down = if (syncEnabled()) bigState().getString("mode", "down") == "down" else state.getString("mode", "down") == "down"
        val p = if (syncEnabled()) bigState() else state
        val running = if (syncEnabled()) p.getBoolean(if (down) "down_running" else "up_running", false) else p.getBoolean("running", false)
        val base = if (syncEnabled()) p.getLong(if (down) "down_base" else "up_base", p.getLong("down_duration", 0L)) else p.getLong("base", p.getLong("duration", 0L))
        val anchor = if (syncEnabled()) p.getLong(if (down) "down_anchor" else "up_anchor", 0L) else p.getLong("anchor", 0L)
        val delta = if (running) (SystemClock.elapsedRealtime() - anchor).coerceAtLeast(0) else 0L
        val ms = if (down) (base - delta).coerceAtLeast(0) else (base + delta).coerceAtLeast(0)
        val total = ms / 1000
        status.text = String.format(Locale.getDefault(), "%02d:%02d:%02d  ·  %s", total / 3600, (total % 3600) / 60, total % 60, if (running) "运行中" else "已暂停")
    }

    companion object {
        private const val ID_UP = 101
        private const val ID_DOWN = 102
    }
}
