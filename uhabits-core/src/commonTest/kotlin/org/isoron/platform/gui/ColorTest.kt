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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */

package org.isoron.platform.gui

import kotlin.test.Test
import kotlin.test.assertTrue

class ColorTest {

    @Test
    fun contrastUsesCorrectRelativeLuminanceForRedColors() {
        val deepRed = Color(0xD90000)
        val pureRed = Color(0xFF0000)
        val darkRed = Color(0x8B0000)

        assertTrue(deepRed.contrast(Color.WHITE) > deepRed.contrast(Color.BLACK))
        assertTrue(pureRed.contrast(Color.BLACK) > pureRed.contrast(Color.WHITE))
        assertTrue(darkRed.contrast(Color.WHITE) > darkRed.contrast(Color.BLACK))
    }

    @Test
    fun contrastChoosesReadableTextForBlackWhiteAndGray() {
        val lightGray = Color(0xF5F5F5)
        val middleGray = Color(0x777777)
        val darkGray = Color(0x333333)

        assertTrue(Color.WHITE.contrast(Color.BLACK) > Color.BLACK.contrast(Color.BLACK))
        assertTrue(lightGray.contrast(Color.BLACK) > lightGray.contrast(Color.WHITE))
        assertTrue(middleGray.contrast(Color.BLACK) > middleGray.contrast(Color.WHITE))
        assertTrue(darkGray.contrast(Color.WHITE) > darkGray.contrast(Color.BLACK))
    }
}
