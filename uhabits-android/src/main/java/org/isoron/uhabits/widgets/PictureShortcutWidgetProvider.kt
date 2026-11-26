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

/** User-selected photo widget with four independently configurable tap zones. */
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
                .remove(id.toString() + "_split").remove(id.toString() + "_imageMode")
                .remove(id.toString() + "_splitY")
                .remove(id.toString() + "_topLeft").remove(id.toString() + "_topRight")
                .remove(id.toString() + "_bottomLeft").remove(id.toString() + "_bottomRight").apply()
        }
    }

    companion object {
        const val PREFS = "picture_shortcut_widget_links"
        private fun key(id: Int, name: String) = "${id}_${name}"
        private val zoneIds = intArrayOf(
            R.id.picture_zone_top_left, R.id.picture_zone_top_right,
            R.id.picture_zone_bottom_left, R.id.picture_zone_bottom_right
        )

        fun save(
            context: Context,
            id: Int,
            imageName: String,
            topLeft: String,
            topRight: String,
            bottomLeft: String,
            bottomRight: String,
            splitX: Int,
            splitY: Int,
            imageMode: String = "fit"
        ) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key(id, "image"), imageName)
                // Keep legacy keys for compatibility with older app versions.
                .putString(key(id, "left"), topLeft)
                .putString(key(id, "right"), topRight)
                .putString(key(id, "topLeft"), topLeft)
                .putString(key(id, "topRight"), topRight)
                .putString(key(id, "bottomLeft"), bottomLeft)
                .putString(key(id, "bottomRight"), bottomRight)
                .putInt(key(id, "split"), splitX.coerceIn(1, 9))
                .putInt(key(id, "splitY"), splitY.coerceIn(1, 9))
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
                // RemoteViews cannot pass ImageView.ScaleType objects. Select one of
                // three ImageViews whose native scaleType is fixed in XML.
                val mode = prefs.getString(key(id, "imageMode"), "fit") ?: "fit"
                val selectedId = when (mode) {
                    "stretch" -> R.id.picture_shortcut_image_stretch
                    "crop" -> R.id.picture_shortcut_image_crop
                    else -> R.id.picture_shortcut_image
                }
                intArrayOf(
                    R.id.picture_shortcut_image,
                    R.id.picture_shortcut_image_stretch,
                    R.id.picture_shortcut_image_crop
                ).forEach { imageId ->
                    views.setImageViewBitmap(imageId, bitmap)
                    views.setViewVisibility(imageId, if (imageId == selectedId) View.VISIBLE else View.GONE)
                }
                views.setViewVisibility(R.id.picture_shortcut_empty, View.GONE)
            } else {
                views.setViewVisibility(R.id.picture_shortcut_empty, View.VISIBLE)
            }


            val splitX = prefs.getInt(key(id, "split"), 5).coerceIn(1, 9)
            val splitY = prefs.getInt(key(id, "splitY"), 5).coerceIn(1, 9)
            val legacyLeft = prefs.getString(key(id, "left"), "feature:__feature_center")
            val legacyRight = prefs.getString(key(id, "right"), "feature:__settings")
            val targets = arrayOf(
                prefs.getString(key(id, "topLeft"), null) ?: legacyLeft,
                prefs.getString(key(id, "topRight"), null) ?: legacyRight,
                prefs.getString(key(id, "bottomLeft"), null) ?: legacyLeft,
                prefs.getString(key(id, "bottomRight"), null) ?: legacyRight
            )
            zoneIds.forEachIndexed { index, viewId ->
                val target = targets[index].orEmpty()
                if (target.isBlank()) {
                    // An unconfigured quadrant is intentionally inert.
                    views.setOnClickPendingIntent(viewId, null)
                } else {
                    val pending = PendingIntent.getActivity(
                        context, id * 100 + index, targetIntent(context, target),
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
                    )
                    views.setOnClickPendingIntent(viewId, pending)
                }
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
                target.startsWith("pictureWidgetSettings:") -> {
                    val widgetId = target.substringAfter(":").toIntOrNull()
                    if (widgetId != null) Intent(context, PictureShortcutWidgetConfigureActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    else Intent(context, ListHabitsActivity::class.java)
                }
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
