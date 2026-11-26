package org.isoron.uhabits.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
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
        restoreManuallyAddedSimpleWidget()
    }

    private fun restoreManuallyAddedSimpleWidget() {
        val config = WidgetRestoreStore.selected(this)?.takeIf { it.type == "simple" } ?: return
        val manager = AppWidgetManager.getInstance(this)
        val component = ComponentName(packageName, config.providerClass)
        val newIds = manager.getAppWidgetIds(component).toSet() - config.baselineWidgetIds.toSet()
        if (newIds.size != 1) return
        val widgetId = newIds.first()
        runCatching {
            val provider = Class.forName(config.providerClass)
                .getDeclaredConstructor().newInstance() as android.appwidget.AppWidgetProvider
            provider.onUpdate(this, manager, intArrayOf(widgetId))
        }.onSuccess {
            WidgetRestoreStore.recordSuccessfulRestore(this, config)
            WidgetRestoreStore.consume(this, config.sourceWidgetId, config.providerClass)
            WidgetRestoreStore.clearSelection(this, config.sourceWidgetId)
            Toast.makeText(this, "组件已识别，恢复完成。", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "已识别新组件，但初始化失败；恢复记录仍保留。", Toast.LENGTH_LONG).show()
        }
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
            text = "这里列出完全备份中保存的桌面组件配置。恢复会创建新的组件；图片需要重新选择。历史恢复次数仅保存在本机。"
            textSize = 12f
            setTextColor(if (dark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY)
            setPadding(0, 0, 0, dp(12))
        })
        root.addView(TextView(this).apply {
            text = "显示上次导入完全备份后保存的记录；进入本页不会重新扫描备份文件。"
            textSize = 11f
            setTextColor(if (dark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY)
            setPadding(0, 0, 0, dp(6))
        })
        root.addView(Button(this).apply {
            text = "读取待恢复组件"
            setOnClickListener {
                val count = WidgetRestoreStore.pending(this@PictureWidgetRestoreActivity).size +
                    PictureWidgetRestoreStore.pending(this@PictureWidgetRestoreActivity).size
                Toast.makeText(
                    this@PictureWidgetRestoreActivity,
                    "读取完成：本机存档中有 $count 项待恢复组件配置。",
                    Toast.LENGTH_LONG
                ).show()
                recreate()
            }
        })
        // Migrate any pending picture-only queue created by an earlier 990B3 build
        // into the shared restore queue before rendering the unified wizard.
        val legacyPictureConfigs = PictureWidgetRestoreStore.pending(this)
        if (legacyPictureConfigs.isNotEmpty()) {
            val providerClass = PictureShortcutWidgetProvider::class.java.name
            val currentGenericKeys = WidgetRestoreStore.pending(this)
                .filter { it.type == "picture" }
                .map { "${it.providerClass}#${it.sourceWidgetId}" }
                .toSet()
            val legacyToMigrate = legacyPictureConfigs.filterNot {
                "${providerClass}#${it.sourceWidgetId}" in currentGenericKeys
            }
            WidgetRestoreStore.mergePending(this, legacyToMigrate.map { item ->
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
        val pending = WidgetRestoreStore.pending(this)
        val history = WidgetRestoreStore.historyRecords(this)
        fun restoreTitle(type: String, providerClass: String): String = when (type) {
            "picture" -> "图片组件"
            "habit" -> providerClass.substringAfterLast('.')
                .removeSuffix("WidgetProvider").replace(Regex("([a-z])([A-Z])"), "\$1 \$2") + "组件"
            "time_record" -> "时间记录组件"
            "simple" -> when (providerClass) {
                DigitalClockWidgetProvider::class.java.name -> "数字时钟组件"
                BatchAddWidgetProvider::class.java.name -> "批量添加组件"
                else -> providerClass.substringAfterLast('.')
            }
            else -> providerClass.substringAfterLast('.')
        }

        if (pending.isEmpty() && history.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "没有待恢复的桌面组件配置，也没有历史恢复记录。请先导入包含组件设置的完全备份。"
                textSize = 15f
                setTextColor(if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                setPadding(dp(12), dp(18), dp(12), dp(18))
            })
        } else {
            fun showInfoBubble(
                anchor: View,
                count: Int,
                detailText: String,
                statusText: String = "等待恢复",
                statusDescription: String = "恢复配置已读取，等待添加新的桌面组件。"
            ) {
                val popupWidth = minOf(dp(286), resources.displayMetrics.widthPixels - dp(40))
                val bubbleColor = if (dark) 0xFF30343B.toInt() else android.graphics.Color.WHITE
                val primaryText = if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                val secondaryText = if (dark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY
                val accent = if (dark) 0xFF90CAF9.toInt() else android.graphics.Color.rgb(33, 120, 195)

                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(2), 0, dp(2), dp(2))
                }
                container.addView(object : View(this) {
                    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bubbleColor }
                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(width.toFloat(), 0f)
                            lineTo(width / 2f, height.toFloat())
                            close()
                        }
                        canvas.drawPath(path, paint)
                    }
                }, LinearLayout.LayoutParams(dp(16), dp(8)).apply {
                    gravity = Gravity.END
                    rightMargin = dp(20)
                })

                val panel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(bubbleColor)
                        cornerRadius = dp(12).toFloat()
                        setStroke(dp(1), if (dark) 0xFF555B66.toInt() else 0xFFD7E3EF.toInt())
                    }
                    elevation = dp(6).toFloat()
                }
                panel.addView(TextView(this).apply {
                    text = "组件信息"
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(primaryText)
                })
                panel.addView(TextView(this).apply {
                    text = "备份内容"
                    textSize = 11f
                    setPadding(0, dp(8), 0, dp(2))
                    setTextColor(accent)
                })
                panel.addView(TextView(this).apply {
                    text = detailText
                    textSize = 12f
                    setTextColor(secondaryText)
                })
                panel.addView(View(this).apply {
                    setBackgroundColor(if (dark) 0xFF50545C.toInt() else 0xFFE8EBEF.toInt())
                }, LinearLayout.LayoutParams(-1, dp(1)).apply {
                    topMargin = dp(9)
                    bottomMargin = dp(8)
                })
                panel.addView(TextView(this).apply {
                    text = "当前状态 · " + statusText
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(accent)
                })
                panel.addView(TextView(this).apply {
                    text = statusDescription
                    textSize = 12f
                    setPadding(0, dp(3), 0, 0)
                    setTextColor(secondaryText)
                })
                panel.addView(View(this).apply {
                    setBackgroundColor(if (dark) 0xFF50545C.toInt() else 0xFFE8EBEF.toInt())
                }, LinearLayout.LayoutParams(-1, dp(1)).apply {
                    topMargin = dp(9)
                    bottomMargin = dp(8)
                })
                panel.addView(TextView(this).apply {
                    text = "历史成功恢复：" + count + " 次"
                    textSize = 12f
                    setTextColor(secondaryText)
                })
                container.addView(panel)

                val popup = PopupWindow(container, popupWidth, -2, true).apply {
                    setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    isOutsideTouchable = true
                    elevation = dp(8).toFloat()
                }
                container.setOnClickListener { popup.dismiss() }
                popup.showAsDropDown(anchor, 0, dp(2), Gravity.END)
            }

            fun addSection(
                sectionTitle: String,
                titleColor: Int,
                items: List<WidgetRestoreStore.Config>,
                detail: (WidgetRestoreStore.Config) -> String
            ) {
                if (items.isEmpty()) return
                root.addView(TextView(this).apply {
                    text = sectionTitle
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(titleColor)
                    setPadding(0, dp(11), 0, dp(6))
                })
                items.forEachIndexed { index, config ->
                    val title = restoreTitle(config.type, config.providerClass)
                    val historyRecord = history.firstOrNull {
                        it.sourceWidgetId == config.sourceWidgetId && it.providerClass == config.providerClass
                    }
                    val count = historyRecord?.count ?: 0
                    val recordTitle = if (config.type == "time_record") {
                        config.recordId?.let { recordId ->
                            org.isoron.uhabits.activities.timerecords.TimeRecordStore
                                .get(this@PictureWidgetRestoreActivity, recordId)?.title
                        } ?: "记录待核对"
                    } else null

                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), dp(9), dp(12), dp(9))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(if (dark) 0xFF2D3037.toInt() else android.graphics.Color.WHITE)
                            cornerRadius = dp(12).toFloat()
                        }
                    }

                    val titleRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    titleRow.addView(TextView(this).apply {
                        text = if (recordTitle != null) title + " " + (index + 1) + " · " + recordTitle
                            else title + " " + (index + 1)
                        textSize = 14f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                    }, LinearLayout.LayoutParams(0, -2, 1f))

                    val info = TextView(this).apply {
                        text = "ⓘ"
                        textSize = 19f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(if (dark) 0xFF90CAF9.toInt() else android.graphics.Color.rgb(33, 120, 195))
                        contentDescription = "查看" + title + "恢复说明"
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            showInfoBubble(this, count, detail(config))
                        }
                    }
                    titleRow.addView(info, LinearLayout.LayoutParams(dp(34), dp(34)))
                    titleRow.addView(TextView(this).apply {
                        text = "原编号 " + config.sourceWidgetId
                        textSize = 10f
                        setTextColor(android.graphics.Color.GRAY)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    })
                    card.addView(titleRow)

                    val actionRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    actionRow.addView(TextView(this).apply {
                        text = "历史恢复 " + count + " 次"
                        textSize = 12f
                        setTextColor(if (dark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY)
                    }, LinearLayout.LayoutParams(0, dp(38), 1f))

                    val clearButton = Button(this).apply {
                        text = "清零"
                        textSize = 11f
                        isAllCaps = false
                        isEnabled = count > 0
                        minWidth = 0
                        minimumWidth = 0
                        minHeight = dp(34)
                        minimumHeight = dp(34)
                        setPadding(dp(2), 0, dp(2), 0)
                        setOnClickListener {
                            WidgetRestoreStore.clearHistory(this@PictureWidgetRestoreActivity,
                                config.sourceWidgetId, config.providerClass)
                            recreate()
                        }
                    }
                    actionRow.addView(clearButton, LinearLayout.LayoutParams(dp(62), dp(38)).apply {
                        marginEnd = dp(6)
                    })

                    val button = Button(this).apply {
                        text = "添加并恢复"
                        textSize = 11f
                        isAllCaps = false
                        minWidth = 0
                        minimumWidth = 0
                        minHeight = dp(34)
                        minimumHeight = dp(34)
                        setPadding(dp(2), 0, dp(2), 0)
                        setOnClickListener { requestHabitRestore(config) }
                    }
                    restoreButtons += button
                    actionRow.addView(button, LinearLayout.LayoutParams(dp(116), dp(38)))
                    card.addView(actionRow)

                    root.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(7)
                    })
                }
            }

            addSection("图片组件", android.graphics.Color.rgb(33, 150, 243), pending.filter { it.type == "picture" }) {
                "点击目标、分界比例和显示方式已保存；轻量备份不含图片文件，恢复时需要重新选择图片。"
            }
            addSection("习惯类组件", android.graphics.Color.rgb(76, 175, 80), pending.filter { it.type == "habit" }) {
                "已保存原来选择的 ${it.habitIds.size} 个习惯，添加后会自动绑定到新组件。"
            }
            addSection("时间记录组件", android.graphics.Color.rgb(255, 152, 0), pending.filter { it.type == "time_record" }) {
                val record = it.recordId?.let { id ->
                    org.isoron.uhabits.activities.timerecords.TimeRecordStore.get(this@PictureWidgetRestoreActivity, id)
                }
                if (record != null) "将重新绑定时间记录：${record.title}"
                else "原时间记录编号：${it.recordId ?: "未知"}（请确认记录数据已恢复）"
            }
            addSection("其他组件", android.graphics.Color.rgb(171, 71, 188), pending.filter { it.type == "simple" }) {
                "组件实例数量和类型已保存；数字时钟样式等应用级设置会随完全备份恢复。"
            }

            val pendingKeys = pending.map { "${it.providerClass}#${it.sourceWidgetId}" }.toSet()
            val historyOnly = history.filterNot { "${it.providerClass}#${it.sourceWidgetId}" in pendingKeys }
            if (historyOnly.isNotEmpty()) {
                root.addView(TextView(this).apply {
                    text = "历史恢复记录"
                    textSize = 17f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                    setPadding(0, dp(14), 0, dp(8))
                })
                historyOnly.forEach { record ->
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), dp(9), dp(12), dp(9))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(if (dark) 0xFF2D3037.toInt() else android.graphics.Color.WHITE)
                            cornerRadius = dp(12).toFloat()
                        }
                    }
                    val titleRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    titleRow.addView(TextView(this).apply {
                        text = restoreTitle(record.type, record.providerClass)
                        textSize = 14f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    val info = TextView(this).apply {
                        text = "ⓘ"
                        textSize = 19f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(if (dark) 0xFF90CAF9.toInt() else android.graphics.Color.rgb(33, 120, 195))
                        contentDescription = "查看历史恢复说明"
                        setOnClickListener {
                            showInfoBubble(
                                this,
                                record.count,
                                "当前没有待恢复配置；这里仅保留本机历史成功恢复次数。",
                                "暂无待恢复配置",
                                "重新导入包含该组件设置的完全备份后，可以再次恢复。"
                            )
                        }
                    }
                    titleRow.addView(info, LinearLayout.LayoutParams(dp(34), dp(34)))
                    titleRow.addView(TextView(this).apply {
                        text = "原编号 " + record.sourceWidgetId
                        textSize = 10f
                        setTextColor(android.graphics.Color.GRAY)
                    })
                    card.addView(titleRow)

                    val actionRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    actionRow.addView(TextView(this).apply {
                        text = "历史恢复 " + record.count + " 次"
                        textSize = 12f
                        setTextColor(if (dark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY)
                    }, LinearLayout.LayoutParams(0, dp(38), 1f))
                    actionRow.addView(Button(this).apply {
                        text = "清零"
                        textSize = 11f
                        isAllCaps = false
                        minWidth = 0
                        minimumWidth = 0
                        minHeight = dp(34)
                        minimumHeight = dp(34)
                        setPadding(dp(2), 0, dp(2), 0)
                        setOnClickListener {
                            WidgetRestoreStore.clearHistory(this@PictureWidgetRestoreActivity,
                                record.sourceWidgetId, record.providerClass)
                            recreate()
                        }
                    }, LinearLayout.LayoutParams(dp(62), dp(38)))
                    card.addView(actionRow)
                    root.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(7)
                    })
                }
            }
        }

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }


    private fun requestHabitRestore(config: WidgetRestoreStore.Config) {
        val manager = AppWidgetManager.getInstance(this)
        val component = ComponentName(packageName, config.providerClass)
        WidgetRestoreStore.setBaselineWidgetIds(
            this, config.sourceWidgetId, config.providerClass,
            manager.getAppWidgetIds(component)
        )
        if (!manager.isRequestPinAppWidgetSupported) {
            WidgetRestoreStore.select(this, config.sourceWidgetId)
            val instruction = if (config.type == "simple") {
                "当前桌面不支持应用内添加。请手动添加对应组件，再返回本应用，系统会识别新组件并恢复。"
            } else {
                "当前桌面不支持应用内添加。请手动添加对应组件，配置窗口会自动载入恢复设置。"
            }
            Toast.makeText(this, instruction, Toast.LENGTH_LONG).show()
            window.decorView.post { moveTaskToBack(true) }
            return
        }

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
            WidgetRestoreStore.select(this, config.sourceWidgetId)
            Toast.makeText(
                this,
                "桌面未接受应用内添加请求。可手动添加对应组件，再返回本应用完成恢复。",
                Toast.LENGTH_LONG
            ).show()
        }
    }

}
