/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier
 *
 * This file is part of Loop Habit Tracker.
 * Licensed under the GNU General Public License, version 3 or later.
 */
package org.isoron.uhabits.activities.habits.show.views

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView
import org.isoron.uhabits.R
import org.isoron.uhabits.utils.ColorUtils
import org.isoron.uhabits.utils.StyledResources

/** Keeps the calendar title's original theme-derived text color. */
internal fun getThemeTextColor(context: Context): Int =
    StyledResources(context).getColor(R.attr.contrast100)

/** Applies a consistent, unframed, left-aligned style to section titles. */
internal fun applySectionTitleStyle(title: TextView, color: Int) {
    title.background = null
    title.setPadding(0, 0, 0, 0)
    title.gravity = Gravity.START or Gravity.CENTER_VERTICAL
    applyColorTextOutline(title, color)
}

internal fun applyColorTextOutline(title: TextView, color: Int) {
    if (ColorUtils.shouldUseLightOutline(title.context, color)) {
        title.setShadowLayer(title.resources.displayMetrics.density * 0.7f, 0f, 0f, Color.WHITE)
    } else {
        title.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
    }
}
