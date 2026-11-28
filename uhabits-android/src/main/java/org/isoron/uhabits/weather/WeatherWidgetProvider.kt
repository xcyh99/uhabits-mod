package org.isoron.uhabits.weather

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import org.isoron.uhabits.R

/**
 * Temporary fourth-round diagnostic.
 *
 * Uses only the original weather header section in an isolated layout.
 * Weather data, forecast rows, chart views, and background drawables remain
 * bypassed so this test isolates the header layout itself.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = createHeaderTestViews(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        manager.updateAppWidget(appWidgetId, createHeaderTestViews(context))
    }

    private fun createHeaderTestViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather_header_test)
        views.setTextViewText(R.id.weather_city, "天气顶部栏测试")
        views.setTextViewText(R.id.weather_updated, "Provider直连正常")
        views.setTextViewText(R.id.weather_current_desc, "晴")
        views.setTextViewText(R.id.weather_current_temp, "22°")
        views.setTextViewText(R.id.weather_humidity_value, "55%")
        views.setTextViewText(R.id.weather_wind_value, "东风2级")
        views.setTextViewText(R.id.weather_rain_value, "0%")
        return views
    }
}
