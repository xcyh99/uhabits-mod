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
        private val cellIds = intArrayOf(
            R.id.picture_cell_0,
            R.id.picture_cell_1,
            R.id.picture_cell_2,
            R.id.picture_cell_3,
            R.id.picture_cell_4,
            R.id.picture_cell_5,
            R.id.picture_cell_6,
            R.id.picture_cell_7,
            R.id.picture_cell_8,
            R.id.picture_cell_9,
            R.id.picture_cell_10,
            R.id.picture_cell_11,
            R.id.picture_cell_12,
            R.id.picture_cell_13,
            R.id.picture_cell_14,
            R.id.picture_cell_15,
            R.id.picture_cell_16,
            R.id.picture_cell_17,
            R.id.picture_cell_18,
            R.id.picture_cell_19,
            R.id.picture_cell_20,
            R.id.picture_cell_21,
            R.id.picture_cell_22,
            R.id.picture_cell_23,
            R.id.picture_cell_24,
            R.id.picture_cell_25,
            R.id.picture_cell_26,
            R.id.picture_cell_27,
            R.id.picture_cell_28,
            R.id.picture_cell_29,
            R.id.picture_cell_30,
            R.id.picture_cell_31,
            R.id.picture_cell_32,
            R.id.picture_cell_33,
            R.id.picture_cell_34,
            R.id.picture_cell_35,
            R.id.picture_cell_36,
            R.id.picture_cell_37,
            R.id.picture_cell_38,
            R.id.picture_cell_39,
            R.id.picture_cell_40,
            R.id.picture_cell_41,
            R.id.picture_cell_42,
            R.id.picture_cell_43,
            R.id.picture_cell_44,
            R.id.picture_cell_45,
            R.id.picture_cell_46,
            R.id.picture_cell_47,
            R.id.picture_cell_48,
            R.id.picture_cell_49,
            R.id.picture_cell_50,
            R.id.picture_cell_51,
            R.id.picture_cell_52,
            R.id.picture_cell_53,
            R.id.picture_cell_54,
            R.id.picture_cell_55,
            R.id.picture_cell_56,
            R.id.picture_cell_57,
            R.id.picture_cell_58,
            R.id.picture_cell_59,
            R.id.picture_cell_60,
            R.id.picture_cell_61,
            R.id.picture_cell_62,
            R.id.picture_cell_63,
            R.id.picture_cell_64,
            R.id.picture_cell_65,
            R.id.picture_cell_66,
            R.id.picture_cell_67,
            R.id.picture_cell_68,
            R.id.picture_cell_69,
            R.id.picture_cell_70,
            R.id.picture_cell_71,
            R.id.picture_cell_72,
            R.id.picture_cell_73,
            R.id.picture_cell_74,
            R.id.picture_cell_75,
            R.id.picture_cell_76,
            R.id.picture_cell_77,
            R.id.picture_cell_78,
            R.id.picture_cell_79,
            R.id.picture_cell_80,
            R.id.picture_cell_81,
            R.id.picture_cell_82,
            R.id.picture_cell_83,
            R.id.picture_cell_84,
            R.id.picture_cell_85,
            R.id.picture_cell_86,
            R.id.picture_cell_87,
            R.id.picture_cell_88,
            R.id.picture_cell_89,
            R.id.picture_cell_90,
            R.id.picture_cell_91,
            R.id.picture_cell_92,
            R.id.picture_cell_93,
            R.id.picture_cell_94,
            R.id.picture_cell_95,
            R.id.picture_cell_96,
            R.id.picture_cell_97,
            R.id.picture_cell_98,
            R.id.picture_cell_99
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
            val quadrantPending = targets.mapIndexed { quadrant, target ->
                if (target.isNullOrBlank()) null else PendingIntent.getActivity(
                    context, id * 10 + quadrant, targetIntent(context, target),
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
                )
            }
            cellIds.forEachIndexed { index, viewId ->
                val row = index / 10
                val column = index % 10
                val quadrant = when {
                    row < splitY && column < splitX -> 0 // top-left
                    row < splitY -> 1 // top-right
                    column < splitX -> 2 // bottom-left
                    else -> 3 // bottom-right
                }
                // A null PendingIntent leaves this cell completely inert.
                views.setOnClickPendingIntent(viewId, quadrantPending[quadrant])
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
