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
import org.isoron.uhabits.R
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DesktopShortcutActivity : AppCompatActivity() {
    private data class Target(val key: String, val title: String)

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
    private lateinit var targetSpinner: Spinner
    private lateinit var iconSpinner: Spinner
    private lateinit var colorSpinner: Spinner
    private lateinit var photoStatus: TextView
    private var photoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "桌面快捷"
        targets.add(Target("__feature_center", "整个功能中心"))
        targets.add(Target("__settings", "设置首页"))
        targets.addAll(FeatureShortcutCatalog.getEntries(this).map { Target(it.key, it.title) })
        targets.add(Target("historyWhiteWeight", "历史组件白色数字权重"))

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun label(value: String) = TextView(this).apply {
            text = value
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(5))
        }
        fun spinner(items: List<String>) = Spinner(this).apply {
            adapter = ArrayAdapter(this@DesktopShortcutActivity, android.R.layout.simple_spinner_dropdown_item, items)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
        }
        nameInput = EditText(this).apply {
            hint = "例如：白色数字权重"
            setSingleLine(true)
            textSize = 16f
        }
        targetSpinner = spinner(targets.map { it.title })
        iconSpinner = spinner(iconNames)
        colorSpinner = spinner(colorNames)
        photoStatus = TextView(this).apply {
            text = "未选择相册图片（可选）"
            textSize = 12f
            setPadding(0, dp(6), 0, dp(6))
        }
        content.addView(label("快捷方式名称"))
        content.addView(nameInput)
        content.addView(label("打开目标"))
        content.addView(targetSpinner)
        content.addView(label("内置图标"))
        content.addView(iconSpinner)
        content.addView(label("图标底色"))
        content.addView(colorSpinner)
        content.addView(Button(this).apply {
            text = "从手机相册选择图片"
            setOnClickListener { choosePhoto() }
        })
        content.addView(photoStatus)
        content.addView(TextView(this).apply {
            text = "创建后，Android 桌面会弹出确认界面。确认后图标才会添加到桌面。你可以重复创建多个快捷方式。"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(10), 0, dp(14))
        })
        content.addView(Button(this).apply {
            text = "创建并添加到桌面"
            setOnClickListener { createShortcut() }
        })
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
        val target = targets.getOrNull(targetSpinner.selectedItemPosition) ?: return
        val manager = getSystemService(ShortcutManager::class.java)
        if (manager == null || !manager.isRequestPinShortcutSupported) {
            Toast.makeText(this, "当前手机桌面不支持应用内添加快捷方式，请尝试桌面长按添加。", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val id = "user_${UUID.randomUUID()}"
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
            if (manager.requestPinShortcut(info, null)) {
                Toast.makeText(this, "已提交桌面添加请求，请在桌面确认。", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this, "桌面没有接受添加请求。", Toast.LENGTH_LONG).show()
            }
        } catch (error: Exception) {
            Toast.makeText(this, error.message ?: "创建快捷方式失败", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildIcon(): Bitmap {
        photoFile?.takeIf { it.exists() }?.let {
            BitmapFactory.decodeFile(it.absolutePath)?.let { bitmap -> return cropSquare(bitmap, 192) }
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
    }
}
