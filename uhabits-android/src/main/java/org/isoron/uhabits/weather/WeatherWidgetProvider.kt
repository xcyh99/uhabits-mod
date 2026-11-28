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
import kotlin.math.pow

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
            .coerceIn(WeatherWidgetSettingsActivity.MIN_GAIN, WeatherWidgetSettingsActivity.MAX_GAIN)
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
        val rawMin = days.take(7).minOfOrNull { it.low } ?: 0
        val rawMax = days.take(7).maxOfOrNull { it.high } ?: 10
        // Keep the absolute lowest and highest forecast points anchored to the
        // bottom and top edges. Apply gain as a non-linear contrast curve between
        // those fixed endpoints, so changing the setting visibly changes the
        // separation/shape of intermediate points instead of being normalized away.
        val rawRange = max(1f, (rawMax - rawMin).toFloat())

        // Keep the curves close to the chart edges while reserving only a small
        // safety margin for the dots. Temperature labels are placed dynamically
        // around their points on this same bitmap.
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
        fun x(i: Int) = width * (i + 0.5f) / 7f
        fun y(temp: Int): Float {
            val normalized = ((temp - rawMin) / rawRange).coerceIn(0f, 1f)
            val centered = normalized * 2f - 1f
            val shaped = 0.5f + (if (centered < 0f) -1f else 1f) *
                0.5f * kotlin.math.abs(centered).toDouble().pow(1.0 / curveGain.toDouble()).toFloat()
            return height - edge - shaped * (height - edge * 2f)
        }

        val highPath = Path()
        val lowPath = Path()
        days.take(7).forEachIndexed { i, day ->
            if (i == 0) {
                highPath.moveTo(x(i), y(day.high))
                lowPath.moveTo(x(i), y(day.low))
            } else {
                highPath.lineTo(x(i), y(day.high))
                lowPath.lineTo(x(i), y(day.low))
            }
        }
        canvas.drawPath(highPath, highPaint)
        canvas.drawPath(lowPath, lowPaint)
        days.take(7).forEachIndexed { i, day ->
            canvas.drawCircle(x(i), y(day.high), dotRadius, dotHigh)
            canvas.drawCircle(x(i), y(day.low), dotRadius, dotLow)
        }

        data class LabelBox(val left: Float, val top: Float, val right: Float, val bottom: Float)
        data class LabelPlacement(val text: String, val x: Float, val baseline: Float, val box: LabelBox)
        val occupied = mutableListOf<LabelBox>()
        val labels = days.take(7).flatMapIndexed { i, day ->
            listOf(
                Triple("${day.high}°", x(i), y(day.high)),
                Triple("${day.low}°", x(i), y(day.low))
            )
        }.sortedByDescending { (_, _, pointY) ->
            // Place the points nearest the chart boundaries first.
            max(pointY, height - pointY)
        }

        labels.forEachIndexed { index, (text, pointX, pointY) ->
            val textWidth = labelPaint.measureText(text)
            val halfWidth = textWidth / 2f
            val textHeight = metrics.descent - metrics.ascent
            val gap = 2f * chartScale
            val preferBelow = index % 2 == 0
            val verticalOffsets = if (preferBelow) listOf(1, -1, 2, -2) else listOf(-1, 1, -2, 2)
            var best: LabelPlacement? = null
            var bestCost = Float.MAX_VALUE
            verticalOffsets.forEach { direction ->
                listOf(0f, -cellWidth * 0.22f, cellWidth * 0.22f, -cellWidth * 0.38f, cellWidth * 0.38f).forEach { dx ->
                    val cx = (pointX + dx).coerceIn(halfWidth + 1f, width - halfWidth - 1f)
                    val baseline = if (direction > 0) pointY + gap - metrics.ascent
                        else pointY - gap - metrics.descent
                    val box = LabelBox(cx - halfWidth, baseline + metrics.ascent, cx + halfWidth, baseline + metrics.descent)
                    val overflow = max(0f, -box.top) + max(0f, box.bottom - height)
                    val overlap = occupied.sumOf { other ->
                        val w = max(0f, min(box.right, other.right) - max(box.left, other.left))
                        val h = max(0f, min(box.bottom, other.bottom) - max(box.top, other.top))
                        (w * h).toDouble()
                    }.toFloat()
                    val preferredPenalty = if ((direction > 0) != preferBelow) 3f * chartScale else 0f
                    val cost = overflow * 10000f + overlap * 100f + abs(dx) * 0.08f + preferredPenalty
                    if (cost < bestCost) {
                        bestCost = cost
                        best = LabelPlacement(text, cx, baseline, box)
                    }
                }
            }
            best?.let {
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
