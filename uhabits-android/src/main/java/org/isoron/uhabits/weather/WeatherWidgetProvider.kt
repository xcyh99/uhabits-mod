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
        val showChart = widthDp >= 120 && heightDp >= 130
        val showUpdated = widthDp >= 160 && heightDp >= 145
        val showBottom = widthDp >= 210 && heightDp >= 175

        val padding = if (tiny) 4 else if (showDays) 8 else 6
        views.setViewPadding(R.id.weather_root, padding, padding, padding, padding)
        views.setViewVisibility(R.id.weather_city, if (widthDp >= 180 && !tiny) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_updated, if (showUpdated) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_day_desc_row, if (showDays) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_chart, if (showChart) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_bottom_info, if (showBottom) View.VISIBLE else View.GONE)

        views.setTextViewText(R.id.weather_city, snapshot?.city ?: "天气")
        views.setTextViewText(R.id.weather_current_temp, snapshot?.let { "${it.currentTemperature}°" } ?: "--°")
        views.setTextViewText(R.id.weather_current_desc, snapshot?.let { WeatherRepository.weatherText(it.currentCode) } ?: "获取天气中")
        views.setTextViewText(
            R.id.weather_updated,
            snapshot?.let { "更新于 ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(it.fetchedAt))}" }
                ?: "等待天气数据"
        )
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
                val chartWidth = max(240, ((widthDp - padding * 2) * density).toInt())
                val chartHeight = max(64, ((heightDp - 112) * density).toInt())
                views.setImageViewBitmap(R.id.weather_chart, drawChart(snapshot.days, chartWidth, chartHeight))
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

    private fun drawChart(days: List<WeatherDay>, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val highPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFA34A.toInt(); style = Paint.Style.STROKE; strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val lowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF65A9FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        // Text remains a fixed pixel size while chart coordinates expand with widget width.
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE8EDF5.toInt(); textSize = 22f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dotHigh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFA34A.toInt() }
        val dotLow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF65A9FF.toInt() }
        val minTemp = (days.take(7).minOfOrNull { it.low } ?: 0) - 2
        val maxTemp = (days.take(7).maxOfOrNull { it.high } ?: 10) + 2
        val range = max(1, maxTemp - minTemp).toFloat()
        val side = 34f
        val top = 24f
        val bottom = 28f
        fun x(i: Int) = side + i * (width - side * 2f) / 6f
        fun y(temp: Int) = height - bottom - (temp - minTemp) * (height - top - bottom) / range
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
            canvas.drawCircle(x(i), y(day.high), 8f, dotHigh)
            canvas.drawCircle(x(i), y(day.low), 8f, dotLow)
            canvas.drawText("${day.high}°", x(i), max(22f, y(day.high) - 12f), labelPaint)
            canvas.drawText("${day.low}°", x(i), min(height - 3f, y(day.low) + 25f), labelPaint)
        }
        return bitmap
    }
}
