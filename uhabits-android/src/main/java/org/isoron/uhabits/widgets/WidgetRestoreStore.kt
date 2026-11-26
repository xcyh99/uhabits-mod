package org.isoron.uhabits.widgets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Portable restore queue for widget-instance settings.
 *
 * The source widget ID is only a lookup key into the backup. It must never be
 * reused as the new Android widget ID allocated by the launcher.
 */
internal object WidgetRestoreStore {
    private const val PREFS = "widget_restore_queue"
    private const val PENDING = "pending_configs"
    private const val SELECTED = "selected_source_widget_id"
    private const val SELECTED_AT = "selected_source_widget_id_at"
    private const val SELECTION_TTL_MS = 10 * 60 * 1000L

    const val EXTRA_RESTORE_SOURCE_WIDGET_ID =
        "org.isoron.uhabits.extra.WIDGET_RESTORE_SOURCE_WIDGET_ID"

    data class Config(
        val sourceWidgetId: Int,
        val providerClass: String,
        val habitIds: List<Long>,
        val baselineWidgetIds: List<Int> = emptyList(),
        val type: String = "habit",
        val recordId: String? = null
    )

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pending(context: Context): List<Config> {
        val raw = preferences(context).getString(PENDING, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val sourceId = item.optInt("sourceWidgetId", -1)
                val provider = item.optString("providerClass")
                val ids = item.optJSONArray("habitIds")
                if (sourceId < 0 || provider.isBlank() || ids == null) {
                    return@mapNotNull null
                }
                Config(
                    sourceId,
                    provider,
                    (0 until ids.length()).map { ids.optLong(it, -1L) }.filter { it > 0L },
                    item.optJSONArray("baselineWidgetIds")?.let { values ->
                        (0 until values.length()).map { values.optInt(it, -1) }.filter { it >= 0 }
                    } ?: emptyList(),
                    item.optString("type", "habit"),
                    item.optString("recordId").takeIf { it.isNotBlank() }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun replacePending(context: Context, configs: List<Config>) {
        val array = JSONArray()
        configs.distinctBy { "${it.providerClass}#${it.sourceWidgetId}" }.forEach { item ->
            val ids = JSONArray()
            item.habitIds.filter { it > 0L }.forEach { ids.put(it) }
            array.put(JSONObject()
                .put("sourceWidgetId", item.sourceWidgetId)
                .put("providerClass", item.providerClass)
                .put("habitIds", ids)
                .put("baselineWidgetIds", JSONArray(item.baselineWidgetIds))
                .put("type", item.type)
                .put("recordId", item.recordId))
        }
        preferences(context).edit()
            .putString(PENDING, array.toString())
            .remove(SELECTED)
            .remove(SELECTED_AT)
            .commit()
    }

    fun mergePending(context: Context, configs: List<Config>) {
        val incomingKeys = configs.map { "${it.providerClass}#${it.sourceWidgetId}" }.toSet()
        val merged = pending(context)
            .filterNot { "${it.providerClass}#${it.sourceWidgetId}" in incomingKeys } + configs
        replacePending(context, merged)
    }

    fun setBaselineWidgetIds(context: Context, sourceWidgetId: Int, providerClass: String, ids: IntArray) {
        val updated = pending(context).map { item ->
            if (item.sourceWidgetId == sourceWidgetId && item.providerClass == providerClass) {
                item.copy(baselineWidgetIds = ids.toList())
            } else item
        }
        replacePending(context, updated)
    }

    fun find(context: Context, sourceWidgetId: Int, providerClass: String? = null): Config? =
        pending(context).firstOrNull {
            it.sourceWidgetId == sourceWidgetId &&
                (providerClass == null || it.providerClass == providerClass)
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
        if (id < 0 || selectedAt <= 0L ||
            System.currentTimeMillis() - selectedAt > SELECTION_TTL_MS
        ) {
            clearSelection(context)
            return null
        }
        return find(context, id)
    }

    fun clearSelection(context: Context, sourceWidgetId: Int? = null) {
        val prefs = preferences(context)
        if (sourceWidgetId == null || prefs.getInt(SELECTED, -1) == sourceWidgetId) {
            prefs.edit().remove(SELECTED).remove(SELECTED_AT).commit()
        }
    }

    fun consume(context: Context, sourceWidgetId: Int, providerClass: String? = null) {
        replacePending(
            context,
            pending(context).filterNot {
                it.sourceWidgetId == sourceWidgetId &&
                    (providerClass == null || it.providerClass == providerClass)
            }
        )
    }
}
