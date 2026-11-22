package org.isoron.uhabits.activities.settings

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.util.Locale

class FloatingTimerService : Service() {
    companion object {
        const val ACTION_SETUP = "org.isoron.uhabits.FLOAT_TIMER_SETUP"
        private const val ACTION_STOP = "org.isoron.uhabits.FLOAT_TIMER_STOP"
        private const val CHANNEL = "floating_timer"
        private const val NOTIFICATION_ID = 7312
        private const val PREF = "floating_timer_state"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var timerView: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var longPress = false
    private val prefs by lazy { getSharedPreferences(PREF, MODE_PRIVATE) }

    private val ticker = object : Runnable {
        override fun run() {
            updateText()
            handler.postDelayed(this, 250)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("悬浮计时器正在运行")
            .setContentText("点击悬浮窗口暂停或继续")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (timerView == null) showOverlay()
        if (intent?.action == ACTION_SETUP || !prefs.contains("mode")) showSetupDialog()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "悬浮计时器", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(18, 12, 18, 12)
            setBackgroundColor(0xDD20242B.toInt())
            elevation = 10f
        }
        timerView = view
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", 80)
            y = prefs.getInt("y", 240)
        }
        params = p
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = p.x; startY = p.y
                    moved = false; longPress = false
                    handler.postDelayed({
                        if (!moved) longPress = true
                    }, 550)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    if (moved) {
                        handler.removeCallbacksAndMessages(null)
                        p.x = startX + dx; p.y = startY + dy
                        windowManager?.updateViewLayout(view, p)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacksAndMessages(null)
                    if (moved) prefs.edit().putInt("x", p.x).putInt("y", p.y).apply()
                    else if (longPress) showMenu()
                    else togglePause()
                    handler.post(ticker)
                    true
                }
                else -> true
            }
        }
        windowManager?.addView(view, p)
        updateText()
    }

    private fun elapsedMs(): Long {
        val running = prefs.getBoolean("running", false)
        val base = prefs.getLong("base", 0L)
        val anchor = prefs.getLong("anchor", 0L)
        return (base + if (running) (SystemClock.elapsedRealtime() - anchor).coerceAtLeast(0) else 0).coerceAtLeast(0)
    }

    private fun remainingMs(): Long {
        val running = prefs.getBoolean("running", false)
        val base = prefs.getLong("base", prefs.getLong("duration", 0L))
        val anchor = prefs.getLong("anchor", 0L)
        return (base - if (running) (SystemClock.elapsedRealtime() - anchor).coerceAtLeast(0) else 0).coerceAtLeast(0)
    }

    private fun updateText() {
        val countdown = prefs.getString("mode", "down") == "down"
        val ms = if (countdown) remainingMs() else elapsedMs()
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val state = if (prefs.getBoolean("running", false)) "" else " Ⅱ"
        timerView?.text = String.format(Locale.getDefault(), "%02d:%02d:%02d%s", h, m, s, state)
        if (countdown && ms == 0L && prefs.getBoolean("running", false)) {
            prefs.edit().putBoolean("running", false).putLong("base", 0).apply()
            timerView?.text = "00:00:00"
        }
    }

    private fun togglePause() {
        val running = prefs.getBoolean("running", false)
        val down = prefs.getString("mode", "down") == "down"
        val base = if (down) remainingMs() else elapsedMs()
        prefs.edit().putLong("base", base)
            .putLong("anchor", SystemClock.elapsedRealtime())
            .putBoolean("running", !running).apply()
        updateText()
    }

    private fun showMenu() {
        val choices = arrayOf("设置计时方式和时间", "重新开始", "清零", "关闭悬浮计时器")
        AlertDialog.Builder(this)
            .setTitle("悬浮计时器")
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> showSetupDialog()
                    1 -> {
                        val down = prefs.getString("mode", "down") == "down"
                        val value = if (down) prefs.getLong("duration", 0) else 0L
                        prefs.edit().putLong("base", value).putLong("anchor", SystemClock.elapsedRealtime()).putBoolean("running", true).apply()
                        updateText()
                    }
                    2 -> {
                        prefs.edit().putLong("base", 0).putBoolean("running", false).apply()
                        updateText()
                    }
                    3 -> stopSelf()
                }
            }.showOverlayDialog()
    }

    private fun showSetupDialog() {
        val modes = arrayOf("倒计时", "正计时")
        val current = if (prefs.getString("mode", "down") == "down") 0 else 1
        AlertDialog.Builder(this)
            .setTitle("选择计时方式")
            .setSingleChoiceItems(modes, current) { dialog, which ->
                prefs.edit().putString("mode", if (which == 0) "down" else "up").apply()
                dialog.dismiss()
                if (which == 0) showDurationDialog() else {
                    prefs.edit().putLong("base", 0).putLong("anchor", SystemClock.elapsedRealtime()).putBoolean("running", true).apply()
                    updateText()
                }
            }
            .setNegativeButton("取消", null)
            .showOverlayDialog()
    }

    private fun showDurationDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(24, 8, 24, 0) }
        val fields = (0..2).map { index ->
            EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                hint = listOf("时", "分", "秒")[index]
                setText(if (index == 0) "00" else "00")
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                layout.addView(this, LinearLayout.LayoutParams(0, 56, 1f))
            }
        }
        AlertDialog.Builder(this)
            .setTitle("设置倒计时时长")
            .setView(layout)
            .setPositiveButton("开始") { _, _ ->
                val h = fields[0].text.toString().toLongOrNull() ?: 0
                val m = fields[1].text.toString().toLongOrNull() ?: 0
                val s = fields[2].text.toString().toLongOrNull() ?: 0
                val duration = ((h * 3600 + m * 60 + s).coerceAtMost(359999) * 1000)
                prefs.edit().putString("mode", "down").putLong("duration", duration)
                    .putLong("base", duration).putLong("anchor", SystemClock.elapsedRealtime())
                    .putBoolean("running", duration > 0).apply()
                updateText()
            }
            .setNegativeButton("取消", null)
            .showOverlayDialog()
    }

    private fun AlertDialog.Builder.showOverlayDialog(): AlertDialog {
        val dialog = show()
        dialog.window?.setType(if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE)
        return dialog
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        timerView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        timerView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
