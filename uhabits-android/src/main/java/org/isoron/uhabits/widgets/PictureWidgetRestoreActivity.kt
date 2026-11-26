package org.isoron.uhabits.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.activities.AndroidThemeSwitcher

/** Re-creates Android widget instances from portable configuration records. */
class PictureWidgetRestoreActivity : AppCompatActivity() {
    private val restoreButtons = mutableListOf<Button>()

    override fun onResume() {
        super.onResume()
        restoreButtons.forEach { it.isEnabled = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as HabitsApplication).component.preferences.let { AndroidThemeSwitcher(this, it).apply() }
        super.onCreate(savedInstanceState)
        title = "组件恢复向导"
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
            setBackgroundColor(if (dark) 0xFF191B1F.toInt() else 0xFFF6F7F9.toInt())
        }
        root.addView(TextView(this).apply {
            text = "这里列出完全备份中保存的图片组件配置。选择一项后，将新的图片组件添加到桌面，应用会把原来的点击目标、分界比例和显示方式绑定到新组件。"
            textSize = 13f
            setTextColor(if (dark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY)
            setPadding(0, 0, 0, dp(12))
        })
        val configs = PictureWidgetRestoreStore.pending(this)
        if (configs.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "没有待恢复的图片组件配置。请先导入包含图片组件设置的完全备份。"
                textSize = 15f
                setTextColor(if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                setPadding(dp(12), dp(18), dp(12), dp(18))
            })
        } else {
            configs.forEachIndexed { index, config ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (dark) 0xFF2D3037.toInt() else android.graphics.Color.WHITE)
                        cornerRadius = dp(12).toFloat()
                    }
                }
                card.addView(TextView(this).apply {
                    text = "图片组件 ${index + 1}（原组件编号 ${config.sourceWidgetId}）"
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                })
                card.addView(TextView(this).apply {
                    text = "点击目标、分界比例和显示方式已保存；轻量备份不含图片文件，需要重新选择图片。"
                    textSize = 12f
                    setPadding(0, dp(5), 0, dp(9))
                    setTextColor(if (dark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY)
                })
                val restoreButton = Button(this).apply {
                    text = "添加并恢复此组件"
                    setOnClickListener { requestRestore(config.sourceWidgetId) }
                }
                restoreButtons += restoreButton
                card.addView(restoreButton)
                root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun requestRestore(sourceWidgetId: Int) {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            PictureWidgetRestoreStore.select(this, sourceWidgetId)
            Toast.makeText(this, "当前桌面不支持应用内添加。请到桌面手动添加“图片快捷组件”，配置窗口会自动载入这项恢复设置。", Toast.LENGTH_LONG).show()
            return
        }

        // requestPinAppWidget does NOT launch the provider's configure activity.
        // Its success callback must open the configuration screen and carry the
        // newly allocated widget ID together with this source configuration ID.
        val callbackIntent = Intent(this, PictureShortcutWidgetConfigureActivity::class.java)
            .putExtra(PictureWidgetRestoreStore.EXTRA_RESTORE_SOURCE_WIDGET_ID, sourceWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val callback = PendingIntent.getActivity(
            this,
            sourceWidgetId,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val accepted = runCatching {
            manager.requestPinAppWidget(
                ComponentName(this, PictureShortcutWidgetProvider::class.java),
                null,
                callback
            )
        }.getOrDefault(false)

        if (accepted) {
            restoreButtons.forEach { it.isEnabled = false }
            Toast.makeText(this, "已请求桌面添加；确认后会自动打开配置页。请逐个恢复组件。", Toast.LENGTH_LONG).show()
            window.decorView.post { moveTaskToBack(true) }
        } else {
            PictureWidgetRestoreStore.select(this, sourceWidgetId)
            Toast.makeText(this, "桌面未接受应用内添加请求。请手动添加图片快捷组件，配置窗口会载入这项恢复设置。", Toast.LENGTH_LONG).show()
        }
    }
}
