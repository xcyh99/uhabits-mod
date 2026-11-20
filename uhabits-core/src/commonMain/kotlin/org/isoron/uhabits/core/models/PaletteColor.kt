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
package org.isoron.uhabits.core.models

/**
 * A habit color. Values 0..19 remain the legacy palette identifiers;
 * every other value is stored as a full ARGB integer.
 */
data class PaletteColor(val paletteIndex: Int) {
    val isCustom: Boolean
        get() = paletteIndex !in LEGACY_COLORS.indices

    val argb: Int
        get() = if (isCustom) paletteIndex else LEGACY_COLORS[paletteIndex]

    fun toCsvColor(): String {
        if (!isCustom) return LEGACY_CSV_COLORS[paletteIndex]
        val rgb = argb and 0x00FFFFFF
        val digits = "0123456789ABCDEF"
        val result = StringBuilder("#")
        for (shift in intArrayOf(20, 16, 12, 8, 4, 0)) {
            result.append(digits[(rgb shr shift) and 0xF])
        }
        return result.toString()
    }

    fun compareTo(other: PaletteColor): Int = argb.compareTo(other.argb)

    companion object {
        private val LEGACY_CSV_COLORS = arrayOf(
            "#D32F2F", "#E64A19", "#F57C00", "#FF8F00", "#F9A825",
            "#AFB42B", "#7CB342", "#388E3C", "#00897B", "#00ACC1",
            "#039BE5", "#1976D2", "#303F9F", "#5E35B1", "#8E24AA",
            "#D81B60", "#5D4037", "#303030", "#757575", "#aaaaaa"
        )

        val LEGACY_COLORS = intArrayOf(
            0xFFD32F2F.toInt(), 0xFFE64A19.toInt(), 0xFFF57C00.toInt(),
            0xFFFF8F00.toInt(), 0xFFF9A825.toInt(), 0xFFAFB42B.toInt(),
            0xFF7CB342.toInt(), 0xFF388E3C.toInt(), 0xFF00897B.toInt(),
            0xFF00ACC1.toInt(), 0xFF039BE5.toInt(), 0xFF1976D2.toInt(),
            0xFF303F9F.toInt(), 0xFF5E35B1.toInt(), 0xFF8E24AA.toInt(),
            0xFFD81B60.toInt(), 0xFF5D4037.toInt(), 0xFF303030.toInt(),
            0xFF757575.toInt(), 0xFFAAAAAA.toInt()
        )

        /** Converts an Android ARGB value to a palette color, retaining legacy swatches. */
        fun fromArgb(argb: Int): PaletteColor {
            val index = LEGACY_COLORS.indexOf(argb)
            if (index >= 0) return PaletteColor(index)
            val normalized = if ((argb ushr 24) == 0) argb or -0x1000000 else argb
            return PaletteColor(normalized)
        }

        /** Reads either an old palette index or a new ARGB value from storage. */
        fun fromStoredValue(value: Int): PaletteColor {
            return if (value in LEGACY_COLORS.indices) PaletteColor(value) else fromArgb(value)
        }

        /** Parses #RRGGBB or #AARRGGBB strings. */
        fun fromHex(value: String): PaletteColor? {
            val hex = value.trim().removePrefix("#")
            if (hex.length != 6 && hex.length != 8) return null
            val parsed = hex.toLongOrNull(16)?.toInt() ?: return null
            val argb = if (hex.length == 6) parsed or -0x1000000 else parsed
            return fromArgb(argb)
        }
    }
}
