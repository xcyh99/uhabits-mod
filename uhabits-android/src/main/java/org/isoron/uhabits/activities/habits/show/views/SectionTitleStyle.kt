/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier
 *
 * This file is part of Loop Habit Tracker.
 * Licensed under the GNU General Public License, version 3 or later.
 */
package org.isoron.uhabits.activities.habits.show.views

import android.content.Context
import android.graphics.Color
import org.isoron.uhabits.R
import org.isoron.uhabits.utils.StyledResources
import android.graphics.drawable.GradientDrawable
import android.widget.TextView

/**
 * Returns the theme's primary readable text color, independent of the habit palette.
 */
internal fun getThemeTextColor(context: Context): Int =
    StyledResources(context).getColor(R.attr.contrast100)

/**
 * Gives section headings a subtle outline in the habit's check-in color.
 * The title text itself keeps its existing theme-derived color and typography.
 */
internal fun applySectionTitleStyle(title: TextView, color: Int) {
    val density = title.resources.displayMetrics.density
    fun dp(value: Int) = (value * density + 0.5f).toInt()

    title.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.TRANSPARENT)
        setStroke(dp(1), color)
        cornerRadius = dp(4).toFloat()
    }
    title.setPadding(dp(6), dp(1), dp(6), dp(1))
}
