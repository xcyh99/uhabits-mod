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
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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

        // Habit name occupies the previously unused top area; long names are safely ellipsized.
        titlePaint.textSize = 9f * density
        titlePaint.color = activeColor
        var title = habitName
        while (title.isNotEmpty() && titlePaint.measureText(title) > maxTextWidth) {
            title = title.dropLast(1)
        }
        if (title != habitName && title.isNotEmpty()) {
            while (title.isNotEmpty() && titlePaint.measureText("$title…") > maxTextWidth) {
                title = title.dropLast(1)
            }
            title += "…"
        }
        if (title.isNotEmpty()) {
            canvas.drawText(title, width / 2f, height * 0.22f, titlePaint)
        }

        // Shrink the number only as much as needed, so 3–4 digit counts stay inside the card.
        var numberSize = min(width * 0.62f, height * 0.52f)
        numberPaint.textSize = numberSize
        val measuredNumberWidth = numberPaint.measureText(number)
        if (measuredNumberWidth > maxTextWidth) {
            numberSize *= maxTextWidth / measuredNumberWidth
            numberPaint.textSize = numberSize
        }
        numberPaint.color = activeColor
        labelPaint.textSize = 8f * density
        labelPaint.color = activeColor
        outlinePaint.textSize = numberSize

        val bgLum = Color.luminance(cardBackgroundColor)
        val colorLum = Color.luminance(activeColor)
        val drawOutline = ColorOutline.shouldUseLightOutline(bgLum.toDouble(), colorLum.toDouble())
        val numberBaseline = height * 0.61f - (numberPaint.ascent() + numberPaint.descent()) / 2f
        if (drawOutline) {
            outlinePaint.strokeWidth = 0.6f * density
            canvas.drawText(number, width / 2f, numberBaseline, outlinePaint)
        }
        canvas.drawText(number, width / 2f, numberBaseline, numberPaint)
        canvas.drawText("天未打卡", width / 2f, height * 0.87f, labelPaint)
    }
}
