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
package org.isoron.uhabits.utils

import android.content.Context
import android.graphics.Color
import org.isoron.uhabits.R
import org.isoron.uhabits.utils.StyledResources
import kotlin.math.max

object ColorUtils {
    @JvmStatic
    fun shouldUseLightOutline(context: Context, color: Int): Boolean {
        val themeTextColor = StyledResources(context).getColor(R.attr.contrast100)
        val isDarkTheme = Color.luminance(themeTextColor) > 0.5f
        // Only add a white edge when the habit color is almost indistinguishable from the dark surface.
        return isDarkTheme && Color.luminance(color) < 0.08f
    }

    /**
     * Chooses widget text contrast using the selected app theme. Translucent
     * widget backgrounds are composited over the matching theme surface.
     */
    @JvmStatic
    fun getContrastingTextColor(
        backgroundColor: Int,
        backgroundAlpha: Int = Color.alpha(backgroundColor),
        isDarkTheme: Boolean = true
    ): Int {
        val alpha = backgroundAlpha.coerceIn(0, 255) / 255.0
        val surface = if (isDarkTheme) 0xFF212121.toInt() else Color.WHITE
        val visibleBackground = Color.rgb(
            (Color.red(backgroundColor) * alpha + Color.red(surface) * (1.0 - alpha)).toInt(),
            (Color.green(backgroundColor) * alpha + Color.green(surface) * (1.0 - alpha)).toInt(),
            (Color.blue(backgroundColor) * alpha + Color.blue(surface) * (1.0 - alpha)).toInt()
        )

        fun linearize(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92
            else Math.pow((value + 0.055) / 1.055, 2.4)
        }

        val luminance = 0.2126 * linearize(Color.red(visibleBackground)) +
            0.7152 * linearize(Color.green(visibleBackground)) +
            0.0722 * linearize(Color.blue(visibleBackground))

        // Dark theme deliberately favors white text. Switch to black only
        // when the visible background is very light. Light theme is reversed.
        // The slightly higher dark-theme threshold preserves the user's
        // preferred white text on medium-bright habit colors.
        return if (isDarkTheme) {
            if (luminance >= 0.70) Color.BLACK else Color.WHITE
        } else {
            if (luminance <= 0.18) Color.WHITE else Color.BLACK
        }
    }

    private const val ALPHA_CHANNEL = 24
    private const val RED_CHANNEL = 16
    private const val GREEN_CHANNEL = 8
    private const val BLUE_CHANNEL = 0

    @JvmStatic
    fun mixColors(color1: Int, color2: Int, amount: Float): Int {
        val a = mixColorChannel(color1, color2, amount, ALPHA_CHANNEL)
        val r = mixColorChannel(color1, color2, amount, RED_CHANNEL)
        val g = mixColorChannel(color1, color2, amount, GREEN_CHANNEL)
        val b = mixColorChannel(color1, color2, amount, BLUE_CHANNEL)
        return a or r or g or b
    }

    fun changeHue(color: Int, delta: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + delta).mod(360f)
        return Color.HSVToColor(hsv)
    }

    @JvmStatic
    fun setAlpha(color: Int, newAlpha: Float): Int {
        val intAlpha = (newAlpha * 255).toInt()
        return Color.argb(intAlpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    @JvmStatic
    fun setMinValue(color: Int, newValue: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = max(hsv[2], newValue)
        return Color.HSVToColor(hsv)
    }

    private fun mixColorChannel(color1: Int, color2: Int, amount: Float, channel: Int): Int {
        val fl = (color1 shr channel and 0xff).toFloat() * amount
        val f2 = (color2 shr channel and 0xff).toFloat() * (1.0f - amount)
        return (fl + f2).toInt() and 0xff shl channel
    }
}
