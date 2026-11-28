package org.isoron.uhabits.weather

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
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
     * The fixed-width widget uses its height to select the number of visible
     * diagonal glyph segments. Dimensions supplied by AppWidgetOptions are dp.
     */
    private fun applyResponsiveLineCount(views: RemoteViews, options: Bundle) {
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val widgetHeightDp = when {
            minHeight > 0 -> minHeight
            maxHeight > 0 -> maxHeight
            else -> 50
        }

        // Typical launcher row sizing: roughly 70dp per row minus 30dp spacing.
        val visibleSegments = when {
            widgetHeightDp < 85 -> 1   // 2x1
            widgetHeightDp < 155 -> 2  // 2x2
            widgetHeightDp < 225 -> 4  // 2x3
            widgetHeightDp < 295 -> 5  // 2x4
            else -> 7                  // 2x5
        }

        val high = "╱╲╱╱╲╱╲"
        val low = "╲╱╲╲╱╲╱"
        views.setTextViewText(R.id.weather_high_line, sampleLine(high, visibleSegments))
        views.setTextViewText(R.id.weather_low_line, sampleLine(low, visibleSegments))
    }

    /** Keep both ends and distribute selected glyphs evenly across the trend. */
    private fun sampleLine(line: String, count: Int): String {
        if (count >= line.length) return line
        if (count <= 1) return line.substring(0, 1)
        val lastIndex = line.lastIndex
        return buildString(count) {
            for (i in 0 until count) {
                val sourceIndex = (i * lastIndex.toFloat() / (count - 1)).toInt()
                append(line[sourceIndex])
            }
        }
    }
}
