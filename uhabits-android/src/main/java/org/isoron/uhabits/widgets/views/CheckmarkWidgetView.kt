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
package org.isoron.uhabits.widgets.views

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.common.views.RingView
import org.isoron.uhabits.activities.habits.list.views.toShortString
import org.isoron.uhabits.core.models.Entry.Companion.NO
import org.isoron.uhabits.core.models.Entry.Companion.SKIP
import org.isoron.uhabits.core.models.Entry.Companion.UNKNOWN
import org.isoron.uhabits.core.models.Entry.Companion.YES_AUTO
import org.isoron.uhabits.core.models.Entry.Companion.YES_MANUAL
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.preferences.WhiteOutlineTarget
import org.isoron.uhabits.core.preferences.WhiteOutlineSettings
import org.isoron.uhabits.core.ui.ThemeSwitcher
import org.isoron.uhabits.core.ui.views.ColorOutline
import org.isoron.uhabits.inject.HabitsApplicationComponent
import org.isoron.uhabits.utils.InterfaceUtils.getDimension
import org.isoron.uhabits.utils.InterfaceUtils.dpToPixels
import org.isoron.uhabits.utils.ColorUtils.getContrastingTextColor
import org.isoron.uhabits.utils.drawNotesIndicator
import org.isoron.uhabits.utils.PaletteUtils.getAndroidTestColor
import org.isoron.uhabits.utils.StyledResources
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CheckmarkWidgetView : HabitWidgetView {
    var activeColor: Int = 0

    var percentage = 0f
    var name: String? = null
    var notes: String = ""
    private val notesIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var notesIndicatorColor: Int = 0
    private var cardOutlineEnabled = false
    private val cardOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
    }
    private val cardOutlineRect = RectF()
    private lateinit var ring: RingView
    private lateinit var label: TextView
    var entryValue = 0
    var entryState = 0
    var isNumerical = false
    private var preferences: Preferences? = null

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private val isDarkTheme: Boolean
        get() {
            return when (preferences?.theme) {
                ThemeSwitcher.THEME_DARK -> true
                ThemeSwitcher.THEME_LIGHT -> false
                else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            }
        }

    fun refresh() {
        if (backgroundPaint == null || frame == null) return
        val res = StyledResources(context)
        val bgColor: Int
        val fgColor: Int
        setShadowAlpha(0x4f)
        when (entryState) {
            YES_MANUAL, YES_AUTO -> {
                bgColor = activeColor
                // Completed check-ins use the same weighted black/white contrast
                // rule as completed history squares. The active color is opaque,
                // so widget/card transparency is intentionally not part of this test.
                fgColor = org.isoron.uhabits.utils.ColorUtils.getWeightedCheckInTextColor(
                    bgColor,
                    preferences?.checkmarkWhiteWeight ?: Preferences.DEFAULT_CHECKMARK_WHITE_WEIGHT
                )
                // Keep completed checkmarks fully colored, as in the original widget.
                // setWidgetBackgroundColor() applies widget opacity and can wash out
                // the habit color; the active state must retain its opaque fill.
                backgroundPaint!!.color = bgColor
                frame!!.setBackgroundDrawable(background)
            }
            SKIP -> {
                bgColor = activeColor
                fgColor = getContrastingTextColor(bgColor, 255, isDarkTheme)
                backgroundPaint!!.color = bgColor
                frame!!.setBackgroundDrawable(background)
            }
            NO, UNKNOWN -> {
                bgColor = res.getColor(R.attr.cardBgColor)
                fgColor = getContrastingTextColor(bgColor, widgetBackgroundAlpha, isDarkTheme)
                setWidgetBackgroundColor(bgColor)
            }
            else -> {
                bgColor = res.getColor(R.attr.cardBgColor)
                fgColor = getContrastingTextColor(bgColor, widgetBackgroundAlpha)
                setWidgetBackgroundColor(bgColor)
            }
        }
        notesIndicatorColor = fgColor
        val isCompleted = entryState == YES_MANUAL || entryState == YES_AUTO
        val outline = preferences?.getWhiteOutlineSettings(WhiteOutlineTarget.WIDGET_CHECKMARK)
            ?: WhiteOutlineSettings(0.008, 0.2)
        cardOutlineEnabled = isCompleted && ColorOutline.shouldUseLightOutline(
            Color.luminance(res.getColor(R.attr.cardBgColor)).toDouble(),
            Color.luminance(activeColor).toDouble(),
            outline.threshold
        )
        ring.setPercentage(percentage)
        ring.setColor(fgColor)
        ring.setBackgroundColor(bgColor)
        ring.setText(text)
        ring.setIsStrokedTextEnabled(strokedTextEnabled)
        // The checkmark glyph itself should not receive an outline.
        // The conditional white outline belongs to the outer card only.
        ring.setTextOutlineEnabled(false)
        label.text = name
        label.setTextColor(fgColor)
        requestLayout()
        postInvalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (cardOutlineEnabled && frame != null) {
            val outlineWidth = preferences?.getWhiteOutlineSettings(WhiteOutlineTarget.WIDGET_CHECKMARK)?.width ?: 0.2
            val halfStroke = dpToPixels(context, outlineWidth.toFloat() / 2f)
            cardOutlineRect.set(
                frame!!.left + halfStroke,
                frame!!.top + halfStroke,
                frame!!.right - halfStroke,
                frame!!.bottom - halfStroke
            )
            cardOutlinePaint.strokeWidth = dpToPixels(context, outlineWidth.toFloat())
            canvas.drawRoundRect(
                cardOutlineRect,
                dpToPixels(context, 18f),
                dpToPixels(context, 18f),
                cardOutlinePaint
            )
        }
        drawNotesIndicator(
            pNotesIndicator = notesIndicatorPaint,
            canvas = canvas,
            color = notesIndicatorColor,
            size = label.textSize,
            notes = notes
        )
    }

    private val strokedTextEnabled: Boolean
        get() = if (isNumerical) {
            false
        } else {
            when (entryState) {
                YES_AUTO -> true
                else -> false
            }
        }

    private val text: String
        get() = if (isNumerical) {
            (max(0, entryValue) / 1000.0).toShortString()
        } else {
            when (entryState) {
                YES_MANUAL, YES_AUTO -> resources.getString(R.string.fa_check)
                SKIP -> resources.getString(R.string.fa_skipped)
                UNKNOWN -> {
                    run {
                        if (preferences!!.areQuestionMarksEnabled) {
                            return resources.getString(R.string.fa_question)
                        } else {
                            resources.getString(R.string.fa_times)
                        }
                    }
                    resources.getString(R.string.fa_times)
                }
                NO -> resources.getString(R.string.fa_times)
                else -> resources.getString(R.string.fa_times)
            }
        }

    override val innerLayoutId: Int
        get() = R.layout.widget_checkmark

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = MeasureSpec.getSize(widthMeasureSpec)
        var height = MeasureSpec.getSize(heightMeasureSpec)
        if (height >= width) {
            height = min(height, (width * 1.5).roundToInt())
        } else {
            width = min(width, height)
        }
        val textSize = min(0.175f * width, getDimension(context, R.dimen.smallTextSize))
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        if (isNumerical) {
            ring.setTextSize(textSize * 0.9f)
        } else {
            ring.setTextSize(textSize)
        }
        ring.setThickness(0.03f * width)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
    }

    private fun init() {
        val appComponent: HabitsApplicationComponent =
            (context.applicationContext as HabitsApplication).component
        preferences = appComponent.preferences
        ring = findViewById<View>(R.id.scoreRing) as RingView
        label = findViewById<View>(R.id.label) as TextView
        ring.setIsTransparencyEnabled(true)
        if (isInEditMode) {
            percentage = 0.75f
            name = "Wake up early"
            activeColor = getAndroidTestColor(6)
            entryValue = YES_MANUAL
            refresh()
        }
    }
}
