package org.isoron.uhabits.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PathEffect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Four equal shortcut-editing quadrants with independent X/Y split controls.
 * The solid center axes never move; dashed lines preview the real touch split.
 */
class PictureShortcutSplitEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var splitX: Int = 5
        set(value) { field = value.coerceIn(1, 9); invalidate() }
    var splitY: Int = 5
        set(value) { field = value.coerceIn(1, 9); invalidate() }
    var zoneLabels: List<String> = listOf(
        "选择快捷方式", "选择快捷方式", "选择快捷方式", "选择快捷方式"
    )
        set(value) { field = value; invalidate() }
    var onZoneClick: ((Int) -> Unit)? = null
    var onSplitChanged: ((Int, Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val slop = 10f * density
    private val handleRadius = 13f * density
    private val hitRadius = 25f * density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF252A32.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7A8492.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.2f * density
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB8C0CC.toInt(); strokeWidth = 2f * density
    }
    private val xDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4F9DFF.toInt(); strokeWidth = 2f * density
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f * density, 5f * density), 0f)
    }
    private val yDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF35C98A.toInt(); strokeWidth = 2f * density
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f * density, 5f * density), 0f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4F6FA.toInt() }
    private val handleInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF303843.toInt() }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF1F3F6.toInt(); textSize = 14f * density; textAlign = Paint.Align.CENTER
    }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE4EAF2.toInt(); textSize = 10f * density; textAlign = Paint.Align.LEFT
    }

    private var square = RectF()
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var gesture = Gesture.NONE
    private var pendingAxis = 0 // 1=X, 2=Y, 3=ambiguous center
    private var lockedAxis = 0
    private var downInSquare = false

    private enum class Gesture { NONE, HANDLE, QUADRANT }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "四象限快捷方式编制区，可拖动中心滑块调整分区"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desired = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val height = resolveSize(desired + paddingTop + paddingBottom, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (side <= 0f) return
        val left = paddingLeft + (width - paddingLeft - paddingRight - side) / 2f
        val top = paddingTop + (height - paddingTop - paddingBottom - side) / 2f
        square = RectF(left, top, left + side, top + side)
        val cx = square.centerX()
        val cy = square.centerY()
        val x = square.left + side * splitX / 10f
        val y = square.top + side * splitY / 10f

        canvas.drawRoundRect(square, 10f * density, 10f * density, bgPaint)
        canvas.drawRoundRect(square, 10f * density, 10f * density, borderPaint)

        // Dynamic preview lines are drawn first; fixed equal-quadrant axes remain visible.
        canvas.drawLine(x, square.top, x, square.bottom, xDashPaint)
        canvas.drawLine(square.left, y, square.right, y, yDashPaint)
        canvas.drawLine(square.left, cy, square.right, cy, axisPaint)
        canvas.drawLine(cx, square.top, cx, square.bottom, axisPaint)

        val half = side / 2f
        val labelRects = ArrayList<RectF>()
        for (i in 0..3) {
            val col = i % 2
            val row = i / 2
            val r = RectF(
                square.left + col * half,
                square.top + row * half,
                square.left + (col + 1) * half,
                square.top + (row + 1) * half
            )
            val lines = wrapLabel(zoneLabels.getOrNull(i) ?: "选择快捷方式", 9).take(2)
            val lineHeight = labelPaint.textSize * 1.35f
            val startY = r.centerY() - (lines.size - 1) * lineHeight / 2f + labelPaint.textSize * 0.35f
            lines.forEachIndexed { index, line ->
                canvas.drawText(line, r.centerX(), startY + index * lineHeight, labelPaint)
            }
            val maxWidth = lines.maxOfOrNull { labelPaint.measureText(it) } ?: 0f
            labelRects.add(RectF(r.centerX() - maxWidth / 2f, startY - labelPaint.textSize,
                r.centerX() + maxWidth / 2f, startY + (lines.size - 1) * lineHeight + 3f * density))
        }

        val occupied = ArrayList<RectF>()
        fun drawPercent(text: String, zone: Int, candidates: List<Triple<Float, Float, Paint.Align>>) {
            val p = Paint(percentPaint).apply { textAlign = Paint.Align.LEFT }
            val textWidth = p.measureText(text)
            val textHeight = p.textSize
            val area = RectF(
                square.left + (zone % 2) * half + 4f * density,
                square.top + (zone / 2) * half + 4f * density,
                square.left + (zone % 2 + 1) * half - 4f * density,
                square.top + (zone / 2 + 1) * half - 4f * density
            )
            for ((ax, baseline, align) in candidates) {
                val l = when (align) {
                    Paint.Align.CENTER -> ax - textWidth / 2f
                    Paint.Align.RIGHT -> ax - textWidth
                    else -> ax
                }
                val rect = RectF(l, baseline - textHeight, l + textWidth, baseline + 2f * density)
                if (area.contains(rect) && !RectF.intersects(rect, labelRects[zone]) &&
                    occupied.none { RectF.intersects(it, rect) }) {
                    p.textAlign = align
                    canvas.drawText(text, ax, baseline, p)
                    occupied.add(rect)
                    return
                }
            }
            // If no free place exists, hide this low-priority annotation rather than cover a shortcut name.
        }

        val xLine = x
        val yLine = y
        val inset = 9f * density
        drawPercent("${splitX * 10}%", 0, listOf(
            Triple(xLine - inset, square.top + 20f * density, Paint.Align.RIGHT),
            Triple(square.left + inset, square.top + 20f * density, Paint.Align.LEFT),
            Triple(square.left + half / 2f, square.top + 38f * density, Paint.Align.CENTER)
        ))
        drawPercent("${(10 - splitX) * 10}%", 1, listOf(
            Triple(xLine + inset, square.top + 20f * density, Paint.Align.LEFT),
            Triple(square.right - inset, square.top + 20f * density, Paint.Align.RIGHT),
            Triple(square.left + half + half / 2f, square.top + 38f * density, Paint.Align.CENTER)
        ))
        drawPercent("${splitY * 10}%", 1, listOf(
            Triple(square.right - inset, yLine - 8f * density, Paint.Align.RIGHT),
            Triple(square.right - inset, square.top + 20f * density, Paint.Align.RIGHT),
            Triple(square.left + half / 2f, yLine - 8f * density, Paint.Align.CENTER)
        ))
        drawPercent("${(10 - splitY) * 10}%", 3, listOf(
            Triple(square.right - inset, yLine + 20f * density, Paint.Align.RIGHT),
            Triple(square.right - inset, square.bottom - 8f * density, Paint.Align.RIGHT),
            Triple(square.left + half / 2f, yLine + 20f * density, Paint.Align.CENTER)
        ))

        // Slider handles live on the fixed center axes; dashed lines track their values.
        canvas.drawCircle(x, cy, handleRadius, handlePaint)
        canvas.drawCircle(x, cy, handleRadius * 0.48f, handleInnerPaint)
        canvas.drawCircle(cx, y, handleRadius, handlePaint)
        canvas.drawCircle(cx, y, handleRadius * 0.48f, handleInnerPaint)
    }

    private fun wrapLabel(value: String, maxChars: Int): List<String> {
        val clean = value.trim().ifBlank { "选择快捷方式" }
        if (clean.length <= maxChars) return listOf(clean)
        val first = clean.take(maxChars)
        val second = clean.drop(maxChars).take(maxChars)
        return listOf(first, second)
    }

    private fun handleCenters(): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val side = square.width()
        return Pair(
            Pair(square.left + side * splitX / 10f, square.centerY()),
            Pair(square.centerX(), square.top + side * splitY / 10f)
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                lastX = downX; lastY = downY
                downInSquare = square.contains(downX, downY)
                val (xHandle, yHandle) = handleCenters()
                val dx = hypot((downX - xHandle.first).toDouble(), (downY - xHandle.second).toDouble()).toFloat()
                val dy = hypot((downX - yHandle.first).toDouble(), (downY - yHandle.second).toDouble()).toFloat()
                val hitX = dx <= hitRadius
                val hitY = dy <= hitRadius
                if (hitX || hitY) {
                    gesture = Gesture.HANDLE
                    pendingAxis = when {
                        hitX && hitY && abs(dx - dy) < 8f * density -> 3
                        hitX && (!hitY || dx < dy) -> 1
                        else -> 2
                    }
                    lockedAxis = if (pendingAxis == 3) 0 else pendingAxis
                } else if (downInSquare) {
                    gesture = Gesture.QUADRANT
                } else {
                    gesture = Gesture.NONE
                    return false
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (gesture == Gesture.HANDLE) {
                    val moveX = event.x - downX
                    val moveY = event.y - downY
                    if (lockedAxis == 0 && hypot(moveX.toDouble(), moveY.toDouble()) >= slop) {
                        if (abs(moveX) > abs(moveY) * 1.25f) lockedAxis = 1
                        else if (abs(moveY) > abs(moveX) * 1.25f) lockedAxis = 2
                    }
                    if (lockedAxis == 1) {
                        splitX = (((event.x - square.left) / square.width()) * 10f).toInt().coerceIn(1, 9)
                        onSplitChanged?.invoke(splitX, splitY)
                        invalidate()
                    } else if (lockedAxis == 2) {
                        splitY = (((event.y - square.top) / square.height()) * 10f).toInt().coerceIn(1, 9)
                        onSplitChanged?.invoke(splitX, splitY)
                        invalidate()
                    }
                }
                lastX = event.x; lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (gesture == Gesture.QUADRANT &&
                    hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) < slop &&
                    square.contains(event.x, event.y)) {
                    val zone = (if (event.y < square.centerY()) 0 else 2) +
                        (if (event.x < square.centerX()) 0 else 1)
                    onZoneClick?.invoke(zone)
                    performClick()
                }
                gesture = Gesture.NONE
                pendingAxis = 0
                lockedAxis = 0
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                gesture = Gesture.NONE
                pendingAxis = 0
                lockedAxis = 0
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
