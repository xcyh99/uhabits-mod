package org.isoron.uhabits.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import org.isoron.uhabits.activities.settings.SettingsActivity

/**
 * Continues a batch pin request after the launcher confirms the previous widget.
 * If the launcher rejects the next request, the remaining queue is shown in the
 * in-app picker as a manual fallback.
 */
class WidgetPinQueueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_WIDGET_PINNED) return

        val store = PreferenceManager.getDefaultSharedPreferences(context)
        val queue = store.getString("widget_pin_queue", null)
            ?.split("|")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (queue.isEmpty()) return

        val remaining = queue.drop(1)
        if (remaining.isEmpty()) {
            store.edit().remove("widget_pin_queue").apply()
            return
        }

        store.edit().putString("widget_pin_queue", remaining.joinToString("|")).apply()
        val nextName = remaining.first()
        val provider = runCatching {
            Class.forName(nextName).asSubclass(android.appwidget.AppWidgetProvider::class.java)
        }.getOrNull()
        val manager = AppWidgetManager.getInstance(context)
        if (provider != null && manager.isRequestPinAppWidgetSupported) {
            val callbackIntent = Intent(context, WidgetPinQueueReceiver::class.java)
                .setAction(ACTION_WIDGET_PINNED)
            val callback = PendingIntent.getBroadcast(
                context,
                nextName.hashCode(),
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val accepted = runCatching {
                manager.requestPinAppWidget(ComponentName(context, provider), null, callback)
            }.getOrDefault(false)
            if (accepted) return
        }

        // Manual continuation remains available if the launcher cannot accept
        // another pin request directly from the callback receiver.
        val resume = Intent(context, SettingsActivity::class.java).apply {
            putExtra("open_preference_key", "widgetManager")
            putStringArrayListExtra("resume_widget_pin_queue", ArrayList(remaining))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(resume) }
    }

    companion object {
        const val ACTION_WIDGET_PINNED = "org.isoron.uhabits.ACTION_WIDGET_PINNED"
    }
}
