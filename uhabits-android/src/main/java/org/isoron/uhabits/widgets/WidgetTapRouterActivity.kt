package org.isoron.uhabits.widgets

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.isoron.uhabits.activities.habits.show.ShowHabitActivity
import org.isoron.uhabits.activities.settings.SettingsActivity

/**
 * A transparent tap router used only by the 1x1 days-since-check-in widget.
 *
 * RemoteViews exposes click PendingIntents, not gesture callbacks. Keeping this
 * tiny Activity alive for a short window lets the launcher deliver a second
 * click to onNewIntent() without changing the widget's appearance.
 */
class WidgetTapRouterActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var habitId: Long = -1L
    private var handled = false

    private val singleTapAction = Runnable {
        if (handled || habitId < 0L || isFinishing) return@Runnable
        handled = true
        val target = Intent(this, ShowHabitActivity::class.java).apply {
            data = Uri.parse("content://org.isoron.uhabits/habit/$habitId")
        }
        // Keep the destination in this tap-router task. NEW_TASK/CLEAR_TOP
        // could expose the main activity before ShowHabitActivity on launchers
        // that reconstruct the parent task.
        startActivity(target)
        overridePendingTransition(0, 0)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        handler.postDelayed(singleTapAction, DOUBLE_TAP_WINDOW_MS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        overridePendingTransition(0, 0)
        if (handled) return
        handled = true
        handler.removeCallbacks(singleTapAction)
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .putExtra("open_preference_key", "widgetManager")
        )
        overridePendingTransition(0, 0)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        handler.removeCallbacks(singleTapAction)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_HABIT_ID = "widget_tap_router_habit_id"
        private const val DOUBLE_TAP_WINDOW_MS = 350L
    }
}
