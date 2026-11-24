/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.HabitNotFoundException
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.preferences.WidgetPreferences
import org.isoron.uhabits.utils.InterfaceUtils.dpToPixels
import java.util.ArrayList

abstract class BaseWidgetProvider : AppWidgetProvider() {
    private lateinit var habits: HabitList
    lateinit var preferences: Preferences
        private set
    private lateinit var widgetPrefs: WidgetPreferences
    fun getDimensionsFromOptions(
        ctx: Context,
        options: Bundle,
        widgetId: Int,
        fallback: WidgetDimensions
    ): WidgetDimensions {
        val maxWidth = dpToPixels(
            ctx,
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
        ).toInt()
        val maxHeight = dpToPixels(
            ctx,
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
        ).toInt()
        val minWidth = dpToPixels(
            ctx,
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
        ).toInt()
        val minHeight = dpToPixels(
            ctx,
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat()
        ).toInt()

        // Some launchers briefly send incomplete widget options during restore,
        // resize, or process recreation. A zero/tiny dimension would otherwise
        // produce a nearly 1px bitmap. Keep the last known good size per widget.
        val stored = ctx.getSharedPreferences("widget_dimensions", Context.MODE_PRIVATE)
        val minimumValidSize = dpToPixels(ctx, 40f).toInt().coerceAtLeast(1)
        fun resolve(key: String, candidate: Int, defaultValue: Int): Int {
            if (candidate >= minimumValidSize) return candidate
            return stored.getInt("widget_${widgetId}_${key}", defaultValue)
                .takeIf { it >= minimumValidSize } ?: defaultValue
        }

        val resolved = WidgetDimensions(
            resolve("portrait_width", minWidth, fallback.portraitWidth),
            resolve("portrait_height", maxHeight, fallback.portraitHeight),
            resolve("landscape_width", maxWidth, fallback.landscapeWidth),
            resolve("landscape_height", minHeight, fallback.landscapeHeight)
        )
        stored.edit()
            .putInt("widget_${widgetId}_portrait_width", resolved.portraitWidth)
            .putInt("widget_${widgetId}_portrait_height", resolved.portraitHeight)
            .putInt("widget_${widgetId}_landscape_width", resolved.landscapeWidth)
            .putInt("widget_${widgetId}_landscape_height", resolved.landscapeHeight)
            .apply()
        return resolved
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        options: Bundle
    ) {
        try {
            updateDependencies(context)
            context.setTheme(R.style.WidgetTheme)
            val widget = getWidgetFromId(context, widgetId)
            val dims = getDimensionsFromOptions(context, options, widgetId, widget.defaultDimensions())
            widget.setDimensions(dims)
            updateAppWidget(manager, widget)
        } catch (e: RuntimeException) {
            drawErrorWidget(context, manager, widgetId, e)
            e.printStackTrace()
        }
    }

    override fun onDeleted(context: Context?, ids: IntArray?) {
        if (context == null) throw RuntimeException("context is null")
        if (ids == null) throw RuntimeException("ids is null")
        updateDependencies(context)
        for (id in ids) {
            try {
                val widget = getWidgetFromId(context, id)
                widget.delete()
            } catch (e: HabitNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray
    ) {
        updateDependencies(context)
        context.setTheme(R.style.WidgetTheme)
        Thread {
            Looper.prepare()
            for (id in widgetIds) update(context, manager, id)
        }.start()
    }

    protected fun getHabitsFromWidgetId(widgetId: Int): List<Habit> {
        val selectedIds = widgetPrefs.getHabitIdsFromWidgetId(widgetId)
        val selectedHabits = ArrayList<Habit>(selectedIds.size)
        for (id in selectedIds) {
            val h = habits.getById(id) ?: throw HabitNotFoundException()
            selectedHabits.add(h)
        }
        return selectedHabits
    }

    protected abstract fun getWidgetFromId(
        context: Context,
        id: Int
    ): BaseWidget

    private fun drawErrorWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        e: RuntimeException
    ) {
        val errorView = RemoteViews(context.packageName, R.layout.widget_error)
        val detail = if (e is HabitNotFoundException) {
            context.getString(R.string.habit_not_found)
        } else {
            e.javaClass.simpleName + ": " + (e.message ?: "no detail")
        }
        Log.e("ShiziWidget", "Widget " + widgetId + " failed to render: " + detail, e)
        errorView.setCharSequence(R.id.label, "setText", detail)
        manager.updateAppWidget(widgetId, errorView)
    }

    private fun update(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        try {
            val widget = getWidgetFromId(context, widgetId)
            val options = manager.getAppWidgetOptions(widgetId)
            widget.setDimensions(getDimensionsFromOptions(context, options, widgetId, widget.defaultDimensions()))
            updateAppWidget(manager, widget)
        } catch (e: RuntimeException) {
            drawErrorWidget(context, manager, widgetId, e)
            e.printStackTrace()
        }
    }

    private fun updateDependencies(context: Context) {
        val app = context.applicationContext as HabitsApplication
        habits = app.component.habitList
        preferences = app.component.preferences
        widgetPrefs = app.component.widgetPreferences
    }

    companion object {
        fun updateAppWidget(
            manager: AppWidgetManager,
            widget: BaseWidget
        ) {
            val landscape = widget.landscapeRemoteViews
            val portrait = widget.portraitRemoteViews
            val views = RemoteViews(landscape, portrait)
            manager.updateAppWidget(widget.id, views)
        }
    }
}
