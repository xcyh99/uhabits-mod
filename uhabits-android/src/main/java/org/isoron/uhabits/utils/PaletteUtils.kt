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
package org.isoron.uhabits.utils

import android.content.Context
import org.isoron.uhabits.core.models.PaletteColor

object PaletteUtils {
    @JvmStatic
    fun getAndroidTestColor(index: Int) = PaletteColor(index).toFixedAndroidColor()
}

fun PaletteColor.toFixedAndroidColor(): Int = argb

fun Int.toPaletteColor(context: Context): PaletteColor {
    val palette = StyledResources(context).getPalette()
    val index = palette.indexOf(this)
    return if (index >= 0) PaletteColor(index) else PaletteColor.fromArgb(this)
}
