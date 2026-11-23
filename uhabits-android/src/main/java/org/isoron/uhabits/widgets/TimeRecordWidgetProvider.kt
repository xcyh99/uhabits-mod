package org.isoron.uhabits.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.timerecords.TimeRecord
import org.isoron.uhabits.activities.timerecords.TimeRecordStore
import org.isoron.uhabits.activities.timerecords.TimeRecordsActivity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

class TimeRecordWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            appWidgetIds.forEach { remove(it.toString()) }
        }.apply()
    }

    companion object {
        private const val WIDGET_PREFS = "time_record_widget_links"
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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
            val manager = AppWidgetManager.getInstance(context)
            val recordId = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .getString(widgetId.toString(), null)
            val record = recordId?.let { TimeRecordStore.get(context, it) }
            val views = RemoteViews(context.packageName, R.layout.widget_time_record)
            if (record == null) {
                views.setTextViewText(R.id.time_record_title, context.getString(R.string.time_records_widget_unconfigured))
                views.setTextViewText(R.id.time_record_number, "—")
                views.setTextViewText(R.id.time_record_date, "")
            } else {
                val target = LocalDate.parse(record.date)
                val today = LocalDate.now()
                val difference = if (record.type == TimeRecord.ELAPSED) {
                    ChronoUnit.DAYS.between(target, today)
                } else {
                    ChronoUnit.DAYS.between(today, target)
                }
                views.setTextViewText(R.id.time_record_title, record.title)
                views.setTextViewText(R.id.time_record_number, kotlin.math.abs(difference).toString())
                views.setTextViewText(R.id.time_record_date, target.format(dateFormatter))
                views.setTextColor(R.id.time_record_title, record.color)
                views.setTextColor(R.id.time_record_number, record.color)
                views.setTextColor(
                    R.id.time_record_date,
                    if (record.type == TimeRecord.ELAPSED) 0xFF2EAD67.toInt() else 0xFFE05252.toInt()
                )
            }
            val intent = Intent(context, TimeRecordsActivity::class.java)
            val pending = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.time_record_root, pending)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
