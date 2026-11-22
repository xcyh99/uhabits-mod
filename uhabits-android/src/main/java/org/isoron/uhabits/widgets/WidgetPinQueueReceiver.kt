package org.isoron.uhabits.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import org.isoron.uhabits.activities.settings.SettingsActivity

/** Resumes the selected widget queue after the launcher confirms a successful pin. */
class WidgetPinQueueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_WIDGET_PINNED) return
        val store = PreferenceManager.getDefaultSharedPreferences(context)
        val queue = store.getString("widget_pin_queue", null)?.split("|")?.filter { it.isNotBlank() }.orEmpty()
        if (queue.isEmpty()) return
        val remaining = queue.drop(1)
        if (remaining.isEmpty()) {
            store.edit().remove("widget_pin_queue").apply()
            return
        }
        store.edit().putString("widget_pin_queue", remaining.joinToString("|")).apply()
        val resume = Intent(context, SettingsActivity::class.java).apply {
            putExtra("open_preference_key", "widgetManager")
            putStringArrayListExtra("resume_widget_pin_queue", ArrayList(remaining))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(resume)
    }
    companion object { const val ACTION_WIDGET_PINNED = "org.isoron.uhabits.ACTION_WIDGET_PINNED" }
}