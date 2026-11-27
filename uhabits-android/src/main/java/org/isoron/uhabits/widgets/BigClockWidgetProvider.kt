package org.isoron.uhabits.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.settings.BigClockActivity

/** Host-driven TextClock/Chronometer widget; no polling service or repeating alarm. */
class BigClockWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MODE) {
            val mode = intent.getStringExtra(EXTRA_MODE)?.takeIf { it in setOf("clock", "up", "down") }
            if (mode != null) {
                context.getSharedPreferences(BigClockActivity.PREFS, Context.MODE_PRIVATE)
                    .edit().putString("mode", mode).apply()
                updateAll(context)
            }
        } else super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        private const val ACTION_MODE = "org.isoron.uhabits.BIG_CLOCK_WIDGET_MODE"
        private const val EXTRA_MODE = "mode"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BigClockWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences(BigClockActivity.PREFS, Context.MODE_PRIVATE)
            val mode = prefs.getString("mode", "clock") ?: "clock"
            val views = RemoteViews(context.packageName, R.layout.widget_big_clock)
            views.setTextViewText(R.id.big_clock_mode_label, when (mode) {
                "up" -> "正计时"
                "down" -> "倒计时"
                else -> "时间"
            })
            val clockMode = mode == "clock"
            views.setViewVisibility(R.id.big_clock_wall_time, if (clockMode) android.view.View.VISIBLE else android.view.View.GONE)
            views.setViewVisibility(R.id.big_clock_chronometer, if (clockMode) android.view.View.GONE else android.view.View.VISIBLE)
            if (clockMode) {
                views.setCharSequence(R.id.big_clock_wall_time, "setFormat12Hour", "h:mm:ss a")
                views.setCharSequence(R.id.big_clock_wall_time, "setFormat24Hour", "HH:mm:ss")
            } else if (mode == "up") {
                val base = prefs.getLong("up_base", 0L)
                val running = prefs.getBoolean("up_running", false)
                val elapsed = base + if (running) (SystemClock.elapsedRealtime() - prefs.getLong("up_anchor", 0L)).coerceAtLeast(0) else 0L
                views.setChronometer(R.id.big_clock_chronometer, SystemClock.elapsedRealtime() - elapsed, "%s", running)
                views.setChronometerCountDown(R.id.big_clock_chronometer, false)
            } else {
                val base = prefs.getLong("down_base", prefs.getLong("down_duration", 0L))
                val running = prefs.getBoolean("down_running", false)
                val remaining = (base - if (running) (SystemClock.elapsedRealtime() - prefs.getLong("down_anchor", 0L)).coerceAtLeast(0) else 0L).coerceAtLeast(0)
                views.setChronometer(R.id.big_clock_chronometer, SystemClock.elapsedRealtime() + remaining, "%s", running && remaining > 0)
                views.setChronometerCountDown(R.id.big_clock_chronometer, true)
            }

            val open = PendingIntent.getActivity(
                context, id,
                Intent(context, BigClockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.big_clock_display, open)
            listOf("clock", "up", "down").forEachIndexed { index, item ->
                val intent = Intent(context, BigClockWidgetProvider::class.java).apply {
                    action = ACTION_MODE
                    putExtra(EXTRA_MODE, item)
                }
                val pending = PendingIntent.getBroadcast(
                    context, 8800 + index, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(intArrayOf(R.id.big_clock_tab_clock, R.id.big_clock_tab_up, R.id.big_clock_tab_down)[index], pending)
            }
            manager.updateAppWidget(id, views)
        }
    }
}
