package org.isoron.uhabits.activities.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.timerecords.TimeRecord
import org.isoron.uhabits.activities.timerecords.TimeRecordStore
import org.isoron.uhabits.notifications.StatusBarDaysNotifier
import org.isoron.uhabits.utils.StyledResources
import org.json.JSONArray

/** Manages visibility and order without deleting any selected status-bar day project. */
class StatusBarDaysManagementActivity : AppCompatActivity() {
    private data class Row(val key: String, val title: String)
    private lateinit var adapter: RowsAdapter
    private val rows = mutableListOf<Row>()
    private val hidden = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as HabitsApplication
        AndroidThemeSwitcher(this, app.component.preferences).apply()
        super.onCreate(savedInstanceState)
        title = "状态栏天数"
        val store = PreferenceManager.getDefaultSharedPreferences(this)
        hidden.addAll(store.getStringSet(KEY_HIDDEN, emptySet()).orEmpty())
        val selected = store.getStringSet(KEY_ITEMS, emptySet()).orEmpty()
        val savedOrder = runCatching {
            val array = JSONArray(store.getString(KEY_ORDER, "[]"))
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
        val orderedKeys = (savedOrder.filter { it in selected } + selected.filter { it !in savedOrder }).distinct()
        orderedKeys.forEach { key -> rowFor(key)?.let { rows.add(it) } }
        // Normalize and persist a deterministic order, including projects added after the last reorder.
        persistOrder()

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val background = StyledResources(this).getColor(R.attr.contrast0)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(background)
        }
        root.addView(TextView(this).apply {
            text = "显示项目"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (androidx.core.graphics.ColorUtils.calculateLuminance(background) < .5) Color.WHITE else Color.BLACK)
        })
        root.addView(TextView(this).apply {
            text = "勾选控制显示；按住右侧拖动手柄调整状态栏顺序。隐藏项目不会删除原有记录。"
            textSize = 13f
            setPadding(0, dp(6), 0, dp(12))
        })
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@StatusBarDaysManagementActivity)
            itemAnimator = null
        }
        adapter = RowsAdapter()
        list.adapter = adapter
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val item = rows.removeAt(from)
                rows.add(to, item)
                adapter.notifyItemMoved(from, to)
                persistOrder()
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled() = false
        }
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(list)
        adapter.startDrag = { holder -> touchHelper.startDrag(holder) }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "＋ 添加项目"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@StatusBarDaysManagementActivity, SettingsActivity::class.java)
                    .putExtra("open_preference_key", "statusBarDays"))
            }
        })
        setContentView(root)
    }

    private fun rowFor(key: String): Row? {
        val parts = key.split(":", limit = 2)
        if (parts.size != 2) return null
        val id = parts[1]
        val title = when (parts[0]) {
            "checkin" -> {
                val habitId = id.toLongOrNull() ?: return null
                (application as HabitsApplication).component.habitList.getById(habitId)?.name
            }
            "countdown", "elapsed" -> TimeRecordStore.get(this, id)?.title
            else -> null
        } ?: return null
        val prefix = when (parts[0]) {
            "checkin" -> "距离上次打卡"
            "countdown" -> "倒计天"
            else -> "正计天"
        }
        return Row(key, "$prefix · $title")
    }

    private fun persistOrder() {
        val store = PreferenceManager.getDefaultSharedPreferences(this)
        store.edit().putString(KEY_ORDER, JSONArray(rows.map { it.key }).toString()).apply()
    }

    private fun setVisible(row: Row, visible: Boolean) {
        if (visible) hidden.remove(row.key) else hidden.add(row.key)
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putStringSet(KEY_HIDDEN, hidden.toSet()).apply()
        StatusBarDaysNotifier.update(this)
    }

    private inner class RowsAdapter : RecyclerView.Adapter<RowsAdapter.Holder>() {
        var startDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
        inner class Holder(val box: LinearLayout, val check: CheckBox, val drag: TextView) : RecyclerView.ViewHolder(box)
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
            val d = resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            val line = LinearLayout(this@StatusBarDaysManagementActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            val check = CheckBox(this@StatusBarDaysManagementActivity)
            val drag = TextView(this@StatusBarDaysManagementActivity).apply {
                text = "☰"
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(4), dp(8), dp(4))
                contentDescription = "按住拖动排序"
            }
            line.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(drag, LinearLayout.LayoutParams(-2, -2))
            return Holder(line, check, drag)
        }
        override fun getItemCount() = rows.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.check.setOnCheckedChangeListener(null)
            holder.check.text = row.title
            holder.check.isChecked = row.key !in hidden
            holder.check.setOnCheckedChangeListener { _, checked -> setVisible(row, checked) }
            holder.drag.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) startDrag?.invoke(holder)
                false
            }
        }
    }

    companion object {
        private const val KEY_ITEMS = "status_bar_days_items"
        private const val KEY_HIDDEN = "status_bar_days_hidden_items"
        private const val KEY_ORDER = "status_bar_days_order"
    }
}
