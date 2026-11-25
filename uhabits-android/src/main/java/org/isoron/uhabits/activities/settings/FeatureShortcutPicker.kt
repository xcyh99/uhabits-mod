package org.isoron.uhabits.activities.settings

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/** Shared picker for all shortcut destination selectors. Discovery remains in the catalog manager. */
object FeatureShortcutPicker {
    fun show(
        context: Context,
        title: String,
        entries: List<FeatureShortcutCatalog.Entry>,
        targetKey: (FeatureShortcutCatalog.Entry) -> String = { it.widgetTarget },
        includeNone: Boolean = false,
        onSelected: (String) -> Unit
    ) {
        data class Choice(val key: String, val title: String, val category: String)
        val categoryOrder = listOf("功能", "设置", "习惯", "应用", "系统")
        val choices = mutableListOf<Choice>()
        if (includeNone) choices.add(Choice("none", "不执行操作", "设置"))
        entries.forEach { choices.add(Choice(targetKey(it), it.title, it.category)) }
        val sortedChoices = choices.distinctBy { it.key }.sortedWith(
            compareBy<Choice> { categoryOrder.indexOf(it.category).let { index -> if (index < 0) Int.MAX_VALUE else index } }
                .thenBy { it.title.lowercase() }
        )
        val filtered = sortedChoices.toMutableList()
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val primaryText = TextView(context).currentTextColor
        val search = EditText(context).apply {
            hint = "搜索功能、设置、习惯、应用或系统项目"
            setSingleLine(true)
            textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val list = ListView(context).apply { dividerHeight = dp(1) }
        val empty = TextView(context).apply { text = "没有找到匹配的快捷入口"; textSize = 14f; gravity = Gravity.CENTER; visibility = View.GONE }
        val adapter = object : BaseAdapter() {
            override fun getCount() = filtered.size
            override fun getItem(position: Int) = filtered[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? TextView) ?: TextView(context)
                val item = getItem(position)
                row.text = FeatureShortcutCatalog.styledTitle("【${item.category}】 ${item.title}")
                row.textSize = 15f; row.setTextColor(primaryText); row.gravity = Gravity.CENTER_VERTICAL
                row.minHeight = dp(48); row.setPadding(dp(16), dp(12), dp(16), dp(12))
                return row
            }
        }
        list.adapter = adapter
        fun filter(query: String) {
            val q = query.trim().lowercase()
            filtered.clear()
            filtered.addAll(sortedChoices.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.key.lowercase().contains(q) || it.category.lowercase().contains(q) })
            adapter.notifyDataSetChanged()
            list.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
            empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(4))
            addView(search, LinearLayout.LayoutParams(-1, -2))
            addView(list, LinearLayout.LayoutParams(-1, dp(360)))
            addView(empty, LinearLayout.LayoutParams(-1, dp(360)))
        }
        val dialog = AlertDialog.Builder(context).setTitle(title).setView(content).setNegativeButton("取消", null).create()
        list.setOnItemClickListener { _, _, position, _ -> onSelected(filtered[position].key); dialog.dismiss() }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filter(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        dialog.show(); filter("")
    }
}
