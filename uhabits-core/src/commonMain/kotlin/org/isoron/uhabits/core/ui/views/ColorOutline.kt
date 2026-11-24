/*
 * Shared chart outline rule.
 */
package org.isoron.uhabits.core.ui.views

object ColorOutline {
    private const val DARK_SURFACE_LUMINOSITY_THRESHOLD = 0.45
    private const val NEAR_BLACK_COLOR_LUMINOSITY_THRESHOLD = 0.008
    private const val CALENDAR_NEAR_BLACK_COLOR_LUMINOSITY_THRESHOLD = 0.004

    fun shouldUseLightOutline(backgroundLuminosity: Double, colorLuminosity: Double, threshold: Double = NEAR_BLACK_COLOR_LUMINOSITY_THRESHOLD): Boolean {
        return backgroundLuminosity < DARK_SURFACE_LUMINOSITY_THRESHOLD &&
            colorLuminosity < threshold
    }

    fun shouldUseCalendarLightOutline(backgroundLuminosity: Double, colorLuminosity: Double, threshold: Double = CALENDAR_NEAR_BLACK_COLOR_LUMINOSITY_THRESHOLD): Boolean {
        return backgroundLuminosity < DARK_SURFACE_LUMINOSITY_THRESHOLD &&
            colorLuminosity < threshold
    }
}
