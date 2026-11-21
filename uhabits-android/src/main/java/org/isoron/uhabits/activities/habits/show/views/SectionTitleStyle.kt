/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier
 *
 * This file is part of Loop Habit Tracker.
 * Licensed under the GNU General Public License, version 3 or later.
 */
package org.isoron.uhabits.activities.habits.show.views

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.TextView
import org.isoron.uhabits.utils.ColorUtils

internal fun applySectionTitleStyle(title: TextView, color: Int) {
    val density = title.resources.displayMetrics.density
    fun dp(value: Float) = (value * density + 0.5f).toInt()

    title.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.TRANSPARENT)
        setStroke(dp(1f), color)
        cornerRadius = dp(4f).toFloat()
    }
    title.setPadding(dp(5f), dp(1f), dp(5f), dp(1f))
    title.layoutParams = title.layoutParams?.apply {
        width = ViewGroup.LayoutParams.WRAP_CONTENT
    }
    applyColorTextOutline(title, color)
}

internal fun applyColorTextOutline(title: TextView, color: Int) {
    if (ColorUtils.shouldUseLightOutline(title.context, color)) {
        title.setShadowLayer(title.resources.displayMetrics.density * 0.8f, 0f, 0f, Color.WHITE)
    } else {
        title.clearShadowLayer()
    }
}
