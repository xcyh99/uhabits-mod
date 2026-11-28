package org.isoron.uhabits.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import org.isoron.uhabits.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Weather widget: cached-first rendering with an asynchronous forecast refresh.
 *
 * Each temperature curve is rendered as one monospace TEXT block. The six
 * intervals are rasterized into adjacent character cells inside that single
 * TextView, avoiding independent segment-view boundaries.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val cached = WeatherRepository.load(context)
        ids.forEach { id ->
            manager.updateAppWidget(id, createWeatherViews(context, manager.getAppWidgetOptions(id), cached))
        }

        // Refresh once for this update batch. The repository returns cached data on network failure.
        WeatherRepository.fetch(context) { fresh ->
            if (fresh != null) {
                ids.forEach { id ->
                    manager.updateAppWidget(id, createWeatherViews(context, manager.getAppWidgetOptions(id), fresh))
                }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        manager.updateAppWidget(
            appWidgetId,
            createWeatherViews(context, newOptions, WeatherRepository.load(context))
        )
    }

    private fun createWeatherViews(
        context: Context,
        options: Bundle,
        snapshot: WeatherSnapshot?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather_line_test)
        val widgetWidthDp = getWidgetWidthDp(options)
        applyResponsiveLineScale(context, views, widgetWidthDp)

        val openDetails = Intent(context, WeatherDetailActivity::class.java)
        val clickIntent = PendingIntent.getActivity(
            context,
            7001,
            openDetails,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weather_widget_root, clickIntent)

        if (snapshot == null || snapshot.days.isEmpty()) {
            views.setTextViewText(R.id.weather_city, "天气")
            views.setTextViewText(R.id.weather_updated, "正在获取预报")
            views.setTextViewText(R.id.weather_current_desc, "…")
            views.setTextViewText(R.id.weather_current_temp, "--°")
            views.setTextViewText(R.id.weather_humidity_value, "--%")
            views.setTextViewText(R.id.weather_wind_value, "--")
            views.setTextViewText(R.id.weather_rain_value, "--%")
            for (i in 0..6) {
                views.setTextViewText(dayIds[i], "--")
                views.setTextViewText(iconIds[i], "·")
                views.setTextViewText(tempIds[i], "--°/--°")
            }
            clearChartText(views)
            return views
        }

        views.setTextViewText(R.id.weather_city, snapshot.city)
        val updated = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snapshot.fetchedAt))
        views.setTextViewText(R.id.weather_updated, "更新于 $updated")
        views.setTextViewText(R.id.weather_current_desc, WeatherRepository.weatherIcon(snapshot.currentCode))
        views.setTextViewText(R.id.weather_current_temp, "${snapshot.currentTemperature}°")
        views.setTextViewText(R.id.weather_humidity_value, "${snapshot.humidity}%")
        views.setTextViewText(R.id.weather_wind_value, "${snapshot.wind} km/h")
        views.setTextViewText(R.id.weather_rain_value, "${snapshot.days.first().rain}%")

        val days = snapshot.days.take(7)
        for (i in 0..6) {
            val day = days.getOrNull(i)
            if (day == null) {
                views.setTextViewText(dayIds[i], "--")
                views.setTextViewText(iconIds[i], "·")
                views.setTextViewText(tempIds[i], "--°/--°")
            } else {
                views.setTextViewText(dayIds[i], WeatherRepository.dayLabel(day.date, i))
                views.setTextViewText(iconIds[i], WeatherRepository.weatherIcon(day.code))
                views.setTextViewText(tempIds[i], "${day.high}°/${day.low}°")
            }
        }

        // A chart is valid only with seven real forecast days: seven nodes, six intervals.
        if (days.size == 7) {
            val highChart = WeatherLineGeometry.calculate(
                temperatures = days.map { it.high },
                width = widgetWidthDp * 6f / 7f,
                height = CHART_ROW_HEIGHT_DP,
                leftInset = widgetWidthDp / 14f
            )
            val lowChart = WeatherLineGeometry.calculate(
                temperatures = days.map { it.low },
                width = widgetWidthDp * 6f / 7f,
                height = CHART_ROW_HEIGHT_DP,
                leftInset = widgetWidthDp / 14f
            )
            applyChartText(context, views, R.id.weather_high_line, highChart, widgetWidthDp)
            applyChartText(context, views, R.id.weather_low_line, lowChart, widgetWidthDp)
        } else {
            clearChartText(views)
        }
        return views
    }

    /**
     * Renders a complete six-interval curve into one TextView. The renderer
     * uses a 7-row x 56-column monospace character grid: eight character cells
     * per day interval, with each day node located at the center of its column.
     */
    private fun applyChartText(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        chart: WeatherLineGeometry.Chart,
        widgetWidthDp: Int
    ) {
        views.setTextViewText(viewId, WeatherLineTextRenderer.render(chart))
        val metrics = context.resources.displayMetrics
        val fontScale = metrics.scaledDensity / metrics.density
        val cellWidthDp = LINE_TEXT_SIZE_SP * fontScale * MONOSPACE_WIDTH_FACTOR
        val scaleX = widgetWidthDp / (WeatherLineTextRenderer.COLUMN_COUNT * cellWidthDp)
        views.setFloat(viewId, "setTextScaleX", scaleX.coerceAtLeast(0.1f))
    }

    private fun clearChartText(views: RemoteViews) {
        views.setTextViewText(R.id.weather_high_line, "")
        views.setTextViewText(R.id.weather_low_line, "")
    }

    private fun getWidgetWidthDp(options: Bundle): Int {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        return when {
            minWidth > 0 -> minWidth
            maxWidth > 0 -> maxWidth
            else -> 50
        }
    }

    private fun applyResponsiveLineScale(context: Context, views: RemoteViews, widgetWidthDp: Int) {
        val metrics = context.resources.displayMetrics
        val fontScale = metrics.scaledDensity / metrics.density
        val cellWidthDp = LINE_TEXT_SIZE_SP * fontScale * MONOSPACE_WIDTH_FACTOR
        val scaleX = widgetWidthDp / (WeatherLineTextRenderer.COLUMN_COUNT * cellWidthDp)
        views.setFloat(R.id.weather_high_line, "setTextScaleX", scaleX.coerceAtLeast(0.1f))
        views.setFloat(R.id.weather_low_line, "setTextScaleX", scaleX.coerceAtLeast(0.1f))
    }

    companion object {
        private const val CHART_ROW_HEIGHT_DP = 27f
        private const val LINE_TEXT_SIZE_SP = 3.5f
        private const val MONOSPACE_WIDTH_FACTOR = 0.6f

        private val dayIds = intArrayOf(
            R.id.weather_day_0, R.id.weather_day_1, R.id.weather_day_2,
            R.id.weather_day_3, R.id.weather_day_4, R.id.weather_day_5, R.id.weather_day_6
        )
        private val iconIds = intArrayOf(
            R.id.weather_icon_0, R.id.weather_icon_1, R.id.weather_icon_2,
            R.id.weather_icon_3, R.id.weather_icon_4, R.id.weather_icon_5, R.id.weather_icon_6
        )
        private val tempIds = intArrayOf(
            R.id.weather_desc_0, R.id.weather_desc_1, R.id.weather_desc_2,
            R.id.weather_desc_3, R.id.weather_desc_4, R.id.weather_desc_5, R.id.weather_desc_6
        )

    }
}
