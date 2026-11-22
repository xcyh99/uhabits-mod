package org.isoron.uhabits.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.settings.SettingsActivity

/** A dedicated home-screen entry that opens the batch widget picker with one tap. */
class BatchAddWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            putExtra("open_preference_key", "widgetManager")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_batch_add)
            val pending = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.batch_add_root, pending)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
