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
    private var topLeftTarget = ""
    private var topRightTarget = ""
    private var bottomLeftTarget = ""
    private var bottomRightTarget = ""
    private var splitX = 5
    private var splitY = 5
    private var imageMode = "fit"
    private var restoreSourceWidgetId: Int? = null
    private var restoreSaved = false
    private var restoreFromGenericQueue = false
    private lateinit var modeSpinner: Spinner
    private lateinit var preview: ImageView
    private lateinit var imageStatus: TextView
    private lateinit var zoneButtons: List<Button>
    private lateinit var splitXLabel: TextView
    private lateinit var splitYLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as HabitsApplication).component.preferences.let { AndroidThemeSwitcher(this, it).apply() }
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        // New widgets default both tap regions to this widget's own settings page.
        val ownSettingsTarget = "pictureWidgetSettings:$widgetId"
        topLeftTarget = ownSettingsTarget
        topRightTarget = ownSettingsTarget
        bottomLeftTarget = ownSettingsTarget
        bottomRightTarget = ownSettingsTarget

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
        zoneButtons = listOf(
            Button(this).apply { text = "左上：选择快捷方式"; setOnClickListener { chooseTarget(0) } },
            Button(this).apply { text = "右上：选择快捷方式"; setOnClickListener { chooseTarget(1) } },
            Button(this).apply { text = "左下：选择快捷方式"; setOnClickListener { chooseTarget(2) } },
            Button(this).apply { text = "右下：选择快捷方式"; setOnClickListener { chooseTarget(3) } }
        )
        zoneButtons.forEach { content.addView(it) }
        splitXLabel = TextView(this).apply { textSize = 14f }
        content.addView(splitXLabel)
        val splitXSeekBar = SeekBar(this).apply {
            max = 8
            progress = 4
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    splitX = progress + 1
                    updateSplitLabels()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        content.addView(splitXSeekBar)
        splitYLabel = TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, 0) }
        content.addView(splitYLabel)
        val splitYSeekBar = SeekBar(this).apply {
            max = 8
            progress = 4
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    splitY = progress + 1
                    updateSplitLabels()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        content.addView(splitYSeekBar)
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
            text = "两条分界线按 10% 调整。未设置快捷方式的区域点击无反应。图片显示方式与触控区域互不影响。"
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
        splitX = saved.getInt("${widgetId}_split", 5).coerceIn(1, 9)
        splitY = saved.getInt("${widgetId}_splitY", 5).coerceIn(1, 9)
        val legacyLeft = saved.getString("${widgetId}_left", ownSettingsTarget) ?: ownSettingsTarget
        val legacyRight = saved.getString("${widgetId}_right", ownSettingsTarget) ?: ownSettingsTarget
        topLeftTarget = saved.getString("${widgetId}_topLeft", legacyLeft) ?: ""
        topRightTarget = saved.getString("${widgetId}_topRight", legacyRight) ?: ""
        bottomLeftTarget = saved.getString("${widgetId}_bottomLeft", legacyLeft) ?: ""
        bottomRightTarget = saved.getString("${widgetId}_bottomRight", legacyRight) ?: ""
        imageMode = saved.getString("${widgetId}_imageMode", "fit") ?: "fit"
        // Migrate unsupported legacy modes to the default native FIT_CENTER mode.
        if (imageMode == "width" || imageMode == "height") imageMode = "fit"
        modeSpinner.setSelection(when (imageMode) { "stretch" -> 1; "crop" -> 2; else -> 0 })
        splitXSeekBar.progress = splitX - 1
        splitYSeekBar.progress = splitY - 1
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
        val explicitGenericSourceId = intent.getIntExtra(
            WidgetRestoreStore.EXTRA_RESTORE_SOURCE_WIDGET_ID, -1
        )
        val hasExistingWidgetConfig = saved.contains("${widgetId}_left") ||
            saved.contains("${widgetId}_right") || saved.contains("${widgetId}_split") ||
            saved.contains("${widgetId}_imageMode")
        val providerClass = PictureShortcutWidgetProvider::class.java.name
        val genericRestoredConfig = when {
            explicitGenericSourceId >= 0 ->
                WidgetRestoreStore.find(this, explicitGenericSourceId, providerClass)
            !hasExistingWidgetConfig ->
                WidgetRestoreStore.selected(this)?.takeIf {
                    it.type == "picture" && it.providerClass == providerClass
                }
            else -> null
        }
        val legacyRestoredConfig = if (genericRestoredConfig == null) {
            if (explicitRestoreSourceId >= 0) {
                PictureWidgetRestoreStore.find(this, explicitRestoreSourceId)
            } else if (!hasExistingWidgetConfig) {
                PictureWidgetRestoreStore.selected(this)
            } else null
        } else null

        genericRestoredConfig?.let { restored ->
            restoreSourceWidgetId = restored.sourceWidgetId
            restoreFromGenericQueue = true
            val legacyLeft = restored.pictureLeft ?: "feature:__feature_center"
            val legacyRight = restored.pictureRight ?: "feature:__settings"
            topLeftTarget = restored.pictureTopLeft ?: legacyLeft
            topRightTarget = restored.pictureTopRight ?: legacyRight
            bottomLeftTarget = restored.pictureBottomLeft ?: legacyLeft
            bottomRightTarget = restored.pictureBottomRight ?: legacyRight
            splitX = restored.pictureSplit
            splitY = restored.pictureSplitY
            imageMode = restored.pictureImageMode
            imageFile = null
            modeSpinner.setSelection(when (imageMode) { "stretch" -> 1; "crop" -> 2; else -> 0 })
            splitXSeekBar.progress = splitX - 1
            splitYSeekBar.progress = splitY - 1
            imageStatus.text = "设置已载入；轻量备份不含图片，请重新选择原图片后保存"
        }
        legacyRestoredConfig?.let { restored ->
            restoreSourceWidgetId = restored.sourceWidgetId
            topLeftTarget = restored.left
            bottomLeftTarget = restored.left
            topRightTarget = restored.right
            bottomRightTarget = restored.right
            splitX = restored.split
            splitY = 5
            imageMode = restored.imageMode
            imageFile = null
            modeSpinner.setSelection(when (imageMode) { "stretch" -> 1; "crop" -> 2; else -> 0 })
            splitSeekBar.progress = split - 1
            imageStatus.text = "设置已载入；轻量备份不含图片，请重新选择图片（也可先保存组件）"
        }
        updateSplitLabels()
        updateTargetLabels()
    }

    override fun onDestroy() {
        if (isFinishing && !restoreSaved) {
            restoreSourceWidgetId?.let {
                if (restoreFromGenericQueue) WidgetRestoreStore.clearSelection(this, it)
                else PictureWidgetRestoreStore.clearSelection(this, it)
            }
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

    private fun chooseTarget(region: Int) {
        // This is a contextual shortcut: when selected for this widget, bind this
        // widget's own ID so its tap region always opens its own configuration.
        val ownSettingsKey = "__current_picture_widget_settings"
        val entries = FeatureShortcutCatalog.getSelectableEntries(this).toMutableList().apply {
            add(FeatureShortcutCatalog.Entry(key = ownSettingsKey, name = "打开本图片组件设置", source = "图片组件", category = "设置"))
            add(FeatureShortcutCatalog.Entry(key = "__no_picture_zone_action", name = "不设置（点击无反应）", source = "图片组件", category = "其他"))
        }
        FeatureShortcutPicker.show(
            context = this,
            title = "选择${listOf("左上", "右上", "左下", "右下")[region]}区域点击目标",
            entries = entries,
            targetKey = { entry ->
                when (entry.key) {
                    ownSettingsKey -> "pictureWidgetSettings:$widgetId"
                    "__no_picture_zone_action" -> ""
                    else -> entry.widgetTarget
                }
            }
        ) { selectedKey ->
            when (region) {
                0 -> topLeftTarget = selectedKey
                1 -> topRightTarget = selectedKey
                2 -> bottomLeftTarget = selectedKey
                3 -> bottomRightTarget = selectedKey
            }
            updateTargetLabels()
        }
    }

    private fun updateTargetLabels() {
        val all = FeatureShortcutCatalog.getSelectableEntries(this).associate { it.widgetTarget to it.title }
        fun label(target: String): String = when {
            target.isBlank() -> "未设置（点击无反应）"
            target == "pictureWidgetSettings:$widgetId" -> "打开本图片组件设置"
            else -> all[target] ?: "选择快捷方式"
        }
        val targets = listOf(topLeftTarget, topRightTarget, bottomLeftTarget, bottomRightTarget)
        val names = listOf("左上", "右上", "左下", "右下")
        zoneButtons.forEachIndexed { index, button -> button.text = "${names[index]}：${label(targets[index])}" }
    }

    private fun updateSplitLabels() {
        splitXLabel.text = "X 轴左右分界：左侧 ${splitX * 10}%　|　右侧 ${(10 - splitX) * 10}%"
        splitYLabel.text = "Y 轴上下分界：上方 ${splitY * 10}%　|　下方 ${(10 - splitY) * 10}%"
    }

    private fun saveWidget() {
        val file = imageFile
        if (file == null || !file.isFile) {
            Toast.makeText(
                this,
                if (restoreSourceWidgetId != null) "恢复图片组件需要重新选择原图片" else "请先选择一张图片",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val restoredConfig = restoreSourceWidgetId?.takeIf { restoreFromGenericQueue }?.let { sourceId ->
            WidgetRestoreStore.find(this, sourceId, PictureShortcutWidgetProvider::class.java.name)
        }
        PictureShortcutWidgetProvider.save(
            this, widgetId, file.name,
            topLeftTarget, topRightTarget, bottomLeftTarget, bottomRightTarget,
            splitX, splitY, imageMode
        )
        restoreSaved = true
        // Count only after the restored settings have been saved and the widget updated.
        PictureShortcutWidgetProvider.updateWidget(this, widgetId)
        restoredConfig?.let { config ->
            WidgetRestoreStore.recordSuccessfulRestore(this, config)
        }
        restoreSourceWidgetId?.let { sourceId ->
            if (restoreFromGenericQueue) {
                WidgetRestoreStore.consume(this, sourceId, PictureShortcutWidgetProvider::class.java.name)
                WidgetRestoreStore.clearSelection(this, sourceId)
            } else {
                PictureWidgetRestoreStore.consume(this, sourceId)
            }
            restoreSourceWidgetId = null
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    companion object { private const val REQUEST_IMAGE = 7102 }
}
