package org.isoron.uhabits.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.preference.PreferenceManager
import org.isoron.uhabits.R
import kotlin.math.max
import kotlin.math.min

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val snapshot = WeatherRepository.load(context)
        ids.forEach { render(context, manager, it, snapshot) }
        WeatherRepository.fetch(context) { fresh -> updateAll(context, fresh) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        render(context, manager, appWidgetId, WeatherRepository.load(context))
        WeatherRepository.fetch(context) { fresh -> updateAll(context, fresh) }
    }

    private fun updateAll(context: Context, snapshot: WeatherSnapshot?) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
        ids.forEach { render(context, manager, it, snapshot) }
    }

    private fun render(
        context: Context, manager: AppWidgetManager, id: Int, snapshot: WeatherSnapshot?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        val options = manager.getAppWidgetOptions(id)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
        val density = context.resources.displayMetrics.density

        // Keep the essential current conditions in a 1x1 cell. Add forecast rows and
        // the chart only when the launcher gives the widget enough real space.
        val tiny = widthDp < 100 || heightDp < 85
        val showDays = widthDp >= 120 && heightDp >= 105
        // Keep the trend line visible in 1x2 and 2x1 widgets as well.
        // A single-cell side is narrow or short, but the chart must not disappear.
        val oneCellSide = (widthDp < 100 && heightDp >= 100) || (heightDp < 100 && widthDp >= 100)
        val showChart = (widthDp >= 120 && heightDp >= 130) || oneCellSide
        val showUpdated = widthDp >= 160 && heightDp >= 145
        val showBottom = widthDp >= 210 && heightDp >= 175

        // Use the full widget canvas: no artificial inset around the weather content.
        val padding = 0
        views.setViewPadding(R.id.weather_root, 0, 0, 0, 0)
        val tinyCell = widthDp < 130 && heightDp < 130
        views.setViewVisibility(R.id.weather_tiny_panel, if (tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_header, if (tinyCell) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.weather_city, if (widthDp >= 180 && !tiny) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_updated, if (showUpdated && !tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_day_desc_row, if (showDays && !tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_chart, if (showChart && !tinyCell) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_bottom_info, if (showBottom && !tinyCell) View.VISIBLE else View.GONE)

        val cityText = snapshot?.city ?: "天气"
        val tempText = snapshot?.let { "${it.currentTemperature}°" } ?: "--°"
        val conditionText = snapshot?.let { WeatherRepository.weatherText(it.currentCode) } ?: "获取中"
        views.setTextViewText(R.id.weather_city, cityText)
        views.setTextViewText(R.id.weather_current_temp, tempText)
        views.setTextViewText(R.id.weather_current_desc, conditionText)
        views.setTextViewText(R.id.weather_tiny_city, cityText)
        views.setTextViewText(R.id.weather_tiny_desc, conditionText)
        views.setTextViewText(R.id.weather_tiny_temp, tempText)
        if (tinyCell) {
            views.setTextViewTextSize(R.id.weather_tiny_desc, android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
            views.setTextViewTextSize(R.id.weather_tiny_temp, android.util.TypedValue.COMPLEX_UNIT_SP, 17f)
        }
        views.setTextViewText(
            R.id.weather_updated,
            snapshot?.let { "更新于 ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(it.fetchedAt))}" }
                ?: "等待天气数据"
        )
        if (oneCellSide) {
            views.setTextViewTextSize(R.id.weather_current_temp, android.util.TypedValue.COMPLEX_UNIT_SP, 17f)
            views.setTextViewTextSize(R.id.weather_current_desc, android.util.TypedValue.COMPLEX_UNIT_SP, 8f)
        }
        views.setTextViewTextSize(
            R.id.weather_current_temp,
            android.util.TypedValue.COMPLEX_UNIT_SP,
            when {
                tiny -> 21f
                widthDp < 160 || heightDp < 130 -> 25f
                else -> 30f
            }
        )

        if (snapshot != null && snapshot.days.isNotEmpty()) {
            val dayViews = intArrayOf(
                R.id.weather_day_0, R.id.weather_day_1, R.id.weather_day_2,
                R.id.weather_day_3, R.id.weather_day_4, R.id.weather_day_5, R.id.weather_day_6
            )
            val iconViews = intArrayOf(
                R.id.weather_icon_0, R.id.weather_icon_1, R.id.weather_icon_2,
                R.id.weather_icon_3, R.id.weather_icon_4, R.id.weather_icon_5, R.id.weather_icon_6
            )
            val descViews = intArrayOf(
                R.id.weather_desc_0, R.id.weather_desc_1, R.id.weather_desc_2,
                R.id.weather_desc_3, R.id.weather_desc_4, R.id.weather_desc_5, R.id.weather_desc_6
            )
            val hitViews = intArrayOf(
                R.id.weather_hit_0, R.id.weather_hit_1, R.id.weather_hit_2,
                R.id.weather_hit_3, R.id.weather_hit_4, R.id.weather_hit_5, R.id.weather_hit_6
            )
            snapshot.days.take(7).forEachIndexed { index, day ->
                views.setTextViewText(dayViews[index], WeatherRepository.dayLabel(day.date, index))
                views.setTextViewText(iconViews[index], WeatherRepository.weatherIcon(day.code))
                views.setTextViewText(descViews[index], "${day.high}°/${day.low}°")
                if (widthDp < 180 || heightDp < 150) {
                    views.setTextViewTextSize(dayViews[index], android.util.TypedValue.COMPLEX_UNIT_SP, 8f)
                    views.setTextViewTextSize(iconViews[index], android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                    views.setTextViewTextSize(descViews[index], android.util.TypedValue.COMPLEX_UNIT_SP, 7f)
                }
                val intent = Intent(context, WeatherDetailActivity::class.java)
                    .putExtra(WeatherDetailActivity.EXTRA_DATE, day.date)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pending = PendingIntent.getActivity(
                    context, id * 20 + index, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(dayViews[index], pending)
                views.setOnClickPendingIntent(iconViews[index], pending)
                views.setOnClickPendingIntent(hitViews[index], pending)
            }

            if (showChart) {
                val chartWidth = max(1, ((widthDp - padding * 2) * density).toInt())
                val reservedHeightDp = padding * 2 + 36 +
                    (if (showUpdated) 13 else 0) +
                    (if (showDays) 49 else 0) +
                    (if (showBottom) 17 else 0) + 4
                val chartHeight = max(1, ((heightDp - reservedHeightDp) * density).toInt())
                val chartLabelSizeSp = if (widthDp < 180 || heightDp < 150) 7f else 9f
                views.setImageViewBitmap(R.id.weather_chart, drawChart(context, snapshot.days, chartWidth, chartHeight, chartLabelSizeSp))
            }
            views.setTextViewText(
                R.id.weather_bottom_info,
                "湿度 ${snapshot.humidity}%   风速 ${snapshot.wind} km/h   今日降水 ${snapshot.days.first().rain}%"
            )
        } else {
            views.setTextViewText(R.id.weather_bottom_info, "天气数据暂不可用，点击查看")
        }

        // A tap anywhere on the widget opens the full forecast page.
        val open = PendingIntent.getActivity(
            context, id * 20 + 19,
            Intent(context, WeatherDetailActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weather_root, open)
        manager.updateAppWidget(id, views)
    }

    private fun drawChart(context: Context, days: List<WeatherDay>, width: Int, height: Int, labelSizeSp: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // 240x64 px is the reference drawing size. Larger widgets never enlarge
        // strokes or labels; smaller widgets scale every chart mark down uniformly.
        val chartScale = min(1f, min(width / 240f, height / 64f)).coerceAtLeast(0f)
        val highPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFA34A.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f * chartScale
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val lowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF65A9FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f * chartScale
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        // Text remains a fixed pixel size while chart coordinates expand with widget width.
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE8EDF5.toInt(); textSize = labelSizeSp * context.resources.displayMetrics.density * chartScale; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dotHigh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFA34A.toInt() }
        val dotLow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF65A9FF.toInt() }
        // Amplify vertical temperature differences so warming/cooling is easier to read.
        // This changes only the curve's vertical movement, never its stroke or label size.
        val rawMin = days.take(7).minOfOrNull { it.low } ?: 0
        val rawMax = days.take(7).maxOfOrNull { it.high } ?: 10
        val centerTemp = (rawMin + rawMax) / 2f
        val curveGain = PreferenceManager.getDefaultSharedPreferences(context)
            .getFloat(WeatherWidgetSettingsActivity.PREF_CURVE_GAIN, WeatherWidgetSettingsActivity.DEFAULT_CURVE_GAIN)
            .coerceIn(WeatherWidgetSettingsActivity.MIN_GAIN, WeatherWidgetSettingsActivity.MAX_GAIN)
        fun emphasized(temp: Int) = centerTemp + (temp - centerTemp) * curveGain
        // Keep the vertical plotting range fixed against the maximum supported gain.
        // Do not derive these bounds from emphasized values: doing so cancels the gain.
        val rawRange = max(1f, rawMax - rawMin.toFloat())
        val plotHalfRange = rawRange * WeatherWidgetSettingsActivity.MAX_GAIN / 2f + 1.5f
        val minTemp = centerTemp - plotHalfRange
        val maxTemp = centerTemp + plotHalfRange
        val range = max(1f, maxTemp - minTemp)
        val side = min(34f * chartScale, width * 0.12f)
        val top = min(24f * chartScale, height * 0.22f)
        val bottom = min(28f * chartScale, height * 0.25f)
        fun x(i: Int) = side + i * (width - side * 2f) / 6f
        fun y(temp: Int) = height - bottom - (emphasized(temp) - minTemp) * (height - top - bottom) / range
        val highPath = Path()
        val lowPath = Path()
        days.take(7).forEachIndexed { i, day ->
            if (i == 0) {
                highPath.moveTo(x(i), y(day.high))
                lowPath.moveTo(x(i), y(day.low))
            } else {
                highPath.lineTo(x(i), y(day.high))
                lowPath.lineTo(x(i), y(day.low))
            }
        }
        canvas.drawPath(highPath, highPaint)
        canvas.drawPath(lowPath, lowPaint)
        days.take(7).forEachIndexed { i, day ->
            canvas.drawCircle(x(i), y(day.high), 3f * chartScale, dotHigh)
            canvas.drawCircle(x(i), y(day.low), 3f * chartScale, dotLow)
            canvas.drawText("${day.high}°", x(i), max(22f * chartScale, y(day.high) - 12f * chartScale), labelPaint)
            canvas.drawText("${day.low}°", x(i), min(height - 3f * chartScale, y(day.low) + 25f * chartScale), labelPaint)
        }
        return bitmap
    }
}
