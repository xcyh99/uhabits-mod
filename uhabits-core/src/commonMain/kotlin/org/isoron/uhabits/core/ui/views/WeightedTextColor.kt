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
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.core.ui.views

import kotlin.math.pow

/**
 * Shared weighted black/white text decision for completed habit check-ins.
 *
 * RGB channels are expected in the normalized 0.0..1.0 range. The preference
 * weight changes the comparison only; it does not force either text color.
 */
object WeightedTextColor {
    fun shouldUseWhiteText(
        red: Double,
        green: Double,
        blue: Double,
        whitePreferenceWeight: Double
    ): Boolean {
        val luminance =
            0.2126 * linearize(red) +
            0.7152 * linearize(green) +
            0.0722 * linearize(blue)
        val whiteContrast = 1.05 / (luminance + 0.05)
        val blackContrast = (luminance + 0.05) / 0.05
        val safeWeight = whitePreferenceWeight.takeIf {
            !it.isNaN() && !it.isInfinite() && it >= 0.1 && it <= 100.0
        } ?: DEFAULT_WEIGHT
        return blackContrast / whiteContrast <= safeWeight
    }

    private fun linearize(channel: Double): Double {
        val value = channel.coerceIn(0.0, 1.0)
        return if (value <= 0.04045) value / 12.92
        else ((value + 0.055) / 1.055).pow(2.4)
    }

    private const val DEFAULT_WEIGHT = 6.6
}
