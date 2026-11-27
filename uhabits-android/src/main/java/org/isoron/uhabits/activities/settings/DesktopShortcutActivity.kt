package org.isoron.uhabits.activities.settings

import android.content.pm.ShortcutInfo
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ScrollView
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.provider.OpenableColumns
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.isoron.uhabits.R
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.utils.StyledResources
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DesktopShortcutActivity : AppCompatActivity() {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private data class Target(val key: String, val title: String)
    private data class ShortcutEntry(
        val id: String,
        val name: String,
        val targetKey: String,
        val iconIndex: Int,
        val colorIndex: Int,
        val photoPath: String?,
        val originalUri: String? = null,
        val originalName: String? = null,
        val originalLocation: String? = null,
        val visible: Boolean = true
    )

    private val settings by lazy {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
    }
    private var editingShortcutId: String? = null

    private val targets = mutableListOf<Target>()
    private val iconNames = listOf("十字", "设置", "日历", "图表", "计时", "打卡", "历史", "相册")
    private val iconResources = listOf(
        android.R.drawable.ic_menu_view,
        android.R.drawable.ic_menu_manage,
        android.R.drawable.ic_menu_my_calendar,
        android.R.drawable.ic_menu_sort_by_size,
        android.R.drawable.ic_media_play,
        android.R.drawable.ic_menu_edit,
        android.R.drawable.ic_menu_recent_history,
        android.R.drawable.ic_menu_gallery
    )
    private val colorNames = listOf("深蓝", "青绿", "紫色", "橙色", "红色", "黑色")
    private val iconColors = listOf(
        Color.rgb(35, 93, 160), Color.rgb(0, 137, 123),
        Color.rgb(103, 80, 164), Color.rgb(230, 126, 34),
        Color.rgb(198, 55, 55), Color.rgb(45, 48, 55)
    )
    private lateinit var nameInput: EditText
    private lateinit var targetButton: Button
    private var selectedTargetKey: String? = null
    private lateinit var iconSpinner: Spinner
    private lateinit var colorSpinner: Spinner
    private lateinit var photoStatus: TextView
    private lateinit var saveButton: Button
    private var photoFile: File? = null
    private var originalPhotoUri: Uri? = null
    private var originalPhotoName: String? = null
    private var originalPhotoLocation: String? = null
    private lateinit var iconPreview: ImageView
    private lateinit var photoNameView: TextView
    private lateinit var photoLocationView: TextView
    private lateinit var shortcutsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the selected app theme before Activity/AppCompat initializes views.
        val component = (application as HabitsApplication).component
        val themeSwitcher = AndroidThemeSwitcher(this, component.preferences)
        themeSwitcher.apply()
        super.onCreate(savedInstanceState)
        title = "桌面快捷管理"
        // Only current, explicitly selected catalog entries are offered as targets.
        // Previously saved shortcut targets are not re-imported into the candidate list.
        targets.addAll(
            FeatureShortcutCatalog.getSelectedEntries(this)
                .map { Target(it.key, "【${it.category}】 ${it.title}") }
        )
        selectedTargetKey = targets.firstOrNull()?.key

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        // Resolve colors from the app theme because this screen is built programmatically.
        val styled = StyledResources(this)
        val pageBackground = styled.getColor(R.attr.contrast0)
        // Programmatic views on some Android skins can resolve theme attrs against
        // the pre-switch theme. Derive text contrast from the actual page background
        // so dark pages always get bright text and light pages get dark text.
        val isDarkBackground = ColorUtils.calculateLuminance(pageBackground) < 0.5
        val primaryText = if (isDarkBackground) Color.WHITE else Color.BLACK
        val secondaryText = if (isDarkBackground) Color.LTGRAY else Color.DKGRAY
        fun label(value: String) = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(if (ColorUtils.calculateLuminance(StyledResources(this@DesktopShortcutActivity).getColor(R.attr.contrast0)) < 0.5) Color.WHITE else Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(5))
        }
        fun spinner(items: List<String>) = Spinner(this).apply {
            val itemAdapter = object : ArrayAdapter<String>(
                this@DesktopShortcutActivity, android.R.layout.simple_spinner_item, items
            ) {
                private fun style(view: TextView, dropdown: Boolean): TextView {
                    view.setTextColor(primaryText)
                    view.textSize = 15f
                    view.setPadding(dp(12), dp(10), dp(12), dp(10))
                    view.setBackgroundColor(if (dropdown) pageBackground else Color.TRANSPARENT)
                    return view
                }
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val view = (convertView as? TextView) ?: TextView(this@DesktopShortcutActivity)
                    view.text = FeatureShortcutCatalog.styledTitle(getItem(position).orEmpty())
                    return style(view, false)
                }
                override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val view = (convertView as? TextView) ?: TextView(this@DesktopShortcutActivity)
                    view.text = FeatureShortcutCatalog.styledTitle(getItem(position).orEmpty())
                    return style(view, true)
                }
            }
            adapter = itemAdapter
            setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(pageBackground))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBackground)
            setPadding(dp(20), dp(12), dp(20), dp(24))
        }
        nameInput = EditText(this).apply {
            hint = "例如：白色数字权重"
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            setSingleLine(true)
            textSize = 16f
        }
        targetButton = Button(this).apply {
            text = selectedTargetTitle()
            setOnClickListener { chooseTarget() }
        }
        iconSpinner = spinner(iconNames)
        colorSpinner = spinner(colorNames)
        photoStatus = TextView(this).apply {
            text = "未选择相册图片（可选）"
            textSize = 12f
            setTextColor(if (ColorUtils.calculateLuminance(StyledResources(this).getColor(R.attr.contrast0)) < 0.5) Color.LTGRAY else Color.DKGRAY)
            setPadding(0, dp(6), 0, dp(6))
        }
        content.addView(label("快捷方式名称"))
        content.addView(nameInput)
        content.addView(label("打开目标"))
        content.addView(targetButton)

        val options = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val iconColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val colorColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        iconColumn.addView(label("内置图标")); iconColumn.addView(iconSpinner)
        colorColumn.addView(label("图标底色")); colorColumn.addView(colorSpinner)
        options.addView(iconColumn); options.addView(colorColumn); content.addView(options)

        val photoButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        photoButtons.addView(Button(this).apply {
            text = "从手机相册选择图片"; setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(126,87,194))
            layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(4) }
            setOnClickListener { choosePhoto() }
        })
        photoButtons.addView(Button(this).apply {
            text = "清除图片，改用内置图标"; setTextColor(Color.rgb(103,80,164))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(238,231,247))
            layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(4) }
            setOnClickListener {
                photoFile = null; originalPhotoUri = null; originalPhotoName = null; originalPhotoLocation = null
                iconSpinner.setSelection(0); updatePhotoInfo(); updateIconPreview()
            }
        })
        content.addView(photoButtons)

        val previewRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0,dp(8),0,dp(8)) }
        iconPreview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(0,dp(164),1f).apply { marginEnd = dp(8) }
        }
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0,dp(164),1f) }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(-1,0,1f); setPadding(dp(6),0,0,0) }
        photoNameView = TextView(this).apply {
            textSize = 14f; setTextColor(Color.rgb(103,80,164)); setTypeface(typeface,Typeface.BOLD)
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setOnClickListener { openOriginalPhoto() }
        }
        photoLocationView = TextView(this).apply {
            textSize = 11f; setTextColor(if (isDarkBackground) Color.LTGRAY else Color.rgb(135,127,145))
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setOnClickListener { openPhotoFolder() }
        }
        info.addView(photoNameView); info.addView(photoLocationView)
        val createAction = Button(this).apply {
            text = "创建并添加到桌面"; textSize = 13f; setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(126,87,194))
            layoutParams = LinearLayout.LayoutParams(-1,0,1f); setOnClickListener { createShortcut() }
        }
        right.addView(info); right.addView(createAction); previewRow.addView(iconPreview); previewRow.addView(right); content.addView(previewRow)
        saveButton = createAction
        iconSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { updateIconPreview() }
        }
        colorSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { updateIconPreview() }
        }
        updatePhotoInfo(); updateIconPreview()
        content.addView(label("已有桌面快捷方式"))
        shortcutsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(shortcutsContainer); refreshShortcutList()
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        setContentView(scroll)

    }


    private fun updatePhotoInfo() {
        if (!::photoNameView.isInitialized) return
        photoNameView.text = originalPhotoName ?: "未选择自定义图片"
        photoLocationView.text = originalPhotoLocation ?: "原图位置暂不可读取"
        photoNameView.alpha = if (originalPhotoUri != null) 1f else 0.55f
        photoLocationView.alpha = if (originalPhotoLocation != null) 1f else 0.55f
    }

    private fun updateIconPreview() {
        if (!::iconPreview.isInitialized) return
        runCatching { iconPreview.setImageBitmap(buildIcon()) }
    }

    private fun openOriginalPhoto() {
        val uri = originalPhotoUri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, contentResolver.getType(uri) ?: "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(Intent.createChooser(intent, "选择图片查看方式")) }
        catch (_: Exception) { Toast.makeText(this, "没有可用的图片查看应用", Toast.LENGTH_SHORT).show() }
    }

    private fun openPhotoFolder() {
        val location = originalPhotoLocation
        if (location.isNullOrBlank()) {
            Toast.makeText(this, "系统没有提供原始图片所在文件夹信息", Toast.LENGTH_SHORT).show()
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                val relative = location.trimStart('/').trimEnd('/')
                val documentId = if (relative.startsWith("primary:")) relative else "primary:$relative"
                val initialUri = android.provider.DocumentsContract.buildTreeDocumentUri(
                    "com.android.externalstorage.documents", documentId
                )
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                }
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "原始图片位置：" + location, Toast.LENGTH_LONG).show()
            }
        } else Toast.makeText(this, "原始图片位置：" + location, Toast.LENGTH_LONG).show()
    }

    private fun refreshShortcutList() {
        if (!::shortcutsContainer.isInitialized) return
        shortcutsContainer.removeAllViews()
        val entries = readShortcuts()
        val darkMode = ColorUtils.calculateLuminance(StyledResources(this).getColor(R.attr.contrast0)) < 0.5
        val listPrimaryText = if (darkMode) Color.WHITE else Color.BLACK
        val listSecondaryText = if (darkMode) Color.LTGRAY else Color.DKGRAY
        if (entries.isEmpty()) {
            shortcutsContainer.addView(TextView(this).apply { text = "暂无已创建的桌面快捷方式"; textSize = 12f; setTextColor(Color.GRAY) })
            return
        }
        entries.forEach { entry ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0,dp(4),0,dp(4)) }
            val check = CheckBox(this).apply {
                isChecked = entry.visible
                setOnCheckedChangeListener { _, checked ->
                    val updated = readShortcuts().map { if (it.id == entry.id) it.copy(visible = checked) else it }
                    saveShortcuts(updated)
                    val manager = getSystemService(ShortcutManager::class.java)
                    if (!checked) {
                        runCatching { manager?.disableShortcuts(listOf(entry.id), "已隐藏") }
                    } else {
                        runCatching {
                            val launch = Intent(this@DesktopShortcutActivity, org.isoron.uhabits.activities.habits.list.ListHabitsActivity::class.java).apply {
                                action = ACTION_OPEN_DESKTOP_SHORTCUT
                                putExtra(EXTRA_TARGET, entry.targetKey)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            val info = ShortcutInfo.Builder(this@DesktopShortcutActivity, entry.id)
                                .setShortLabel(entry.name.take(25)).setLongLabel(entry.name.take(40))
                                .setIcon(Icon.createWithBitmap(buildIcon(entry))).setIntent(launch).build()
                            manager?.requestPinShortcut(info, null)
                        }
                    }
                }
            }
            val rowIcon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(7) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(runCatching { buildIcon(entry) }.getOrNull())
            }
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0,-2,1f) }
            details.addView(TextView(this).apply { text = entry.name; textSize = 13f; setTextColor(listPrimaryText) })
            val target = targets.firstOrNull { it.key == entry.targetKey }?.title ?: entry.targetKey
            details.addView(TextView(this).apply { text = "打开：" + target.substringAfter("】",target); textSize = 10f; setTextColor(listSecondaryText); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            row.addView(check); row.addView(rowIcon); row.addView(details)
            row.addView(TextView(this).apply { text = "编辑"; textSize = 12f; setTextColor(Color.rgb(126,87,194)); setPadding(dp(6),dp(10),dp(6),dp(10)); setOnClickListener { editShortcut(entry) } })
            row.addView(TextView(this).apply { text = "删除"; textSize = 12f; setTextColor(Color.rgb(180,70,70)); setPadding(dp(6),dp(10),0,dp(10)); setOnClickListener { confirmDeleteShortcut(entry) } })
            shortcutsContainer.addView(row)
            shortcutsContainer.addView(View(this).apply { setBackgroundColor(if (darkMode) Color.DKGRAY else 0xFFE5E1E8.toInt()) }, LinearLayout.LayoutParams(-1,dp(1)))
        }
    }

    private fun selectedTargetTitle(): CharSequence {
        val target = targets.firstOrNull { it.key == selectedTargetKey }
        return target?.let { FeatureShortcutCatalog.styledTitle(it.title) } ?: "请选择打开目标"
    }

    private fun chooseTarget() {
        FeatureShortcutPicker.show(
            context = this,
            title = "选择桌面快捷方式的打开目标",
            entries = FeatureShortcutCatalog.getSelectedEntries(this),
            targetKey = { it.key }
        ) { key ->
            selectedTargetKey = key
            targetButton.text = selectedTargetTitle()
        }
    }

    private fun choosePhoto() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PHOTO)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PHOTO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalStateException("无法读取这张图片")
            originalPhotoUri = uri
            originalPhotoName = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
            originalPhotoLocation = runCatching {
                val columns = arrayOf(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.provider.MediaStore.Images.Media.DATA)
                contentResolver.query(uri, columns, null, null, null)?.use { c ->
                    if (!c.moveToFirst()) null else {
                        val relativeIndex = c.getColumnIndex(android.provider.MediaStore.Images.Media.RELATIVE_PATH)
                        val dataIndex = c.getColumnIndex(android.provider.MediaStore.Images.Media.DATA)
                        val relative = if (relativeIndex >= 0) c.getString(relativeIndex) else null
                        val data = if (dataIndex >= 0) c.getString(dataIndex) else null
                        relative?.takeIf { it.isNotBlank() } ?: data?.let { File(it).parent }
                    }
                }
            }.getOrNull()
            val file = File(filesDir, "desktop_shortcut_photo_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                cropSquare(bitmap, 512).compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            photoFile = file
            photoStatus.text = "已选择相册图片：${originalPhotoName ?: file.name}"
            iconSpinner.setSelection(iconNames.lastIndex)
            updatePhotoInfo(); updateIconPreview()
        } catch (error: Exception) {
            Toast.makeText(this, error.message ?: "图片读取失败", Toast.LENGTH_LONG).show()
        }
    }

    private fun cropSquare(source: Bitmap, size: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, Rect(x, y, x + side, y + side), Rect(0, 0, size, size), paint)
        return output
    }

    private fun createShortcut() {
        val name = nameInput.text.toString().trim()
        if (name.isBlank()) {
            nameInput.error = "请填写快捷方式名称"
            return
        }
        val target = targets.firstOrNull { it.key == selectedTargetKey }
        if (target == null) {
            Toast.makeText(this, "请先选择一个打开目标", Toast.LENGTH_SHORT).show()
            return
        }
        val manager = getSystemService(ShortcutManager::class.java)
        if (manager == null || !manager.isRequestPinShortcutSupported) {
            Toast.makeText(this, "当前手机桌面不支持应用内添加快捷方式，请尝试桌面长按添加。", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val id = editingShortcutId ?: "user_${UUID.randomUUID()}"
            val launchIntent = Intent(this, org.isoron.uhabits.activities.habits.list.ListHabitsActivity::class.java).apply {
                action = ACTION_OPEN_DESKTOP_SHORTCUT
                putExtra(EXTRA_TARGET, target.key)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val icon = buildIcon()
            val info = ShortcutInfo.Builder(this, id)
                .setShortLabel(name.take(25))
                .setLongLabel(name.take(40))
                .setIcon(Icon.createWithBitmap(icon))
                .setIntent(launchIntent)
                .build()
            val existing = manager.pinnedShortcuts.any { it.id == id }
            val accepted = if (existing) manager.updateShortcuts(listOf(info)) else manager.requestPinShortcut(info, null)
            if (accepted) {
                val entries = readShortcuts().toMutableList()
                val selectedPhotoPath = if (iconSpinner.selectedItemPosition == iconNames.lastIndex) {
                    photoFile?.absolutePath
                } else null
                val entry = ShortcutEntry(
                    id, name, target.key, iconSpinner.selectedItemPosition,
                    colorSpinner.selectedItemPosition, selectedPhotoPath,
                    originalPhotoUri?.toString(), originalPhotoName, originalPhotoLocation, true
                )
                val oldIndex = entries.indexOfFirst { it.id == id }
                val oldPhotoPath = entries.getOrNull(oldIndex)?.photoPath
                if (oldIndex >= 0) entries[oldIndex] = entry else entries.add(entry)
                saveShortcuts(entries)
                if (oldPhotoPath != null && oldPhotoPath != selectedPhotoPath) {
                    runCatching { File(oldPhotoPath).delete() }
                }
                Toast.makeText(this, if (existing) "桌面快捷方式已更新" else "已提交桌面添加请求，请在桌面确认", Toast.LENGTH_LONG).show()
                editingShortcutId = null
                saveButton.text = "创建并添加到桌面"
                refreshShortcutList()
                nameInput.text.clear()
                photoFile = null
                originalPhotoUri = null
                originalPhotoName = null
                originalPhotoLocation = null
                updatePhotoInfo()
                updateIconPreview()
            } else {
                Toast.makeText(this, "桌面没有接受添加请求。", Toast.LENGTH_LONG).show()
            }
        } catch (error: Exception) {
            Toast.makeText(this, error.message ?: "创建快捷方式失败", Toast.LENGTH_LONG).show()
        }
    }


    private fun readShortcuts(): List<ShortcutEntry> {
        val raw = settings.getString(KEY_SHORTCUTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                ShortcutEntry(
                    item.optString("id"),
                    item.optString("name"),
                    item.optString("target"),
                    item.optInt("icon", 0),
                    item.optInt("color", 0),
                    item.optString("photo").takeIf { it.isNotBlank() },
                    item.optString("originalUri").takeIf { it.isNotBlank() },
                    item.optString("originalName").takeIf { it.isNotBlank() },
                    item.optString("originalLocation").takeIf { it.isNotBlank() },
                    item.optBoolean("visible", true)
                ).takeIf { it.id.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveShortcuts(entries: List<ShortcutEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("name", entry.name)
                put("target", entry.targetKey)
                put("icon", entry.iconIndex)
                put("color", entry.colorIndex)
                put("photo", entry.photoPath ?: "")
                put("originalUri", entry.originalUri ?: "")
                put("originalName", entry.originalName ?: "")
                put("originalLocation", entry.originalLocation ?: "")
                put("visible", entry.visible)
            })
        }
        settings.edit().putString(KEY_SHORTCUTS, array.toString()).apply()
    }

    private fun showShortcutManager() {
        val entries = readShortcuts()
        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("桌面快捷方式管理")
                .setMessage("目前还没有由本功能创建的快捷方式。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }
        val labels = entries.mapIndexed { index, entry ->
            val rawTitle = targets.firstOrNull { it.key == entry.targetKey }?.title ?: entry.targetKey
            val targetTitle = if (rawTitle.startsWith("【")) rawTitle.substringAfter("】").trim() else rawTitle
            "${index + 1}. ${entry.name}  ·  $targetTitle"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("桌面快捷方式（选择一项管理）")
            .setItems(labels) { _, which -> showShortcutActions(entries, which) }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showShortcutActions(entries: List<ShortcutEntry>, index: Int) {
        val entry = entries.getOrNull(index) ?: return
        val actions = mutableListOf("编辑名称、目标和图标", "删除此快捷方式")
        if (index > 0) actions.add("上移")
        if (index < entries.lastIndex) actions.add("下移")
        AlertDialog.Builder(this)
            .setTitle(entry.name)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "编辑名称、目标和图标" -> editShortcut(entry)
                    "删除此快捷方式" -> confirmDeleteShortcut(entry)
                    "上移" -> moveShortcut(entries, index, index - 1)
                    "下移" -> moveShortcut(entries, index, index + 1)
                }
            }
            .setNegativeButton("返回") { _, _ -> showShortcutManager() }
            .show()
    }

    private fun editShortcut(entry: ShortcutEntry) {
        editingShortcutId = entry.id
        nameInput.setText(entry.name)
        selectedTargetKey = entry.targetKey
        targetButton.text = selectedTargetTitle()
        iconSpinner.setSelection(entry.iconIndex.coerceIn(iconNames.indices))
        colorSpinner.setSelection(entry.colorIndex.coerceIn(colorNames.indices))
        photoFile = entry.photoPath?.let(::File)?.takeIf { it.exists() }
        originalPhotoUri = entry.originalUri?.let(Uri::parse)
        originalPhotoName = entry.originalName
        originalPhotoLocation = entry.originalLocation
        photoStatus.text = if (photoFile != null) "已选择相册图片：${originalPhotoName ?: photoFile!!.name}" else "未选择相册图片（可选）"
        updatePhotoInfo(); updateIconPreview()
        saveButton.text = "保存修改并更新桌面图标"
        Toast.makeText(this, "已载入快捷方式，修改后点击下方按钮保存", Toast.LENGTH_LONG).show()
    }

    private fun confirmDeleteShortcut(entry: ShortcutEntry) {
        AlertDialog.Builder(this)
            .setTitle("删除桌面快捷方式")
            .setMessage("将从本应用的快捷方式管理列表中删除“${entry.name}”。如果它已固定在桌面，系统会尝试停用该图标；部分桌面可能仍需你手动移除。不会删除实际功能或任何数据。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                runCatching {
                    getSystemService(ShortcutManager::class.java)?.disableShortcuts(
                        listOf(entry.id), "此快捷方式已由应用删除"
                    )
                }
                saveShortcuts(readShortcuts().filterNot { it.id == entry.id })
                entry.photoPath?.let { runCatching { File(it).delete() } }
                Toast.makeText(this, "已从管理列表删除", Toast.LENGTH_SHORT).show()
                refreshShortcutList()
            }
            .show()
    }

    private fun moveShortcut(entries: List<ShortcutEntry>, from: Int, to: Int) {
        if (to !in entries.indices) return
        val updated = entries.toMutableList()
        val moved = updated.removeAt(from)
        updated.add(to, moved)
        saveShortcuts(updated)
        // This order is the management-list order. The launcher's physical icon
        // placement remains user-controlled by Android.
        showShortcutManager()
    }

    private fun buildIcon(entry: ShortcutEntry): Bitmap {
        val size = 192
        if (entry.iconIndex == iconNames.lastIndex) {
            entry.photoPath?.let { path -> BitmapFactory.decodeFile(path)?.let { return cropSquare(it, size) } }
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = iconColors[entry.colorIndex.coerceIn(iconColors.indices)] }
        canvas.drawRoundRect(RectF(0f,0f,size.toFloat(),size.toFloat()),42f,42f,paint)
        val drawable = getDrawable(iconResources[entry.iconIndex.coerceIn(iconResources.indices)])!!
        drawable.setTint(Color.WHITE); drawable.setBounds(42,42,size-42,size-42); drawable.draw(canvas)
        return bitmap
    }

    private fun buildIcon(): Bitmap {
        if (iconSpinner.selectedItemPosition == iconNames.lastIndex) {
            photoFile?.takeIf { it.exists() }?.let {
                BitmapFactory.decodeFile(it.absolutePath)?.let { bitmap -> return cropSquare(bitmap, 192) }
            }
        }
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val color = iconColors[colorSpinner.selectedItemPosition.coerceIn(iconColors.indices)]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 42f, 42f, paint)
        val drawable = getDrawable(iconResources[iconSpinner.selectedItemPosition.coerceIn(iconResources.indices)])!!
        drawable.setTint(Color.WHITE)
        val inset = 42
        drawable.setBounds(inset, inset, size - inset, size - inset)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        const val ACTION_OPEN_DESKTOP_SHORTCUT = "org.isoron.uhabits.action.OPEN_DESKTOP_SHORTCUT"
        const val EXTRA_TARGET = "desktop_shortcut_target"
        private const val REQUEST_PHOTO = 7124
        private const val KEY_SHORTCUTS = "desktop_shortcut_entries_v1"
    }
}
