package org.isoron.uhabits.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.settings.BigClockActivity
import org.isoron.uhabits.widgets.BigClockWidgetProvider

/** Delivers the countdown completion reminder even when the Big Clock page is closed. */
class BigClockAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(BigClockActivity.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("down_running", false)) return
        val base = prefs.getLong("down_base", prefs.getLong("down_duration", 0L))
        val remaining = (base - (SystemClock.elapsedRealtime() - prefs.getLong("down_anchor", 0L))).coerceAtLeast(0L)
        if (remaining > 1500L) { schedule(context, remaining); return }
        prefs.edit().putLong("down_base", 0L).putBoolean("down_running", false).apply()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "大字钟提醒", NotificationManager.IMPORTANCE_HIGH))
        }
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("大字钟倒计时结束")
                .setContentText("设定的倒计时时间已到")
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("大字钟倒计时结束")
                .setContentText("设定的倒计时时间已到")
                .setAutoCancel(true)
                .build()
        }
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(android.os.VibrationEffect.createOneShot(700, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            else vibrator.vibrate(700)
        } catch (_: Exception) {}
        BigClockWidgetProvider.updateAll(context)
    }

    companion object {
        private const val CHANNEL = "big_clock_countdown"
        private const val NOTIFICATION_ID = 7320
        private const val REQUEST_CODE = 7321

        fun schedule(context: Context, delayMs: Long) {
            cancel(context)
            if (delayMs <= 0L) return
            val intent = Intent(context, BigClockAlarmReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val trigger = System.currentTimeMillis() + delayMs
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } catch (_: SecurityException) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        }

        fun cancel(context: Context) {
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, Intent(context, BigClockAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
        }
    }
}
