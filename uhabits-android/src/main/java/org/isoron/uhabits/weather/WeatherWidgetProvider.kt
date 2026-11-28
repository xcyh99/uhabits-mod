package org.isoron.uhabits.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.util.LruCache
import androidx.preference.PreferenceManager
import org.isoron.uhabits.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Render cached data immediately so the widget is useful without waiting
        // for the network, then refresh all instances when fresh data arrives.
        val cached = WeatherRepository.load(context)
        ids.forEach { render(context, manager, it, cached) }
        WeatherRepository.fetch(context, force = false) { fresh ->
            updateAll(context, fresh ?: cached)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        val cached = WeatherRepository.load(context)
        render(context, manager, appWidgetId, cached)
        WeatherRepository.fetch(context, force = false) { fresh ->
            updateAll(context, fresh ?: cached)
        }
    }

    private fun updateAll(context: Context, snapshot: WeatherSnapshot?) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
        ids.forEach { render(context, manager, it, snapshot) }
    }

    private fun render(
        context: Context, manager: AppWidgetManager, id: Int, snapshot: WeatherSnapshot?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        val options = manager.getAppWidgetOptions(id)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
        val density = context.resources.displayMetrics.density

        // Keep the essential current conditions in a 1x1 cell. Add forecast rows and
        // the chart only when the launcher gives the widget enough real space.
        val tiny = widthDp < 100 || heightDp < 85
        val showDays = widthDp >= 120 && heightDp >= 105
        // Keep the trend line visible in 1x2 and 2x1 widgets as well.
        // A single-cell side is narrow or short, but the chart must not disappear.
        val oneCellSide = (widthDp < 100 && heightDp >= 100) || (heightDp < 100 && widthDp >= 100)
        val showChart = (widthDp >= 120 && heightDp >= 130) || oneCellSide
        val showUpdated = widthDp >= 160 && heightDp >= 145
        val showDetails = widthDp >= 180 && heightDp >= 100

        // Use the full widget canvas: no artificial inset around the weather content.
        val padding = 0
        views.setViewPadding(R.id.weather_root, 0, 0, 0, 0)
        val tinyCell = widthDp < 130 && heightDp < 130
        views.setViewVisibility(R.id.weather_tiny_panel, if (tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_header, if (tinyCell) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.weather_city, if (!tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_updated, if (showUpdated && !tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_day_desc_row, if (showDays && !tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_chart, if (showChart && !tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_bottom_info, View.GONE)
        views.setViewVisibility(R.id.weather_detail_info, if (showDetails && !tinyCell) View.VISIBLE else View.GONE)

        val cityText = snapshot?.city ?: "天气"
        val tempText = snapshot?.let { "${it.currentTemperature}°" } ?: "--°"
        val conditionText = snapshot?.let { WeatherRepository.weatherText(it.currentCode) } ?: "获取中"
        views.setTextViewText(R.id.weather_city, cityText)
        views.setTextViewText(R.id.weather_current_temp, tempText)
        views.setTextViewText(R.id.weather_humidity_value, snapshot?.let { "${it.humidity}%" } ?: "--%")
        views.setTextViewText(R.id.weather_wind_value, snapshot?.let { "${it.wind} km/h" } ?: "--")
        views.setTextViewText(R.id.weather_rain_value, snapshot?.let { "${it.days.firstOrNull()?.rain ?: 0}%" } ?: "--%")
        views.setTextViewText(R.id.weather_current_desc, conditionText)
        views.setTextViewText(R.id.weather_tiny_city, cityText)
        views.setTextViewText(R.id.weather_tiny_desc, conditionText)
        views.setTextViewText(R.id.weather_tiny_temp, tempText)
        if (tinyCell) {
            views.setTextViewTextSize(R.id.weather_tiny_desc, android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
            views.setTextViewTextSize(R.id.weather_tiny_temp, android.util.TypedValue.COMPLEX_UNIT_SP, 17f)
        }
        views.setTextViewText(
            R.id.weather_updated,
            snapshot?.let { "更新于 ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(it.fetchedAt))}" }
                ?: "等待天气数据"
        )
        val headerTextSize = when {
            tiny -> 21f
            widthDp < 160 || heightDp < 130 -> 20f
            else -> 26f
        }
        views.setTextViewTextSize(
            R.id.weather_current_temp,
            android.util.TypedValue.COMPLEX_UNIT_SP,
            headerTextSize
        )
        views.setTextViewTextSize(
            R.id.weather_current_desc,
            android.util.TypedValue.COMPLEX_UNIT_SP,
            headerTextSize
        )

        if (snapshot != null && snapshot.days.isNotEmpty()) {
            val dayViews = intArrayOf(
                R.id.weather_day_0, R.id.weather_day_1, R.id.weather_day_2,
                R.id.weather_day_3, R.id.weather_day_4, R.id.weather_day_5, R.id.weather_day_6
            )
            val iconViews = intArrayOf(
                R.id.weather_icon_0, R.id.weather_icon_1, R.id.weather_icon_2,
                R.id.weather_icon_3, R.id.weather_icon_4, R.id.weather_icon_5, R.id.weather_icon_6
            )
            val descViews = intArrayOf(
                R.id.weather_desc_0, R.id.weather_desc_1, R.id.weather_desc_2,
                R.id.weather_desc_3, R.id.weather_desc_4, R.id.weather_desc_5, R.id.weather_desc_6
            )
            val hitViews = intArrayOf(
                R.id.weather_hit_0, R.id.weather_hit_1, R.id.weather_hit_2,
                R.id.weather_hit_3, R.id.weather_hit_4, R.id.weather_hit_5, R.id.weather_hit_6
            )
            val compactForecast = widthDp < 180 || heightDp < 150
            val dayTextSize = if (compactForecast) 8f else 10f
            val iconTextSize = if (compactForecast) 17f else 22f
            val forecastTempSize = if (compactForecast) 7f else 9f
            val cellWidthPx = (widthDp / 7f - 4f).coerceAtLeast(1f) * density
            val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            measurePaint.textSize = dayTextSize * density
            val fullDayLabels = snapshot.days.take(7).mapIndexed { index, day ->
                WeatherRepository.dayLabel(day.date, index, compact = false)
            }
            val compactDayLabels = snapshot.days.take(7).mapIndexed { index, day ->
                WeatherRepository.dayLabel(day.date, index, compact = true)
            }
            val fullDatesFit = fullDayLabels.all { measurePaint.measureText(it) <= cellWidthPx }
            measurePaint.textSize = forecastTempSize * density
            val singleLineTempsFit = snapshot.days.take(7).all { day ->
                measurePaint.measureText("${day.high}°/${day.low}°") <= cellWidthPx
            }
            snapshot.days.take(7).forEachIndexed { index, day ->
                views.setTextViewText(dayViews[index], if (fullDatesFit) fullDayLabels[index] else compactDayLabels[index])
                views.setTextViewText(iconViews[index], WeatherRepository.weatherIcon(day.code))
                views.setTextViewText(descViews[index], if (singleLineTempsFit) "${day.high}°/${day.low}°" else "${day.high}°\n${day.low}°")
                views.setTextViewTextSize(dayViews[index], android.util.TypedValue.COMPLEX_UNIT_SP, dayTextSize)
                views.setTextViewTextSize(iconViews[index], android.util.TypedValue.COMPLEX_UNIT_SP, iconTextSize)
                views.setTextViewTextSize(descViews[index], android.util.TypedValue.COMPLEX_UNIT_SP, forecastTempSize)
                val intent = Intent(context, WeatherDetailActivity::class.java)
                    .putExtra(WeatherDetailActivity.EXTRA_DATE, day.date)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pending = PendingIntent.getActivity(
                    context, id * 20 + index, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(dayViews[index], pending)
                views.setOnClickPendingIntent(iconViews[index], pending)
                views.setOnClickPendingIntent(hitViews[index], pending)
            }

            if (showChart) {
                val chartWidth = max(1, ((widthDp - padding * 2) * density).toInt())
                // The city and update timestamp share the 34dp header.
                // The chart fills the remaining weighted FrameLayout height.
                val reservedHeightDp = 40 +
                    (if (showDays) 63 else 0) + 4
                val chartHeight = max(1, ((heightDp - reservedHeightDp) * density).toInt())
                views.setImageViewBitmap(R.id.weather_chart, drawChart(context, snapshot.days, chartWidth, chartHeight, 7f))
            }
            // Weather details now live in the compact right-hand header column.
            views.setViewVisibility(R.id.weather_bottom_info, View.GONE)
        } else {
            views.setViewVisibility(R.id.weather_bottom_info, View.GONE)
            views.setTextViewText(R.id.weather_humidity_value, "--%")
            views.setTextViewText(R.id.weather_wind_value, "--")
            views.setTextViewText(R.id.weather_rain_value, "--%")
        }

        // A tap anywhere on the widget opens the full forecast page.
        val open = PendingIntent.getActivity(
            context, id * 20 + 19,
            Intent(context, WeatherDetailActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weather_root, open)
        manager.updateAppWidget(id, views)
    }

    /**
     * Draw the forecast curves, dots and temperature labels on one canvas.
     * A single coordinate system prevents labels drifting away from their points
     * when the launcher resizes or reloads the widget.
     */
    private fun drawChart(
        context: Context, days: List<WeatherDay>, width: Int, height: Int, labelSizeSp: Float
    ): Bitmap {
        val curveGain = PreferenceManager.getDefaultSharedPreferences(context)
            .getFloat(WeatherWidgetSettingsActivity.PREF_CURVE_GAIN, WeatherWidgetSettingsActivity.DEFAULT_CURVE_GAIN)
            .coerceAtLeast(WeatherWidgetSettingsActivity.MIN_GAIN)
        val density = context.resources.displayMetrics.density
        val cacheKey = buildString {
            append(width).append('x').append(height).append('|').append(density)
                .append('|').append(curveGain).append('|').append(labelSizeSp)
            days.take(7).forEach { append('|').append(it.date).append(':').append(it.high).append('/').append(it.low) }
        }
        WeatherChartBitmapCache.get(cacheKey)?.let { return it }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Scale every chart element together. The lower bound keeps labels legible
        // in narrow one-cell widgets; the upper bound avoids oversized strokes.
        val chartScale = min(width / 240f, height / 64f).coerceIn(0.45f, 2f)
        val highPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFA34A.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f * chartScale
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val lowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF65A9FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f * chartScale
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val dotHigh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFA34A.toInt() }
        val dotLow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF65A9FF.toInt() }
        val visibleDays = days.take(7)
        val cellWidth = width / 7f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE8EDF5.toInt()
            textSize = labelSizeSp * chartScale * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val metrics = labelPaint.fontMetrics
        val dotRadius = 3f * chartScale
        val edge = dotRadius + 1f * chartScale

        // Position each complete series by its average, not by pinning an
        // individual minimum/maximum point to an edge. Both series share one
        // pixels-per-degree scale, so their internal proportions stay linear
        // and the gain setting changes the whole curve uniformly.
        val highMean = visibleDays.map { it.high.toFloat() }.average().toFloat()
        val lowMean = visibleDays.map { it.low.toFloat() }.average().toFloat()
        val highDeviation = (visibleDays.maxOfOrNull { abs(it.high - highMean) } ?: 0f)
            .coerceAtLeast(0.01f)
        val lowDeviation = (visibleDays.maxOfOrNull { abs(it.low - lowMean) } ?: 0f)
            .coerceAtLeast(0.01f)
        val maxDeviation = max(highDeviation, lowDeviation)

        // No 10x ceiling: scale the complete series uniformly. Any portion
        // outside the finite bitmap is naturally clipped by Canvas.
        // The generous safety bound only prevents coordinate overflow.
        val rawScale = height.toDouble() * 0.08 * curveGain.toDouble() / maxDeviation.toDouble()
        val curveScale = rawScale.coerceAtMost(height.toDouble() * 1_000_000.0).toFloat()
        val meanGap = max(1f, height * 0.008f)
        // Anchor the high-series mean near the top and the low-series mean near
        // the bottom while preserving every point's linear distance from its mean.
        val highCenterY = edge + meanGap
        val lowCenterY = height - edge - meanGap
        fun x(i: Int) = width * (i + 0.5f) / 7f
        fun yHigh(temp: Int): Float = highCenterY - (temp - highMean) * curveScale
        fun yLow(temp: Int): Float = lowCenterY - (temp - lowMean) * curveScale

        val highPath = Path()
        val lowPath = Path()
        days.take(7).forEachIndexed { i, day ->
            if (i == 0) {
                highPath.moveTo(x(i), yHigh(day.high))
                lowPath.moveTo(x(i), yLow(day.low))
            } else {
                highPath.lineTo(x(i), yHigh(day.high))
                lowPath.lineTo(x(i), yLow(day.low))
            }
        }
        canvas.drawPath(highPath, highPaint)
        canvas.drawPath(lowPath, lowPaint)
        days.take(7).forEachIndexed { i, day ->
            canvas.drawCircle(x(i), yHigh(day.high), dotRadius, dotHigh)
            canvas.drawCircle(x(i), yLow(day.low), dotRadius, dotLow)
        }

        data class LabelBox(val left: Float, val top: Float, val right: Float, val bottom: Float)
        data class LabelPlacement(val text: String, val x: Float, val baseline: Float, val box: LabelBox)
        val occupied = mutableListOf<LabelBox>()
        data class ChartLabel(val text: String, val pointX: Float, val pointY: Float, val preferBelow: Boolean)
        val labels = days.take(7).flatMapIndexed { i, day ->
            listOf(
                ChartLabel("${day.high}°", x(i), yHigh(day.high), false),
                ChartLabel("${day.low}°", x(i), yLow(day.low), true)
            )
        }.sortedBy { if (it.preferBelow) 1 else 0 }

        // Test the actual line segments against a candidate label rectangle.
        // This makes "space above/below" mean genuinely unobstructed space,
        // not merely room before the bitmap boundary.
        fun segmentHitsBox(
            x1: Float, y1: Float, x2: Float, y2: Float, box: LabelBox
        ): Boolean {
            var t0 = 0f
            var t1 = 1f
            val dx = x2 - x1
            val dy = y2 - y1
            fun clip(p: Float, q: Float): Boolean {
                if (abs(p) < 0.0001f) return q >= 0f
                val t = q / p
                if (p < 0f) {
                    if (t > t1) return false
                    if (t > t0) t0 = t
                } else {
                    if (t < t0) return false
                    if (t < t1) t1 = t
                }
                return true
            }
            return clip(-dx, x1 - box.left) &&
                clip(dx, box.right - x1) &&
                clip(-dy, y1 - box.top) &&
                clip(dy, box.bottom - y1) && t0 <= t1
        }
        fun curveOverlaps(box: LabelBox): Boolean {
            val points = days.take(7).mapIndexed { i, day ->
                Triple(x(i), yHigh(day.high), yLow(day.low))
            }
            val dotPad = dotRadius + 0.8f * chartScale
            for (point in points) {
                val highDot = LabelBox(point.first - dotPad, point.second - dotPad,
                    point.first + dotPad, point.second + dotPad)
                val lowDot = LabelBox(point.first - dotPad, point.third - dotPad,
                    point.first + dotPad, point.third + dotPad)
                if (box.left < highDot.right && box.right > highDot.left &&
                    box.top < highDot.bottom && box.bottom > highDot.top) return true
                if (box.left < lowDot.right && box.right > lowDot.left &&
                    box.top < lowDot.bottom && box.bottom > lowDot.top) return true
            }
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                if (segmentHitsBox(a.first, a.second, b.first, b.second, box) ||
                    segmentHitsBox(a.first, a.third, b.first, b.third, box)) return true
            }
            return false
        }

        labels.forEach { label ->
            val textWidth = labelPaint.measureText(label.text)
            val halfWidth = textWidth / 2f
            // Keep the label outside the full dot radius plus a clear gap.
            // The previous gap was smaller than the collision box around each dot.
            val gap = dotRadius + 3f * chartScale
            // Keep the label centered over its own day first; only shift sideways
            // when another label occupies that exact position.
            val horizontalOffsets = listOf(0f, -cellWidth * 0.22f, cellWidth * 0.22f, -cellWidth * 0.38f, cellWidth * 0.38f)
            val preferredDirection = if (label.preferBelow) 1 else -1
            val directions = listOf(preferredDirection, -preferredDirection)
            var chosen: LabelPlacement? = null

            for (direction in directions) {
                for (dx in horizontalOffsets) {
                    val cx = (label.pointX + dx).coerceIn(halfWidth + 1f, width - halfWidth - 1f)
                    val baseline = if (direction > 0) label.pointY + gap - metrics.ascent
                        else label.pointY - gap - metrics.descent
                    val box = LabelBox(
                        cx - halfWidth,
                        baseline + metrics.ascent,
                        cx + halfWidth,
                        baseline + metrics.descent
                    )
                    val inside = box.top >= 0f && box.bottom <= height
                    val overlapsLabel = occupied.any { other ->
                        box.left < other.right && box.right > other.left &&
                            box.top < other.bottom && box.bottom > other.top
                    }
                    val overlapsCurve = curveOverlaps(box)
                    if (inside && !overlapsLabel && !overlapsCurve) {
                        chosen = LabelPlacement(label.text, cx, baseline, box)
                        break
                    }
                }
                if (chosen != null) break
            }

            // Last resort: score both sides and all small horizontal offsets.
            // Never blindly force the label to the opposite side.
            if (chosen == null) {
                var bestScore = Float.POSITIVE_INFINITY
                for (direction in directions) {
                    for (dx in horizontalOffsets) {
                        val cx = (label.pointX + dx).coerceIn(halfWidth + 1f, width - halfWidth - 1f)
                        val baseline = if (direction > 0) label.pointY + gap - metrics.ascent
                            else label.pointY - gap - metrics.descent
                        val clampedBaseline = baseline.coerceIn(-metrics.ascent, height - metrics.descent)
                        val box = LabelBox(
                            cx - halfWidth,
                            clampedBaseline + metrics.ascent,
                            cx + halfWidth,
                            clampedBaseline + metrics.descent
                        )
                        val outsidePenalty = if (box.top < 0f || box.bottom > height) 1000f else 0f
                        val curvePenalty = if (curveOverlaps(box)) 100f else 0f
                        val labelPenalty = if (occupied.any { other ->
                                box.left < other.right && box.right > other.left &&
                                    box.top < other.bottom && box.bottom > other.top
                            }) 50f else 0f
                        val directionPenalty = if (direction == preferredDirection) 0f else 10f
                        val offsetPenalty = abs(dx) / cellWidth
                        val score = outsidePenalty + curvePenalty + labelPenalty +
                            directionPenalty + offsetPenalty
                        if (score < bestScore) {
                            bestScore = score
                            chosen = LabelPlacement(label.text, cx, clampedBaseline, box)
                        }
                    }
                }
            }
            chosen?.let {
                canvas.drawText(it.text, it.x, it.baseline, labelPaint)
                occupied.add(it.box)
            }
        }

        WeatherChartBitmapCache.put(cacheKey, bitmap)
        return bitmap
    }

}

/** Small process-local cache: keeps only a few recently used weather chart bitmaps. */
private object WeatherChartBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(2 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            max(1, value.byteCount / 1024)
    }

    @Synchronized
    fun get(key: String): Bitmap? = cache.get(key)

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}
