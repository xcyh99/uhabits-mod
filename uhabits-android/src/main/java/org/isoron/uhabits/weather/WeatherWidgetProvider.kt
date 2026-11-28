package org.isoron.uhabits.weather

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import org.isoron.uhabits.R

/**
 * Temporary third-round diagnostic.
 *
 * Bypass WeatherRepository, cached-data loading, network refresh, dynamic
 * RemoteViews operations, and the full weather layout. This tests the
 * provider's direct update path using a single TextView layout.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = createDiagnosticViews(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        manager.updateAppWidget(appWidgetId, createDiagnosticViews(context))
    }

    private fun createDiagnosticViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather_loading)
        views.setTextViewText(R.id.weather_loading_text, "天气Provider直连测试")
        return views
    }
}
