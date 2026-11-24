package org.isoron.uhabits.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.widget.RemoteViews
import androidx.preference.PreferenceManager
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.habits.list.ListHabitsActivity
import org.isoron.uhabits.activities.settings.DesktopShortcutActivity
import org.isoron.uhabits.activities.settings.FeatureShortcutCatalog

/** Native TextClock remains host-managed; no timer or foreground service is used. */
class DigitalClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, manager, appWidgetIds)
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        updateWidget(context, manager, appWidgetId)
    }
    companion object {
        private val containers = intArrayOf(
            R.id.digital_clock_shortcut1, R.id.digital_clock_shortcut2, R.id.digital_clock_shortcut3, R.id.digital_clock_shortcut4,
            R.id.digital_clock_shortcut5, R.id.digital_clock_shortcut6, R.id.digital_clock_shortcut7, R.id.digital_clock_shortcut8
        )
        private val icons = intArrayOf(
            R.id.digital_clock_shortcut1_icon, R.id.digital_clock_shortcut2_icon, R.id.digital_clock_shortcut3_icon, R.id.digital_clock_shortcut4_icon,
            R.id.digital_clock_shortcut5_icon, R.id.digital_clock_shortcut6_icon, R.id.digital_clock_shortcut7_icon, R.id.digital_clock_shortcut8_icon
        )
        private val labels = intArrayOf(
            R.id.digital_clock_shortcut1_label, R.id.digital_clock_shortcut2_label, R.id.digital_clock_shortcut3_label, R.id.digital_clock_shortcut4_label,
            R.id.digital_clock_shortcut5_label, R.id.digital_clock_shortcut6_label, R.id.digital_clock_shortcut7_label, R.id.digital_clock_shortcut8_label
        )
        fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { updateWidget(context, manager, it) }
        }
        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val views = RemoteViews(context.packageName, R.layout.widget_digital_clock)
            views.setTextViewTextSize(R.id.digital_clock_time, android.util.TypedValue.COMPLEX_UNIT_SP, prefs.getFloat("digital_clock_hour_size", 48f))
            views.setTextViewTextSize(R.id.digital_clock_minute, android.util.TypedValue.COMPLEX_UNIT_SP, prefs.getFloat("digital_clock_minute_size", 48f))
            views.setTextViewTextSize(R.id.digital_clock_date, android.util.TypedValue.COMPLEX_UNIT_SP, prefs.getFloat("digital_clock_date_size", 15f))
            views.setTextColor(R.id.digital_clock_time, prefs.getInt("digital_clock_time_color", Color.WHITE))
            views.setTextColor(R.id.digital_clock_minute, prefs.getInt("digital_clock_time_color", Color.WHITE))
            views.setTextColor(R.id.digital_clock_date, prefs.getInt("digital_clock_date_color", Color.LTGRAY))
            val dateFormat = prefs.getString("digital_clock_date_format", "M月d日  EEEE")
            views.setCharSequence(R.id.digital_clock_date, "setFormat12Hour", dateFormat)
            views.setCharSequence(R.id.digital_clock_date, "setFormat24Hour", dateFormat)
            when (prefs.getInt("digital_clock_time_mode", 0)) {
                1 -> {
                    views.setCharSequence(R.id.digital_clock_time, "setFormat12Hour", "h")
                    views.setCharSequence(R.id.digital_clock_time, "setFormat24Hour", "h")
                }
                2 -> {
                    views.setCharSequence(R.id.digital_clock_time, "setFormat12Hour", "HH")
                    views.setCharSequence(R.id.digital_clock_time, "setFormat24Hour", "HH")
                }
                else -> {
                    views.setCharSequence(R.id.digital_clock_time, "setFormat12Hour", "h")
                    views.setCharSequence(R.id.digital_clock_time, "setFormat24Hour", "HH")
                }
            }
            val bg = prefs.getInt("digital_clock_bg_color", Color.rgb(35, 45, 60))
            val alpha = prefs.getInt("digital_clock_bg_alpha", 90).coerceIn(0, 100) * 255 / 100
            views.setInt(R.id.digital_clock_widget_root, "setBackgroundColor", Color.argb(alpha, Color.red(bg), Color.green(bg), Color.blue(bg)))
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val options = manager.getAppWidgetOptions(widgetId)
                val totalDp = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180) - 15).coerceAtLeast(60)
                val totalPx = (totalDp * context.resources.displayMetrics.density).toInt()
                val shortcutCount = prefs.getInt("digital_clock_shortcut_count", 4).coerceIn(0, 8)
                val weights = mutableListOf(
                    prefs.getInt("digital_clock_hour_weight", 115).coerceIn(20, 250),
                    prefs.getInt("digital_clock_minute_weight", 115).coerceIn(20, 250),
                    prefs.getInt("digital_clock_date_weight", 65).coerceIn(20, 250)
                )
                if (shortcutCount > 0) weights.add(prefs.getInt("digital_clock_shortcut_weight", 90).coerceIn(20, 250))
                val rowIds = mutableListOf(R.id.digital_clock_time, R.id.digital_clock_minute, R.id.digital_clock_date)
                if (shortcutCount > 0) rowIds.add(R.id.digital_clock_shortcut_row)
                val sum = weights.sum().coerceAtLeast(1)
                rowIds.forEachIndexed { i, viewId ->
                    views.setViewLayoutHeight(viewId, (totalPx * weights[i] / sum).coerceAtLeast(1))
                }
            }
            val count = prefs.getInt("digital_clock_shortcut_count", 4).coerceIn(0, 8)
            views.setViewVisibility(R.id.digital_clock_shortcut_row, if (count == 0) android.view.View.GONE else android.view.View.VISIBLE)
            for (i in containers.indices) {
                val container = containers[i]
                if (i >= count) {
                    views.setViewVisibility(container, android.view.View.GONE)
                    continue
                }
                views.setViewVisibility(container, android.view.View.VISIBLE)
                val target = prefs.getString("digital_clock_target_shortcut" + (i + 1), null)
                val resolved = resolveShortcut(context, target, i + 1)
                views.setTextViewText(labels[i], resolved.first)
                if (resolved.second != null) views.setImageViewBitmap(icons[i], resolved.second)
                if (target != "none") {
                    views.setOnClickPendingIntent(container, createTargetPendingIntent(context, widgetId, i + 2, target))
                }
            }
            val timeTarget = prefs.getString("digital_clock_target_time", null)
            if (timeTarget != "none") {
                val timePending = createTargetPendingIntent(context, widgetId, 0, timeTarget)
                views.setOnClickPendingIntent(R.id.digital_clock_time, timePending)
                views.setOnClickPendingIntent(R.id.digital_clock_minute, timePending)
            }
            val dateTarget = prefs.getString("digital_clock_target_date", null)
            if (dateTarget != "none") views.setOnClickPendingIntent(R.id.digital_clock_date, createTargetPendingIntent(context, widgetId, 1, dateTarget))
            manager.updateAppWidget(widgetId, views)
        }
        private fun resolveShortcut(context: Context, value: String?, index: Int): Pair<String, Bitmap?> {
            if (value.isNullOrBlank() || value == "none") return "快捷$index" to null
            if (value.startsWith("app:")) {
                val component = ComponentName.unflattenFromString(value.removePrefix("app:"))
                if (component != null) {
                    val resolved = runCatching {
                        val info = context.packageManager.getActivityInfo(component, 0)
                        info.loadLabel(context.packageManager).toString() to drawableBitmap(info.loadIcon(context.packageManager))
                    }.getOrNull()
                    if (resolved != null) return resolved
                }
            }
            if (value.startsWith("feature:")) {
                val key = value.removePrefix("feature:")
                val raw = FeatureShortcutCatalog.getEntries(context).firstOrNull { it.key == key }?.title ?: "功能"
                return raw.replace(Regex("^【[^】]+】\\s*"), "") to null
            }
            return "快捷$index" to null
        }
        private fun drawableBitmap(drawable: Drawable): Bitmap {
            val w = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(96)
            val h = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(96)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            return bitmap
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
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            return PendingIntent.getActivity(context, widgetId * 100 + slot, intent, flags)
        }
    }
}
