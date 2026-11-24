package org.isoron.uhabits.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import org.isoron.uhabits.R

/**
 * A lightweight 2x3 home-screen clock.
 *
 * TextClock is deliberately kept as a real RemoteViews child instead of being
 * rasterized into the bitmap used by BaseWidget. Android can then update the
 * displayed time itself at minute boundaries without waking this application.
 */
class DigitalClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateWidget(context, manager, appWidgetId)
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_digital_clock)
        manager.updateAppWidget(widgetId, views)
    }
}
