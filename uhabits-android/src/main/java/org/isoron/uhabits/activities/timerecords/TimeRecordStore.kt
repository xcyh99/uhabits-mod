package org.isoron.uhabits.activities.timerecords

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TimeRecord(
    val id: String,
    val title: String,
    val type: String,
    val date: String
) {
    companion object {
        const val ELAPSED = "elapsed"
        const val COUNTDOWN = "countdown"
    }
}

object TimeRecordStore {
    private const val PREFS = "time_records_data"
    private const val RECORDS_KEY = "records"

    fun getAll(context: Context): List<TimeRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(RECORDS_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val type = item.optString("type")
                    if (type == TimeRecord.ELAPSED || type == TimeRecord.COUNTDOWN) {
                        add(
                            TimeRecord(
                                item.getString("id"),
                                item.getString("title"),
                                type,
                                item.getString("date")
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun get(context: Context, id: String): TimeRecord? =
        getAll(context).firstOrNull { it.id == id }

    fun save(context: Context, record: TimeRecord) {
        val records = getAll(context).filterNot { it.id == record.id } + record
        val array = JSONArray()
        records.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("type", item.type)
                    .put("date", item.date)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(RECORDS_KEY, array.toString())
            .apply()
        org.isoron.uhabits.notifications.StatusBarDaysNotifier.update(context)
        org.isoron.uhabits.widgets.TimeRecordWidgetProvider.updateAll(context)
    }

    fun delete(context: Context, id: String) {
        val records = getAll(context).filterNot { it.id == id }
        val array = JSONArray()
        records.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("type", item.type)
                    .put("date", item.date)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(RECORDS_KEY, array.toString())
            .apply()
        org.isoron.uhabits.notifications.StatusBarDaysNotifier.update(context)
    }
}
