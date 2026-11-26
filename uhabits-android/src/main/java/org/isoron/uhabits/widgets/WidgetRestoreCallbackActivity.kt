package org.isoron.uhabits.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.activities.AndroidThemeSwitcher

/**
 * Receives the launcher's pin-success callback, finds the newly allocated ID,
 * and binds the saved habit selection to that ID.
 */
class WidgetRestoreCallbackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        (application as HabitsApplication).component.preferences.let {
            AndroidThemeSwitcher(this, it).apply()
        }
        super.onCreate(savedInstanceState)

        val sourceId = intent.getIntExtra(WidgetRestoreStore.EXTRA_RESTORE_SOURCE_WIDGET_ID, -1)
        val providerClass = intent.getStringExtra(EXTRA_PROVIDER_CLASS).orEmpty()
        val config = WidgetRestoreStore.find(this, sourceId, providerClass)
        if (config == null) {
            finish()
            return
        }

        val component = ComponentName(packageName, providerClass)
        val manager = AppWidgetManager.getInstance(this)
        val currentIds = manager.getAppWidgetIds(component).toSet()
        val newIds = currentIds - config.baselineWidgetIds.toSet()
        if (newIds.size != 1) {
            Toast.makeText(
                this,
                if (newIds.isEmpty()) "桌面已确认添加，但暂时无法识别新组件编号；恢复配置仍保留。"
                else "检测到多个新组件，暂时无法安全匹配；恢复配置仍保留。",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        val newWidgetId = newIds.first()
        try {
            when (config.type) {
                "habit" -> {
                    val widgetPreferences = (application as HabitsApplication).component.widgetPreferences
                    widgetPreferences.addWidget(newWidgetId, config.habitIds.toLongArray())
                    val provider = Class.forName(providerClass)
                        .getDeclaredConstructor().newInstance() as android.appwidget.AppWidgetProvider
                    provider.onUpdate(this, manager, intArrayOf(newWidgetId))
                    WidgetRestoreStore.consume(this, sourceId, providerClass)
                    WidgetRestoreStore.clearSelection(this, sourceId)
                    Toast.makeText(this, "习惯组件配置已恢复。", Toast.LENGTH_SHORT).show()
                }
                "time_record" -> {
                    val recordId = config.recordId ?: throw IllegalStateException("缺少时间记录编号")
                    TimeRecordWidgetProvider.setRecord(this, newWidgetId, recordId)
                    TimeRecordWidgetProvider.updateWidget(this, newWidgetId)
                    WidgetRestoreStore.consume(this, sourceId, providerClass)
                    WidgetRestoreStore.clearSelection(this, sourceId)
                    Toast.makeText(this, "时间记录组件配置已恢复。", Toast.LENGTH_SHORT).show()
                }
                "picture" -> {
                    startActivity(
                        Intent(this, PictureShortcutWidgetConfigureActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newWidgetId)
                            .putExtra(WidgetRestoreStore.EXTRA_RESTORE_SOURCE_WIDGET_ID, sourceId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                    Toast.makeText(this, "图片组件已添加，请重新选择图片并保存。", Toast.LENGTH_LONG).show()
                }
                else -> throw IllegalStateException("暂不支持此组件的恢复类型：${config.type}")
            }
        } catch (error: Exception) {
            Toast.makeText(this, "组件已添加，但配置恢复失败；原恢复记录已保留。", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_PROVIDER_CLASS = "org.isoron.uhabits.extra.WIDGET_RESTORE_PROVIDER_CLASS"
    }
}
