package org.isoron.uhabits.core.ui.views

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeightedTextColorTest {
    @Test
    fun usesWhiteForDarkCompletedColorAtDefaultWeight() {
        assertTrue(WeightedTextColor.shouldUseWhiteText(0.2, 0.2, 0.2, 6.6))
    }

    @Test
    fun weightChangesPreferenceWithoutForcingWhite() {
        // Mid-grey favors white at the default weight, but black at a low weight.
        assertTrue(WeightedTextColor.shouldUseWhiteText(0.5, 0.5, 0.5, 6.6))
        assertFalse(WeightedTextColor.shouldUseWhiteText(0.5, 0.5, 0.5, 0.1))
    }

    @Test
    fun usesBlackForBrightCompletedColorAtDefaultWeight() {
        assertFalse(WeightedTextColor.shouldUseWhiteText(0.9, 0.9, 0.9, 6.6))
    }

    @Test
    fun invalidWeightFallsBackToDefault() {
        assertTrue(WeightedTextColor.shouldUseWhiteText(0.5, 0.5, 0.5, Double.NaN))
    }
}
