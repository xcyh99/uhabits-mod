package org.isoron.uhabits.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.timerecords.TimeRecordStore

class TimeRecordWidgetProvider : BaseWidgetProvider() {
    override fun getWidgetFromId(context: Context, id: Int): BaseWidget {
        val recordId = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .getString(id.toString(), null)
        return TimeRecordWidget(context, id, recordId?.let { TimeRecordStore.get(context, it) })
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        if (context == null || appWidgetIds == null) return
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit().apply {
            appWidgetIds.forEach { remove(it.toString()) }
        }.apply()
    }

    companion object {
        private const val WIDGET_PREFS = "time_record_widget_links"

        fun setRecord(context: Context, widgetId: Int, recordId: String) {
            context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .edit().putString(widgetId.toString(), recordId).apply()
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TimeRecordWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, it) }
        }

        fun updateWidget(context: Context, widgetId: Int) {
            context.setTheme(R.style.WidgetTheme)
            val manager = AppWidgetManager.getInstance(context)
            val provider = TimeRecordWidgetProvider()
            val recordId = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .getString(widgetId.toString(), null)
            val widget = TimeRecordWidget(context, widgetId, recordId?.let { TimeRecordStore.get(context, it) })
            widget.setDimensions(
                provider.getDimensionsFromOptions(
                    context,
                    manager.getAppWidgetOptions(widgetId),
                    widgetId,
                    widget.defaultDimensions()
                )
            )
            BaseWidgetProvider.updateAppWidget(manager, widget)
        }
    }
}
