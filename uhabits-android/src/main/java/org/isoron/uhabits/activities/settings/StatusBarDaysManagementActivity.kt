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
                val item = rows.removeAt(from); rows.add(to, item); adapter.notifyItemMoved(from, to); persistOrder(); return true
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
                "elapsed" -> { val record = TimeRecordStore.get(this, id) ?: return@mapNotNull null; "正计天" to record.title }
                "countdown" -> { val record = TimeRecordStore.get(this, id) ?: return@mapNotNull null; "倒计天" to record.title }
                else -> return@mapNotNull null
            }
            Row(key, pair.first, pair.second)
        }
        val saved = runCatching { val array = JSONArray(store.getString(KEY_ORDER, "[]")); (0 until array.length()).map { array.getString(it) } }.getOrDefault(emptyList())
        rows.clear(); rows.addAll(saved.mapNotNull { key -> resolved.firstOrNull { it.key == key } }); resolved.forEach { row -> if (rows.none { it.key == row.key }) rows.add(row) }
        persistOrder(); if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    private fun persistOrder() { PreferenceManager.getDefaultSharedPreferences(this).edit().putString(KEY_ORDER, JSONArray(rows.map { it.key }).toString()).apply() }
    private fun setVisible(row: Row, visible: Boolean) { if (visible) hidden.remove(row.key) else hidden.add(row.key); PreferenceManager.getDefaultSharedPreferences(this).edit().putStringSet(KEY_HIDDEN, hidden.toSet()).apply(); StatusBarDaysNotifier.update(this) }

    private inner class RowsAdapter : RecyclerView.Adapter<RowsAdapter.Holder>() {
        var startDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
        inner class Holder(val check: CheckBox, val categoryLabel: TextView, val drag: TextView) : RecyclerView.ViewHolder(LinearLayout(this@StatusBarDaysManagementActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(7), dp(8), dp(7)); addView(check, LinearLayout.LayoutParams(0, -2, 1f)); addView(categoryLabel, LinearLayout.LayoutParams(-2, -2)); addView(drag, LinearLayout.LayoutParams(-2, -2)) })
        private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
            val check = CheckBox(this@StatusBarDaysManagementActivity).apply { textSize = 15f; setPadding(0, dp(4), dp(8), dp(4)) }
            val category = TextView(this@StatusBarDaysManagementActivity).apply { textSize = 11f; setTextColor(if (androidx.core.graphics.ColorUtils.calculateLuminance(StyledResources(this@StatusBarDaysManagementActivity).getColor(R.attr.contrast0)) < .5) Color.LTGRAY else Color.DKGRAY); setPadding(dp(6), 0, dp(4), 0); maxLines = 1 }
            val drag = TextView(this@StatusBarDaysManagementActivity).apply { text = "☰"; textSize = 22f; gravity = Gravity.CENTER; setPadding(dp(10), dp(4), dp(4), dp(4)); contentDescription = "按住拖动排序" }
            check.maxLines = 1; check.isSingleLine = true; check.ellipsize = android.text.TextUtils.TruncateAt.END
            return Holder(check, category, drag)
        }
        override fun getItemCount() = rows.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]; holder.check.setOnCheckedChangeListener(null); holder.check.text = row.title; holder.categoryLabel.text = row.category; holder.check.isChecked = row.key !in hidden
            holder.check.setOnCheckedChangeListener { _, checked -> setVisible(row, checked) }
            holder.drag.setOnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_DOWN) { startDrag?.invoke(holder); true } else true }
        }
    }

    companion object { private const val KEY_ITEMS = "status_bar_days_items"; private const val KEY_HIDDEN = "status_bar_days_hidden_items"; private const val KEY_ORDER = "status_bar_days_item_order" }
}