package org.isoron.uhabits.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Single source of truth for widgets shipped by this app.
 * The launcher picker and in-app widget management both read this catalog,
 * so newly registered AppWidgetProviders appear without maintaining another list.
 */
object WidgetCatalog {
    data class Entry(
        val title: String,
        val provider: ComponentName,
        val installedCount: Int,
        val configureActivity: ComponentName?
    )

    fun discover(context: Context): List<Entry> {
        val manager = AppWidgetManager.getInstance(context)
        val packageManager = context.packageManager
        return manager.installedProviders
            .mapNotNull { info ->
                val component = info.provider ?: return@mapNotNull null
                if (component.packageName != context.packageName) return@mapNotNull null
                val title = runCatching {
                    packageManager.getReceiverInfo(component, PackageManager.GET_META_DATA)
                        .loadLabel(packageManager).toString()
                }.getOrDefault(component.className.substringAfterLast('.'))
                Entry(
                    title = title,
                    provider = component,
                    installedCount = manager.getAppWidgetIds(component).size,
                    configureActivity = info.configure
                )
            }
            .distinctBy { it.provider }
            .sortedBy { it.title }
    }
}
