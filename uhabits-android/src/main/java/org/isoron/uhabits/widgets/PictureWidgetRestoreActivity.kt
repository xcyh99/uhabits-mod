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
            text = "这里列出完全备份中保存的桌面组件配置。选择一项后，应用会将原来的设置和关联关系恢复到新添加的组件；图片组件需要重新选择图片。"
            textSize = 13f
            setTextColor(if (dark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY)
            setPadding(0, 0, 0, dp(12))
        })
        // Migrate any pending picture-only queue created by an earlier 990B3 build
        // into the shared restore queue before rendering the unified wizard.
        val legacyPictureConfigs = PictureWidgetRestoreStore.pending(this)
        if (legacyPictureConfigs.isNotEmpty()) {
            val providerClass = PictureShortcutWidgetProvider::class.java.name
            WidgetRestoreStore.mergePending(this, legacyPictureConfigs.map { item ->
                WidgetRestoreStore.Config(
                    sourceWidgetId = item.sourceWidgetId,
                    providerClass = providerClass,
                    habitIds = emptyList(),
                    type = "picture",
                    pictureLeft = item.left,
                    pictureRight = item.right,
                    pictureSplit = item.split,
                    pictureImageMode = item.imageMode
                )
            })
            PictureWidgetRestoreStore.replacePending(this, emptyList())
        }
        val configs = PictureWidgetRestoreStore.pending(this)
        if (configs.isEmpty() && WidgetRestoreStore.pending(this).none { it.type == "picture" }) {
            root.addView(TextView(this).apply {
                text = "没有待恢复的图片组件配置。请先导入包含组件设置的完全备份。"
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


        val pictureConfigs = WidgetRestoreStore.pending(this).filter { it.type == "picture" }
        if (pictureConfigs.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = "图片组件"
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                setPadding(0, dp(14), 0, dp(8))
            })
            pictureConfigs.forEachIndexed { index, config ->
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
                card.addView(Button(this).apply {
                    text = "添加并恢复此组件"
                    setOnClickListener { requestHabitRestore(config) }
                })
                root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
        }

        val habitConfigs = WidgetRestoreStore.pending(this).filter { it.type == "habit" }
        if (habitConfigs.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = "习惯类组件"
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                setPadding(0, dp(14), 0, dp(8))
            })
            val catalog = WidgetCatalog.discover(this).associateBy { it.provider.className }
            habitConfigs.forEachIndexed { index, config ->
                val title = catalog[config.providerClass]?.title
                    ?: config.providerClass.substringAfterLast('.')
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (dark) 0xFF2D3037.toInt() else android.graphics.Color.WHITE)
                        cornerRadius = dp(12).toFloat()
                    }
                }
                card.addView(TextView(this).apply {
                    text = "${title} ${index + 1}（原组件编号 ${config.sourceWidgetId}）"
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                })
                card.addView(TextView(this).apply {
                    text = "已保存原来选择的 ${config.habitIds.size} 个习惯。添加到桌面后会自动绑定到新组件。"
                    textSize = 12f
                    setPadding(0, dp(5), 0, dp(9))
                    setTextColor(if (dark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY)
                })
                card.addView(Button(this).apply {
                    text = "添加并恢复此组件"
                    setOnClickListener { requestHabitRestore(config) }
                })
                root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
        }


        val timeRecordConfigs = WidgetRestoreStore.pending(this).filter { it.type == "time_record" }
        if (timeRecordConfigs.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = "时间记录组件"
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                setPadding(0, dp(14), 0, dp(8))
            })
            timeRecordConfigs.forEachIndexed { index, config ->
                val record = config.recordId?.let {
                    org.isoron.uhabits.activities.timerecords.TimeRecordStore.get(this@PictureWidgetRestoreActivity, it)
                }
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (dark) 0xFF2D3037.toInt() else android.graphics.Color.WHITE)
                        cornerRadius = dp(12).toFloat()
                    }
                }
                card.addView(TextView(this).apply {
                    text = "时间记录组件 ${index + 1}（原组件编号 ${config.sourceWidgetId}）"
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                })
                card.addView(TextView(this).apply {
                    text = if (record != null) "将重新绑定时间记录：${record.title}" else "原时间记录编号：${config.recordId}（请先确认记录数据已恢复）"
                    textSize = 12f
                    setPadding(0, dp(5), 0, dp(9))
                    setTextColor(if (dark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY)
                })
                card.addView(Button(this).apply {
                    text = "添加并恢复此组件"
                    setOnClickListener { requestHabitRestore(config) }
                })
                root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
        }

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }


    private fun requestHabitRestore(config: WidgetRestoreStore.Config) {
        val manager = AppWidgetManager.getInstance(this)
        val component = ComponentName(packageName, config.providerClass)
        if (!manager.isRequestPinAppWidgetSupported) {
            WidgetRestoreStore.select(this, config.sourceWidgetId)
            Toast.makeText(
                this,
                "当前桌面不支持应用内添加。请手动添加对应的习惯组件，配置窗口会自动载入恢复设置。",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        WidgetRestoreStore.setBaselineWidgetIds(
            this, config.sourceWidgetId, config.providerClass,
            manager.getAppWidgetIds(component)
        )
        val callbackIntent = Intent(this, WidgetRestoreCallbackActivity::class.java)
            .putExtra(WidgetRestoreStore.EXTRA_RESTORE_SOURCE_WIDGET_ID, config.sourceWidgetId)
            .putExtra(WidgetRestoreCallbackActivity.EXTRA_PROVIDER_CLASS, config.providerClass)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val callback = PendingIntent.getActivity(
            this,
            31 * config.sourceWidgetId + config.providerClass.hashCode(),
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val accepted = runCatching {
            manager.requestPinAppWidget(component, null, callback)
        }.getOrDefault(false)
        if (accepted) {
            restoreButtons.forEach { it.isEnabled = false }
            Toast.makeText(this, "已请求桌面添加习惯组件；确认后会自动恢复原来的习惯选择。", Toast.LENGTH_LONG).show()
            window.decorView.post { moveTaskToBack(true) }
        } else {
            Toast.makeText(this, "桌面未接受添加请求，原恢复记录仍保留。", Toast.LENGTH_LONG).show()
        }
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
