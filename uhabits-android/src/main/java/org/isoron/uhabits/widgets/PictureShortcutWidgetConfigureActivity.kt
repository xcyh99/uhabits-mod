package org.isoron.uhabits.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
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
            text = "分界线每次移动 10%。桌面上可通过长按组件调整整体大小。"
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
        val entries = FeatureShortcutCatalog.getEntries(this)
        return entries.mapNotNull { entry ->
            Choice(entry.widgetTarget, entry.title)
        }.distinctBy { it.key }.sortedBy { it.title }
    }

    private fun chooseTarget(isLeft: Boolean) {
        val items = choices()
        val labels = items.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(if (isLeft) "选择左侧点击目标" else "选择右侧点击目标")
            .setItems(labels) { _, which ->
                if (isLeft) leftTarget = items[which].key else rightTarget = items[which].key
                updateTargetLabels()
            }
            .setNegativeButton("取消", null)
            .show()
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
        PictureShortcutWidgetProvider.save(this, widgetId, file.name, leftTarget, rightTarget, split)
        PictureShortcutWidgetProvider.updateWidget(this, widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    companion object { private const val REQUEST_IMAGE = 7102 }
}
