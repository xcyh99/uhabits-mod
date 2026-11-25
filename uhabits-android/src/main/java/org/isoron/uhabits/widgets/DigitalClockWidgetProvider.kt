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
import kotlin.math.roundToInt
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
        fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { updateWidget(context, manager, it) }
        }
        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val views = RemoteViews(context.packageName, R.layout.widget_digital_clock)
            val rowBorderEnabled = prefs.getBoolean("digital_clock_row_border_enabled", true)
            val autoLayout = prefs.getInt("digital_clock_layout_mode", 0) == 1
            val keepDigitRatio = prefs.getBoolean("digital_clock_keep_digit_ratio", true)
            val density = context.resources.displayMetrics.density
            val scaledDensity = context.resources.displayMetrics.scaledDensity
            val rowHeights = intArrayOf(
                prefs.getInt("digital_clock_hour_height", 48),
                prefs.getInt("digital_clock_minute_height", 48),
                prefs.getInt("digital_clock_date_height", 27),
                prefs.getInt("digital_clock_shortcut_height", 37)
            ).map { it.coerceAtLeast(0) }
            // Row height is a clipping viewport, never a font-size ceiling.
            // Both layout modes preserve the exact user-selected font size.
            val hourSize = prefs.getFloat("digital_clock_hour_size", 48f).coerceIn(1f, 300f)
            val minuteSize = prefs.getFloat("digital_clock_minute_size", 48f).coerceIn(1f, 300f)
            val dateSize = prefs.getFloat("digital_clock_date_size", 15f).coerceIn(1f, 300f)
            views.setTextViewTextSize(R.id.digital_clock_time, android.util.TypedValue.COMPLEX_UNIT_SP, hourSize)
            views.setTextViewTextSize(R.id.digital_clock_minute, android.util.TypedValue.COMPLEX_UNIT_SP, minuteSize)
            views.setTextViewTextSize(R.id.digital_clock_date, android.util.TypedValue.COMPLEX_UNIT_SP, dateSize)

            val widthDp = manager.getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110).coerceAtLeast(80)
            fun autoDigitWidth(sizeSp: Float): Float {
                val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = sizeSp * scaledDensity
                    typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                }
                val availablePx = widthDp.coerceAtLeast(48) * density
                val naturalPx = paint.measureText("88").coerceAtLeast(1f)
                return (availablePx / naturalPx).coerceIn(0.72f, 1.25f)
            }
            // Keep manual spacing/transforms intact; auto mode applies a safe spacing floor
            // and optionally adapts digit width to the actual widget width.
            val textRows = listOf(
                Triple(R.id.digital_clock_time, "hour", -0.04f),
                Triple(R.id.digital_clock_minute, "minute", -0.04f),
                Triple(R.id.digital_clock_date, "date", -0.04f)
            )
            textRows.forEachIndexed { index, (textId, rowKey, defaultSpacing) ->
                val savedSpacing = prefs.getFloat("digital_clock_${rowKey}_letter_spacing", defaultSpacing)
                    .coerceIn(-0.20f, 0.20f)
                val letterSpacing = if (autoLayout) savedSpacing.coerceAtLeast(-0.12f) else savedSpacing
                val transformEnabled = prefs.getBoolean("digital_clock_${rowKey}_transform_enabled", false)
                val manualScale = if (transformEnabled) prefs.getFloat("digital_clock_${rowKey}_horizontal_scale", 0.90f).coerceIn(0.60f, 1.20f) else 1f
                val horizontalScale = when {
                    !autoLayout -> manualScale
                    index == 2 || keepDigitRatio -> 1f
                    index == 0 -> autoDigitWidth(hourSize)
                    else -> autoDigitWidth(minuteSize)
                }
                views.setFloat(textId, "setLetterSpacing", letterSpacing)
                // Horizontal adaptation never changes the fixed row height.
                views.setFloat(textId, "setTextScaleX", horizontalScale)
            }
            views.setTextColor(R.id.digital_clock_time, prefs.getInt("digital_clock_hour_color", prefs.getInt("digital_clock_time_color", Color.WHITE)))
            views.setTextColor(R.id.digital_clock_minute, prefs.getInt("digital_clock_minute_color", prefs.getInt("digital_clock_time_color", Color.WHITE)))
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
            val shortcutCount = prefs.getInt("digital_clock_shortcut_count", 4).coerceIn(0, 8)
            val textIds = intArrayOf(
                R.id.digital_clock_time,
                R.id.digital_clock_minute,
                R.id.digital_clock_date
            )
            val viewportIds = intArrayOf(
                R.id.digital_clock_time_viewport,
                R.id.digital_clock_minute_viewport,
                R.id.digital_clock_date_viewport
            )
            val rowIds = intArrayOf(
                viewportIds[0],
                viewportIds[1],
                viewportIds[2],
                R.id.digital_clock_shortcut_row
            )
            rowIds.forEach { viewId ->
                views.setInt(viewId, "setBackgroundResource", if (rowBorderEnabled) R.drawable.digital_clock_row_border else android.R.color.transparent)
            }
            val offsets = intArrayOf(
                prefs.getInt("digital_clock_hour_offset_y", 0),
                prefs.getInt("digital_clock_minute_offset_y", 0),
                prefs.getInt("digital_clock_date_offset_y", 0),
                prefs.getInt("digital_clock_shortcut_offset_y", 0)
            )
            val heights = rowHeights
            val naturalHeightsDp = floatArrayOf(
                hourSize * scaledDensity / density * 1.05f,
                minuteSize * scaledDensity / density * 1.05f,
                dateSize * scaledDensity / density * 1.05f,
                37f
            )
            rowIds.forEachIndexed { index, viewId ->
                val rowHeight = heights[index]
                val offsetPx = (offsets[index].coerceIn(-40, 40) * density).roundToInt()
                // B2: the outer FrameLayout is the fixed clipping viewport.
                // The TextClock is deliberately taller than it and centered inside it,
                // so the TextClock's own bounds no longer become the crop edge.
                // Signed padding retains B1's proven movement on Android 10 and newer.
                val offsetTopPadding = if (index < 3) offsetPx else (offsetPx * 2).coerceAtLeast(0)
                val offsetBottomPadding = if (index < 3) -offsetPx else (-offsetPx * 2).coerceAtLeast(0)
                views.setViewVisibility(viewId, if (rowHeight == 0 || (index == 3 && shortcutCount == 0)) android.view.View.GONE else android.view.View.VISIBLE)
                if (index < 3) {
                    val textId = textIds[index]
                    // TextClock is taller than the viewport. Its fixed -40dp top/bottom
                    // margins make the wrap-content FrameLayout measure to rowHeight,
                    // while centering the oversized child. This works on API 28+ without
                    // RemoteViews.setViewLayoutHeight (API 31+ only).
                    val expandedTextHeightPx = ((maxOf(rowHeight, naturalHeightsDp[index]) + 80f) * density).roundToInt()
                    views.setInt(textId, "setHeight", expandedTextHeightPx)
                    views.setViewPadding(textId, 0, offsetTopPadding, 0, offsetBottomPadding)
                } else if (android.os.Build.VERSION.SDK_INT >= 31) {
                    views.setViewLayoutHeight(viewId, rowHeight.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                    val extraPaddingPx = (((rowHeight - naturalHeightsDp[index]).coerceAtLeast(0f) / 2f) * density).toInt()
                    views.setViewPadding(viewId, 0, extraPaddingPx + offsetTopPadding, 0, extraPaddingPx + offsetBottomPadding)
                } else {
                    val extraPaddingPx = (((rowHeight - naturalHeightsDp[index]).coerceAtLeast(0f) / 2f) * density).toInt()
                    views.setViewPadding(viewId, 0, extraPaddingPx + offsetTopPadding, 0, extraPaddingPx + offsetBottomPadding)
                }
            }
            val count = prefs.getInt("digital_clock_shortcut_count", 4).coerceIn(0, 8)
            views.setViewVisibility(R.id.digital_clock_shortcut_row, if (count == 0) android.view.View.GONE else android.view.View.VISIBLE)
            if (android.os.Build.VERSION.SDK_INT >= 31 && count > 0) {
                val options = manager.getAppWidgetOptions(widgetId)
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110).coerceAtLeast(80)
                val rowHeight = rowHeights[3].coerceAtLeast(16)
                val iconDp = minOf((widthDp / count - 4).coerceAtLeast(12), (rowHeight - 2).coerceAtLeast(12)).coerceIn(12, 64)
                icons.forEach { iconId ->
                    views.setViewLayoutWidth(iconId, iconDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                    views.setViewLayoutHeight(iconId, iconDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                }
            }
            for (i in containers.indices) {
                val container = containers[i]
                if (i >= count) {
                    views.setViewVisibility(container, android.view.View.GONE)
                    continue
                }
                views.setViewVisibility(container, android.view.View.VISIBLE)
                val gravity = when (i) {
                    0 -> android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                    count - 1 -> android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
                    else -> android.view.Gravity.CENTER
                }
                views.setInt(container, "setGravity", gravity)
                val target = prefs.getString("digital_clock_target_shortcut" + (i + 1), null)
                val resolved = resolveShortcut(context, target, i + 1)
                resolved.second?.let { views.setImageViewBitmap(icons[i], it) }
                if (target != "none") {
                    views.setOnClickPendingIntent(container, createTargetPendingIntent(context, widgetId, i + 2, target))
                }
            }
            // Keep the former shared time target as a fallback for existing installations.
            val legacyTimeTarget = prefs.getString("digital_clock_target_time", null)
            val hourTarget = prefs.getString("digital_clock_target_hour", legacyTimeTarget)
            val minuteTarget = prefs.getString("digital_clock_target_minute", legacyTimeTarget)
            if (hourTarget != "none") {
                views.setOnClickPendingIntent(R.id.digital_clock_time, createTargetPendingIntent(context, widgetId, 0, hourTarget))
            }
            if (minuteTarget != "none") {
                views.setOnClickPendingIntent(R.id.digital_clock_minute, createTargetPendingIntent(context, widgetId, 1, minuteTarget))
            }
            val dateTarget = prefs.getString("digital_clock_target_date", null)
            if (dateTarget != "none") views.setOnClickPendingIntent(R.id.digital_clock_date, createTargetPendingIntent(context, widgetId, 2, dateTarget))
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
            if (value == "system:settings") return "系统设置" to drawableBitmap(context.getDrawable(android.R.drawable.ic_menu_manage)!!)
            if (value.startsWith("feature:")) {
                val key = value.removePrefix("feature:")
                val entry = FeatureShortcutCatalog.getCachedEntry(context, key)
                val icon = entry?.icon?.let(::drawableBitmap)
                    ?: drawableBitmap(context.getDrawable(android.R.drawable.ic_menu_view)!!)
                return (entry?.name ?: "功能") to icon
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
                value == "system:settings" -> Intent(android.provider.Settings.ACTION_SETTINGS)
                value.startsWith("feature:") -> {
                    val key = value.removePrefix("feature:")
                    when (key) {
                        // These destinations have their own Activity. Open them directly,
                        // without starting ListHabitsActivity as a forwarding trampoline.
                        "desktopWidgetSettings" -> Intent(context, org.isoron.uhabits.activities.settings.DesktopWidgetSettingsActivity::class.java)
                        "timeRecords" -> Intent(context, org.isoron.uhabits.activities.timerecords.TimeRecordsActivity::class.java)
                        "puzzleGames" -> Intent(context, org.isoron.uhabits.activities.games.PuzzleGamesActivity::class.java)
                        "__feature_center" -> Intent(context, org.isoron.uhabits.activities.settings.SettingsActivity::class.java)
                            .putExtra("open_feature_center", true)
                        "__settings" -> Intent(context, org.isoron.uhabits.activities.settings.SettingsActivity::class.java)
                        // SettingsFragment still handles the selected preference's existing
                        // click action, but the main habit-list Activity is no longer launched.
                        else -> Intent(context, org.isoron.uhabits.activities.settings.SettingsActivity::class.java)
                            .putExtra("open_preference_key", key)
                    }
                }
                else -> Intent(context, ListHabitsActivity::class.java)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            return PendingIntent.getActivity(context, widgetId * 100 + slot, intent, flags)
        }
    }
}
