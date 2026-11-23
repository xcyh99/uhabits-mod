package org.isoron.uhabits.widgets

import android.app.PendingIntent
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import org.isoron.platform.gui.toInt
import org.isoron.platform.time.getToday
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.ui.views.ColorOutline
import org.isoron.uhabits.core.ui.views.WidgetTheme
import org.isoron.uhabits.utils.StyledResources
import kotlin.math.min

class DaysSinceLastCheckinWidget(
    context: Context,
    id: Int,
    private val habit: Habit
) : BaseWidget(context, id, false) {
    override val defaultHeight: Int = 80
    override val defaultWidth: Int = 80

    override fun getOnClickPendingIntent(context: Context): PendingIntent =
        pendingIntentFactory.showHabit(habit)

    override fun refreshData(widgetView: View) {
        (widgetView as DaysSinceLastCheckinView).apply {
            habitName = habit.name
            dayCount = habit.computedEntries.getKnown()
                .filter { it.completed && it.value != Entry.SKIP }
                .maxByOrNull { it.date }
                ?.date
                ?.daysUntil(getToday())
            activeColor = WidgetTheme().color(habit.color).toInt()
            footerColor = activeColor
            footerText = "天未打卡"
            cardBackgroundColor = StyledResources(context).getColor(R.attr.cardBgColor)
            backgroundAlpha = preferedBackgroundAlpha
            refresh()
        }
    }

    override fun buildView(): View = DaysSinceLastCheckinView(context)
}

internal class DaysSinceLastCheckinView(context: Context) : View(context) {
    var dayCount: Int? = null
    var habitName: String = ""
    var activeColor: Int = Color.WHITE
    var footerColor: Int = Color.WHITE
    var footerText: String = "天未打卡"
    var cardBackgroundColor: Int = Color.DKGRAY
    var backgroundAlpha: Int = 255

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val cardRect = RectF()

    fun refresh() {
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val inset = 2f * density
        cardRect.set(inset, inset, width - inset, height - inset)
        val bg = (cardBackgroundColor and 0x00FFFFFF) or
            ((Color.alpha(cardBackgroundColor) * backgroundAlpha / 255) shl 24)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        canvas.drawRoundRect(cardRect, 12f * density, 12f * density, bgPaint)

        val number = dayCount?.toString() ?: "—"
        val horizontalPadding = 7f * density
        val maxTextWidth = (width - horizontalPadding * 2f).coerceAtLeast(1f)

        // Keep the habit title on one centered line; truncate with an ellipsis if needed.
        titlePaint.textSize = 9f * density
        titlePaint.color = activeColor
        var title = habitName
        while (title.isNotEmpty() && titlePaint.measureText("$title…") > maxTextWidth) {
            title = title.dropLast(1)
        }
        if (title != habitName) title += "…"
        val titleBaseline = height * 0.18f
        canvas.drawText(title, width / 2f, titleBaseline, titlePaint)

        // Use the actual single-line title boundary so the number can grow vertically.
        val titleBottom = titleBaseline + titlePaint.descent()
        labelPaint.textSize = 6.5f * density
        labelPaint.color = footerColor
        val labelBaseline = height * 0.97f
        val labelTop = labelBaseline + labelPaint.ascent()
        val verticalGap = 0.5f * density
        val numberTopLimit = titleBottom + verticalGap
        val numberBottomLimit = labelTop - verticalGap
        val availableNumberHeight = (numberBottomLimit - numberTopLimit).coerceAtLeast(1f)

        val baseNumberSize = min(width * 0.86f, height * 0.94f)
        // Let the enlarged digits use nearly the full vertical space between the title and label.
        var numberSize = min(baseNumberSize, availableNumberHeight / 1.0f)
        // Short counts use a fuller width; longer counts progressively return to / narrow
        // the condensed face. Font height is independent of digit count.
        val digitWidthScale = when (number.length) {
            1 -> 1.18f
            2 -> 0.98f
            3 -> 0.92f
            4 -> 0.86f
            else -> (0.86f - (number.length - 4) * 0.04f).coerceAtLeast(0.70f)
        }
        numberPaint.textScaleX = digitWidthScale
        outlinePaint.textScaleX = digitWidthScale
        numberPaint.textSize = numberSize
        var glyphWidths = number.map { numberPaint.measureText(it.toString()) }
        val gapCount = (number.length - 1).coerceAtLeast(0)
        // Keep a small positive gap between digits. If the full number is too wide,
        // narrow the glyphs further while preserving both the gap and the font height.
        val digitSpacing = if (gapCount > 0) 1.2f * density else 0f
        var totalNumberWidth = glyphWidths.sum() + digitSpacing * gapCount
        if (totalNumberWidth > maxTextWidth) {
            val availableGlyphWidth = (maxTextWidth - digitSpacing * gapCount).coerceAtLeast(1f)
            val fitScale = availableGlyphWidth / glyphWidths.sum().coerceAtLeast(1f)
            numberPaint.textScaleX *= fitScale
            outlinePaint.textScaleX *= fitScale
            glyphWidths = number.map { numberPaint.measureText(it.toString()) }
            totalNumberWidth = glyphWidths.sum() + digitSpacing * gapCount
        }

        numberPaint.color = activeColor
        outlinePaint.textSize = numberSize
        val bgLum = Color.luminance(cardBackgroundColor)
        val colorLum = Color.luminance(activeColor)
        val drawOutline = ColorOutline.shouldUseLightOutline(bgLum.toDouble(), colorLum.toDouble())
        val numberMetrics = numberPaint.fontMetrics
        val numberCenterY = (numberTopLimit + numberBottomLimit) / 2f
        val numberBaseline = numberCenterY - (numberMetrics.ascent + numberMetrics.descent) / 2f
        var numberX = (width - totalNumberWidth) / 2f
        if (drawOutline) outlinePaint.strokeWidth = 0.6f * density
        number.forEachIndexed { index, character ->
            val glyph = character.toString()
            if (drawOutline) canvas.drawText(glyph, numberX, numberBaseline, outlinePaint)
            // A restrained same-color glow adds depth without changing the digit fill.
            val glowColor = (activeColor and 0x00FFFFFF) or (96 shl 24)
            numberPaint.setShadowLayer(2.2f * density, 0f, 0f, glowColor)
            canvas.drawText(glyph, numberX, numberBaseline, numberPaint)
            numberPaint.clearShadowLayer()
            numberX += glyphWidths[index] + digitSpacing
        }
        canvas.drawText(footerText, width / 2f, labelBaseline, labelPaint)
    }
}
