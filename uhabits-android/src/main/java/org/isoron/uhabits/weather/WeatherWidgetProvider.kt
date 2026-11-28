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
import android.view.Gravity
import android.widget.RemoteViews
import android.util.LruCache
import androidx.preference.PreferenceManager
import org.isoron.uhabits.R
import kotlin.math.max
import kotlin.math.min

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val snapshot = WeatherRepository.load(context)
        ids.forEach { render(context, manager, it, snapshot) }
        WeatherRepository.fetch(context) { fresh -> updateAll(context, fresh) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        // Launcher resize callbacks can arrive repeatedly while the user drags a
        // widget edge. Re-render from the local cache only; never start network
        // requests during interactive resizing.
        render(context, manager, appWidgetId, WeatherRepository.load(context))
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
        val showBottom = widthDp >= 210 && heightDp >= 175

        // Use the full widget canvas: no artificial inset around the weather content.
        val padding = 0
        views.setViewPadding(R.id.weather_root, 0, 0, 0, 0)
        val tinyCell = widthDp < 130 && heightDp < 130
        views.setViewVisibility(R.id.weather_tiny_panel, if (tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_header, if (tinyCell) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.weather_city, if (widthDp >= 180 && !tiny) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_updated, if (showUpdated && !tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_day_desc_row, if (showDays && !tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_chart, if (showChart && !tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_bottom_info, if (showBottom && !tinyCell) View.VISIBLE else View.GONE)

        val cityText = snapshot?.city ?: "天气"
        val tempText = snapshot?.let { "${it.currentTemperature}°" } ?: "--°"
        val conditionText = snapshot?.let { WeatherRepository.weatherText(it.currentCode) } ?: "获取中"
        views.setTextViewText(R.id.weather_city, cityText)
        views.setTextViewText(R.id.weather_current_temp, tempText)
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
            widthDp < 160 || heightDp < 130 -> 25f
            else -> 30f
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
            snapshot.days.take(7).forEachIndexed { index, day ->
                views.setTextViewText(dayViews[index], WeatherRepository.dayLabel(day.date, index))
                views.setTextViewText(iconViews[index], WeatherRepository.weatherIcon(day.code))
                views.setTextViewText(descViews[index], "${day.high}°/${day.low}°")
                if (widthDp < 180 || heightDp < 150) {
                    views.setTextViewTextSize(dayViews[index], android.util.TypedValue.COMPLEX_UNIT_SP, 8f)
                    views.setTextViewTextSize(iconViews[index], android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                    views.setTextViewTextSize(descViews[index], android.util.TypedValue.COMPLEX_UNIT_SP, 7f)
                }
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
                val reservedHeightDp = padding * 2 + 36 +
                    (if (showUpdated) 13 else 0) +
                    (if (showDays) 49 else 0) +
                    (if (showBottom) 17 else 0) + 4
                val chartHeight = max(1, ((heightDp - reservedHeightDp) * density).toInt())
                val chartLabelSizeSp = if (widthDp < 180 || heightDp < 150) 7f else 9f
                views.setImageViewBitmap(R.id.weather_chart, drawChart(context, snapshot.days, chartWidth, chartHeight))
                updateChartLabels(context, views, snapshot.days, chartWidth, chartHeight, chartLabelSizeSp)
            }
            views.setTextViewText(
                R.id.weather_bottom_info,
                "湿度 ${snapshot.humidity}%   风速 ${snapshot.wind} km/h   今日降水 ${snapshot.days.first().rain}%"
            )
        } else {
            views.setTextViewText(R.id.weather_bottom_info, "天气数据暂不可用，点击查看")
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

    private fun drawChart(context: Context, days: List<WeatherDay>, width: Int, height: Int): Bitmap {
        val curveGain = PreferenceManager.getDefaultSharedPreferences(context)
            .getFloat(WeatherWidgetSettingsActivity.PREF_CURVE_GAIN, WeatherWidgetSettingsActivity.DEFAULT_CURVE_GAIN)
            .coerceIn(WeatherWidgetSettingsActivity.MIN_GAIN, WeatherWidgetSettingsActivity.MAX_GAIN)
        val density = context.resources.displayMetrics.density
        val cacheKey = buildString {
            append(width).append('x').append(height).append('|').append(density).append('|').append(curveGain)
            days.take(7).forEach { append('|').append(it.date).append(':').append(it.high).append('/').append(it.low) }
        }
        WeatherChartBitmapCache.get(cacheKey)?.let { return it }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val chartScale = min(1f, min(width / 240f, height / 64f)).coerceAtLeast(0f)
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
        val centerTemp = (rawMin + rawMax) / 2f
        fun emphasized(temp: Int) = centerTemp + (temp - centerTemp) * curveGain
        val rawRange = max(1f, rawMax - rawMin.toFloat())
        val plotHalfRange = rawRange * WeatherWidgetSettingsActivity.MAX_GAIN / 2f + 1.5f
        val minTemp = centerTemp - plotHalfRange
        val maxTemp = centerTemp + plotHalfRange
        val range = max(1f, maxTemp - minTemp)
        val side = min(34f * chartScale, width * 0.12f)
        val top = 4f * chartScale
        val bottom = 4f * chartScale
        fun x(i: Int) = side + i * (width - side * 2f) / 6f
        fun y(temp: Int) = height - bottom - (emphasized(temp) - minTemp) * (height - top - bottom) / range
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
            canvas.drawCircle(x(i), y(day.high), 3f * chartScale, dotHigh)
            canvas.drawCircle(x(i), y(day.low), 3f * chartScale, dotLow)
        }
        WeatherChartBitmapCache.put(cacheKey, bitmap)
        return bitmap
    }

    /**
     * Temperature labels are native TextViews layered over the line-only chart.
     * Their font size and aspect ratio are independent from chart bitmap scaling.
     */
    private fun updateChartLabels(
        context: Context, views: RemoteViews, days: List<WeatherDay>,
        width: Int, height: Int, labelSizeSp: Float
    ) {
        val density = context.resources.displayMetrics.density
        val curveGain = PreferenceManager.getDefaultSharedPreferences(context)
            .getFloat(WeatherWidgetSettingsActivity.PREF_CURVE_GAIN, WeatherWidgetSettingsActivity.DEFAULT_CURVE_GAIN)
            .coerceIn(WeatherWidgetSettingsActivity.MIN_GAIN, WeatherWidgetSettingsActivity.MAX_GAIN)
        val rawMin = days.take(7).minOfOrNull { it.low } ?: 0
        val rawMax = days.take(7).maxOfOrNull { it.high } ?: 10
        val centerTemp = (rawMin + rawMax) / 2f
        fun emphasized(temp: Int) = centerTemp + (temp - centerTemp) * curveGain
        val rawRange = max(1f, rawMax - rawMin.toFloat())
        val plotHalfRange = rawRange * WeatherWidgetSettingsActivity.MAX_GAIN / 2f + 1.5f
        val minTemp = centerTemp - plotHalfRange
        val maxTemp = centerTemp + plotHalfRange
        val range = max(1f, maxTemp - minTemp)
        val chartScale = min(1f, min(width / 240f, height / 64f)).coerceAtLeast(0f)
        val top = 4f * chartScale
        val bottom = 4f * chartScale
        fun y(temp: Int) = height - bottom - (emphasized(temp) - minTemp) * (height - top - bottom) / range

        val highIds = intArrayOf(
            R.id.weather_chart_high_0, R.id.weather_chart_high_1, R.id.weather_chart_high_2,
            R.id.weather_chart_high_3, R.id.weather_chart_high_4, R.id.weather_chart_high_5,
            R.id.weather_chart_high_6
        )
        val lowIds = intArrayOf(
            R.id.weather_chart_low_0, R.id.weather_chart_low_1, R.id.weather_chart_low_2,
            R.id.weather_chart_low_3, R.id.weather_chart_low_4, R.id.weather_chart_low_5,
            R.id.weather_chart_low_6
        )
        val labelHeight = labelSizeSp * density * 1.25f
        val gap = 2f * density
        fun place(viewId: Int, pointY: Float, preferAbove: Boolean) {
            val aboveFits = pointY - gap - labelHeight >= 0f
            val belowFits = pointY + gap + labelHeight <= height
            val above = when {
                preferAbove && aboveFits -> true
                !preferAbove && belowFits -> false
                aboveFits -> true
                else -> false
            }
            val topPadding: Int
            val bottomPadding: Int
            if (above) {
                topPadding = (pointY - gap - labelHeight).coerceAtLeast(0f).toInt()
                bottomPadding = 0
                views.setInt(viewId, "setGravity", Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            } else {
                topPadding = 0
                bottomPadding = (height - pointY - gap - labelHeight).coerceAtLeast(0f).toInt()
                views.setInt(viewId, "setGravity", Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            }
            views.setViewPadding(viewId, 0, topPadding, 0, bottomPadding)
            views.setTextViewTextSize(viewId, android.util.TypedValue.COMPLEX_UNIT_SP, labelSizeSp)
        }
        days.take(7).forEachIndexed { i, day ->
            views.setTextViewText(highIds[i], "${day.high}°")
            views.setTextViewText(lowIds[i], "${day.low}°")
            val highY = y(day.high)
            val lowY = y(day.low)
            val labelsOverlap = kotlin.math.abs(highY - lowY) < labelHeight * 2f + gap * 2f
            place(highIds[i], highY, true)
            place(lowIds[i], lowY, !labelsOverlap)
            if (labelsOverlap) {
                // Separate close high/low labels onto opposite sides of their points.
                place(highIds[i], highY, highY >= lowY)
                place(lowIds[i], lowY, lowY > highY)
            }
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
