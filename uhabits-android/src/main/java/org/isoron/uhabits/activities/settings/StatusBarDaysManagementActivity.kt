package org.isoron.uhabits.activities.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.activities.timerecords.TimeRecordStore
import org.isoron.uhabits.notifications.StatusBarDaysNotifier
import org.isoron.uhabits.utils.StyledResources
import org.json.JSONArray

/** One row per selected habit or time record; rows are intentionally not grouped. */
class StatusBarDaysManagementActivity : AppCompatActivity() {
    private data class Row(val key: String, val category: String, val title: String)
    private val rows = mutableListOf<Row>()
    private val hidden = mutableSetOf<String>()
    private lateinit var adapter: RowsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        val component = (application as HabitsApplication).component
        AndroidThemeSwitcher(this, component.preferences).apply()
        super.onCreate(savedInstanceState)
        title = "状态栏天数"
        hidden.addAll(PreferenceManager.getDefaultSharedPreferences(this).getStringSet(KEY_HIDDEN, emptySet()).orEmpty())
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val background = StyledResources(this).getColor(R.attr.contrast0)
        val dark = androidx.core.graphics.ColorUtils.calculateLuminance(background) < .5
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(16)); setBackgroundColor(background) }
        root.addView(TextView(this).apply { text = "显示项目"; textSize = 20f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(if (dark) Color.WHITE else Color.BLACK) })
        root.addView(TextView(this).apply { text = "所有习惯和时间记录放在同一个列表中。每一项可单独显示或隐藏，按住右侧手柄即可上下拖动排序；类别标记仅用于辨认。隐藏不会删除原有记录。"; textSize = 13f; setTextColor(if (dark) Color.LTGRAY else Color.DKGRAY); setPadding(0, dp(6), 0, dp(12)) })
        val list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@StatusBarDaysManagementActivity); itemAnimator = null }
        adapter = RowsAdapter(); list.adapter = adapter
        val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition; val to = target.adapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val item = rows.removeAt(from); rows.add(to, item); adapter.notifyItemMoved(from, to); adapter.notifyItemRangeChanged(minOf(from, to), kotlin.math.abs(to - from) + 1); persistOrder(); return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled() = false
        }
        val helper = ItemTouchHelper(callback); helper.attachToRecyclerView(list); adapter.startDrag = { helper.startDrag(it) }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply { text = "＋ 添加项目"; textSize = 15f; gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(8)); isClickable = true; isFocusable = true; setOnClickListener { startActivity(Intent(this@StatusBarDaysManagementActivity, SettingsActivity::class.java).putExtra("open_preference_key", "statusBarDays")) } })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); reloadRows() }

    private fun reloadRows() {
        val store = PreferenceManager.getDefaultSharedPreferences(this)
        val configured = store.getStringSet(KEY_ITEMS, emptySet()).orEmpty().toMutableSet()
        if (configured.isEmpty()) {
            val mode = store.getString("status_bar_days_mode", "off") ?: "off"
            val id = when (mode) { "checkin" -> store.getLong("status_bar_days_habit_id", -1L).takeIf { it >= 0 }?.toString(); "elapsed", "countdown" -> store.getString("status_bar_days_record_id", null); else -> null }
            if (id != null) { configured.add("$mode:$id"); store.edit().putStringSet(KEY_ITEMS, configured).apply() }
        }
        val app = application as HabitsApplication
        val resolved = configured.mapNotNull { key ->
            val split = key.split(":", limit = 2); if (split.size != 2) return@mapNotNull null
            val mode = split[0]; val id = split[1]
            val pair = when (mode) {
                "checkin" -> { val habit = app.component.habitList.getById(id.toLongOrNull() ?: return@mapNotNull null) ?: return@mapNotNull null; "距离上次打卡" to habit.name }
                "elapsed" -> { val record = TimeRecordStore.get(this, id) ?: return@mapNotNull null; if (record.type != org.isoron.uhabits.activities.timerecords.TimeRecord.ELAPSED) return@mapNotNull null; "正计天" to record.title }
                "countdown" -> { val record = TimeRecordStore.get(this, id) ?: return@mapNotNull null; if (record.type != org.isoron.uhabits.activities.timerecords.TimeRecord.COUNTDOWN) return@mapNotNull null; "倒计天" to record.title }
                else -> return@mapNotNull null
            }
            Row(key, pair.first, pair.second)
        }
        val saved = runCatching { val array = JSONArray(store.getString(KEY_ORDER, "[]")); (0 until array.length()).map { array.getString(it) } }.getOrDefault(emptyList())
        rows.clear(); rows.addAll(saved.mapNotNull { key -> resolved.firstOrNull { it.key == key } }); resolved.forEach { row -> if (rows.none { it.key == row.key }) rows.add(row) }
        persistOrder(); if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    private fun persistOrder() {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(KEY_ORDER, JSONArray(rows.map { it.key }).toString()).apply()
        StatusBarDaysNotifier.update(this)
    }
    private fun setVisible(row: Row, visible: Boolean) { if (visible) hidden.remove(row.key) else hidden.add(row.key); PreferenceManager.getDefaultSharedPreferences(this).edit().putStringSet(KEY_HIDDEN, hidden.toSet()).apply(); StatusBarDaysNotifier.update(this) }

    private fun editRow(row: Row) {
        val parts = row.key.split(":", limit = 2)
        if (parts.size != 2) return
        when (parts[0]) {
            "elapsed", "countdown" -> startActivity(
                Intent(this, org.isoron.uhabits.activities.timerecords.TimeRecordsActivity::class.java)
                    .putExtra(org.isoron.uhabits.activities.timerecords.TimeRecordsActivity.EXTRA_EDIT_RECORD_ID, parts[1])
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            "checkin" -> {
                val id = parts[1].toLongOrNull() ?: return
                val habit = (application as HabitsApplication).component.habitList.getById(id) ?: return
                startActivity(org.isoron.uhabits.intents.IntentFactory().startEditActivity(this, habit))
            }
        }
    }

    private fun removeRow(row: Row) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("从状态栏移除")
            .setMessage("只将“" + row.title + "”从状态栏天数列表中移除，不删除原项目、日期或历史记录。")
            .setNegativeButton("取消", null)
            .setPositiveButton("移除") { _, _ ->
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val configured = prefs.getStringSet(KEY_ITEMS, emptySet()).orEmpty().toMutableSet()
                configured.remove(row.key)
                hidden.remove(row.key)
                val order = runCatching {
                    val array = JSONArray(prefs.getString(KEY_ORDER, "[]"))
                    (0 until array.length()).map { array.getString(it) }.filterNot { it == row.key }
                }.getOrDefault(rows.map { it.key }.filterNot { it == row.key })
                prefs.edit()
                    .putStringSet(KEY_ITEMS, configured)
                    .putStringSet(KEY_HIDDEN, hidden.toSet())
                    .putString(KEY_ORDER, JSONArray(order).toString())
                    .apply()
                if (configured.isEmpty()) {
                    // Prevent the legacy single-item setting from being migrated back into the list.
                    prefs.edit().putString("status_bar_days_mode", "off").apply()
                }
                reloadRows()
                StatusBarDaysNotifier.update(this)
            }
            .show()
    }

    private inner class RowsAdapter : RecyclerView.Adapter<RowsAdapter.Holder>() {
        var startDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
        inner class Holder(val check: CheckBox, val number: TextView, val title: TextView, val categoryLabel: TextView, val drag: TextView, val edit: TextView, val remove: TextView) : RecyclerView.ViewHolder(LinearLayout(this@StatusBarDaysManagementActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            val dark = androidx.core.graphics.ColorUtils.calculateLuminance(StyledResources(this@StatusBarDaysManagementActivity).getColor(R.attr.contrast0)) < .5
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (dark) Color.rgb(45, 48, 55) else Color.WHITE)
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(70, 74, 82) else Color.rgb(230, 233, 238))
            }
            addView(check, LinearLayout.LayoutParams(dp(32), dp(44)))
            addView(number, LinearLayout.LayoutParams(dp(24), dp(44)))
            addView(title, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(categoryLabel, LinearLayout.LayoutParams(-2, dp(44)))
            addView(drag, LinearLayout.LayoutParams(dp(38), dp(44)))
            addView(edit, LinearLayout.LayoutParams(dp(42), dp(44)))
            addView(remove, LinearLayout.LayoutParams(dp(42), dp(44)).apply { marginStart = dp(3) })
        })
        private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
            val check = CheckBox(this@StatusBarDaysManagementActivity).apply { text = ""; buttonTintList = android.content.res.ColorStateList.valueOf(0xFF9575CD.toInt()) }
            val number = TextView(this@StatusBarDaysManagementActivity).apply { textSize = 13f; gravity = Gravity.CENTER; setTextColor(0xFF9575CD.toInt()); includeFontPadding = false }
            val title = TextView(this@StatusBarDaysManagementActivity).apply { textSize = 14f; gravity = Gravity.CENTER_VERTICAL; setTextColor(if (androidx.core.graphics.ColorUtils.calculateLuminance(StyledResources(this@StatusBarDaysManagementActivity).getColor(R.attr.contrast0)) < .5) Color.WHITE else Color.BLACK); setPadding(dp(5), 0, dp(4), 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            val category = TextView(this@StatusBarDaysManagementActivity).apply { textSize = 10f; setTextColor(if (androidx.core.graphics.ColorUtils.calculateLuminance(StyledResources(this@StatusBarDaysManagementActivity).getColor(R.attr.contrast0)) < .5) Color.LTGRAY else Color.DKGRAY); setPadding(dp(3), 0, dp(3), 0); maxLines = 1 }
            val drag = TextView(this@StatusBarDaysManagementActivity).apply { text = "☷"; textSize = 23f; gravity = Gravity.CENTER; setTextColor(if (androidx.core.graphics.ColorUtils.calculateLuminance(StyledResources(this@StatusBarDaysManagementActivity).getColor(R.attr.contrast0)) < .5) Color.LTGRAY else Color.DKGRAY); contentDescription = "拖动排序" }
            val edit = TextView(this@StatusBarDaysManagementActivity).apply { text = "编辑"; textSize = 12f; setTextColor(0xFF9575CD.toInt()); gravity = Gravity.CENTER; setPadding(dp(3), dp(10), dp(3), dp(10)) }
            val remove = TextView(this@StatusBarDaysManagementActivity).apply { text = "删除"; textSize = 12f; setTextColor(0xFF9575CD.toInt()); gravity = Gravity.CENTER; setPadding(dp(3), dp(10), dp(3), dp(10)) }
            return Holder(check, number, title, category, drag, edit, remove)
        }
        override fun getItemCount() = rows.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.number.text = (position + 1).toString()
            holder.title.text = row.title
            holder.categoryLabel.text = row.category
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = row.key !in hidden
            holder.check.setOnCheckedChangeListener { _, checked -> setVisible(row, checked) }
            holder.edit.setOnClickListener { editRow(row) }
            holder.remove.setOnClickListener { removeRow(row) }
            holder.drag.setOnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_DOWN) { startDrag?.invoke(holder); true } else true }
        }
    }

    companion object { private const val KEY_ITEMS = "status_bar_days_items"; private const val KEY_HIDDEN = "status_bar_days_hidden_items"; private const val KEY_ORDER = "status_bar_days_item_order" }
}