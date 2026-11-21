/*
 * Shared chart outline rule.
 */
package org.isoron.uhabits.core.ui.views

object ColorOutline {
    private const val DARK_SURFACE_LUMINOSITY_THRESHOLD = 0.45
    private const val NEAR_BLACK_COLOR_LUMINOSITY_THRESHOLD = 0.008
    private const val CALENDAR_NEAR_BLACK_COLOR_LUMINOSITY_THRESHOLD = 0.004

    fun shouldUseLightOutline(backgroundLuminosity: Double, colorLuminosity: Double): Boolean {
        return backgroundLuminosity < DARK_SURFACE_LUMINOSITY_THRESHOLD &&
            colorLuminosity < NEAR_BLACK_COLOR_LUMINOSITY_THRESHOLD
    }

    fun shouldUseCalendarLightOutline(backgroundLuminosity: Double, colorLuminosity: Double): Boolean {
        return backgroundLuminosity < DARK_SURFACE_LUMINOSITY_THRESHOLD &&
            colorLuminosity < CALENDAR_NEAR_BLACK_COLOR_LUMINOSITY_THRESHOLD
    }
}
