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
import org.isoron.uhabits.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Weather widget backed by the shared WeatherRepository.
 * The proven TEXT line layout and responsive glyph scaling are kept unchanged.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { appWidgetId ->
            val options = manager.getAppWidgetOptions(appWidgetId)
            manager.updateAppWidget(
                appWidgetId,
                createForecastViews(context, options, WeatherRepository.load(context))
            )
        }

        // Render cached data immediately, then refresh in the background.
        WeatherRepository.fetch(context) {
            updateAllWidgets(context, manager, it)
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
            createForecastViews(context, newOptions, WeatherRepository.load(context))
        )
    }

    private fun updateAllWidgets(
        context: Context,
        manager: AppWidgetManager,
        snapshot: WeatherSnapshot?
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
        ids.forEach { appWidgetId ->
            val options = manager.getAppWidgetOptions(appWidgetId)
            manager.updateAppWidget(appWidgetId, createForecastViews(context, options, snapshot))
        }
    }

    private fun createForecastViews(
        context: Context,
        options: Bundle,
        snapshot: WeatherSnapshot?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather_line_test)
        applyResponsiveLineScale(views, options)

        val openDetail = Intent(context, WeatherDetailActivity::class.java)
        val clickIntent = PendingIntent.getActivity(
            context,
            0,
            openDetail,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weather_widget_root, clickIntent)

        if (snapshot == null) {
            views.setTextViewText(R.id.weather_city, "天气")
            views.setTextViewText(R.id.weather_updated, "等待天气数据")
            views.setTextViewText(R.id.weather_current_desc, "--")
            views.setTextViewText(R.id.weather_current_temp, "--°")
            views.setTextViewText(R.id.weather_humidity_value, "--%")
            views.setTextViewText(R.id.weather_wind_value, "--")
            views.setTextViewText(R.id.weather_rain_value, "--%")
            return views
        }

        views.setTextViewText(R.id.weather_city, snapshot.city)
        val updated = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snapshot.fetchedAt))
        views.setTextViewText(R.id.weather_updated, "更新 " + updated)
        views.setTextViewText(R.id.weather_current_desc, WeatherRepository.weatherText(snapshot.currentCode))
        views.setTextViewText(R.id.weather_current_temp, snapshot.currentTemperature.toString() + "°")
        views.setTextViewText(R.id.weather_humidity_value, snapshot.humidity.toString() + "%")
        views.setTextViewText(R.id.weather_wind_value, snapshot.wind.toString())
        views.setTextViewText(R.id.weather_rain_value, (snapshot.days.firstOrNull()?.rain ?: 0).toString() + "%")

        val dayIds = intArrayOf(
            R.id.weather_day_0, R.id.weather_day_1, R.id.weather_day_2,
            R.id.weather_day_3, R.id.weather_day_4, R.id.weather_day_5,
            R.id.weather_day_6
        )
        val iconIds = intArrayOf(
            R.id.weather_icon_0, R.id.weather_icon_1, R.id.weather_icon_2,
            R.id.weather_icon_3, R.id.weather_icon_4, R.id.weather_icon_5,
            R.id.weather_icon_6
        )
        val temperatureIds = intArrayOf(
            R.id.weather_desc_0, R.id.weather_desc_1, R.id.weather_desc_2,
            R.id.weather_desc_3, R.id.weather_desc_4, R.id.weather_desc_5,
            R.id.weather_desc_6
        )

        val days = snapshot.days.take(7)
        for (i in 0 until 7) {
            val day = days.getOrNull(i)
            if (day == null) {
                views.setTextViewText(dayIds[i], "--")
                views.setTextViewText(iconIds[i], "")
                views.setTextViewText(temperatureIds[i], "--°/--°")
            } else {
                views.setTextViewText(dayIds[i], WeatherRepository.dayLabel(day.date, i, compact = false))
                views.setTextViewText(iconIds[i], WeatherRepository.weatherIcon(day.code))
                views.setTextViewText(temperatureIds[i], day.high.toString() + "°/" + day.low + "°")
            }
        }

        // Each glyph joins the temperatures of two adjacent forecast days.
        // Increasing values rise visually to the right; decreasing values fall.
        val highGlyphs = lineGlyphs(days.map { it.high })
        val lowGlyphs = lineGlyphs(days.map { it.low })
        val highIds = intArrayOf(
            R.id.weather_high_segment_0, R.id.weather_high_segment_1,
            R.id.weather_high_segment_2, R.id.weather_high_segment_3,
            R.id.weather_high_segment_4, R.id.weather_high_segment_5
        )
        val lowIds = intArrayOf(
            R.id.weather_low_segment_0, R.id.weather_low_segment_1,
            R.id.weather_low_segment_2, R.id.weather_low_segment_3,
            R.id.weather_low_segment_4, R.id.weather_low_segment_5
        )
        for (i in 0 until 6) {
            views.setTextViewText(highIds[i], highGlyphs.getOrNull(i)?.toString() ?: "")
            views.setTextViewText(lowIds[i], lowGlyphs.getOrNull(i)?.toString() ?: "")
            views.setViewVisibility(highIds[i], if (i < highGlyphs.length) View.VISIBLE else View.INVISIBLE)
            views.setViewVisibility(lowIds[i], if (i < lowGlyphs.length) View.VISIBLE else View.INVISIBLE)
        }
        return views
    }

    private fun lineGlyphs(values: List<Int>): String {
        if (values.size < 7) return ""
        return (0 until 6).joinToString("") { index ->
            if (values[index + 1] >= values[index]) "╱" else "╲"
        }
    }

    /**
     * Keep the verified responsive TEXT line geometry unchanged.
     * All six intervals remain present at every widget width.
     */
    private fun applyResponsiveLineScale(views: RemoteViews, options: Bundle) {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val widgetWidthDp = when {
            minWidth > 0 -> minWidth
            maxWidth > 0 -> maxWidth
            else -> 50
        }
        val glyphTextScaleX = if (widgetWidthDp >= 295) 2.43f else 1.9f

        val highIds = intArrayOf(
            R.id.weather_high_segment_0, R.id.weather_high_segment_1,
            R.id.weather_high_segment_2, R.id.weather_high_segment_3,
            R.id.weather_high_segment_4, R.id.weather_high_segment_5
        )
        val lowIds = intArrayOf(
            R.id.weather_low_segment_0, R.id.weather_low_segment_1,
            R.id.weather_low_segment_2, R.id.weather_low_segment_3,
            R.id.weather_low_segment_4, R.id.weather_low_segment_5
        )
        for (i in 0 until 6) {
            views.setFloat(highIds[i], "setTextScaleX", glyphTextScaleX)
            views.setFloat(lowIds[i], "setTextScaleX", glyphTextScaleX)
        }
    }
}
