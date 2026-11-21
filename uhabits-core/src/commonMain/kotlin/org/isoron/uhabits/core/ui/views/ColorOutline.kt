/*
 * Shared chart outline rule.
 */
package org.isoron.uhabits.core.ui.views

object ColorOutline {
    private const val DARK_SURFACE_LUMINOSITY_THRESHOLD = 0.45
    private const val NEAR_BLACK_COLOR_LUMINOSITY_THRESHOLD = 0.1

    fun shouldUseLightOutline(backgroundLuminosity: Double, colorLuminosity: Double): Boolean {
        return backgroundLuminosity < DARK_SURFACE_LUMINOSITY_THRESHOLD &&
            colorLuminosity < NEAR_BLACK_COLOR_LUMINOSITY_THRESHOLD
    }
}
