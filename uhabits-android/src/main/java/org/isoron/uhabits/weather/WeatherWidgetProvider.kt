package org.isoron.uhabits.weather

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import org.isoron.uhabits.R

/**
 * Fifth-round diagnostic: isolated header plus seven-day forecast row.
 * All values are fixed; custom chart drawing, repository, cache, and network are bypassed.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { appWidgetId ->
            val options = manager.getAppWidgetOptions(appWidgetId)
            manager.updateAppWidget(appWidgetId, createForecastTestViews(context, options))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        manager.updateAppWidget(appWidgetId, createForecastTestViews(context, newOptions))
    }

    private fun createForecastTestViews(context: Context, options: Bundle): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather_line_test)
        applyResponsiveLineCount(views, options)
        views.setTextViewText(R.id.weather_city, "天气七天预报测试")
        views.setTextViewText(R.id.weather_updated, "顶部栏与预报区独立测试")
        views.setTextViewText(R.id.weather_current_desc, "晴")
        views.setTextViewText(R.id.weather_current_temp, "22°")
        views.setTextViewText(R.id.weather_humidity_value, "55%")
        views.setTextViewText(R.id.weather_wind_value, "东风2级")
        views.setTextViewText(R.id.weather_rain_value, "0%")

        val dayLabels = arrayOf("今天", "周日", "周一", "周二", "周三", "周四", "周五")
        val weatherIcons = arrayOf("☀", "☁", "🌦", "☀", "☁", "🌧", "☀")
        val temperatures = arrayOf("22°/13°", "20°/12°", "18°/11°", "21°/12°", "23°/14°", "19°/10°", "22°/13°")
        for (i in 0..6) {
            val dayId = intArrayOf(R.id.weather_day_0, R.id.weather_day_1, R.id.weather_day_2, R.id.weather_day_3, R.id.weather_day_4, R.id.weather_day_5, R.id.weather_day_6)[i]
            val iconId = intArrayOf(R.id.weather_icon_0, R.id.weather_icon_1, R.id.weather_icon_2, R.id.weather_icon_3, R.id.weather_icon_4, R.id.weather_icon_5, R.id.weather_icon_6)[i]
            val tempId = intArrayOf(R.id.weather_desc_0, R.id.weather_desc_1, R.id.weather_desc_2, R.id.weather_desc_3, R.id.weather_desc_4, R.id.weather_desc_5, R.id.weather_desc_6)[i]
            views.setTextViewText(dayId, dayLabels[i])
            views.setTextViewText(iconId, weatherIcons[i])
            views.setTextViewText(tempId, temperatures[i])
        }
        return views
    }

    /**
     * Choose how many of the six day-to-day intervals are visible from widget width.
     * Each interval keeps its own fixed slot, aligned between two adjacent day columns.
     */
    private fun applyResponsiveLineCount(views: RemoteViews, options: Bundle) {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val widgetWidthDp = when {
            minWidth > 0 -> minWidth
            maxWidth > 0 -> maxWidth
            else -> 50
        }

        // Keep all six intervals visible at every widget width.
        // The launcher can report a different final size after resize is released;
        // hiding individual slots here caused gaps in the settled widget.

        // The 5-column widget has wider segment slots than the proven 4-column layout.
        // Stretch only the glyph text (not the TextView bounds) so adjacent intervals stay connected.
        // 4x2 and all narrower sizes retain the verified 1.9 scale.
        val glyphTextScaleX = if (widgetWidthDp >= 295) 2.43f else 1.9f

        val highGlyphs = "╱╲╱╱╲╱"
        val lowGlyphs = "╲╱╲╲╱╲"
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
            val visibility = View.VISIBLE
            views.setTextViewText(highIds[i], highGlyphs[i].toString())
            views.setTextViewText(lowIds[i], lowGlyphs[i].toString())
            views.setFloat(highIds[i], "setTextScaleX", glyphTextScaleX)
            views.setFloat(lowIds[i], "setTextScaleX", glyphTextScaleX)
            views.setViewVisibility(highIds[i], visibility)
            views.setViewVisibility(lowIds[i], visibility)
        }
    }

}
