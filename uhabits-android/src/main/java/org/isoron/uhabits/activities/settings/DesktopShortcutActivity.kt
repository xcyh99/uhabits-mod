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
    private data class Target(val key: String, val title: String)
    private data class ShortcutEntry(
        val id: String,
        val name: String,
        val targetKey: String,
        val iconIndex: Int,
        val colorIndex: Int,
        val photoPath: String?
    )

    private val settings by lazy {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
    }
    private var editingShortcutId: String? = null

    private val targets = mutableListOf<Target>()
    private val visibleTargets = mutableListOf<Target>()
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
    private lateinit var targetSpinner: Spinner
    private lateinit var iconSpinner: Spinner
    private lateinit var colorSpinner: Spinner
    private lateinit var photoStatus: TextView
    private lateinit var saveButton: Button
    private var photoFile: File? = null

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
        visibleTargets.addAll(targets)

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
            setTextColor(primaryText)
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
        targetSpinner = spinner(visibleTargets.map { it.title })
        val targetSearch = EditText(this).apply {
            hint = "搜索目标名称或分类"
            setSingleLine(true)
            textSize = 15f
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString()?.trim()?.lowercase().orEmpty()
                    visibleTargets.clear()
                    visibleTargets.addAll(targets.filter { target ->
                        query.isEmpty() || target.title.lowercase().contains(query) || target.key.lowercase().contains(query)
                    })
                    targetSpinner.adapter = spinner(visibleTargets.map { it.title }).adapter
                    if (visibleTargets.isNotEmpty()) targetSpinner.setSelection(0)
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        iconSpinner = spinner(iconNames)
        colorSpinner = spinner(colorNames)
        photoStatus = TextView(this).apply {
            text = "未选择相册图片（可选）"
            textSize = 12f
            setTextColor(secondaryText)
            setPadding(0, dp(6), 0, dp(6))
        }
        content.addView(Button(this).apply {
            text = "管理已有桌面快捷方式"
            setOnClickListener { showShortcutManager() }
        })
        content.addView(label("快捷方式名称"))
        content.addView(nameInput)
        content.addView(label("打开目标"))
        content.addView(targetSearch, LinearLayout.LayoutParams(-1, -2))
        content.addView(targetSpinner)
        content.addView(label("内置图标"))
        content.addView(iconSpinner)
        content.addView(label("图标底色"))
        content.addView(colorSpinner)
        content.addView(Button(this).apply {
            text = "从手机相册选择图片"
            setOnClickListener { choosePhoto() }
        })
        content.addView(Button(this).apply {
            text = "清除已选图片，改用内置图标"
            setOnClickListener {
                // Defer deleting the previous image until the edited shortcut is saved.
                photoFile = null
                photoStatus.text = "未选择相册图片（可选）"
                iconSpinner.setSelection(0)
            }
        })
        content.addView(photoStatus)
        content.addView(TextView(this).apply {
            text = "创建后，Android 桌面会弹出确认界面。确认后图标才会添加到桌面。你可以重复创建多个快捷方式。"
            textSize = 12f
            setTextColor(secondaryText)
            setPadding(0, dp(10), 0, dp(14))
        })
        saveButton = Button(this).apply {
            text = "创建并添加到桌面"
            setOnClickListener { createShortcut() }
        }
        content.addView(saveButton)
        setContentView(content)
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
            val file = File(filesDir, "desktop_shortcut_photo_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                cropSquare(bitmap, 512).compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            photoFile = file
            photoStatus.text = "已选择相册图片：${file.name}"
            iconSpinner.setSelection(iconNames.lastIndex)
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
        val target = visibleTargets.getOrNull(targetSpinner.selectedItemPosition) ?: return
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
                    colorSpinner.selectedItemPosition, selectedPhotoPath
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
                finish()
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
                    item.optString("photo").takeIf { it.isNotBlank() }
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
        targetSpinner.setSelection(visibleTargets.indexOfFirst { it.key == entry.targetKey }.coerceAtLeast(0))
        iconSpinner.setSelection(entry.iconIndex.coerceIn(iconNames.indices))
        colorSpinner.setSelection(entry.colorIndex.coerceIn(colorNames.indices))
        photoFile = entry.photoPath?.let(::File)?.takeIf { it.exists() }
        photoStatus.text = if (photoFile != null) "已选择相册图片：${photoFile!!.name}" else "未选择相册图片（可选）"
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
                showShortcutManager()
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
