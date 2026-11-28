package org.isoron.uhabits.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
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
                renderChartViews(context, views, snapshot.days, chartWidth, chartHeight, density)
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
    /**
     * Seven-day chart built entirely from ordinary RemoteViews controls.
     * Text labels are TextViews; curve segments and dots are regular Views.
     */
    private fun renderChartViews(context: Context, views: RemoteViews, days: List<WeatherDay>, widthPx: Int, heightPx: Int, density: Float) {
        val visible = days.take(7)
        val gain = PreferenceManager.getDefaultSharedPreferences(context)
            .getFloat(WeatherWidgetSettingsActivity.PREF_CURVE_GAIN, WeatherWidgetSettingsActivity.DEFAULT_CURVE_GAIN)
            .coerceAtLeast(WeatherWidgetSettingsActivity.MIN_GAIN)
        val chartScale = min(widthPx / 240f, heightPx / 64f).coerceIn(0.45f, 2f)
        val highMean = visible.map { it.high.toFloat() }.average().toFloat()
        val lowMean = visible.map { it.low.toFloat() }.average().toFloat()
        val highDev = visible.maxOfOrNull { abs(it.high - highMean) } ?: 0f
        val lowDev = visible.maxOfOrNull { abs(it.low - lowMean) } ?: 0f
        val maxDev = max(highDev, lowDev).coerceAtLeast(0.01f)
        val wantedScale = heightPx * 0.075f * gain / maxDev
        val curveScale = min(wantedScale, heightPx * 0.19f / maxDev)
        val highCenter = heightPx * 0.34f
        val lowCenter = heightPx * 0.66f
        val step = widthPx / 7f
        val highY = visible.map { highCenter - (it.high - highMean) * curveScale }
        val lowY = visible.map { lowCenter - (it.low - lowMean) * curveScale }

        fun viewId(name: String) = context.resources.getIdentifier(name, "id", context.packageName)
        fun show(name: String, visibleNow: Boolean) {
            val id = viewId(name)
            if (id != 0) views.setViewVisibility(id, if (visibleNow) View.VISIBLE else View.GONE)
        }
        fun placeLabel(name: String, value: String, y: Float, above: Boolean) {
            val id = viewId(name)
            if (id == 0) return
            views.setTextViewText(id, value)
            views.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_SP, 7f * chartScale)
            val half = 6f * density * chartScale
            val center = (if (above) y - half - 3f * density else y + half + 3f * density)
                .coerceIn(half, heightPx - half)
            views.setFloat(id, "setTranslationY", center - heightPx / 2f)
        }
        for (i in 0 until 7) {
            show("weather_chart_cell_$i", i < visible.size)
            if (i >= visible.size) continue
            val hd = viewId("weather_high_dot_$i")
            val ld = viewId("weather_low_dot_$i")
            if (hd != 0) {
                views.setViewVisibility(hd, View.VISIBLE)
                views.setFloat(hd, "setTranslationY", highY[i] - heightPx / 2f)
            }
            if (ld != 0) {
                views.setViewVisibility(ld, View.VISIBLE)
                views.setFloat(ld, "setTranslationY", lowY[i] - heightPx / 2f)
            }
            placeLabel("weather_high_label_$i", "${visible[i].high}°", highY[i], true)
            placeLabel("weather_low_label_$i", "${visible[i].low}°", lowY[i], false)
        }
        for (i in 0 until 6) {
            val hasNext = i + 1 < visible.size
            for ((prefix, ys) in listOf("weather_high_line_" to highY, "weather_low_line_" to lowY)) {
                val id = viewId("$prefix$i")
                if (id == 0) continue
                views.setViewVisibility(id, if (hasNext) View.VISIBLE else View.GONE)
                if (hasNext) {
                    val angle = Math.toDegrees(kotlin.math.atan2((ys[i + 1] - ys[i]).toDouble(), step.toDouble())).toFloat()
                    views.setFloat(id, "setRotation", angle)
                    views.setFloat(id, "setTranslationY", (ys[i] + ys[i + 1]) / 2f - heightPx / 2f)
                }
            }
        }
    }


