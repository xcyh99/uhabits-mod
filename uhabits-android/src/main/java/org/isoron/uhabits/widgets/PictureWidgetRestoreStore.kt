package org.isoron.uhabits.widgets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Portable configuration records only; image files are deliberately excluded. */
internal object PictureWidgetRestoreStore {
    private const val PREFS = "picture_widget_restore_queue"
    private const val PENDING = "pending_configs"
    private const val SELECTED = "selected_source_widget_id"
    private const val SELECTED_AT = "selected_source_widget_id_at"
    private const val SELECTION_TTL_MS = 10 * 60 * 1000L
    const val EXTRA_RESTORE_SOURCE_WIDGET_ID = "org.isoron.uhabits.extra.PICTURE_RESTORE_SOURCE_WIDGET_ID"
    data class Config(val sourceWidgetId: Int, val left: String, val right: String, val split: Int, val imageMode: String)
    private fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pending(context: Context): List<Config> {
        val raw = preferences(context).getString(PENDING, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optInt("sourceWidgetId", -1)
                if (id < 0) return@mapNotNull null
                Config(id, item.optString("left", "feature:__feature_center"),
                    item.optString("right", "feature:__settings"), item.optInt("split", 5).coerceIn(1, 9),
                    item.optString("imageMode", "fit").let { if (it in setOf("fit", "stretch", "crop")) it else "fit" })
            }
        }.getOrDefault(emptyList())
    }

    fun replacePending(context: Context, configs: List<Config>) {
        val array = JSONArray()
        configs.distinctBy { it.sourceWidgetId }.forEach { item ->
            array.put(JSONObject().put("sourceWidgetId", item.sourceWidgetId).put("left", item.left)
                .put("right", item.right).put("split", item.split.coerceIn(1, 9)).put("imageMode", item.imageMode))
        }
        preferences(context).edit().putString(PENDING, array.toString()).remove(SELECTED).remove(SELECTED_AT).commit()
    }

    fun select(context: Context, sourceWidgetId: Int) {
        preferences(context).edit()
            .putInt(SELECTED, sourceWidgetId)
            .putLong(SELECTED_AT, System.currentTimeMillis())
            .commit()
    }
    fun selected(context: Context): Config? {
        val prefs = preferences(context)
        val id = prefs.getInt(SELECTED, -1)
        val selectedAt = prefs.getLong(SELECTED_AT, 0L)
        if (id < 0 || selectedAt <= 0L || System.currentTimeMillis() - selectedAt > SELECTION_TTL_MS) {
            clearSelection(context)
            return null
        }
        return find(context, id)
    }
    fun find(context: Context, sourceWidgetId: Int): Config? =
        pending(context).firstOrNull { it.sourceWidgetId == sourceWidgetId }

    fun clearSelection(context: Context, sourceWidgetId: Int? = null) {
        val prefs = preferences(context)
        if (sourceWidgetId == null || prefs.getInt(SELECTED, -1) == sourceWidgetId) {
            prefs.edit().remove(SELECTED).remove(SELECTED_AT).commit()
        }
    }

    fun consume(context: Context, sourceWidgetId: Int) {
        replacePending(context, pending(context).filterNot { it.sourceWidgetId == sourceWidgetId })
    }
}
