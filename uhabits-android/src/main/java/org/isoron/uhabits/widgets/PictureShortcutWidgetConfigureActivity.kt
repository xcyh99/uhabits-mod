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
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.activities.settings.FeatureShortcutCatalog
import org.isoron.uhabits.activities.settings.FeatureShortcutPicker
import java.io.File
import java.io.FileOutputStream

class PictureShortcutWidgetConfigureActivity : AppCompatActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var imageFile: File? = null
    private var leftTarget = "feature:__feature_center"
    private var rightTarget = "feature:__settings"
    private var split = 5
    private var imageMode = "fit"
    private var restoreSourceWidgetId: Int? = null
    private var restoreSaved = false
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
        // New widgets default both tap regions to this widget's own settings page.
        val ownSettingsTarget = "pictureWidgetSettings:$widgetId"
        leftTarget = ownSettingsTarget
        rightTarget = ownSettingsTarget

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
        val modeLabels = listOf("完全适应（完整显示）", "拉伸填满（可能变形）", "居中裁剪（保持原比例）")
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeLabels)
        modeSpinner.setSelection(0)
        modeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                imageMode = listOf("fit", "stretch", "crop")[position]
                preview.scaleType = when (imageMode) {
                    "stretch" -> ImageView.ScaleType.FIT_XY
                    "crop" -> ImageView.ScaleType.CENTER_CROP
                    else -> ImageView.ScaleType.FIT_CENTER
                }
            }
        }
        content.addView(modeSpinner)
        content.addView(TextView(this).apply {
            text = "新建图片组件默认尺寸为 1×1。添加到桌面后，仍可长按并拖动边框自由调整大小。图片显示由 Android 根据组件实际区域自动适配。分界线每次移动 10%。"
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
        imageMode = saved.getString("${widgetId}_imageMode", "fit") ?: "fit"
        // Migrate unsupported legacy modes to the default native FIT_CENTER mode.
        if (imageMode == "width" || imageMode == "height") imageMode = "fit"
        modeSpinner.setSelection(when (imageMode) { "stretch" -> 1; "crop" -> 2; else -> 0 })
        splitSeekBar.progress = split - 1
        leftTarget = saved.getString("${widgetId}_left", leftTarget) ?: leftTarget
        rightTarget = saved.getString("${widgetId}_right", rightTarget) ?: rightTarget
        saved.getString("${widgetId}_image", null)?.takeIf { it.isNotBlank() }?.let { name ->
            val existing = File(filesDir, name)
            if (existing.isFile) {
                imageFile = existing
                BitmapFactory.decodeFile(existing.absolutePath)?.let { preview.setImageBitmap(it) }
                imageStatus.text = "当前图片：${existing.name}"
            }
        }
        val explicitRestoreSourceId = intent.getIntExtra(
            PictureWidgetRestoreStore.EXTRA_RESTORE_SOURCE_WIDGET_ID, -1
        )
        val hasExistingWidgetConfig = saved.contains("${widgetId}_left") ||
            saved.contains("${widgetId}_right") || saved.contains("${widgetId}_split") ||
            saved.contains("${widgetId}_imageMode")
        val restoredConfig = if (explicitRestoreSourceId >= 0) {
            PictureWidgetRestoreStore.find(this, explicitRestoreSourceId)
        } else if (!hasExistingWidgetConfig) {
            // Manual-add fallback is only eligible for a fresh widget ID; never
            // let a stale restore selection overwrite an existing widget's settings.
            PictureWidgetRestoreStore.selected(this)
        } else {
            null
        }
        restoredConfig?.let { restored ->
            restoreSourceWidgetId = restored.sourceWidgetId
            leftTarget = restored.left
            rightTarget = restored.right
            split = restored.split
            imageMode = restored.imageMode
            imageFile = null
            modeSpinner.setSelection(when (imageMode) { "stretch" -> 1; "crop" -> 2; else -> 0 })
            splitSeekBar.progress = split - 1
            imageStatus.text = "设置已载入；轻量备份不含图片，请重新选择图片（也可先保存组件）"
        }
        updateSplitLabel()
        updateTargetLabels()
    }

    override fun onDestroy() {
        if (isFinishing && !restoreSaved) {
            restoreSourceWidgetId?.let { PictureWidgetRestoreStore.clearSelection(this, it) }
        }
        super.onDestroy()
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

    private fun chooseTarget(isLeft: Boolean) {
        // This is a contextual shortcut: when selected for this widget, bind this
        // widget's own ID so its tap region always opens its own configuration.
        val ownSettingsKey = "__current_picture_widget_settings"
        val entries = FeatureShortcutCatalog.getSelectableEntries(this).toMutableList().apply {
            add(
                FeatureShortcutCatalog.Entry(
                    key = ownSettingsKey,
                    name = "打开本图片组件设置",
                    source = "图片组件",
                    category = "设置"
                )
            )
        }
        FeatureShortcutPicker.show(
            context = this,
            title = if (isLeft) "选择左侧点击目标" else "选择右侧点击目标",
            entries = entries,
            targetKey = { entry ->
                if (entry.key == ownSettingsKey) "pictureWidgetSettings:$widgetId"
                else entry.widgetTarget
            }
        ) { selectedKey ->
            if (isLeft) leftTarget = selectedKey else rightTarget = selectedKey
            updateTargetLabels()
        }
    }

    private fun updateTargetLabels() {
        val all = FeatureShortcutCatalog.getSelectableEntries(this).associate { it.widgetTarget to it.title }
        fun label(target: String): String = when {
            target == "pictureWidgetSettings:$widgetId" -> "打开本图片组件设置"
            else -> all[target] ?: "选择快捷程序"
        }
        leftButton.text = "左侧：${label(leftTarget)}"
        rightButton.text = "右侧：${label(rightTarget)}"
    }

    private fun updateSplitLabel() {
        splitLabel.text = "左侧 ${split * 10}%　|　右侧 ${(10 - split) * 10}%"
    }

    private fun saveWidget() {
        val file = imageFile
        if ((file == null || !file.isFile) && restoreSourceWidgetId == null) {
            Toast.makeText(this, "请先选择一张图片", Toast.LENGTH_LONG).show()
            return
        }
        PictureShortcutWidgetProvider.save(this, widgetId, file?.takeIf { it.isFile }?.name.orEmpty(),
            leftTarget, rightTarget, split, imageMode)
        restoreSourceWidgetId?.let { sourceId ->
            PictureWidgetRestoreStore.consume(this, sourceId)
            restoreSourceWidgetId = null
        }
        restoreSaved = true
        PictureShortcutWidgetProvider.updateWidget(this, widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    companion object { private const val REQUEST_IMAGE = 7102 }
}
