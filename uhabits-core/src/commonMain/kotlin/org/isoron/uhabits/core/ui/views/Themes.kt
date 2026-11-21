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

package org.isoron.uhabits.core.ui.views

import org.isoron.platform.gui.Color
import org.isoron.uhabits.core.models.PaletteColor
import kotlin.math.pow

abstract class Theme {
    open val lightOutlineWidth = 0.3
    open val calendarOutlineWidth = 0.3
    open val appBackgroundColor = Color(0xf4f4f4)
    open val cardBackgroundColor = Color(0xFAFAFA)
    open val headerBackgroundColor = Color(0xeeeeee)
    open val headerBorderColor = Color(0xcccccc)
    open val headerTextColor = Color(0x9E9E9E)
    open val highContrastTextColor = Color(0x202020)
    open val itemBackgroundColor = Color(0xffffff)
    open val lowContrastTextColor = Color(0xe0e0e0)
    open val mediumContrastTextColor = Color(0x9E9E9E)
    open val statusBarBackgroundColor = Color(0x333333)
    open val toolbarBackgroundColor = Color(0xf4f4f4)
    open val toolbarColor = Color(0xffffff)

    fun shouldUseLightOutline(color: Color): Boolean {
        // Match Android's Color.luminance() calculation. The old raw-channel
        // weighted average made near-black custom colors miss the outline
        // threshold in core charts, even when Android charts outlined them.
        fun linearize(channel: Double): Double {
            return if (channel <= 0.04045) channel / 12.92
            else ((channel + 0.055) / 1.055).pow(2.4)
        }
        fun luminance(value: Color): Double {
            return 0.2126 * linearize(value.red) +
                0.7152 * linearize(value.green) +
                0.0722 * linearize(value.blue)
        }
        return ColorOutline.shouldUseLightOutline(
            luminance(cardBackgroundColor),
            luminance(color)
        )
    }

    fun shouldUseCalendarLightOutline(color: Color): Boolean {
        fun linearize(channel: Double): Double {
            return if (channel <= 0.04045) channel / 12.92
            else ((channel + 0.055) / 1.055).pow(2.4)
        }
        fun luminance(value: Color): Double {
            return 0.2126 * linearize(value.red) +
                0.7152 * linearize(value.green) +
                0.0722 * linearize(value.blue)
        }
        return ColorOutline.shouldUseCalendarLightOutline(
            luminance(cardBackgroundColor),
            luminance(color)
        )
    }

    fun color(paletteColor: PaletteColor): Color {
        return if (paletteColor.isCustom) Color(paletteColor.argb and 0x00FFFFFF) else color(paletteColor.paletteIndex)
    }

    open fun color(paletteIndex: Int): Color {
        return when (paletteIndex) {
            0 -> Color(0xD32F2F)
            1 -> Color(0xE64A19)
            2 -> Color(0xF57C00)
            3 -> Color(0xFF8F00)
            4 -> Color(0xF9A825)
            5 -> Color(0xAFB42B)
            6 -> Color(0x7CB342)
            7 -> Color(0x388E3C)
            8 -> Color(0x00897B)
            9 -> Color(0x00ACC1)
            10 -> Color(0x039BE5)
            11 -> Color(0x1976D2)
            12 -> Color(0x303F9F)
            13 -> Color(0x5E35B1)
            14 -> Color(0x8E24AA)
            15 -> Color(0xD81B60)
            16 -> Color(0x5D4037)
            17 -> Color(0x424242)
            18 -> Color(0x757575)
            19 -> Color(0x9E9E9E)
            else -> Color(0x000000)
        }
    }

    val checkmarkButtonSize = 48.0
    val smallTextSize = 10.0
    val regularTextSize = 17.0
}

open class LightTheme : Theme()

open class DarkTheme : Theme() {
    override val appBackgroundColor = Color(0x212121)
    override val cardBackgroundColor = Color(0x303030)
    override val headerBackgroundColor = Color(0x212121)
    override val headerBorderColor = Color(0xcccccc)
    override val headerTextColor = Color(0x9E9E9E)
    override val highContrastTextColor = Color(0xF5F5F5)
    override val itemBackgroundColor = Color(0xffffff)
    override val lowContrastTextColor = Color(0x424242)
    override val mediumContrastTextColor = Color(0x9E9E9E)
    override val statusBarBackgroundColor = Color(0x333333)
    override val toolbarBackgroundColor = Color(0xf4f4f4)
    override val toolbarColor = Color(0xffffff)

    override fun color(paletteIndex: Int): Color {
        return when (paletteIndex) {
            0 -> Color(0xEF9A9A)
            1 -> Color(0xFFAB91)
            2 -> Color(0xFFCC80)
            3 -> Color(0xFFECB3)
            4 -> Color(0xFFF59D)
            5 -> Color(0xE6EE9C)
            6 -> Color(0xC5E1A5)
            7 -> Color(0x69F0AE)
            8 -> Color(0x80CBC4)
            9 -> Color(0x80DEEA)
            10 -> Color(0x81D4FA)
            11 -> Color(0x64B5F6)
            12 -> Color(0x9FA8DA)
            13 -> Color(0xB39DDB)
            14 -> Color(0xCE93D8)
            15 -> Color(0xF48FB1)
            16 -> Color(0xBCAAA4)
            17 -> Color(0xF5F5F5)
            18 -> Color(0xE0E0E0)
            19 -> Color(0x9E9E9E)
            else -> Color(0xFFFFFF)
        }
    }
}

class PureBlackTheme : DarkTheme() {
    override val appBackgroundColor = Color(0x000000)
    override val cardBackgroundColor = Color(0x000000)
    override val lowContrastTextColor = Color(0x212121)
}

class WidgetTheme(private val isDark: Boolean? = null) : LightTheme() {
    override val lightOutlineWidth = 0.2
    override val calendarOutlineWidth = 0.2
    override val cardBackgroundColor = Color.TRANSPARENT
    override val highContrastTextColor = if (isDark != false) Color.WHITE else Color(0x202020)
    override val mediumContrastTextColor = (if (isDark != false) Color.WHITE else Color(0x202020)).withAlpha(0.50)
    override val lowContrastTextColor = (if (isDark != false) Color.WHITE else Color(0x202020)).withAlpha(0.10)

    override fun color(paletteIndex: Int): Color {
        if (isDark == true) return DarkTheme().color(paletteIndex)
        return when (paletteIndex) {
            0 -> Color(0xD32F2F)
            1 -> Color(0xE64A19)
            2 -> Color(0xF57C00)
            3 -> Color(0xFF8F00)
            4 -> Color(0xF9A825)
            5 -> Color(0xAFB42B)
            6 -> Color(0x7CB342)
            7 -> Color(0x388E3C)
            8 -> Color(0x00897B)
            9 -> Color(0x00ACC1)
            10 -> Color(0x039BE5)
            11 -> Color(0x1976D2)
            12 -> Color(0x6275f0)
            13 -> Color(0x5E35B1)
            14 -> Color(0x8E24AA)
            15 -> Color(0xD81B60)
            16 -> Color(0x5D4037)
            17 -> Color(0x757575)
            18 -> Color(0x757575)
            19 -> Color(0x9E9E9E)
            else -> Color(0x000000)
        }
    }
}
