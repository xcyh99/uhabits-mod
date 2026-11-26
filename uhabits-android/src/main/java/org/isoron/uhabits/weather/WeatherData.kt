package org.isoron.uhabits.weather

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherDay(
    val date: String,
    val code: Int,
    val high: Int,
    val low: Int,
    val rain: Int,
    val wind: Int,
    val sunrise: String,
    val sunset: String
)

data class WeatherHour(
    val time: String,
    val temperature: Int,
    val code: Int,
    val humidity: Int,
    val rain: Int,
    val wind: Int
)

data class WeatherSnapshot(
    val city: String,
    val fetchedAt: Long,
    val currentTemperature: Int,
    val currentCode: Int,
    val humidity: Int,
    val wind: Int,
    val days: List<WeatherDay>,
    val hours: List<WeatherHour>
)

object WeatherRepository {
    private const val PREFS = "weather_data_v1"
    private const val CACHE_KEY = "forecast_json"
    private const val FETCHED_KEY = "fetched_at"
    private const val CACHE_TTL = 30 * 60 * 1000L

    // Initial city can be changed later from the weather page.
    private const val DEFAULT_CITY = "合肥"
    private const val DEFAULT_LAT = 31.8206
    private const val DEFAULT_LON = 117.2272

    fun load(context: Context): WeatherSnapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(CACHE_KEY, null) ?: return null
        return runCatching {
            parse(JSONObject(raw), prefs.getString("city", DEFAULT_CITY) ?: DEFAULT_CITY,
                prefs.getLong(FETCHED_KEY, 0L))
        }.getOrNull()
    }

    fun fetch(context: Context, force: Boolean = false, callback: (WeatherSnapshot?) -> Unit = {}) {
        val appContext = context.applicationContext
        Thread {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cached = load(appContext)
            if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL) {
                callback(cached)
                return@Thread
            }
            val lat = prefs.getString("latitude", null)?.toDoubleOrNull() ?: DEFAULT_LAT
            val lon = prefs.getString("longitude", null)?.toDoubleOrNull() ?: DEFAULT_LON
            val city = prefs.getString("city", DEFAULT_CITY) ?: DEFAULT_CITY
            val query = "latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,relative_humidity_2m,precipitation_probability,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,sunrise,sunset" +
                "&forecast_days=7&timezone=Asia%2FShanghai"
            val connection = (URL("https://api.open-meteo.com/v1/forecast?$query").openConnection() as HttpURLConnection)
            try {
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.requestMethod = "GET"
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val now = System.currentTimeMillis()
                prefs.edit().putString(CACHE_KEY, json.toString())
                    .putLong(FETCHED_KEY, now).putString("city", city).apply()
                callback(parse(json, city, now))
            } catch (_: Exception) {
                callback(cached)
            } finally {
                connection.disconnect()
            }
        }.start()
    }

    private fun parse(json: JSONObject, city: String, fetchedAt: Long): WeatherSnapshot {
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val hourly = json.getJSONObject("hourly")
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val rains = daily.getJSONArray("precipitation_probability_max")
        val winds = daily.getJSONArray("wind_speed_10m_max")
        val sunrises = daily.getJSONArray("sunrise")
        val sunsets = daily.getJSONArray("sunset")
        val days = (0 until dates.length()).map { i ->
            WeatherDay(
                dates.getString(i), codes.optInt(i), highs.optDouble(i).toInt(),
                lows.optDouble(i).toInt(), rains.optDouble(i).toInt(),
                winds.optDouble(i).toInt(), sunrises.optString(i), sunsets.optString(i)
            )
        }
        val times = hourly.getJSONArray("time")
        val temperatures = hourly.getJSONArray("temperature_2m")
        val hourCodes = hourly.getJSONArray("weather_code")
        val humidity = hourly.getJSONArray("relative_humidity_2m")
        val rain = hourly.getJSONArray("precipitation_probability")
        val wind = hourly.getJSONArray("wind_speed_10m")
        val hours = (0 until times.length()).map { i ->
            WeatherHour(
                times.getString(i), temperatures.optDouble(i).toInt(), hourCodes.optInt(i),
                humidity.optDouble(i).toInt(), rain.optDouble(i).toInt(), wind.optDouble(i).toInt()
            )
        }
        return WeatherSnapshot(
            city, fetchedAt, current.optDouble("temperature_2m").toInt(),
            current.optInt("weather_code"), current.optInt("relative_humidity_2m"),
            current.optDouble("wind_speed_10m").toInt(), days, hours
        )
    }

    fun weatherText(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "大致晴朗"
        2 -> "局部多云"
        3 -> "阴"
        45, 48 -> "雾"
        51, 53, 55 -> "毛毛雨"
        56, 57 -> "冻毛毛雨"
        61, 63, 65 -> "雨"
        66, 67 -> "冻雨"
        71, 73, 75, 77 -> "降雪"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95, 96, 99 -> "雷雨"
        else -> "天气"
    }

    fun weatherIcon(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤"
        3 -> "☁️"
        45, 48 -> "🌫"
        in 51..67 -> "🌦"
        in 71..77, 85, 86 -> "❄️"
        in 80..82 -> "🌧"
        in 95..99 -> "⛈"
        else -> "🌡"
    }

    fun dayLabel(date: String, index: Int): String {
        if (index == 0) return "今天"
        if (index == 1) return "明天"
        return runCatching {
            val parts = date.split("-")
            parts[1].toInt().toString() + "/" + parts[2].toInt()
        }.getOrDefault(date)
    }

    fun dayHours(snapshot: WeatherSnapshot, date: String): List<WeatherHour> =
        snapshot.hours.filter { it.time.startsWith(date) }
}
