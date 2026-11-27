package org.isoron.uhabits.activities.settings

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
import org.isoron.uhabits.notifications.StatusBarDaysNotifier
import org.isoron.uhabits.utils.StyledResources
import org.json.JSONArray

/** Three category-level controls. Existing selected habits/records remain untouched. */
class StatusBarDaysManagementActivity : AppCompatActivity() {
    private data class Row(val key: String, val title: String, val summary: String)
    private val rows = mutableListOf<Row>()
    private val hidden = mutableSetOf<String>()
    private lateinit var adapter: RowsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        val component = (application as HabitsApplication).component
        AndroidThemeSwitcher(this, component.preferences).apply()
        super.onCreate(savedInstanceState)
        title = "状态栏天数"

        val store = PreferenceManager.getDefaultSharedPreferences(this)
        hidden.addAll(store.getStringSet(KEY_HIDDEN, emptySet()).orEmpty())
        val order = runCatching {
            val array = JSONArray(store.getString(KEY_ORDER, "[]"))
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
        val definitions = listOf(
            Row("checkin", "距离上次打卡", "控制已选择的习惯天数项目"),
            Row("elapsed", "正计天", "控制已选择的正计天项目"),
            Row("countdown", "倒计天", "控制已选择的倒计天项目")
        )
        rows.addAll(order.mapNotNull { key -> definitions.firstOrNull { it.key == key } })
        definitions.forEach { row -> if (rows.none { it.key == row.key }) rows.add(row) }
        persistOrder()

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val background = StyledResources(this).getColor(R.attr.contrast0)
        val dark = androidx.core.graphics.ColorUtils.calculateLuminance(background) < .5
        val primary = if (dark) Color.WHITE else Color.BLACK
        val secondary = if (dark) Color.LTGRAY else Color.DKGRAY

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(background)
        }
        root.addView(TextView(this).apply {
            text = "显示项目"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(primary)
        })
        root.addView(TextView(this).apply {
            text = "默认全部显示。勾选控制整类项目的显示，按住右侧手柄上下拖动可调整状态栏顺序。隐藏不会删除原有记录。"
            textSize = 13f
            setTextColor(secondary)
            setPadding(0, dp(6), 0, dp(14))
        })

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@StatusBarDaysManagementActivity)
            itemAnimator = null
        }
        adapter = RowsAdapter()
        list.adapter = adapter
        val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition
                val to = target.adapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val moved = rows.removeAt(from)
                rows.add(to, moved)
                adapter.notifyItemMoved(from, to)
                persistOrder()
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled() = false
        }
        val helper = ItemTouchHelper(callback)
        helper.attachToRecyclerView(list)
        adapter.startDrag = { holder -> helper.startDrag(holder) }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun persistOrder() {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(KEY_ORDER, JSONArray(rows.map { it.key }).toString())
            .apply()
    }

    private fun setVisible(row: Row, visible: Boolean) {
        if (visible) hidden.remove(row.key) else hidden.add(row.key)
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putStringSet(KEY_HIDDEN, hidden.toSet())
            .apply()
        StatusBarDaysNotifier.update(this)
    }

    private inner class RowsAdapter : RecyclerView.Adapter<RowsAdapter.Holder>() {
        var startDrag: ((RecyclerView.ViewHolder) -> Unit)? = null

        inner class Holder(val check: CheckBox, val drag: TextView) : RecyclerView.ViewHolder(
            LinearLayout(this@StatusBarDaysManagementActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
                addView(check, LinearLayout.LayoutParams(0, -2, 1f))
                addView(drag, LinearLayout.LayoutParams(-2, -2))
            }
        )

        private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
            val check = CheckBox(this@StatusBarDaysManagementActivity).apply {
                textSize = 16f
                setPadding(0, dp(4), dp(8), dp(4))
            }
            val drag = TextView(this@StatusBarDaysManagementActivity).apply {
                text = "☰"
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(8), dp(6))
                contentDescription = "按住拖动排序"
            }
            return Holder(check, drag)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.check.setOnCheckedChangeListener(null)
            holder.check.text = row.title + "\n" + row.summary
            holder.check.isChecked = row.key !in hidden
            holder.check.setOnCheckedChangeListener { _, checked -> setVisible(row, checked) }
            holder.drag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    startDrag?.invoke(holder)
                    true
                } else true
            }
        }
    }

    companion object {
        private const val KEY_HIDDEN = "status_bar_days_hidden_categories"
        private const val KEY_ORDER = "status_bar_days_category_order"
    }
}
