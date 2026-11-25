package org.isoron.uhabits.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.activities.settings.FeatureShortcutCatalog
import java.io.File
import java.io.FileOutputStream

class PictureShortcutWidgetConfigureActivity : AppCompatActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var imageFile: File? = null
    private var leftTarget = "feature:__feature_center"
    private var rightTarget = "feature:__settings"
    private var split = 5
    private var imageMode = "crop"
    private lateinit var modeSpinner: Spinner
    private lateinit var preview: ImageView
    private lateinit var imageStatus: TextView
    private lateinit var leftButton: Button
    private lateinit var rightButton: Button
    private lateinit var splitLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as HabitsApplication).component.preferences.let { AndroidThemeSwitcher(this, it).apply() }
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
        }
        content.addView(TextView(this).apply {
            text = "图片快捷组件"
            textSize = 20f
            setPadding(0, 0, 0, dp(12))
        })
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF30343B.toInt())
        }
        content.addView(preview, LinearLayout.LayoutParams(-1, dp(180)))
        imageStatus = TextView(this).apply { text = "尚未选择图片"; textSize = 12f }
        content.addView(imageStatus)
        content.addView(Button(this).apply {
            text = "从手机相册选择图片"
            setOnClickListener { chooseImage() }
        })
        leftButton = Button(this).apply {
            text = "左侧：选择快捷程序"
            setOnClickListener { chooseTarget(true) }
        }
        rightButton = Button(this).apply {
            text = "右侧：选择快捷程序"
            setOnClickListener { chooseTarget(false) }
        }
        content.addView(leftButton)
        content.addView(rightButton)
        splitLabel = TextView(this).apply { textSize = 14f }
        content.addView(splitLabel)
        val splitSeekBar = SeekBar(this).apply {
            max = 8
            progress = 4
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    split = progress + 1
                    updateSplitLabel()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        content.addView(splitSeekBar)
        content.addView(TextView(this).apply {
            text = "图片显示方式"
            textSize = 14f
            setPadding(0, dp(10), 0, dp(4))
        })
        modeSpinner = Spinner(this)
        val modeLabels = listOf("完整显示（保持原比例）", "拉伸填满（可能变形）", "居中裁剪（保持原比例）")
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeLabels)
        modeSpinner.setSelection(2)
        modeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                imageMode = listOf("fit", "stretch", "crop")[position]
                preview.scaleType = when (imageMode) {
                    "fit" -> ImageView.ScaleType.FIT_CENTER
                    "stretch" -> ImageView.ScaleType.FIT_XY
                    else -> ImageView.ScaleType.CENTER_CROP
                }
            }
        }
        content.addView(modeSpinner)
        content.addView(TextView(this).apply {
            text = "图片组件默认 2×2。添加到桌面后，长按并拖动边框调整大小（最小尺寸及最大尺寸由桌面网格决定）。图片显示方式可在下方选择。分界线每次移动 10%。"
            textSize = 12f
            setPadding(0, dp(4), 0, dp(8))
        })
        content.addView(Button(this).apply {
            text = "保存并添加组件"
            setOnClickListener { saveWidget() }
        })
        setContentView(content)

        // When opened from 图片组件设置, restore this widget's existing configuration.
        val saved = getSharedPreferences(PictureShortcutWidgetProvider.PREFS, MODE_PRIVATE)
        split = saved.getInt("${widgetId}_split", 5).coerceIn(1, 9)
        imageMode = saved.getString("${widgetId}_imageMode", "crop") ?: "crop"
        modeSpinner.setSelection(when (imageMode) { "fit" -> 0; "stretch" -> 1; else -> 2 })
        splitSeekBar.progress = split - 1
        leftTarget = saved.getString("${widgetId}_left", leftTarget) ?: leftTarget
        rightTarget = saved.getString("${widgetId}_right", rightTarget) ?: rightTarget
        saved.getString("${widgetId}_image", null)?.let { name ->
            val existing = File(filesDir, name)
            if (existing.isFile) {
                imageFile = existing
                BitmapFactory.decodeFile(existing.absolutePath)?.let { preview.setImageBitmap(it) }
                imageStatus.text = "当前图片：${existing.name}"
            }
        }
        updateSplitLabel()
        updateTargetLabels()
    }

    private fun chooseImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMAGE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalStateException("无法读取图片")
            val maxSide = 400
            val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
            val saved = if (scale < 1f) Bitmap.createScaledBitmap(bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
            val file = File(filesDir, "picture_shortcut_widget_$widgetId.jpg")
            FileOutputStream(file).use { saved.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            imageFile = file
            preview.setImageBitmap(saved)
            imageStatus.text = "已选择：${file.name}"
            // Keep the preview bitmap alive until the ImageView releases it.
        } catch (error: Exception) {
            Toast.makeText(this, error.message ?: "图片读取失败", Toast.LENGTH_LONG).show()
        }
    }

    private data class Choice(val key: String, val title: String)

    private fun choices(): List<Choice> {
        val entries = FeatureShortcutCatalog.getSelectableEntries(this)
        return entries.mapNotNull { entry ->
            Choice(entry.widgetTarget, entry.title)
        }.distinctBy { it.key }.sortedBy { it.title }
    }

    private fun chooseTarget(isLeft: Boolean) {
        val items = choices()
        val filtered = items.toMutableList()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val search = android.widget.EditText(this).apply {
            hint = "搜索功能、设置、习惯或应用"
            setSingleLine(true)
            textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val list = android.widget.ListView(this).apply { dividerHeight = dp(1) }
        val empty = TextView(this).apply {
            text = "没有找到匹配的快捷入口"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            visibility = android.view.View.GONE
        }
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = filtered.size
            override fun getItem(position: Int) = filtered[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = (convertView as? TextView) ?: TextView(this@PictureShortcutWidgetConfigureActivity)
                view.text = FeatureShortcutCatalog.styledTitle(getItem(position).title)
                view.textSize = 15f
                view.setTextColor(if ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK)
                view.setPadding(dp(14), dp(13), dp(14), dp(13))
                return view
            }
        }
        list.adapter = adapter
        fun filter(query: String) {
            val q = query.trim().lowercase()
            filtered.clear()
            filtered.addAll(items.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.key.lowercase().contains(q) })
            adapter.notifyDataSetChanged()
            list.visibility = if (filtered.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            empty.visibility = if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
            addView(search, LinearLayout.LayoutParams(-1, -2))
            addView(list, LinearLayout.LayoutParams(-1, dp(360)))
            addView(empty, LinearLayout.LayoutParams(-1, dp(360)))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isLeft) "选择左侧点击目标" else "选择右侧点击目标")
            .setView(content)
            .setNegativeButton("取消", null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            val selected = filtered[position]
            if (isLeft) leftTarget = selected.key else rightTarget = selected.key
            updateTargetLabels()
            dialog.dismiss()
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filter(s?.toString().orEmpty())
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        dialog.show()
        filter("")
    }

    private fun updateTargetLabels() {
        val all = choices().associate { it.key to it.title }
        leftButton.text = "左侧：${all[leftTarget] ?: "选择快捷程序"}"
        rightButton.text = "右侧：${all[rightTarget] ?: "选择快捷程序"}"
    }

    private fun updateSplitLabel() {
        splitLabel.text = "左侧 ${split * 10}%　|　右侧 ${(10 - split) * 10}%"
    }

    private fun saveWidget() {
        val file = imageFile
        if (file == null || !file.isFile) {
            Toast.makeText(this, "请先选择一张图片", Toast.LENGTH_LONG).show()
            return
        }
        PictureShortcutWidgetProvider.save(this, widgetId, file.name, leftTarget, rightTarget, split, imageMode)
        PictureShortcutWidgetProvider.updateWidget(this, widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    companion object { private const val REQUEST_IMAGE = 7102 }
}
