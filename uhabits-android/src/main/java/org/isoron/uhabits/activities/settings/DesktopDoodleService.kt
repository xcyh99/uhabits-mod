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
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
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
import android.provider.Settings
import kotlin.math.abs

/**
 * User-invoked transparent drawing layer above other applications.
 * Touches are intentionally consumed while drawing is active; the app below
 * keeps running and receives touches again as soon as this service stops.
 */
class DesktopDoodleService : Service() {

    companion object {
        const val ACTION_START = "org.isoron.uhabits.DESKTOP_DOODLE_START"
        const val ACTION_STOP = "org.isoron.uhabits.DESKTOP_DOODLE_STOP"
        private const val CHANNEL_ID = "desktop_doodle"
        private const val NOTIFICATION_ID = 7313
    }

    private var windowManager: WindowManager? = null
    private var overlayRoot: FrameLayout? = null
    private var drawingView: DrawingView? = null
    private var toolbar: LinearLayout? = null
    private var currentColorIndex = 0
    private var eraserEnabled = false

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
            .setContentTitle("桌面涂鸦已开启")
            .setContentText("点击通知中的关闭按钮，或点击屏幕工具栏的 × 退出")
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

        if (overlayRoot == null) showOverlay()
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
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
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

        val canvas = DrawingView(this)
        drawingView = canvas
        root.addView(
            canvas,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundedBackground(0xE622252B.toInt(), dp(16).toFloat())
            elevation = dp(12).toFloat()
        }
        toolbar = tools

        val dragHandle = toolButton("⠿", "拖动工具栏")
        tools.addView(dragHandle, buttonLayout())
        dragHandle.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.tag = floatArrayOf(event.rawX, event.rawY, tools.translationX, tools.translationY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val start = view.tag as? FloatArray
                    if (start != null) {
                        val dx = event.rawX - start[0]
                        val dy = event.rawY - start[1]
                        tools.translationX = start[2] + dx
                        tools.translationY = start[3] + dy
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }

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
            background = roundedBackground(0xFFD94343.toInt(), dp(10).toFloat())
        }, buttonLayout())

        val toolbarParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = dp(54) }
        root.addView(tools, toolbarParams)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        overlayRoot = root
        try {
            windowManager?.addView(root, params)
        } catch (_: Exception) {
            overlayRoot = null
            drawingView = null
            stopSelf()
        }
    }

    private fun updateToolLabels() {
        toolbar?.let { row ->
            if (row.childCount > 2) {
                (row.getChildAt(2) as? TextView)?.alpha = if (eraserEnabled) 1f else 0.65f
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

    private fun buttonLayout() = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
        marginStart = dp(3)
        marginEnd = dp(3)
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroy() {
        overlayRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayRoot = null
        drawingView = null
        toolbar = null
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
