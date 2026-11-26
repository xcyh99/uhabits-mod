package org.isoron.uhabits.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
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
    private lateinit var editorView: PictureShortcutSplitEditorView

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
        editorView = PictureShortcutSplitEditorView(this).apply {
            onZoneClick = { region -> chooseTarget(region) }
            onSplitChanged = { x, y ->
                splitX = x
                splitY = y
            }
        }
        content.addView(editorView, LinearLayout.LayoutParams(-1, dp(300)))
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
        val saveButton = Button(this).apply {
            text = if (intent.getBooleanExtra(EXTRA_EDIT_EXISTING_WIDGET, false)) {
                "保存设置"
            } else {
                "保存并添加组件"
            }
            setOnClickListener { saveWidget() }
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        page.addView(ScrollView(this).apply {
            isFillViewport = false
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(saveButton, LinearLayout.LayoutParams(-1, dp(52)).apply {
            setMargins(dp(16), dp(8), dp(16), dp(12))
        })
        setContentView(page)

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
        editorView.splitX = splitX
        editorView.splitY = splitY
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
            editorView.splitX = splitX
            editorView.splitY = splitY
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
            editorView.splitX = splitX
            editorView.splitY = splitY
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
            },
            extraActions = listOf(
                "从其他格子复制" to { showCopySourceDialog(region) },
                "推送到其他格子" to { showPushTargetsDialog(region) }
            )
        ) { selectedKey ->
            setZoneTarget(region, selectedKey)
            updateTargetLabels()
        }
    }

    private val zoneNames = listOf("左上", "右上", "左下", "右下")

    private fun zoneTarget(region: Int): String = when (region) {
        0 -> topLeftTarget
        1 -> topRightTarget
        2 -> bottomLeftTarget
        else -> bottomRightTarget
    }

    private fun setZoneTarget(region: Int, target: String) {
        when (region) {
            0 -> topLeftTarget = target
            1 -> topRightTarget = target
            2 -> bottomLeftTarget = target
            3 -> bottomRightTarget = target
        }
    }

    private fun targetLabel(target: String): String {
        if (target.isBlank()) return "未设置（点击无反应）"
        if (target == "pictureWidgetSettings:$widgetId") return "打开本图片组件设置"
        return FeatureShortcutCatalog.getSelectableEntries(this)
            .firstOrNull { it.widgetTarget == target }?.title ?: "快捷方式"
    }

    private fun otherZones(current: Int): List<Int> = (0..3).filter { it != current }

    private fun showCopySourceDialog(targetRegion: Int) {
        val sources = otherZones(targetRegion)
        var selectedSource = sources.first()
        AlertDialog.Builder(this)
            .setTitle("从哪个格子复制？")
            .setSingleChoiceItems(
                sources.map { "${zoneNames[it]}：${targetLabel(zoneTarget(it))}" }.toTypedArray(),
                0
            ) { dialog, which ->
                selectedSource = sources[which]
                dialog.dismiss()
                showCopyReviewDialog(selectedSource, targetRegion)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCopyReviewDialog(sourceRegion: Int, targetRegion: Int) {
        val source = zoneTarget(sourceRegion)
        val before = zoneTarget(targetRegion)
        val result = source
        val message = "来源：${zoneNames[sourceRegion]}\n复制内容：${targetLabel(source)}\n\n目标：${zoneNames[targetRegion]}\n原内容：${targetLabel(before)}\n复制后：${targetLabel(result)}\n\n确认后将覆盖目标格原有设置；来源为空时，目标格也会变为未设置。"
        AlertDialog.Builder(this)
            .setTitle("核对复制内容")
            .setMessage(message)
            .setNegativeButton("返回", null)
            .setPositiveButton("确认复制") { _, _ ->
                setZoneTarget(targetRegion, result)
                updateTargetLabels()
                Toast.makeText(this, "已复制到${zoneNames[targetRegion]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showPushTargetsDialog(sourceRegion: Int) {
        val targets = otherZones(sourceRegion)
        val source = zoneTarget(sourceRegion)
        val checks = targets.map { region ->
            CheckBox(this).apply {
                text = "${zoneNames[region]}：原内容「${targetLabel(zoneTarget(region))}」\n推送后「${targetLabel(source)}」"
                textSize = 14f
                isChecked = false
                setPadding(0, dp(6), 0, dp(6))
            }
        }
        val summary = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        }
        fun refreshSummary() {
            val selected = checks.indices.filter { checks[it].isChecked }.map { targets[it] }
            val overwritten = selected.count { zoneTarget(it).isNotBlank() }
            summary.text = when {
                selected.isEmpty() -> "尚未选择目标格"
                else -> "已选 ${selected.size} 个目标；其中 ${overwritten} 个将覆盖原有设置。"
            }
        }
        checks.forEach { check ->
            check.setOnCheckedChangeListener { _, _ -> refreshSummary() }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(4))
            checks.forEach { addView(it) }
            addView(summary)
        }
        refreshSummary()
        val dialog = AlertDialog.Builder(this)
            .setTitle("推送到其他格子")
            .setView(content)
            .setNegativeButton("取消", null)
            .setPositiveButton("下一步", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = checks.indices.filter { checks[it].isChecked }.map { targets[it] }
                if (selected.isEmpty()) {
                    Toast.makeText(this, "请至少选择一个目标格", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                showPushReviewDialog(sourceRegion, selected)
            }
        }
        dialog.show()
    }

    private fun showPushReviewDialog(sourceRegion: Int, targetRegions: List<Int>) {
        val source = zoneTarget(sourceRegion)
        val details = targetRegions.joinToString("\n\n") { region ->
            "${zoneNames[region]}：\n原内容：${targetLabel(zoneTarget(region))}\n推送后：${targetLabel(source)}"
        }
        val message = "来源：${zoneNames[sourceRegion]}（${targetLabel(source)}）\n\n目标核对：\n$details\n\n确认后将一次性写入全部所选目标；来源为空时，所选目标将全部清空。"
        AlertDialog.Builder(this)
            .setTitle("最终确认推送")
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认推送") { _, _ ->
                targetRegions.forEach { setZoneTarget(it, source) }
                updateTargetLabels()
                Toast.makeText(this, "已推送到 ${targetRegions.size} 个格子", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun updateTargetLabels() {
        editorView.zoneLabels = listOf(topLeftTarget, topRightTarget, bottomLeftTarget, bottomRightTarget)
            .map { target -> targetLabel(target) }
    }

    private fun updateSplitLabels() {
        editorView.splitX = splitX
        editorView.splitY = splitY
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

    companion object {
        const val EXTRA_EDIT_EXISTING_WIDGET = "org.isoron.uhabits.extra.EDIT_EXISTING_PICTURE_WIDGET"
        private const val REQUEST_IMAGE = 7102
    }
}
