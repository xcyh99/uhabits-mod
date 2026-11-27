package org.isoron.uhabits.activities.settings

import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.activities.AndroidThemeSwitcher

/** Per-slot icon settings for the fourth row of the digital-clock widget. */
class DigitalClockShortcutIconActivity : AppCompatActivity() {
    private val store by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private var slot = 1
    private var selectedUri: Uri? = null
    private var selectedName: String? = null
    private lateinit var preview: ImageView
    private lateinit var fileName: TextView
    private lateinit var mode: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as HabitsApplication).component.preferences.let { AndroidThemeSwitcher(this, it).apply() }
        super.onCreate(savedInstanceState)
        slot = intent.getIntExtra(DigitalClockShortcutManagementActivity.EXTRA_SLOT, 1).coerceIn(1, 8)
        title = "图标设置"
        selectedUri = store.getString(uriKey(), null)?.let(Uri::parse)
        selectedName = store.getString(nameKey(), null)
        val dark = androidx.core.graphics.ColorUtils.calculateLuminance(
            org.isoron.uhabits.utils.StyledResources(this).getColor(org.isoron.uhabits.R.attr.contrast0)
        ) < .5
        val primary = if (dark) Color.WHITE else Color.BLACK
        val secondary = if (dark) Color.LTGRAY else Color.DKGRAY
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(org.isoron.uhabits.utils.StyledResources(this@DigitalClockShortcutIconActivity).getColor(org.isoron.uhabits.R.attr.contrast0))
        }
        val previewRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(if (dark) Color.rgb(38, 40, 45) else Color.rgb(238, 239, 242))
            layoutParams = LinearLayout.LayoutParams(0, dp(164), 1f).apply { marginEnd = dp(8) }
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(0, dp(164), 1f)
        }
        controls.addView(Button(this).apply {
            text = "选择图片"
            layoutParams = LinearLayout.LayoutParams(-1, dp(44))
            setOnClickListener { chooseImage() }
        })
        fileName = TextView(this).apply {
            textSize = 12f
            setTextColor(primary)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(2), dp(6), dp(2), dp(4))
        }
        controls.addView(TextView(this).apply {
            text = "图片文件名"
            textSize = 11f
            setTextColor(secondary)
            setPadding(dp(2), dp(3), 0, 0)
        })
        controls.addView(fileName)
        controls.addView(TextView(this).apply {
            text = "图片显示方式"
            textSize = 11f
            setTextColor(secondary)
            setPadding(dp(2), dp(4), 0, 0)
        })
        mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@DigitalClockShortcutIconActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("完全适应", "拉伸填满", "居中裁剪"))
            setSelection(store.getInt(modeKey(), 0).coerceIn(0, 2))
        }
        controls.addView(mode, LinearLayout.LayoutParams(-1, dp(42)))
        controls.addView(android.widget.Space(this), LinearLayout.LayoutParams(-1, 0, 1f))
        mode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                preview.scaleType = when (position) {
                    1 -> ImageView.ScaleType.FIT_XY
                    2 -> ImageView.ScaleType.CENTER_CROP
                    else -> ImageView.ScaleType.FIT_CENTER
                }
            }
        }
        previewRow.addView(preview)
        previewRow.addView(controls)
        root.addView(previewRow)
        root.addView(Button(this).apply {
            text = "保存"
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) }
            setOnClickListener {
                store.edit()
                    .putString(uriKey(), selectedUri?.toString())
                    .putString(nameKey(), selectedName)
                    .putInt(modeKey(), mode.selectedItemPosition.coerceIn(0, 2))
                    .apply()
                updateWidget()
                finish()
            }
        })
        setContentView(root)
        updatePreview()
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
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalStateException("无法读取图片")
            selectedUri = uri
            selectedName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: uri.lastPathSegment
            updatePreview()
        }.onFailure { Toast.makeText(this, it.message ?: "图片读取失败", Toast.LENGTH_LONG).show() }
    }

    private fun updatePreview() {
        fileName.text = selectedName ?: "使用默认图标"
        preview.scaleType = when (mode.selectedItemPosition) {
            1 -> ImageView.ScaleType.FIT_XY
            2 -> ImageView.ScaleType.CENTER_CROP
            else -> ImageView.ScaleType.FIT_CENTER
        }
        val bitmap = selectedUri?.let { uri ->
            runCatching { contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream) } }.getOrNull()
        }
        if (bitmap != null) {
            preview.setImageBitmap(bitmap)
        } else {
            val target = store.getString("digital_clock_target_shortcut$slot", null)
            val drawable = when {
                target?.startsWith("feature:") == true ->
                    FeatureShortcutCatalog.getCachedEntry(this, target.removePrefix("feature:"))?.icon
                target?.startsWith("app:") == true -> {
                    val component = android.content.ComponentName.unflattenFromString(target.removePrefix("app:"))
                    component?.let { runCatching { packageManager.getActivityInfo(it, 0).loadIcon(packageManager) }.getOrNull() }
                }
                target == "system:settings" -> getDrawable(android.R.drawable.ic_menu_manage)
                else -> getDrawable(android.R.drawable.ic_menu_view)
            }
            preview.setImageDrawable(drawable ?: getDrawable(android.R.drawable.ic_menu_view))
        }
    }

    private fun updateWidget() {
        val manager = android.appwidget.AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, org.isoron.uhabits.widgets.DigitalClockWidgetProvider::class.java))
        if (ids.isNotEmpty()) org.isoron.uhabits.widgets.DigitalClockWidgetProvider.updateAll(this, manager, ids)
    }

    private fun uriKey() = "digital_clock_shortcut_icon_uri_$slot"
    private fun nameKey() = "digital_clock_shortcut_icon_name_$slot"
    private fun modeKey() = "digital_clock_shortcut_icon_mode_$slot"
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { private const val REQUEST_IMAGE = 7171 }
}
