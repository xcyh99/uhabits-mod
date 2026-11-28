package org.isoron.uhabits.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import org.isoron.uhabits.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Weather widget: cached-first rendering with an asynchronous forecast refresh.
 *
 * The chart keeps six independent TEXT glyph controls per temperature line.
 * Each glyph is sized and vertically positioned from the shared seven-node
 * geometry, so adjacent glyphs use the exact same calculated node.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val cached = WeatherRepository.load(context)
        ids.forEach { id ->
            manager.updateAppWidget(id, createWeatherViews(context, manager.getAppWidgetOptions(id), cached))
        }

        // Refresh once for this update batch. The repository returns cached data on network failure.
        WeatherRepository.fetch(context) { fresh ->
            if (fresh != null) {
                ids.forEach { id ->
                    manager.updateAppWidget(id, createWeatherViews(context, manager.getAppWidgetOptions(id), fresh))
                }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        manager.updateAppWidget(
            appWidgetId,
            createWeatherViews(context, newOptions, WeatherRepository.load(context))
        )
    }

    private fun createWeatherViews(
        context: Context,
        options: Bundle,
        snapshot: WeatherSnapshot?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather_line_test)
        val widgetWidthDp = getWidgetWidthDp(options)
        applyResponsiveLineCount(views, widgetWidthDp)

        val openDetails = Intent(context, WeatherDetailActivity::class.java)
        val clickIntent = PendingIntent.getActivity(
            context,
            7001,
            openDetails,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weather_widget_root, clickIntent)

        if (snapshot == null || snapshot.days.isEmpty()) {
            views.setTextViewText(R.id.weather_city, "天气")
            views.setTextViewText(R.id.weather_updated, "正在获取预报")
            views.setTextViewText(R.id.weather_current_desc, "…")
            views.setTextViewText(R.id.weather_current_temp, "--°")
            views.setTextViewText(R.id.weather_humidity_value, "--%")
            views.setTextViewText(R.id.weather_wind_value, "--")
            views.setTextViewText(R.id.weather_rain_value, "--%")
            for (i in 0..6) {
                views.setTextViewText(dayIds[i], "--")
                views.setTextViewText(iconIds[i], "·")
                views.setTextViewText(tempIds[i], "--°/--°")
            }
            clearChartGlyphs(views)
            return views
        }

        views.setTextViewText(R.id.weather_city, snapshot.city)
        val updated = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snapshot.fetchedAt))
        views.setTextViewText(R.id.weather_updated, "更新于 $updated")
        views.setTextViewText(R.id.weather_current_desc, WeatherRepository.weatherIcon(snapshot.currentCode))
        views.setTextViewText(R.id.weather_current_temp, "${snapshot.currentTemperature}°")
        views.setTextViewText(R.id.weather_humidity_value, "${snapshot.humidity}%")
        views.setTextViewText(R.id.weather_wind_value, "${snapshot.wind} km/h")
        views.setTextViewText(R.id.weather_rain_value, "${snapshot.days.first().rain}%")

        val days = snapshot.days.take(7)
        for (i in 0..6) {
            val day = days.getOrNull(i)
            if (day == null) {
                views.setTextViewText(dayIds[i], "--")
                views.setTextViewText(iconIds[i], "·")
                views.setTextViewText(tempIds[i], "--°/--°")
            } else {
                views.setTextViewText(dayIds[i], WeatherRepository.dayLabel(day.date, i))
                views.setTextViewText(iconIds[i], WeatherRepository.weatherIcon(day.code))
                views.setTextViewText(tempIds[i], "${day.high}°/${day.low}°")
            }
        }

        // A chart is valid only with seven real forecast days: seven nodes, six intervals.
        if (days.size == 7) {
            val highChart = WeatherLineGeometry.calculate(
                temperatures = days.map { it.high },
                width = widgetWidthDp * 6f / 7f,
                height = CHART_ROW_HEIGHT_DP,
                leftInset = widgetWidthDp / 14f
            )
            val lowChart = WeatherLineGeometry.calculate(
                temperatures = days.map { it.low },
                width = widgetWidthDp * 6f / 7f,
                height = CHART_ROW_HEIGHT_DP,
                leftInset = widgetWidthDp / 14f
            )
            applyChartGlyphs(context, views, highSegmentIds, highChart, widgetWidthDp)
            applyChartGlyphs(context, views, lowSegmentIds, lowChart, widgetWidthDp)
        } else {
            clearChartGlyphs(views)
        }
        return views
    }

    /**
     * Positions each character glyph so its visual box spans the two shared
     * node Y coordinates. The TextView itself remains in its original interval
     * cell; padding positions the glyph inside that cell without translating
     * the view outside the row (which could be clipped by RemoteViews).
     */
    private fun applyChartGlyphs(
        context: Context,
        views: RemoteViews,
        segmentIds: IntArray,
        chart: WeatherLineGeometry.Chart,
        widgetWidthDp: Int
    ) {
        val density = context.resources.displayMetrics.density
        val rowHeight = CHART_ROW_HEIGHT_DP
        val segmentWidth = widgetWidthDp / 7f

        chart.segments.forEach { segment ->
            val startY = segment.start.y
            val endY = segment.end.y
            val deltaY = endY - startY
            val glyphHeight = abs(deltaY).coerceAtLeast(MIN_GLYPH_HEIGHT_DP)
            val middleY = (startY + endY) / 2f
            val contentTop = (middleY - glyphHeight / 2f)
                .coerceIn(0f, rowHeight - glyphHeight)
            val contentBottom = (rowHeight - contentTop - glyphHeight).coerceAtLeast(0f)

            val glyph = when {
                abs(deltaY) < FLAT_LINE_THRESHOLD_DP -> "─"
                deltaY < 0f -> "╱"
                else -> "╲"
            }
            val viewId = segmentIds[segment.index]
            views.setTextViewText(viewId, glyph)
            views.setTextViewTextSize(
                viewId,
                TypedValue.COMPLEX_UNIT_DIP,
                glyphHeight
            )
            views.setFloat(
                viewId,
                "setTextScaleX",
                (segmentWidth / glyphHeight).coerceAtLeast(0.1f)
            )
            views.setViewPadding(
                viewId,
                0,
                (contentTop * density).toInt(),
                0,
                (contentBottom * density).toInt()
            )
            views.setViewVisibility(viewId, View.VISIBLE)
        }
    }

    private fun clearChartGlyphs(views: RemoteViews) {
        for (i in 0 until 6) {
            views.setTextViewText(highSegmentIds[i], "")
            views.setTextViewText(lowSegmentIds[i], "")
        }
    }

    private fun getWidgetWidthDp(options: Bundle): Int {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        return when {
            minWidth > 0 -> minWidth
            maxWidth > 0 -> maxWidth
            else -> 50
        }
    }

    private fun applyResponsiveLineCount(views: RemoteViews, widgetWidthDp: Int) {
        // Preserve all six independent glyph controls across the verified resize range.
        val glyphTextScaleX = if (widgetWidthDp >= 295) 2.43f else 1.9f
        for (i in 0 until 6) {
            // The per-segment scale is set from its actual geometry after forecast data is loaded.
            // Keep this fallback scale for the loading/empty state only.
            views.setFloat(highSegmentIds[i], "setTextScaleX", glyphTextScaleX)
            views.setFloat(lowSegmentIds[i], "setTextScaleX", glyphTextScaleX)
            views.setViewVisibility(highSegmentIds[i], View.VISIBLE)
            views.setViewVisibility(lowSegmentIds[i], View.VISIBLE)
        }
    }

    companion object {
        private const val CHART_ROW_HEIGHT_DP = 27f
        private const val MIN_GLYPH_HEIGHT_DP = 0.75f
        private const val FLAT_LINE_THRESHOLD_DP = 0.75f

        private val dayIds = intArrayOf(
            R.id.weather_day_0, R.id.weather_day_1, R.id.weather_day_2,
            R.id.weather_day_3, R.id.weather_day_4, R.id.weather_day_5, R.id.weather_day_6
        )
        private val iconIds = intArrayOf(
            R.id.weather_icon_0, R.id.weather_icon_1, R.id.weather_icon_2,
            R.id.weather_icon_3, R.id.weather_icon_4, R.id.weather_icon_5, R.id.weather_icon_6
        )
        private val tempIds = intArrayOf(
            R.id.weather_desc_0, R.id.weather_desc_1, R.id.weather_desc_2,
            R.id.weather_desc_3, R.id.weather_desc_4, R.id.weather_desc_5, R.id.weather_desc_6
        )
        private val highSegmentIds = intArrayOf(
            R.id.weather_high_segment_0, R.id.weather_high_segment_1,
            R.id.weather_high_segment_2, R.id.weather_high_segment_3,
            R.id.weather_high_segment_4, R.id.weather_high_segment_5
        )
        private val lowSegmentIds = intArrayOf(
            R.id.weather_low_segment_0, R.id.weather_low_segment_1,
            R.id.weather_low_segment_2, R.id.weather_low_segment_3,
            R.id.weather_low_segment_4, R.id.weather_low_segment_5
        )
    }
}
