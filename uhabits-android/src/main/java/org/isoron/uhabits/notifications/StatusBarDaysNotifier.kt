package org.isoron.uhabits.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.app.NotificationManagerCompat
import org.isoron.platform.time.getToday
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.habits.list.ListHabitsActivity
import org.isoron.uhabits.activities.timerecords.TimeRecord
import org.isoron.uhabits.activities.timerecords.TimeRecordStore
import org.isoron.uhabits.core.models.Entry
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min

object StatusBarDaysNotifier {
    private const val CHANNEL_ID = "STATUS_BAR_DAYS_MAX"
    private const val NOTIFICATION_ID = 7405
    private const val PREFS = "status_bar_days"
    private const val ACTION_UPDATE = "org.isoron.uhabits.action.UPDATE_STATUS_BAR_DAYS"

    fun update(context: Context) {
        val app = context.applicationContext
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(app)
        val mode = prefs.getString("status_bar_days_mode", "off") ?: "off"
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mode == "off") {
            manager.cancel(NOTIFICATION_ID)
            cancelDailyRefresh(app)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return

        val result = runCatching { resolveValue(app, mode) }.getOrNull()
        if (result == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "状态栏天数", NotificationManager.IMPORTANCE_MAX).apply {
                    description = "仅显示当前选择项目的天数"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            app, 7405, Intent(app, ListHabitsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = IconCompat.createWithBitmap(drawNumberIcon(app, result.days.toString()))
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(result.description)
            .setContentText(result.projectName)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
        scheduleDailyRefresh(app)
    }

    private data class DisplayValue(val days: Long, val description: String, val projectName: String)

    private fun resolveValue(context: Context, mode: String): DisplayValue? {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        return when (mode) {
            "checkin" -> {
                val id = prefs.getLong("status_bar_days_habit_id", -1L)
                val habit = (context.applicationContext as HabitsApplication).component.habitList.getById(id)
                    ?: return null
                val last = habit.computedEntries.getKnown()
                    .filter { it.completed && it.value != Entry.SKIP }
                    .maxByOrNull { it.date }?.date ?: return null
                DisplayValue(last.daysUntil(getToday()).toLong(), "距离上次打卡：${last.daysUntil(getToday())} 天", habit.name)
            }
            "elapsed", "countdown" -> {
                val id = prefs.getString("status_bar_days_record_id", null) ?: return null
                val record = TimeRecordStore.get(context, id) ?: return null
                val date = LocalDate.parse(record.date)
                val today = LocalDate.now()
                val delta = if (mode == "elapsed") ChronoUnit.DAYS.between(date, today)
                    else ChronoUnit.DAYS.between(today, date)
                val text = if (mode == "elapsed") "已经过去：${delta} 天"
                    else if (delta >= 0) "距离目标：还有 ${delta} 天"
                    else "目标已过：${-delta} 天"
                DisplayValue(kotlin.math.abs(delta), text, record.title)
            }
            else -> null
        }
    }

    private fun drawNumberIcon(context: Context, value: String): Bitmap {
        val size = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        var textSize = size * if (value.length <= 2) 0.76f else 0.62f
        paint.textSize = textSize
        while (paint.measureText(value) > size * 0.94f && textSize > size * 0.30f) {
            textSize -= size * 0.025f
            paint.textSize = textSize
        }
        val metrics = paint.fontMetrics
        val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(value, size / 2f, baseline, paint)
        return bitmap
    }

    private fun scheduleDailyRefresh(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, StatusBarDaysReceiver::class.java).setAction(ACTION_UPDATE)
        val pending = PendingIntent.getBroadcast(
            context, 7406, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val next = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 3)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, next, AlarmManager.INTERVAL_DAY, pending)
    }

    private fun cancelDailyRefresh(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, StatusBarDaysReceiver::class.java).setAction(ACTION_UPDATE)
        val pending = PendingIntent.getBroadcast(
            context, 7406, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.cancel(pending)
    }
}

