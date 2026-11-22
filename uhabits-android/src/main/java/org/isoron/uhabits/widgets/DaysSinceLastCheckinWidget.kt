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
            cardBackgroundColor = StyledResources(context).getColor(R.attr.cardBgColor)
            backgroundAlpha = preferedBackgroundAlpha
            refresh()
        }
    }

    override fun buildView(): View = DaysSinceLastCheckinView(context)
}

private class DaysSinceLastCheckinView(context: Context) : View(context) {
    var dayCount: Int? = null
    var habitName: String = ""
    var activeColor: Int = Color.WHITE
    var cardBackgroundColor: Int = Color.DKGRAY
    var backgroundAlpha: Int = 255

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
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

        // Habit name may wrap to at most two centered lines; truncate the second line if needed.
        titlePaint.textSize = 9f * density
        titlePaint.color = activeColor
        val titleLines = mutableListOf<String>()
        var remainingTitle = habitName
        while (remainingTitle.isNotEmpty() && titleLines.size < 2) {
            var end = 0
            while (end < remainingTitle.length &&
                titlePaint.measureText(remainingTitle.substring(0, end + 1)) <= maxTextWidth
            ) {
                end++
            }
            if (end == 0) end = 1
            var line = remainingTitle.substring(0, end)
            remainingTitle = remainingTitle.substring(end)
            if (remainingTitle.isNotEmpty() && titleLines.size == 1) {
                while (line.isNotEmpty() &&
                    titlePaint.measureText("$line…") > maxTextWidth
                ) {
                    line = line.dropLast(1)
                }
                line += "…"
            }
            titleLines.add(line)
        }
        if (remainingTitle.isNotEmpty() && titleLines.size == 2) {
            var line = titleLines[1]
            while (line.isNotEmpty() &&
                titlePaint.measureText("$line…") > maxTextWidth
            ) {
                line = line.dropLast(1)
            }
            titleLines[1] = "$line…"
        }
        titleLines.forEachIndexed { index, line ->
            val baseline = if (titleLines.size == 1) height * 0.18f
                else height * (0.12f + index * 0.13f)
            canvas.drawText(line, width / 2f, baseline, titlePaint)
        }

        // Keep the original font size whenever possible: tighten digit spacing first,
        // and only scale the whole number down if the minimum spacing still cannot fit.
        var numberSize = min(width * 0.82f, height * 0.58f)
        // Widen short counts slightly; keep three-or-more digits in the original condensed style.
        val digitWidthScale = when (number.length) {
            1 -> 1.18f
            2 -> 1.10f
            else -> 1.0f
        }
        numberPaint.textScaleX = digitWidthScale
        outlinePaint.textScaleX = digitWidthScale
        numberPaint.textSize = numberSize
        var glyphWidths = number.map { numberPaint.measureText(it.toString()) }
        val glyphWidth = glyphWidths.sum()
        val gapCount = (number.length - 1).coerceAtLeast(0)
        val minDigitSpacing = -1.5f * density
        var digitSpacing = if (gapCount > 0) {
            ((maxTextWidth - glyphWidth) / gapCount).coerceAtMost(0f)
                .coerceAtLeast(minDigitSpacing)
        } else 0f
        var totalNumberWidth = glyphWidth + digitSpacing * gapCount
        if (totalNumberWidth > maxTextWidth) {
            numberSize *= maxTextWidth / totalNumberWidth
            numberPaint.textSize = numberSize
            glyphWidths = number.map { numberPaint.measureText(it.toString()) }
            digitSpacing = minDigitSpacing * (numberSize / min(width * 0.82f, height * 0.58f))
            totalNumberWidth = glyphWidths.sum() + digitSpacing * gapCount
        }
        numberPaint.color = activeColor
        labelPaint.textSize = 6.5f * density
        labelPaint.color = activeColor
        outlinePaint.textSize = numberSize

        val bgLum = Color.luminance(cardBackgroundColor)
        val colorLum = Color.luminance(activeColor)
        val drawOutline = ColorOutline.shouldUseLightOutline(bgLum.toDouble(), colorLum.toDouble())
        val numberBaseline = height * 0.55f - (numberPaint.ascent() + numberPaint.descent()) / 2f
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
        canvas.drawText("天未打卡", width / 2f, height * 0.94f, labelPaint)
    }
}
