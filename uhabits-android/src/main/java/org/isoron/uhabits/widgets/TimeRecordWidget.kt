package org.isoron.uhabits.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.timerecords.TimeRecord
import org.isoron.uhabits.activities.timerecords.TimeRecordStore
import org.isoron.uhabits.activities.timerecords.TimeRecordsActivity
import org.isoron.uhabits.utils.StyledResources
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class TimeRecordWidget(
    context: Context,
    id: Int,
    private val record: TimeRecord?
) : BaseWidget(context, id, false) {
    override val defaultHeight: Int = 80
    override val defaultWidth: Int = 80

    override fun getOnClickPendingIntent(context: Context): PendingIntent? {
        val recordId = record?.id ?: return null
        val intent = Intent(context, TimeRecordsActivity::class.java).apply {
            putExtra(TimeRecordsActivity.EXTRA_EDIT_RECORD_ID, recordId)
            // Make each widget's click target distinct beyond extras (which are
            // not part of PendingIntent identity).
            data = Uri.parse("uhabits://time-record-widget/$id/$recordId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun refreshData(widgetView: View) {
        (widgetView as DaysSinceLastCheckinView).apply {
            val item = record
            if (item == null) {
                habitName = context.getString(R.string.time_records_widget_unconfigured)
                dayCount = null
                activeColor = android.graphics.Color.WHITE
                footerColor = activeColor
                footerText = ""
            } else {
                val target = LocalDate.parse(item.date)
                val today = LocalDate.now()
                val difference = if (item.type == TimeRecord.ELAPSED) {
                    ChronoUnit.DAYS.between(target, today)
                } else {
                    ChronoUnit.DAYS.between(today, target)
                }
                habitName = item.title
                dayCount = kotlin.math.abs(difference).toInt()
                activeColor = item.color
                footerColor = if (item.type == TimeRecord.ELAPSED) {
                    0xFF2EAD67.toInt()
                } else {
                    // Vivid red for the countdown target date.
                    0xFFFF3030.toInt()
                }
                footerText = target.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }
            cardBackgroundColor = StyledResources(context).getColor(R.attr.cardBgColor)
            backgroundAlpha = preferedBackgroundAlpha
            refresh()
        }
    }

    override fun buildView(): View = DaysSinceLastCheckinView(context)
}
