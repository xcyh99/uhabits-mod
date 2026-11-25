package org.isoron.uhabits.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.games.PuzzleGamesActivity
import org.isoron.uhabits.activities.habits.list.ListHabitsActivity
import org.isoron.uhabits.activities.settings.DesktopWidgetSettingsActivity
import org.isoron.uhabits.activities.settings.FeatureShortcutCatalog
import org.isoron.uhabits.activities.timerecords.TimeRecordsActivity
import java.io.File

/** User-selected photo widget with ten tap segments for a draggable left/right split. */
class PictureShortcutWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateWidget(context, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        appWidgetIds.forEach { id ->
            prefs.getString(key(id, "image"), null)?.takeIf { it.isNotBlank() }?.let { File(context.filesDir, it).delete() }
            prefs.edit().remove(id.toString() + "_image")
                .remove(id.toString() + "_left").remove(id.toString() + "_right")
                .remove(id.toString() + "_split").apply()
        }
    }

    companion object {
        const val PREFS = "picture_shortcut_widget_links"
        private fun key(id: Int, name: String) = "${id}_${name}"
        private val segmentIds = intArrayOf(
            R.id.picture_zone_0, R.id.picture_zone_1, R.id.picture_zone_2,
            R.id.picture_zone_3, R.id.picture_zone_4, R.id.picture_zone_5,
            R.id.picture_zone_6, R.id.picture_zone_7, R.id.picture_zone_8,
            R.id.picture_zone_9
        )

        fun save(context: Context, id: Int, imageName: String, left: String, right: String, split: Int, imageMode: String = "fit") {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key(id, "image"), imageName)
                .putString(key(id, "left"), left)
                .putString(key(id, "right"), right)
                .putInt(key(id, "split"), split.coerceIn(1, 9))
                .putString(key(id, "imageMode"), imageMode)
                .apply()
        }

        fun updateWidget(context: Context, id: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.widget_picture_shortcut)
            val imageName = prefs.getString(key(id, "image"), null)
            val imageFile = imageName?.let { File(context.filesDir, it) }
            val bitmap = imageFile?.takeIf { it.isFile }?.let {
                runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull()
            }
            if (bitmap != null) {
                // Let Android scale against the ImageView's actual on-screen bounds.
                // Legacy width/height values are no longer supported and safely fall back to FIT_CENTER.
                val mode = prefs.getString(key(id, "imageMode"), "fit") ?: "fit"
                val scaleType = when (mode) {
                    "stretch" -> android.widget.ImageView.ScaleType.FIT_XY
                    "crop" -> android.widget.ImageView.ScaleType.CENTER_CROP
                    else -> android.widget.ImageView.ScaleType.FIT_CENTER
                }
                views.setImageViewBitmap(R.id.picture_shortcut_image, bitmap)
                views.setImageViewScaleType(R.id.picture_shortcut_image, scaleType)
                views.setViewVisibility(R.id.picture_shortcut_empty, View.GONE)
            } else {
                views.setViewVisibility(R.id.picture_shortcut_empty, View.VISIBLE)
            }

            val split = prefs.getInt(key(id, "split"), 5).coerceIn(1, 9)
            val left = prefs.getString(key(id, "left"), "feature:__feature_center")
            val right = prefs.getString(key(id, "right"), "feature:__settings")
            val leftIntent = targetIntent(context, left)
            val rightIntent = targetIntent(context, right)
            segmentIds.forEachIndexed { index, viewId ->
                val pending = PendingIntent.getActivity(
                    context, id * 100 + index,
                    if (index < split) leftIntent else rightIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
                )
                views.setOnClickPendingIntent(viewId, pending)
            }
            manager.updateAppWidget(id, views)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PictureShortcutWidgetProvider::class.java))
            ids.forEach { updateWidget(context, it) }
        }

        private fun targetIntent(context: Context, value: String?): Intent {
            val target = value.orEmpty()
            val intent = when {
                target.startsWith("app:") -> {
                    val component = ComponentName.unflattenFromString(target.removePrefix("app:"))
                    if (component != null) Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                    else Intent(context, ListHabitsActivity::class.java)
                }
                target == "system:settings" -> Intent(android.provider.Settings.ACTION_SETTINGS)
                target.startsWith("feature:") -> {
                    val key = target.removePrefix("feature:")
                    FeatureShortcutCatalog.createIntent(context, key) ?: when (key) {
                        "timeRecords" -> Intent(context, TimeRecordsActivity::class.java)
                        "puzzleGames" -> Intent(context, PuzzleGamesActivity::class.java)
                        "desktopWidgetSettings" -> Intent(context, DesktopWidgetSettingsActivity::class.java)
                        "__settings" -> Intent(context, org.isoron.uhabits.activities.settings.SettingsActivity::class.java)
                        "__feature_center" -> Intent(context, org.isoron.uhabits.activities.settings.SettingsActivity::class.java)
                            .putExtra("open_feature_center", true)
                        else -> Intent(context, org.isoron.uhabits.activities.settings.SettingsActivity::class.java)
                            .putExtra("open_preference_key", key)
                    }
                }
                else -> Intent(context, ListHabitsActivity::class.java)
            }
            return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}
