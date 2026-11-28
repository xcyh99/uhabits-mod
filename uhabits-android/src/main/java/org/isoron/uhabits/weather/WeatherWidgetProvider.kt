package org.isoron.uhabits.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import org.isoron.uhabits.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Weather widget: cached-first rendering with an asynchronous forecast refresh.
 * The verified TEXT chart layout and responsive glyph scaling are intentionally preserved.
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
        applyResponsiveLineCount(views, options)

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

        // Each of the six chart segments represents the change between adjacent forecast days.
        val highs = days.map { it.high }
        val lows = days.map { it.low }
        val highGlyphs = (0 until 6).joinToString("") { i ->
            if (highs[i + 1] >= highs[i]) "╱" else "╲"
        }
        val lowGlyphs = (0 until 6).joinToString("") { i ->
            if (lows[i + 1] >= lows[i]) "╱" else "╲"
        }
        for (i in 0 until 6) {
            views.setTextViewText(highSegmentIds[i], highGlyphs[i].toString())
            views.setTextViewText(lowSegmentIds[i], lowGlyphs[i].toString())
        }
        return views
    }

    private fun applyResponsiveLineCount(views: RemoteViews, options: Bundle) {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val widgetWidthDp = when {
            minWidth > 0 -> minWidth
            maxWidth > 0 -> maxWidth
            else -> 50
        }
        // Keep all six intervals visible. This is the phone-verified 1x2–6x2 behavior.
        val glyphTextScaleX = if (widgetWidthDp >= 295) 2.43f else 1.9f
        for (i in 0 until 6) {
            views.setFloat(highSegmentIds[i], "setTextScaleX", glyphTextScaleX)
            views.setFloat(lowSegmentIds[i], "setTextScaleX", glyphTextScaleX)
            views.setViewVisibility(highSegmentIds[i], View.VISIBLE)
            views.setViewVisibility(lowSegmentIds[i], View.VISIBLE)
        }
    }

    companion object {
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
        private val highSegmentIds = intArrayOf(
            R.id.weather_high_segment_0, R.id.weather_high_segment_1,
            R.id.weather_high_segment_2, R.id.weather_high_segment_3,
            R.id.weather_high_segment_4, R.id.weather_high_segment_5
        )
        private val lowSegmentIds = intArrayOf(
            R.id.weather_low_segment_0, R.id.weather_low_segment_1,
            R.id.weather_low_segment_2, R.id.weather_low_segment_3,
            R.id.weather_low_segment_4, R.id.weather_low_segment_5
        )
    }
}
