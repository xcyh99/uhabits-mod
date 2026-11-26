package org.isoron.uhabits.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.provider.OpenableColumns
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ScrollView
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
    private var imageUri: Uri? = null
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
    private lateinit var imageAddress: TextView
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
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF30343B.toInt())
        }
        imageStatus = TextView(this).apply {
            text = "尚未选择图片"
            textSize = 12f
            setTextColor(Color.rgb(196, 196, 196))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        imageAddress = TextView(this).apply {
            text = "尚未选择图片地址"
            textSize = 11f
            setTextColor(Color.rgb(196, 196, 196))
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            isClickable = true
            isLongClickable = true
        }
        imageAddress.setOnClickListener {
            val uri = imageUri
            if (uri == null) {
                Toast.makeText(this, "当前图片没有可打开的原始地址", Toast.LENGTH_SHORT).show()
            } else {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }.onFailure {
                    Toast.makeText(this, "系统暂不支持直接打开这张图片", Toast.LENGTH_SHORT).show()
                }
            }
        }
        imageAddress.setOnLongClickListener {
            val address = imageUri?.toString() ?: imageFile?.absolutePath
            if (address.isNullOrBlank()) {
                Toast.makeText(this, "暂无图片地址可复制", Toast.LENGTH_SHORT).show()
            } else {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                    ClipData.newPlainText("图片地址", address)
                )
                Toast.makeText(this, "已复制图片地址", Toast.LENGTH_SHORT).show()
            }
            true
        }
        val chooseImageButton = Button(this).apply {
            text = "选择图片"
            textSize = 14f
            minHeight = dp(72)
            isAllCaps = false
            setOnClickListener { chooseImage() }
        }
        val fileNameTitle = TextView(this).apply {
            text = "文件名"
            textSize = 13f
            setTextColor(Color.WHITE)
            setSingleLine(true)
        }
        val imageAddressTitle = TextView(this).apply {
            text = "图片地址"
            textSize = 13f
            setTextColor(Color.WHITE)
            setSingleLine(true)
        }
        val imageAddressHint = TextView(this).apply {
            text = "点击打开原图 · 长按复制地址"
            textSize = 10f
            setTextColor(Color.rgb(136, 136, 136))
            setSingleLine(true)
        }
        val modeTitle = TextView(this).apply {
            text = "图片显示方式"
            textSize = 14f
            setTextColor(Color.WHITE)
            maxLines = 1
            setSingleLine(true)
            setPadding(0, dp(6), 0, dp(2))
        }
        modeSpinner = Spinner(this).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        val modeLabels = listOf("完全适应", "拉伸填满", "居中裁剪")
        val modeAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, modeLabels) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    textSize = 12f
                    maxLines = 1
                    setSingleLine(true)
                    ellipsize = null
                    includeFontPadding = false
                    setPadding(0, 0, 0, 0)
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    textSize = 12f
                    maxLines = 1
                    setSingleLine(true)
                    ellipsize = null
                    includeFontPadding = false
                    setPadding(0, 0, 0, 0)
                }
            }
        }
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modeSpinner.adapter = modeAdapter
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
        val imageSettingsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.TOP
        }
        val previewColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(preview, LinearLayout.LayoutParams(-1, dp(280)))
        }
        val settingsColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.TOP
            setPadding(dp(8), 0, 0, 0)
            addView(chooseImageButton, LinearLayout.LayoutParams(-1, dp(72)))
            addView(fileNameTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            addView(imageStatus, LinearLayout.LayoutParams(-1, -2))
            addView(imageAddressTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            addView(imageAddress, LinearLayout.LayoutParams(-1, -2))
            addView(imageAddressHint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
            addView(View(this@PictureShortcutWidgetConfigureActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(modeTitle, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            })
            addView(modeSpinner, LinearLayout.LayoutParams(-1, dp(60)))
        }
        imageSettingsRow.addView(previewColumn, LinearLayout.LayoutParams(0, -2, 2f))
        imageSettingsRow.addView(settingsColumn, LinearLayout.LayoutParams(0, dp(280), 1f))
        content.addView(imageSettingsRow, LinearLayout.LayoutParams(-1, -2))
        editorView = PictureShortcutSplitEditorView(this).apply {
            onZoneClick = { region -> chooseTarget(region) }
            onSplitChanged = { x, y ->
                splitX = x
                splitY = y
            }
        }
        content.addView(editorView, LinearLayout.LayoutParams(-1, dp(300)).apply {
            topMargin = dp(8)
        })
        content.addView(TextView(this).apply {
            text = "两条分界线按 10% 调整。未设置快捷方式的区域点击无反应。"
            textSize = 11f
            setPadding(0, dp(2), 0, dp(6))
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
        imageUri = saved.getString("${widgetId}_sourceUri", null)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        saved.getString("${widgetId}_image", null)?.takeIf { it.isNotBlank() }?.let { name ->
            val existing = File(filesDir, name)
            if (existing.isFile) {
                imageFile = existing
                BitmapFactory.decodeFile(existing.absolutePath)?.let { preview.setImageBitmap(it) }
                imageStatus.text = imageUri?.let { queryImageDisplayName(it) } ?: existing.name
                imageAddress.text = imageUri?.toString() ?: existing.absolutePath
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMAGE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            imageUri = uri
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
            imageStatus.text = queryImageDisplayName(uri) ?: file.name
            imageAddress.text = uri.toString()
            // Keep the preview bitmap alive until the ImageView releases it.
        } catch (error: Exception) {
            Toast.makeText(this, error.message ?: "图片读取失败", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryImageDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
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

    private fun zoneSelectionDialog(
        title: String,
        currentRegion: Int,
        multiSelect: Boolean,
        onConfirmed: (List<Int>) -> Unit
    ) {
        val selected = linkedSetOf<Int>()
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(4))
        }
        val dialogContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        val selectionSummary = TextView(this).apply {
            textSize = 12f
            setPadding(dp(2), dp(8), dp(2), dp(2))
        }
        var positiveButton: Button? = null
        fun refreshSummary() {
            selectionSummary.text = if (selected.isEmpty()) {
                if (multiSelect) "请选择推送目标（可多选）" else "请选择一个复制来源"
            } else {
                if (multiSelect) "已选择 ${selected.size} 个目标" else "来源：${zoneNames[selected.first()]}"
            }
            positiveButton?.isEnabled = selected.isNotEmpty()
        }
        fun renderGrid() {
            grid.removeAllViews()
            for (rowIndex in 0..1) {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (columnIndex in 0..1) {
                    val region = rowIndex * 2 + columnIndex
                    val isCurrent = region == currentRegion
                    val isSelected = selected.contains(region)
                    val color = when {
                        isCurrent -> Color.rgb(211, 55, 55)
                        isSelected -> Color.rgb(34, 160, 90)
                        else -> Color.LTGRAY
                    }
                    val tile = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(9), dp(8), dp(9), dp(8))
                        background = GradientDrawable().apply {
                            setColor(Color.TRANSPARENT)
                            cornerRadius = dp(8).toFloat()
                            setStroke(dp(if (isCurrent || isSelected) 2 else 1), color)
                        }
                        val header = TextView(this@PictureShortcutWidgetConfigureActivity).apply {
                            text = when {
                                isCurrent -> "${zoneNames[region]} · 当前格"
                                isSelected -> "${zoneNames[region]} · ✓"
                                else -> "${zoneNames[region]} · ○"
                            }
                            textSize = 12f
                            setTextColor(if (isCurrent || isSelected) color else currentTextColor)
                        }
                        val detail = TextView(this@PictureShortcutWidgetConfigureActivity).apply {
                            text = compactTargetLabel(zoneTarget(region))
                            textSize = 12f
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setPadding(0, dp(4), 0, 0)
                        }
                        addView(header)
                        addView(detail)
                        isEnabled = !isCurrent
                        if (!isCurrent) setOnClickListener {
                            if (multiSelect) {
                                if (!selected.add(region)) selected.remove(region)
                            } else {
                                selected.clear()
                                selected.add(region)
                            }
                            renderGrid()
                            refreshSummary()
                        }
                    }
                    row.addView(tile, LinearLayout.LayoutParams(0, dp(76), 1f).apply {
                        if (columnIndex == 0) marginEnd = dp(6) else marginStart = dp(6)
                        bottomMargin = dp(6)
                    })
                }
                grid.addView(row)
            }
        }
        renderGrid()
        refreshSummary()
        dialogContent.addView(grid)
        dialogContent.addView(selectionSummary)
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogContent)
            .setNegativeButton("取消", null)
            .setPositiveButton(if (multiSelect) "下一步" else "确认复制", null)
            .create()
        dialog.setOnShowListener {
            positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton?.setOnClickListener {
                if (selected.isEmpty()) {
                    Toast.makeText(this, "请先选择格子", Toast.LENGTH_SHORT).show()
                } else {
                    val result = selected.toList()
                    dialog.dismiss()
                    onConfirmed(result)
                }
            }
            refreshSummary()
        }
        dialog.show()
    }

    private fun compactTargetLabel(target: String): String =
        if (target.isBlank()) "空" else targetLabel(target)

    private fun showCopySourceDialog(targetRegion: Int) {
        zoneSelectionDialog("从其他格子复制", targetRegion, false) { selected ->
            showCopyReviewDialog(selected.first(), targetRegion)
        }
    }

    private fun showCopyReviewDialog(sourceRegion: Int, targetRegion: Int) {
        val source = zoneTarget(sourceRegion)
        val before = zoneTarget(targetRegion)
        if (source == before) {
            Toast.makeText(this, "内容相同，无需修改", Toast.LENGTH_SHORT).show()
            return
        }
        val message = "${zoneNames[sourceRegion]}格 → ${zoneNames[targetRegion]}格\n" +
            "${compactTargetLabel(before)} → ${compactTargetLabel(source)}\n\n" +
            "将覆盖${zoneNames[targetRegion]}格原有内容。"
        val dialog = AlertDialog.Builder(this)
            .setTitle("确认复制？")
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                setZoneTarget(targetRegion, source)
                updateTargetLabels()
                Toast.makeText(this, "已复制到${zoneNames[targetRegion]}", Toast.LENGTH_SHORT).show()
            }
            .show()
        dialog.findViewById<TextView>(android.R.id.alertTitle)?.textSize = 14f
        dialog.findViewById<TextView>(android.R.id.message)?.textSize = 11f
    }

    private fun showPushTargetsDialog(sourceRegion: Int) {
        zoneSelectionDialog("推送到其他格子", sourceRegion, true) { selected ->
            showPushReviewDialog(sourceRegion, selected)
        }
    }

    private fun showPushReviewDialog(sourceRegion: Int, targetRegions: List<Int>) {
        val source = zoneTarget(sourceRegion)
        val changed = targetRegions.filter { zoneTarget(it) != source }
        if (changed.isEmpty()) {
            Toast.makeText(this, "所选内容均相同，无需修改", Toast.LENGTH_SHORT).show()
            return
        }
        val details = changed.joinToString("\n") { region ->
            "${zoneNames[region]}：${compactTargetLabel(zoneTarget(region))} → ${compactTargetLabel(source)}"
        }
        val overwritten = changed.count { zoneTarget(it).isNotBlank() }
        val filled = changed.size - overwritten
        val summary = buildList {
            if (overwritten > 0) add("覆盖 $overwritten 个已有格子")
            if (filled > 0) add("填入 $filled 个空格子")
        }.joinToString("，")
        val message = "来源：${zoneNames[sourceRegion]}格 · ${compactTargetLabel(source)}\n" +
            "$details\n\n共 ${changed.size} 个目标，$summary。"
        val dialog = AlertDialog.Builder(this)
            .setTitle("确认推送？")
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认推送") { _, _ ->
                changed.forEach { setZoneTarget(it, source) }
                updateTargetLabels()
                Toast.makeText(this, "已推送到 ${changed.size} 个格子", Toast.LENGTH_SHORT).show()
            }
            .show()
        dialog.findViewById<TextView>(android.R.id.alertTitle)?.textSize = 14f
        dialog.findViewById<TextView>(android.R.id.message)?.textSize = 11f
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
        // The editor view is the source of truth for the live split positions.
        // Read it at save time so the persisted values cannot lag behind the UI callback.
        splitX = editorView.splitX.coerceIn(1, 9)
        splitY = editorView.splitY.coerceIn(1, 9)
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
        getSharedPreferences(PictureShortcutWidgetProvider.PREFS, MODE_PRIVATE).edit().apply {
            if (imageUri != null) putString("${widgetId}_sourceUri", imageUri.toString())
            else remove("${widgetId}_sourceUri")
        }.apply()
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
