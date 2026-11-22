package org.isoron.uhabits.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.timerecords.TimeRecordStore
import org.isoron.uhabits.activities.timerecords.TimeRecordsActivity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class TimeRecordWidgetConfigureActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val records = TimeRecordStore.getAll(this)
        if (records.isEmpty()) {
            Toast.makeText(this, R.string.time_records_widget_create_first, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, TimeRecordsActivity::class.java))
            finish()
            return
        }

        val today = LocalDate.now()
        val labels = records.map { record ->
            val date = LocalDate.parse(record.date)
            val days = if (record.type == org.isoron.uhabits.activities.timerecords.TimeRecord.ELAPSED) {
                ChronoUnit.DAYS.between(date, today)
            } else {
                ChronoUnit.DAYS.between(today, date)
            }
            val direction = if (record.type == org.isoron.uhabits.activities.timerecords.TimeRecord.ELAPSED) {
                getString(R.string.time_records_elapsed)
            } else {
                getString(R.string.time_records_countdown)
            }
            "${record.title}  ·  $direction  ·  ${kotlin.math.abs(days)} ${getString(R.string.day)}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.time_records_widget_choose_record)
            .setItems(labels) { _, which ->
                val selected = records[which]
                TimeRecordWidgetProvider.setRecord(this, widgetId, selected.id)
                TimeRecordWidgetProvider.updateWidget(this, widgetId)
                val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                setResult(RESULT_OK, result)
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
