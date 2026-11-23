package org.isoron.uhabits.activities.settings

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Transparent desktop doodle layer.
 *
 * The drawing surface and toolbar are separate overlay windows. This allows
 * pause mode to make the drawing surface invisible and NOT_TOUCHABLE while
 * keeping the floating toolbar alive and interactive.
 */
class DesktopDoodleService : Service() {

    companion object {
        const val ACTION_START = "org.isoron.uhabits.DESKTOP_DOODLE_START"
        const val ACTION_STOP = "org.isoron.uhabits.DESKTOP_DOODLE_STOP"
        private const val CHANNEL_ID = "desktop_doodle"
        private const val NOTIFICATION_ID = 7313
    }

    private var windowManager: WindowManager? = null
    private var drawingView: DrawingView? = null
    private var drawingWindowParams: WindowManager.LayoutParams? = null
    private var toolbarWindowRoot: FrameLayout? = null
    private var toolbarWindowParams: WindowManager.LayoutParams? = null
    private var toolbar: LinearLayout? = null
    private var pauseButton: TextView? = null
    private var collapsedHandle: TextView? = null
    private var currentColorIndex = 0
    private var eraserEnabled = false
    private var paused = false

    private val colors = intArrayOf(
        Color.RED,
        Color.BLUE,
        Color.GREEN,
        Color.BLACK,
        Color.YELLOW,
        0xFF9C27B0.toInt(),
        Color.WHITE
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许十字习惯显示在其他应用上层", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val stopIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            Intent(this, DesktopDoodleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(if (paused) "桌面涂鸦已暂停" else "桌面涂鸦已开启")
            .setContentText("暂停时可操作底层应用；点击关闭按钮退出涂鸦")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭涂鸦", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (drawingView == null || toolbarWindowRoot == null) showOverlay()
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "桌面涂鸦",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "桌面涂鸦运行状态和关闭入口" }
            )
        }
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Full-screen drawing window. It never takes keyboard focus.
        val canvas = DrawingView(this)
        drawingView = canvas
        val canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        drawingWindowParams = canvasParams

        try {
            windowManager?.addView(canvas, canvasParams)
        } catch (_: Exception) {
            drawingView = null
            drawingWindowParams = null
            stopSelf()
            return
        }

        // Toolbar is a separate, small overlay window so it remains clickable
        // while the full-screen drawing window is paused and touch-through.
        val toolbarHost = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK &&
                    event.action == KeyEvent.ACTION_UP
                ) {
                    stopSelf()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFocusableInTouchMode = true
            requestFocus()
        }

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = roundedBackground(0xE622252B.toInt(), dp(16).toFloat())
            elevation = dp(12).toFloat()
        }
        toolbar = tools

        val dragHandle = toolButton("⠿", "拖动工具栏")
        tools.addView(dragHandle, buttonLayout())
        attachDrag(dragHandle)

        tools.addView(toolButton("Ⅱ", "暂停涂鸦，恢复操作底层 APP") {
            setPaused(!paused)
        }.also { pauseButton = it }, buttonLayout())

        tools.addView(toolButton("✎", "画笔") {
            eraserEnabled = false
            canvas.setEraser(false)
            updateToolLabels()
        }, buttonLayout())
        tools.addView(toolButton("橡", "橡皮擦") {
            eraserEnabled = !eraserEnabled
            canvas.setEraser(eraserEnabled)
            updateToolLabels()
        }, buttonLayout())
        tools.addView(toolButton("↶", "撤销") { canvas.undo() }, buttonLayout())
        tools.addView(toolButton("色", "切换颜色") {
            currentColorIndex = (currentColorIndex + 1) % colors.size
            eraserEnabled = false
            canvas.setColor(colors[currentColorIndex])
            updateToolLabels()
        }, buttonLayout())
        tools.addView(toolButton("清", "清除全部笔迹") { canvas.clear() }, buttonLayout())
        tools.addView(toolButton("×", "关闭涂鸦") { stopSelf() }.apply {
            setTextColor(Color.WHITE)
            background = roundedBackground(0xFFD94343.toInt(), dp(8).toFloat())
        }, buttonLayout())

        val compact = TextView(this).apply {
            text = "▶"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            alpha = 0.72f
            contentDescription = "展开桌面涂鸦控制条"
            background = roundedBackground(0xE622252B.toInt(), dp(28).toFloat())
            visibility = View.GONE
        }
        collapsedHandle = compact
        attachDrag(compact) { setToolbarCollapsed(false) }

        toolbarHost.addView(
            compact,
            FrameLayout.LayoutParams(dp(34), dp(56), Gravity.CENTER)
        )
        toolbarHost.addView(
            tools,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - dp(360)) / 2
            y = dp(54)
        }
        toolbarWindowRoot = toolbarHost
        toolbarWindowParams = toolbarParams
        try {
            windowManager?.addView(toolbarHost, toolbarParams)
            setPaused(false)
        } catch (_: Exception) {
            toolbarWindowRoot = null
            toolbarWindowParams = null
            stopSelf()
        }
    }

    /**
     * Pause hides the ink and makes the full-screen drawing window
     * NOT_TOUCHABLE, so touches pass to the app underneath. The view and its
     * stroke list stay alive; resume restores the same drawing session.
     */
    private fun setPaused(value: Boolean) {
        paused = value
        val canvas = drawingView ?: return
        val params = drawingWindowParams ?: return
        canvas.visibility = if (paused) View.INVISIBLE else View.VISIBLE
        // A zero-alpha non-touchable overlay is important on Android 12+:
        // it prevents the hidden full-screen window from obscuring app touches.
        params.alpha = if (paused) 0f else 1f
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            if (paused) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
        try {
            windowManager?.updateViewLayout(canvas, params)
        } catch (_: Exception) {
        }
        pauseButton?.apply {
            text = if (paused) "▶" else "Ⅱ"
            contentDescription = if (paused) "恢复涂鸦" else "暂停涂鸦，恢复操作底层 APP"
            background = roundedBackground(
                if (paused) 0xFF287A50.toInt() else 0xFF414750.toInt(),
                dp(10).toFloat()
            )
        }
        setToolbarCollapsed(paused)
        val manager = getSystemService(NotificationManager::class.java)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(if (paused) "桌面涂鸦已暂停" else "桌面涂鸦已开启")
            .setContentText(if (paused) "底层 APP 可正常操作，原笔迹已保留" else "涂鸦已恢复，点击暂停可操作底层 APP")
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "关闭涂鸦",
                PendingIntent.getService(
                    this,
                    NOTIFICATION_ID,
                    Intent(this, DesktopDoodleService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateToolLabels() {
        toolbar?.let { row ->
            if (row.childCount > 3) {
                (row.getChildAt(3) as? TextView)?.alpha = if (eraserEnabled) 1f else 0.65f
            }
        }
    }

    private fun toolButton(label: String, description: String, onClick: (() -> Unit)? = null): TextView {
        return TextView(this).apply {
            text = label
            contentDescription = description
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedBackground(0xFF414750.toInt(), dp(10).toFloat())
            setOnClickListener { onClick?.invoke() }
        }
    }

    private fun buttonLayout() = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
        marginStart = dp(1)
        marginEnd = dp(1)
    }

    private fun setToolbarCollapsed(collapsed: Boolean) {
        val tools = toolbar ?: return
        val handle = collapsedHandle ?: return
        tools.visibility = if (collapsed) View.GONE else View.VISIBLE
        handle.visibility = if (collapsed) View.VISIBLE else View.GONE
        toolbarWindowRoot?.let { host ->
            host.post {
                try {
                    val params = toolbarWindowParams ?: return@post
                    windowManager?.updateViewLayout(host, params)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun attachDrag(view: View, onTap: (() -> Unit)? = null) {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val p = toolbarWindowParams
                    if (p != null) view.tag = floatArrayOf(event.rawX, event.rawY, p.x.toFloat(), p.y.toFloat(), 0f)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val start = view.tag as? FloatArray
                    val p = toolbarWindowParams
                    val host = toolbarWindowRoot
                    if (start != null && p != null && host != null) {
                        val dx = event.rawX - start[0]
                        val dy = event.rawY - start[1]
                        if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) start[4] = 1f
                        p.x = (start[2] + dx).toInt().coerceAtLeast(0)
                        p.y = (start[3] + dy).toInt().coerceAtLeast(0)
                        try { windowManager?.updateViewLayout(host, p) } catch (_: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val start = view.tag as? FloatArray
                    val p = toolbarWindowParams
                    val host = toolbarWindowRoot
                    if (start != null && start[4] == 0f) {
                        onTap?.invoke()
                    } else if (p != null && host != null) {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val hostWidth = if (host.width > 0) host.width else dp(260)
                        p.x = if (p.x + hostWidth / 2 < screenWidth / 2) 0
                              else (screenWidth - hostWidth).coerceAtLeast(0)
                        p.y = p.y.coerceIn(dp(8), (resources.displayMetrics.heightPixels - host.height).coerceAtLeast(dp(8)))
                        try { windowManager?.updateViewLayout(host, p) } catch (_: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroy() {
        toolbarWindowRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        drawingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        toolbarWindowRoot = null
        toolbarWindowParams = null
        drawingWindowParams = null
        drawingView = null
        toolbar = null
        pauseButton = null
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    private class DrawingView(context: android.content.Context) : View(context) {
        private data class Stroke(
            val path: Path,
            val color: Int,
            val width: Float,
            val eraser: Boolean
        )

        private val strokes = mutableListOf<Stroke>()
        private var activePath: Path? = null
        private var activeEraser = false
        private var activeColor = Color.RED
        private var activeWidth = 7f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        init {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            setBackgroundColor(Color.TRANSPARENT)
        }

        fun setColor(color: Int) {
            activeColor = color
            activeEraser = false
        }

        fun setEraser(enabled: Boolean) {
            activeEraser = enabled
        }

        fun undo() {
            if (strokes.isNotEmpty()) {
                strokes.removeAt(strokes.lastIndex)
                invalidate()
            }
        }

        fun clear() {
            strokes.clear()
            activePath = null
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            strokes.forEach { drawStroke(canvas, it.path, it.color, it.width, it.eraser) }
            activePath?.let { drawStroke(canvas, it, activeColor, activeWidth, activeEraser) }
        }

        private fun drawStroke(canvas: Canvas, path: Path, color: Int, width: Float, eraser: Boolean) {
            paint.color = color
            paint.strokeWidth = width
            paint.xfermode = if (eraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
            canvas.drawPath(path, paint)
            paint.xfermode = null
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePath = Path().apply { moveTo(event.x, event.y) }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    activePath?.lineTo(event.x, event.y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activePath?.let {
                        strokes.add(Stroke(it, activeColor, activeWidth, activeEraser))
                    }
                    activePath = null
                    invalidate()
                    return true
                }
            }
            return true
        }
    }
}
