package org.isoron.uhabits.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.habits.list.ListHabitsActivity
import org.isoron.uhabits.activities.settings.DesktopShortcutActivity

/**
 * Lightweight home-screen clock. Both time rows use system TextClock widgets,
 * so Android/Harmony's widget host manages clock refreshes without app timers,
 * periodic alarms, or a foreground service.
 */
class DigitalClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, manager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        updateWidget(context, manager, appWidgetId)
    }

    companion object {
        fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_digital_clock)
            val slots = listOf(
                "time" to R.id.digital_clock_time,
                "time" to R.id.digital_clock_minute,
                "date" to R.id.digital_clock_date,
                "shortcut1" to R.id.digital_clock_shortcut1,
                "shortcut2" to R.id.digital_clock_shortcut2,
                "shortcut3" to R.id.digital_clock_shortcut3,
                "shortcut4" to R.id.digital_clock_shortcut4
            )
            slots.forEachIndexed { index, (slot, viewId) ->
                val value = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("digital_clock_target_$slot", null)
                // An explicit "none" means the area is intentionally inert.
                // A fresh RemoteViews instance has no click action unless one is attached.
                if (value != "none") {
                    val pending = createTargetPendingIntent(context, widgetId, index, value)
                    views.setOnClickPendingIntent(viewId, pending)
                }
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun createTargetPendingIntent(context: Context, widgetId: Int, slot: Int, value: String?): PendingIntent {
            val intent = when {
                value == null || value == "none" -> Intent(context, ListHabitsActivity::class.java)
                value.startsWith("app:") -> {
                    val component = ComponentName.unflattenFromString(value.removePrefix("app:"))
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                }
                value.startsWith("feature:") -> Intent(context, ListHabitsActivity::class.java).apply {
                    action = DesktopShortcutActivity.ACTION_OPEN_DESKTOP_SHORTCUT
                    putExtra(DesktopShortcutActivity.EXTRA_TARGET, value.removePrefix("feature:"))
                }
                else -> Intent(context, ListHabitsActivity::class.java)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            return PendingIntent.getActivity(context, widgetId * 10 + slot, intent, flags)
        }
    }
}
