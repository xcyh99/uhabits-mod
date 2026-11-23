package org.isoron.uhabits.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.graphics.drawable.Icon
import androidx.core.app.NotificationManagerCompat
import org.isoron.platform.time.getToday
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.timerecords.TimeRecord
import org.isoron.uhabits.activities.timerecords.TimeRecordStore
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.intents.IntentFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object StatusBarDaysNotifier {
    private const val CHANNEL_ID = "STATUS_BAR_DAYS_V3"
    private const val NOTIFICATION_ID = 7405
    private const val PREFS = "status_bar_days"
    private const val ACTION_UPDATE = "org.isoron.uhabits.action.UPDATE_STATUS_BAR_DAYS"

    fun update(context: Context) {
        val app = context.applicationContext
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(app)
        val mode = prefs.getString("status_bar_days_mode", "off") ?: "off"
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val previousIds = prefs.getStringSet("status_bar_days_active_ids", emptySet())
            ?.mapNotNull { it.toIntOrNull() }.orEmpty().toSet()
        if (mode == "off") {
            previousIds.forEach { manager.cancel(it) }
            manager.cancel(NOTIFICATION_ID)
            prefs.edit().putStringSet("status_bar_days_active_ids", emptySet()).apply()
            cancelDailyRefresh(app)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return

        // New multi-item configuration; if absent, retain compatibility with the old single-item setting.
        val configured = prefs.getStringSet("status_bar_days_items", emptySet()).orEmpty()
        val items = if (configured.isNotEmpty()) {
            configured.mapNotNull { entry ->
                val split = entry.split(":", limit = 2)
                if (split.size == 2 && split[0] in setOf("checkin", "countdown", "elapsed")) {
                    split[0] to split[1]
                } else null
            }
        } else {
            listOf(mode to "")
        }
        if (items.isEmpty()) {
            previousIds.forEach { manager.cancel(it) }
            manager.cancel(NOTIFICATION_ID)
            prefs.edit().putStringSet("status_bar_days_active_ids", emptySet()).apply()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "状态栏天数", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "分别显示已选择项目的天数"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }

        val activeIds = mutableSetOf<Int>()
        items.forEach { (itemMode, itemId) ->
            val result = runCatching { resolveValue(app, itemMode, itemId.ifBlank { null }) }.getOrNull()
                ?: return@forEach
            val notificationId = if (itemId.isBlank()) NOTIFICATION_ID
                else 7406 + (("${itemMode}:${itemId}".hashCode() and 0x7fffffff) % 100000)
            activeIds.add(notificationId)
            val contentIntent = if (itemMode == "checkin" && result.habitId != null) {
                IntentFactory().startShowHabitActivity(app, result.habitId).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                Intent(app, org.isoron.uhabits.activities.habits.list.ListHabitsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            val openApp = PendingIntent.getActivity(
                app, notificationId, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Try the dynamic day-number icon first. Some Android-derived systems may
            // reject or fail to render bitmap small icons; never leave an older arrow icon
            // silently stuck in the notification shade in that case.
            val posted = runCatching {
                val smallIcon = Icon.createWithBitmap(createDaysBitmap(result.days, result.color))
                android.util.Log.d("StatusBarDaysNotifier", "Publishing native bitmap small icon, type=${smallIcon.type}")
                val notification = android.app.Notification.Builder(app, CHANNEL_ID)
                    .setSmallIcon(smallIcon)
                    .setContentTitle(result.description)
                    .setContentText(result.projectName)
                    .setContentIntent(openApp)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setPriority(android.app.Notification.PRIORITY_MAX)
                    .setCategory(android.app.Notification.CATEGORY_SERVICE)
                    .build()
                manager.notify(notificationId, notification)
            }.onFailure {
                android.util.Log.e("StatusBarDaysNotifier", "Unable to post day-number notification", it)
            }.isSuccess

            if (posted) {
                activeIds.add(notificationId)
            } else {
                // The bundled notification resource is the current Taiji glyph, not the
                // legacy curved arrow. Use it as a safe fallback if bitmap icons fail.
                val fallbackPosted = runCatching {
                    val fallback = android.app.Notification.Builder(app, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(result.description)
                        .setContentText(result.projectName)
                        .setContentIntent(openApp)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setShowWhen(false)
                        .setPriority(android.app.Notification.PRIORITY_MAX)
                        .setCategory(android.app.Notification.CATEGORY_SERVICE)
                        .build()
                    manager.notify(notificationId, fallback)
                }.onFailure {
                    android.util.Log.e("StatusBarDaysNotifier", "Unable to post fallback notification", it)
                }.isSuccess

                if (fallbackPosted) activeIds.add(notificationId)
                else manager.cancel(notificationId)
            }
        }
        (previousIds - activeIds).forEach { manager.cancel(it) }
        if (activeIds.none { it == NOTIFICATION_ID }) manager.cancel(NOTIFICATION_ID)
        prefs.edit().putStringSet("status_bar_days_active_ids", activeIds.map { it.toString() }.toSet()).apply()
        if (activeIds.isEmpty()) cancelDailyRefresh(app) else scheduleDailyRefresh(app)
    }


    // Fill the available icon area with the day number. Each digit count is fitted
    // independently in both axes, allowing multi-digit labels to become very narrow.
    private fun createDaysBitmap(days: Long, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = 100f
        }
        val label = days.coerceAtMost(9999).toString()
        val bounds = android.graphics.Rect()
        paint.getTextBounds(label, 0, label.length, bounds)

        if (bounds.width() > 0 && bounds.height() > 0) {
            val targetSize = 96f
            canvas.translate(48f, 48f)
            canvas.scale(
                targetSize / bounds.width().toFloat(),
                targetSize / bounds.height().toFloat()
            )
            canvas.drawText(
                label,
                -bounds.exactCenterX(),
                -bounds.exactCenterY(),
                paint
            )
        }
        return bitmap
    }

    private data class DisplayValue(
        val days: Long,
        val description: String,
        val projectName: String,
        val habitId: Long? = null,
        val color: Int = android.graphics.Color.WHITE
    )

    private fun resolveValue(context: Context, mode: String, selectedId: String? = null): DisplayValue? {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        return when (mode) {
            "checkin" -> {
                val id = selectedId?.toLongOrNull() ?: prefs.getLong("status_bar_days_habit_id", -1L)
                val habit = (context.applicationContext as HabitsApplication).component.habitList.getById(id)
                    ?: return null
                val last = habit.computedEntries.getKnown()
                    .filter { it.completed && it.value != Entry.SKIP }
                    .maxByOrNull { it.date }?.date ?: return null
                DisplayValue(
                    last.daysUntil(getToday()).toLong(),
                    "距离上次打卡：${last.daysUntil(getToday())} 天",
                    habit.name,
                    id,
                    habit.color.argb
                )
            }
            "elapsed", "countdown" -> {
                val id = selectedId ?: prefs.getString("status_bar_days_record_id", null) ?: return null
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

    private fun scheduleDailyRefresh(context: Context) {
        val app = context.applicationContext as HabitsApplication
        val delayHours = app.component.preferences.midnightDelayHours
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, StatusBarDaysReceiver::class.java).setAction(ACTION_UPDATE)
        val pending = PendingIntent.getBroadcast(
            context, 7406, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Habits may use a delayed day boundary (for example, 03:00 instead of 00:00).
        // Schedule the next refresh just after that boundary, not at natural midnight.
        val now = System.currentTimeMillis()
        val next = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, delayHours)
            set(java.util.Calendar.MINUTE, 3)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        // A one-shot alarm is recalculated after every refresh, so preference changes
        // and daylight-saving/time-zone changes do not leave a stale repeating schedule.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, next, pending)
        }
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

